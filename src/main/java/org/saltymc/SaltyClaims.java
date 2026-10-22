package org.saltymc;

import org.saltymc.message.MessageException;
import org.saltymc.message.Messages;
import org.saltymc.message.Notice;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.saltymc.claim.*;
import org.saltymc.command.ClaimCommands;
import org.saltymc.config.*;
import org.saltymc.flag.*;
import org.saltymc.protection.ProtectionListener;
import org.saltymc.selection.SelectionManager;
import org.saltymc.storage.SqliteStore;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.Level;

public final class SaltyClaims extends JavaPlugin implements Listener {
    private ClaimState state = ClaimState.empty();
    private ClaimIndex index = new ClaimIndex(state);
    private ConfigurationPipeline<ConfigFiles.RuntimeConfiguration> configuration;
    private SqliteStore store;
    private ExecutorService worker;
    private boolean ready, busy, writeFailed;
    private volatile boolean closing;
    private SelectionManager selections;
    private final Set<UUID> bypass = new HashSet<>();
    private final Map<UUID, Long> lastDenial = new HashMap<>();

    @Override
    public void onEnable() {
        try {
            configuration=ConfigFiles.pipeline(getDataFolder().toPath(),message->getLogger().info(message));
            applyConfiguration();
            // No database or worker starts until every editable file is validated, saved and applied.
            worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "SaltyClaims-storage"); t.setDaemon(true); return t; });
            store = new SqliteStore(getDataFolder().toPath().resolve("claims.db"));
            state = store.load(); index = new ClaimIndex(state); ready = true;
        } catch (ConfigException e) { getLogger().severe("Configuration startup rejected; maintenance active: "+e.getMessage()); }
        catch (Exception e) { getLogger().log(Level.SEVERE, "Storage startup failed. Maintenance mode is active; fix the error and restart.", e); }
        selections = new SelectionManager(this);
        ClaimCommands commands = new ClaimCommands(this);
        commands.registerPermissions();
        for (String name : List.of("claim", "claima")) {
            Objects.requireNonNull(getCommand(name)).setExecutor(commands);
            Objects.requireNonNull(getCommand(name)).setTabCompleter(commands);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(commands, this);
        Bukkit.getPluginManager().registerEvents(selections, this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getScheduler().runTaskTimer(this, () -> { selections.expire(); commands.expire(); }, 200, 200);
        getLogger().info(ready ? "Loaded " + state.claims().size() + " claims." : "MAINTENANCE: claim data unavailable.");
    }

    @Override
    public void onDisable() {
        closing = true; ready = false;
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
    public ClaimState state() { return state; }
    public Settings settings() { return configuration.current().settings(); }
    public Messages messages() { return configuration==null||configuration.current()==null?Messages.defaults():configuration.current().messages(); }
    public SelectionManager selections() { return selections; }
    public boolean ready() { return ready && !closing; }
    public Claim at(Location l) { return index.at(l.getWorld().getUID(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); }
    public boolean bypass(Player p) { return bypass.contains(p.getUniqueId()) && p.hasPermission("saltyclaims.command.claima.bypass"); }
    public void setBypass(Player p, boolean enabled) {
        if (enabled) bypass.add(p.getUniqueId()); else bypass.remove(p.getUniqueId());
        getLogger().info(p.getName() + " bypass=" + enabled);
    }
    public void forget(UUID player) { bypass.remove(player); lastDenial.remove(player); }
    public Policy.Value value(Claim c, Flag flag, World world) {
        return Policy.resolve(state, c, flag, world.getName(), settings().defaults(), settings().enforced(), settings().worlds());
    }
    public Policy.Decision decision(Player p, Location location, Flag flag) {
        if (!ready()) return new Policy.Decision(false, messages().text("protection.maintenance"));
        Claim c = at(location); Policy.Value v = value(c, flag, location.getWorld());
        return Policy.check(state, c, p == null ? null : p.getUniqueId(), flag, v, p != null && bypass(p));
    }
    public boolean allow(Player p, Location l, Flag flag) {
        Policy.Decision d = decision(p, l, flag);
        if (!d.allowed() && p != null) deny(p, d.reason());
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
                    messages().send(sender,success);
                    getLogger().info(sender.getName() + ": " + success.key()+" "+success.values());
                } else {
                    writeFailed = true;
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
