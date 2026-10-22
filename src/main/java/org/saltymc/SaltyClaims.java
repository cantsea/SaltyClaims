package org.saltymc;

import org.saltymc.message.MessageException;
import org.saltymc.message.Messages;
import org.saltymc.message.Notice;
import org.saltymc.message.HumanNames;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.saltymc.claim.*;
import org.saltymc.command.ClaimCommands;
import org.saltymc.command.ClaimFlagCommands;
import org.saltymc.config.*;
import org.saltymc.flag.*;
import org.saltymc.protection.ProtectionListener;
import org.saltymc.selection.SelectionManager;
import org.saltymc.storage.SqliteStore;
import org.saltymc.diagnostics.ClaimBenchmark;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;

public final class SaltyClaims extends JavaPlugin implements Listener,SelectionManager.Port,ClaimFlagCommands.Port {
    private ClaimState state = ClaimState.empty();
    private ClaimIndex index = new ClaimIndex(state);
    private ConfigurationPipeline<ConfigFiles.RuntimeConfiguration> configuration;
    private SqliteStore store;
    private ExecutorService worker;
    private ExecutorService benchmarkWorker;
    private final AtomicBoolean testing=new AtomicBoolean();
    private boolean ready, busy, writeFailed;
    private volatile boolean closing;
    private SelectionManager selections;
    private final Set<UUID> bypass = new HashSet<>();
    private final Map<UUID, Long> lastDenial = new HashMap<>();
    private final HumanNames names=new HumanNames(this::state,id->Bukkit.getOfflinePlayer(id).getName(),
            id->{ World world=Bukkit.getWorld(id); return world==null?null:world.getName(); },notice->messages().text(notice));

