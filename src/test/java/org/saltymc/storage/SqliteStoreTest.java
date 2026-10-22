package org.saltymc.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saltymc.claim.*;
import org.saltymc.flag.Flag;
import org.saltymc.trust.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class SqliteStoreTest {
    @TempDir Path directory;
    @Test void booleanFlagOverridesPersistAlongsideLegacyFlagsAcrossRestart() throws Exception {
        Path db=directory.resolve("claims.db"); Claim root=root(0,9);
        root=root.change(root.bounds(),true,Map.of(),Map.of(Flag.PVP,"true",Flag.BLOCK_BREAK,"false",Flag.CONTAINER_USE,"container"));
        ClaimState expected=state(root);
        try(SqliteStore store=new SqliteStore(db)) { store.save(ClaimState.empty(),expected); }
        try(SqliteStore reopened=new SqliteStore(db)) { assertEquals(expected,reopened.load()); }
    }
    @Test void roundTripRootsChildrenTrustFlagsAndBalances() throws Exception {
        Path db=directory.resolve("claims.db"); Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(Subject.PUBLIC,Trust.ACCESS,new Subject(FRIEND),Trust.MANAGE),Map.of(Flag.PVP,"allow"));
        Claim child=child(root,-64,30); child=child.change(child.bounds(),false,Map.of(new Subject(STRANGER),Trust.CONTAINER),Map.of(Flag.BLOCK_BREAK,"owner"));
        ClaimState expected=new ClaimState(state(root,child).claims(),Map.of(OWNER,new ClaimState.Account(25,100)));
        try(SqliteStore store=new SqliteStore(db)) { store.save(ClaimState.empty(),expected); assertEquals(expected,store.load()); }
        try(SqliteStore reopened=new SqliteStore(db)) { assertEquals(expected,reopened.load()); }
    }
    @Test void failedTransactionRollsBackEarlierUpdates() throws Exception {
        try(SqliteStore store=new SqliteStore(directory.resolve("claims.db"))) {
            Claim root=root(0,9), child=child(root,0,30); ClaimState before=state(root,child); store.save(ClaimState.empty(),before);
            Claim updated=root.change(root.bounds(),true,Map.of(),Map.of(Flag.PVP,"allow")); Claim invalid=child(child,1,2);
            ClaimState after=state(updated,child,invalid);
            assertThrows(SQLException.class,()->store.save(before,after)); assertEquals(before,store.load());
        }
    }
    @Test void deletingRootCascadesAndBackupIsRestorable() throws Exception {
        Path db=directory.resolve("claims.db"), backup=directory.resolve("backup.db"); Claim root=root(0,9), child=child(root,0,30); ClaimState before=state(root,child);
        try(SqliteStore store=new SqliteStore(db)) {
            store.save(ClaimState.empty(),before); store.backup(backup);
            store.save(before,ClaimService.delete(before,root.id())); assertTrue(store.load().claims().isEmpty());
        }
        try(SqliteStore restored=new SqliteStore(backup)) { assertEquals(before,restored.load()); }
    }
    @Test void duplicateInstanceCannotOpenSameStore() throws Exception {
        Path db=directory.resolve("claims.db");
        try(SqliteStore first=new SqliteStore(db)) { assertThrows(Exception.class,()->new SqliteStore(db)); }
        assertDoesNotThrow(()->{try(SqliteStore ignored=new SqliteStore(db)) { }});
    }
    @Test void resetAllPersistsAcrossRestartAndRemovesTrustAndFlagsButKeepsAccounts() throws Exception {
        Path database=directory.resolve("claims.db"); Claim root=root(0,9),child=child(root,1,5),other=root(20,29);
        root=root.change(root.bounds(),true,Map.of(Subject.PUBLIC,Trust.ACCESS),Map.of(Flag.PVP,"allow"));
        ClaimState before=new ClaimState(state(root,child,other).claims(),Map.of(OWNER,new ClaimState.Account(42,100)));
        ClaimState after=ClaimService.resetAll(before);
        try(SqliteStore store=new SqliteStore(database)) {
            store.save(ClaimState.empty(),before); store.save(before,after); assertEquals(after,store.load());
        }
        try(SqliteStore reopened=new SqliteStore(database)) { assertEquals(after,reopened.load()); }
        try(Connection connection=DriverManager.getConnection("jdbc:sqlite:"+database); Statement statement=connection.createStatement()) {
            for(String table:List.of("claims","claim_trust","claim_flags")) try(ResultSet rows=statement.executeQuery("SELECT count(*) FROM "+table)) {
                assertTrue(rows.next()); assertEquals(0,rows.getInt(1),table);
            }
        }
    }
    @Test void unclaimAllPersistsAcrossWorldsWithoutDeletingAnotherOwnersTreeOrBalances() throws Exception {
        Path database=directory.resolve("claims.db"); Claim first=root(0,9),child=child(first,1,5);
        Claim second=new Claim(UUID.randomUUID(),UUID.randomUUID(),OWNER,null,new Bounds(0,9,0,9,null,null),true,1,0,Map.of(),Map.of());
        Claim other=new Claim(UUID.randomUUID(),WORLD,STRANGER,null,new Bounds(20,29,20,29,null,null),true,1,0,Map.of(Subject.PUBLIC,Trust.ACCESS),Map.of(Flag.PVP,"allow"));
        Claim otherChild=new Claim(UUID.randomUUID(),WORLD,null,other.id(),new Bounds(21,24,21,24,0,10),true,1,0,Map.of(),Map.of());
        ClaimState before=new ClaimState(state(first,child,second,other,otherChild).claims(),Map.of(OWNER,new ClaimState.Account(1,2)));
        ClaimState after=ClaimService.unclaimAll(before,OWNER);
        try(SqliteStore store=new SqliteStore(database)) { store.save(ClaimState.empty(),before); store.save(before,after); }
        try(SqliteStore store=new SqliteStore(database)) {
            assertEquals(after,store.load()); assertEquals(Set.of(other.id(),otherChild.id()),store.load().claims().keySet());
            assertEquals(before.accounts(),store.load().accounts());
        }
    }
    @Test void unknownSchemaFailsWithoutReinitializing() throws Exception {
        Path db=directory.resolve("claims.db");
        try(SqliteStore ignored=new SqliteStore(db)) { }
        try(Connection connection=DriverManager.getConnection("jdbc:sqlite:"+db);Statement s=connection.createStatement()) { s.execute("PRAGMA user_version=999"); }
        assertThrows(SQLException.class,()->new SqliteStore(db));
    }
    @Test void loadingInvalidFlagsFailsRatherThanDroppingProtection() throws Exception {
        Path db=directory.resolve("claims.db"); Claim root=root(0,9);
        try(SqliteStore store=new SqliteStore(db)) { store.save(ClaimState.empty(),state(root)); }
        try(Connection connection=DriverManager.getConnection("jdbc:sqlite:"+db);Statement s=connection.createStatement()) { s.execute("INSERT INTO claim_flags VALUES('"+root.id()+"','unknown','allow')"); }
        try(SqliteStore store=new SqliteStore(db)) { assertThrows(IllegalArgumentException.class,store::load); }
    }
}
