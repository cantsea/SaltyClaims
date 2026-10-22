package org.saltymc.storage;

import org.saltymc.claim.*;
import org.saltymc.flag.Flag;
import org.saltymc.trust.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** One connection, used by startup then a single writer. Never called by protection events. */
public final class SqliteStore implements AutoCloseable {
    private final Connection db;
    private final FileChannel lockChannel;
    private final FileLock lock;
    public SqliteStore(Path path) throws Exception {
        Files.createDirectories(path.toAbsolutePath().getParent());
        boolean existed=Files.exists(path)&&Files.size(path)>0;
        lockChannel = FileChannel.open(path.resolveSibling("claims.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (Exception e) { lockChannel.close(); throw e; }
        if (acquired == null) { lockChannel.close(); throw new IllegalStateException("Another SaltyClaims instance has this database open."); }
        lock = acquired;
        Connection opened = null;
        try {
            Class.forName("org.sqlite.JDBC");
            opened = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
            db = opened;
            try (Statement s = db.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA synchronous=FULL");
                s.execute("PRAGMA busy_timeout=5000"); s.execute("PRAGMA journal_mode=DELETE");
                int version;
                try (ResultSet r = s.executeQuery("PRAGMA user_version")) { if(!r.next()) throw new SQLException("Cannot read schema version."); version = r.getInt(1); }
                if (version > 1) throw new SQLException("Database was created by a newer plugin schema.");
                if (version == 0) {
                    // Snapshot any existing database before its first schema migration.
                    if (existed) backup(path.resolveSibling("claims-before-v1-" + System.currentTimeMillis() + ".db"));
                    migrate();
                }
            }
        } catch (Exception e) {
            if (opened != null) opened.close(); lock.release(); lockChannel.close(); throw e;
        }
    }
    private void migrate() throws SQLException {
        db.setAutoCommit(false);
        try (Statement s = db.createStatement()) {
            s.execute("CREATE TABLE claims (id TEXT PRIMARY KEY, world TEXT NOT NULL, owner TEXT, parent TEXT REFERENCES claims(id) ON DELETE CASCADE, min_x INTEGER NOT NULL, max_x INTEGER NOT NULL, min_z INTEGER NOT NULL, max_z INTEGER NOT NULL, min_y INTEGER, max_y INTEGER, inherit_trust INTEGER NOT NULL, created INTEGER NOT NULL, revision INTEGER NOT NULL, CHECK(min_x<=max_x AND min_z<=max_z), CHECK((min_y IS NULL AND max_y IS NULL) OR (min_y IS NOT NULL AND max_y IS NOT NULL AND min_y<=max_y)), CHECK((parent IS NULL AND owner IS NOT NULL AND min_y IS NULL) OR (parent IS NOT NULL AND owner IS NULL)), CHECK(parent IS NULL OR parent<>id))");
            s.execute("CREATE TABLE claim_trust (claim TEXT NOT NULL REFERENCES claims(id) ON DELETE CASCADE, subject_type TEXT NOT NULL CHECK(subject_type IN ('PLAYER','PUBLIC')), subject TEXT NOT NULL, level TEXT NOT NULL CHECK(level IN ('access','container','build','manage')), PRIMARY KEY(claim, subject_type, subject), CHECK(subject_type<>'PUBLIC' OR (subject='' AND level<>'manage')))");
            s.execute("CREATE TABLE claim_flags (claim TEXT NOT NULL REFERENCES claims(id) ON DELETE CASCADE, flag TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(claim,flag))");
            s.execute("CREATE TABLE player_data (player TEXT PRIMARY KEY, earned INTEGER NOT NULL CHECK(earned>=0), bonus INTEGER NOT NULL CHECK(bonus>=0))");
            s.execute("CREATE TABLE schema_migrations (version INTEGER PRIMARY KEY, applied INTEGER NOT NULL)");
            s.execute("CREATE INDEX claims_parent ON claims(parent)");
            s.execute("CREATE TRIGGER claims_parent_insert BEFORE INSERT ON claims WHEN NEW.parent IS NOT NULL BEGIN SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM claims p WHERE p.id=NEW.parent AND p.parent IS NULL AND p.world=NEW.world AND p.min_x<=NEW.min_x AND p.max_x>=NEW.max_x AND p.min_z<=NEW.min_z AND p.max_z>=NEW.max_z) THEN RAISE(ABORT,'Invalid root parent or containment') END; END");
            s.execute("CREATE TRIGGER claims_parent_update BEFORE UPDATE ON claims BEGIN SELECT CASE WHEN NEW.parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM claims p WHERE p.id=NEW.parent AND p.parent IS NULL AND p.world=NEW.world AND p.min_x<=NEW.min_x AND p.max_x>=NEW.max_x AND p.min_z<=NEW.min_z AND p.max_z>=NEW.max_z) THEN RAISE(ABORT,'Invalid root parent or containment') END; SELECT CASE WHEN EXISTS(SELECT 1 FROM claims c WHERE c.parent=NEW.id AND (NEW.parent IS NOT NULL OR c.world<>NEW.world OR c.min_x<NEW.min_x OR c.max_x>NEW.max_x OR c.min_z<NEW.min_z OR c.max_z>NEW.max_z)) THEN RAISE(ABORT,'Parent change excludes children') END; END");
            s.execute("INSERT INTO schema_migrations VALUES(1," + System.currentTimeMillis() + ")");
            s.execute("PRAGMA user_version=1"); db.commit();
        } catch (SQLException e) { db.rollback(); throw e; }
        finally { db.setAutoCommit(true); }
    }
    public ClaimState load() throws SQLException {
        Map<UUID, Map<Subject, Trust>> trusts = new HashMap<>();
        Map<UUID, Map<Flag, String>> flags = new HashMap<>();
        Map<UUID, Claim> claims = new HashMap<>();
        Map<UUID, ClaimState.Account> accounts = new HashMap<>();
        try (Statement s = db.createStatement()) {
            try (ResultSet r = s.executeQuery("PRAGMA quick_check")) {
                if (!r.next() || !r.getString(1).equals("ok")) throw new SQLException("SQLite integrity check failed.");
            }
            try (ResultSet r = s.executeQuery("PRAGMA foreign_key_check")) { if (r.next()) throw new SQLException("Orphaned claim records."); }
            try (ResultSet r = s.executeQuery("SELECT * FROM claim_trust")) { while (r.next()) {
                Subject subject = r.getString("subject_type").equals("PUBLIC") ? Subject.PUBLIC : Subject.parse(r.getString("subject"));
                trusts.computeIfAbsent(UUID.fromString(r.getString("claim")), ignored -> new HashMap<>()).put(subject, Trust.parse(r.getString("level")));
            } }
            try (ResultSet r = s.executeQuery("SELECT * FROM claim_flags")) { while (r.next()) {
                Flag flag = Flag.parse(r.getString("flag"));
                flags.computeIfAbsent(UUID.fromString(r.getString("claim")), ignored -> new HashMap<>()).put(flag, flag.validate(r.getString("value")));
            } }
            try (ResultSet r = s.executeQuery("SELECT * FROM claims")) { while (r.next()) {
                UUID id = UUID.fromString(r.getString("id"));
                Integer minY = (Integer) r.getObject("min_y"), maxY = (Integer) r.getObject("max_y");
                String parent = r.getString("parent"), owner = r.getString("owner");
                claims.put(id, new Claim(id, UUID.fromString(r.getString("world")), owner == null ? null : UUID.fromString(owner),
                        parent == null ? null : UUID.fromString(parent), new Bounds(r.getInt("min_x"), r.getInt("max_x"), r.getInt("min_z"), r.getInt("max_z"), minY, maxY),
                        r.getBoolean("inherit_trust"), r.getLong("created"), r.getLong("revision"), trusts.getOrDefault(id, Map.of()), flags.getOrDefault(id, Map.of())));
            } }
            try (ResultSet r = s.executeQuery("SELECT * FROM player_data")) { while (r.next())
                accounts.put(UUID.fromString(r.getString("player")), new ClaimState.Account(r.getLong("earned"), r.getLong("bonus"))); }
        }
        ClaimState result = new ClaimState(claims, accounts); result.validate(); return result;
    }
    public void save(ClaimState before, ClaimState after) throws SQLException {
        db.setAutoCommit(false);
        try {
            try (PreparedStatement s = db.prepareStatement("DELETE FROM claims WHERE id=?")) {
                for (UUID id : before.claims().keySet()) if (!after.claims().containsKey(id)) { s.setString(1, id.toString()); s.executeUpdate(); }
            }
            List<Claim> changed = after.claims().values().stream().filter(c -> !c.equals(before.claims().get(c.id())))
                    .sorted(Comparator.comparing(c -> !c.root())).toList();
            for (Claim c : changed) {
                Claim old=before.claims().get(c.id());
                if(old!=null&&(!old.world().equals(c.world())||!Objects.equals(old.owner(),c.owner())||!Objects.equals(old.parent(),c.parent())||old.created()!=c.created()))
                    throw new SQLException("Claim identity, world, ownership, and parent are immutable.");
                writeClaim(c);
            }
            try (PreparedStatement s = db.prepareStatement("INSERT INTO player_data VALUES(?,?,?) ON CONFLICT(player) DO UPDATE SET earned=excluded.earned, bonus=excluded.bonus")) {
                for (var entry : after.accounts().entrySet()) if (!entry.getValue().equals(before.accounts().get(entry.getKey()))) {
                    s.setString(1, entry.getKey().toString()); s.setLong(2, entry.getValue().earned()); s.setLong(3, entry.getValue().bonus()); s.executeUpdate();
                }
            }
            db.commit();
        } catch (SQLException e) { db.rollback(); throw e; }
        finally { db.setAutoCommit(true); }
    }
    private void writeClaim(Claim c) throws SQLException {
        try (PreparedStatement s = db.prepareStatement("INSERT INTO claims VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET min_x=excluded.min_x,max_x=excluded.max_x,min_z=excluded.min_z,max_z=excluded.max_z,min_y=excluded.min_y,max_y=excluded.max_y,inherit_trust=excluded.inherit_trust,revision=excluded.revision")) {
            s.setString(1,c.id().toString()); s.setString(2,c.world().toString()); s.setString(3,c.owner()==null ? null : c.owner().toString());
            s.setString(4,c.parent()==null ? null : c.parent().toString()); s.setInt(5,c.bounds().minX()); s.setInt(6,c.bounds().maxX());
            s.setInt(7,c.bounds().minZ()); s.setInt(8,c.bounds().maxZ()); s.setObject(9,c.bounds().minY()); s.setObject(10,c.bounds().maxY());
            s.setBoolean(11,c.inheritTrust()); s.setLong(12,c.created()); s.setLong(13,c.revision()); s.executeUpdate();
        }
        for (String table : List.of("claim_trust", "claim_flags")) try (PreparedStatement s = db.prepareStatement("DELETE FROM " + table + " WHERE claim=?")) {
            s.setString(1,c.id().toString()); s.executeUpdate();
        }
        try (PreparedStatement s = db.prepareStatement("INSERT INTO claim_trust VALUES(?,?,?,?)")) {
            for (var entry : c.trust().entrySet()) {
                s.setString(1,c.id().toString()); s.setString(2,entry.getKey().isPublic()?"PUBLIC":"PLAYER");
                s.setString(3,entry.getKey().isPublic()?"":entry.getKey().key()); s.setString(4,entry.getValue().key()); s.executeUpdate();
            }
        }
        try (PreparedStatement s = db.prepareStatement("INSERT INTO claim_flags VALUES(?,?,?)")) {
            for (var entry : c.flags().entrySet()) { s.setString(1,c.id().toString()); s.setString(2,entry.getKey().key()); s.setString(3,entry.getValue()); s.executeUpdate(); }
        }
    }
    public void backup(Path destination) throws SQLException {
        try (Statement s = db.createStatement()) { s.execute("VACUUM INTO '" + destination.toAbsolutePath().toString().replace("'", "''") + "'"); }
    }
    @Override public void close() throws Exception { try { db.close(); } finally { lock.release(); lockChannel.close(); } }
}