    @Override
    public void onEnable() {
        try {
            configuration=ConfigFiles.pipeline(getDataFolder().toPath(),message->getLogger().info(message),names::replaceIdentifiers,
                    ()->Bukkit.getWorlds().stream().map(World::getName).toList());
            applyConfiguration();
            // No database or worker starts until every editable file is validated, saved and applied.
            worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "SaltyClaims-storage"); t.setDaemon(true); return t; });
            store = new SqliteStore(getDataFolder().toPath().resolve("claims.db"));
            state = store.load(); index = new ClaimIndex(state); ready = true;
            benchmarkWorker=Executors.newSingleThreadExecutor(r->{ Thread thread=new Thread(r,"SaltyClaims-test"); thread.setDaemon(true); return thread; });
        } catch (ConfigException e) { getLogger().severe("Configuration startup rejected; maintenance active: "+e.getMessage()); }
        catch (Exception e) { getLogger().log(Level.SEVERE, "Storage startup failed. Maintenance mode is active; fix the error and restart.", e); }
        ClaimCommands commands = new ClaimCommands(this);
        selections = new SelectionManager(this,commands::commitSelection);
        commands.registerPermissions();
        for (String name : List.of("claim", "claima", "unclaim", "claimflag", "claimflags", "trust", "untrust")) {
            Objects.requireNonNull(getCommand(name)).setExecutor(commands);
            Objects.requireNonNull(getCommand(name)).setTabCompleter(commands);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(commands, this);
        Bukkit.getPluginManager().registerEvents(selections, this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getScheduler().runTaskTimer(this, () -> { selections.expire(); commands.expire(); }, 200, 200);
        Bukkit.getScheduler().runTaskTimer(this,selections::tickPreviews,10,10);
        getLogger().info(ready ? "Loaded " + state.claims().size() + " claims." : "MAINTENANCE: claim data unavailable.");
    }

    @Override
    public void onDisable() {
        closing = true; ready = false;
        if(selections!=null) selections.clear();
        if(benchmarkWorker!=null) benchmarkWorker.shutdownNow();
        if (worker == null) return;
        worker.shutdown();
        try {
            if (worker.awaitTermination(15, TimeUnit.SECONDS)) { if (store != null) store.close(); }
            else getLogger().severe("Storage did not stop within 15 seconds. Check the database before restarting; accepted work may remain.");
        } catch (Exception e) { getLogger().log(Level.SEVERE, "Unable to close storage cleanly.", e); if (e instanceof InterruptedException) Thread.currentThread().interrupt(); }
    }
    @EventHandler public void login(PlayerLoginEvent event) {
        if (!ready && !event.getPlayer().hasPermission("saltyclaims.admin.recovery"))
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, String.join("\n",messages().lines(Notice.of("protection.kick"))));
    }
    @EventHandler public void worldLoaded(WorldLoadEvent event) {
        if(!ready()||settings().worlds().containsKey(event.getWorld().getName())) return;
        try { applyConfiguration(); }
        catch(Exception e) { getLogger().severe("Could not add world to worlds.yml; new world uses default wilderness permissions with claiming disabled: "+e.getMessage()); }
    }
    public ClaimState state() { return state; }
    public Settings settings() { return configuration.current().settings(); }
    public Messages messages() { return configuration==null||configuration.current()==null?Messages.defaults():configuration.current().messages(); }
    public String playerName(UUID id) { return names.player(id); }
    public String worldName(UUID id) { return names.world(id); }
    public String claimName(Claim claim) { return names.claim(claim); }
    public SelectionManager selections() { return selections; }
    public void showBounds(Player player,Bounds bounds,World world,boolean child) { selections.preview(player,bounds,world,child); }
    public boolean ready() { return ready && !closing; }
    public Claim at(Location l) { return index.at(l.getWorld().getUID(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); }
    public boolean bypass(Player p) { return bypass.contains(p.getUniqueId()) && p.hasPermission("saltyclaims.command.claima.bypass"); }
    public void setBypass(Player p, boolean enabled) {
        if (enabled) bypass.add(p.getUniqueId()); else bypass.remove(p.getUniqueId());
        getLogger().info(p.getName() + " bypass=" + enabled);
    }
    public void forget(UUID player) { bypass.remove(player); lastDenial.remove(player); }
    public Policy.Value value(Claim c, Flag flag, World world) {
        return Policy.resolve(state, c, flag, world.getName(), settings().claimFlags(), settings().worlds());
    }
    public Policy.Decision decision(Player p, Location location, Flag flag) {
        if (!ready()) return new Policy.Decision(false, messages().text("protection.maintenance"));
        Claim c = at(location); Policy.Value v = value(c, flag, location.getWorld());
        return Policy.check(state, c, p == null ? null : p.getUniqueId(), flag, v, p != null && bypass(p));
    }
    public boolean allow(Player p, Location l, Flag flag) {
        Policy.Decision d = decision(p, l, flag);
        if (!d.allowed() && p != null) deny(p,messages().text("protection.action-denied"));
        return d.allowed();
    }
    public void deny(Player p, String reason) {
        long now = System.currentTimeMillis();
        if (now - lastDenial.getOrDefault(p.getUniqueId(), 0L) >= 1500) { lastDenial.put(p.getUniqueId(), now); messages().send(p,"protection.denied","reason",reason); }
    }
    public void audit(Player p, Location l, String action) {
        if (bypass(p) && at(l) != null) getLogger().info("BYPASS " + p.getUniqueId() + " " + action + " claim=" + at(l).id());
    }
    public void mutate(CommandSender sender, Function<ClaimState, ClaimState> operation, Notice success) {
        mutate(sender,operation,success,()->{});
    }
    public void resetClaims(CommandSender sender,Map<UUID,Claim> expected) {
        mutate(sender,current->{
            if(!current.claims().equals(expected)) throw new MessageException("errors.reset-changed");
            return ClaimService.resetAll(current);
        },Notice.of("success.reset-all","count",expected.size()),selections::clear);
    }
    public void mutate(CommandSender sender, Function<ClaimState, ClaimState> operation, Notice success,Runnable committed) {
        mutate(sender,operation,success,committed,()->{});
    }
    public void mutate(CommandSender sender, Function<ClaimState, ClaimState> operation, Notice success,Runnable committed,Runnable failed) {
        if (!ready() || writeFailed) throw new MessageException("errors.storage-unavailable");
        if (busy) throw new MessageException("errors.storage-busy");
        ClaimState before = state, after = operation.apply(before);
        ClaimIndex nextIndex = new ClaimIndex(after);
        busy = true;
        worker.execute(() -> {
            Exception failure = null;
            try { store.save(before, after); } catch (Exception e) { failure = e; }
            Exception result = failure;
            if (!closing) Bukkit.getScheduler().runTask(this, () -> {
                busy = false;
                if (result == null) {
                    state = after; index = nextIndex;
                    committed.run();
                    messages().send(sender,success);
                    getLogger().info(sender.getName() + ": " + success.key()+" "+success.values());
                } else {
                    writeFailed = true;
                    failed.run();
                    messages().send(sender,"errors.save-failed");
                    getLogger().log(Level.SEVERE, "Claim transaction failed", result);
                }
            });
        });
    }
    public void reloadSettings() throws Exception {
        if (!ready()) throw new MessageException("errors.restart-required");
        if (busy) throw new MessageException("errors.reload-busy");
        applyConfiguration();
    }
    /** All Bukkit/server information is captured here, on the server thread. The worker receives strings only. */
    public void runTests(CommandSender sender) {
        if(!ready()||benchmarkWorker==null) throw new MessageException("errors.maintenance");
        if(!testing.compareAndSet(false,true)) throw new MessageException("errors.test-busy");
        try {
            Runtime runtime=Runtime.getRuntime();
            List<String> environment=List.of("SaltyClaims synthetic performance/correctness test",
                    "UTC start: "+Instant.now(),"Plugin: "+getPluginMeta().getVersion(),"Server: "+Bukkit.getVersion(),
                    "Java: "+System.getProperty("java.version")+" / "+System.getProperty("java.vendor"),
                    "OS: "+System.getProperty("os.name")+" / "+System.getProperty("os.arch"),
                    "Available processors: "+runtime.availableProcessors(),"Heap maximum bytes: "+runtime.maxMemory(),
                    "Online players at start: "+Bukkit.getOnlinePlayers().size(),"Live claims at start (count only): "+state.claims().size(),
                    "TPS at start (server snapshot): "+Arrays.toString(Bukkit.getTPS()),"Average tick ms at start (server snapshot): "+Bukkit.getAverageTickTime());
            String filename="claims-test-"+DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())+"-"+UUID.randomUUID().toString().substring(0,8)+".log";
            Path directory=getDataFolder().toPath().resolve("test-reports"),report=directory.resolve(filename);
            messages().send(sender,"commands.test-started");
            benchmarkWorker.execute(()->{
                long started=System.nanoTime(),heapBefore=runtime.totalMemory()-runtime.freeMemory(); boolean success=false;
                try {
                    Files.createDirectories(directory);
                    try(BufferedWriter writer=Files.newBufferedWriter(report,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                        java.util.function.Consumer<String> line=text->{
                            try { writer.write(text); writer.newLine(); writer.flush(); }
                            catch(IOException e) { throw new UncheckedIOException(e); }
                        };
                        environment.forEach(line);
                        line.accept("Heap used at start bytes (whole JVM, no forced GC): "+heapBefore);
                        try {
                            ClaimBenchmark.run(directory,ClaimBenchmark.Options.standard(),line);
                            line.accept("RESULT: PASS");
                        } catch(Exception e) {
                            line.accept("RESULT: FAILED / "+e.getClass().getSimpleName()+": "+e.getMessage()); throw e;
                        } finally {
                            line.accept("Elapsed seconds: "+(System.nanoTime()-started)/1000000000.0);
                            line.accept("Heap used at end bytes (whole JVM; not retained plugin memory): "+(runtime.totalMemory()-runtime.freeMemory()));
                            line.accept("Interpretation: repeat runs on the same hardware and JVM with similar server activity. First runs include JVM/JIT warmup noise.");
                            line.accept("This test does not simulate real players, Paper event dispatch, particle rendering or Bedrock interaction. TPS/tick values above are start snapshots, not measured test impact.");
                        }
                    }
                    success=true;
                } catch(Exception e) { getLogger().log(Level.WARNING,"Claim test failed; report: "+report,e); }
                finally { testing.set(false); }
                boolean passed=success,hasReport=Files.isRegularFile(report);
                if(!closing) Bukkit.getScheduler().runTask(this,()->{
                    messages().send(sender,passed?"success.test":hasReport?"errors.test-failed":"errors.test-report","file","test-reports/"+filename);
                    getLogger().info("Claim test "+(passed?"passed":"failed")+"; report: "+report);
                });
            });
        } catch(RuntimeException e) { testing.set(false); throw e; }
    }
    private void applyConfiguration() {
        configuration.update(candidate->candidate.settings().validateLiveTools(),candidate->{ if(selections!=null) selections.clear(); });
    }
    public void backup(CommandSender sender) {
        if (!ready() || busy || writeFailed) throw new MessageException("errors.backup-unavailable");
        busy = true;
        worker.execute(() -> {
            Notice message;
            try {
                Path directory = getDataFolder().toPath().resolve("backups"); Files.createDirectories(directory);
                Path destination = directory.resolve("claims-" + System.currentTimeMillis() + ".db");
                store.backup(destination); message = Notice.of("success.backup","file",destination.getFileName());
            } catch (Exception e) { message = Notice.of("errors.backup-failed"); getLogger().log(Level.SEVERE,"Backup failed",e); }
            Notice result = message;
            if (!closing) Bukkit.getScheduler().runTask(this, () -> { busy = false; messages().send(sender,result); });
        });
    }
}
