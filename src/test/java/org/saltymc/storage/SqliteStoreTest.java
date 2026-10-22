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
