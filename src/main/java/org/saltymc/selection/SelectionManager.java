package org.saltymc.selection;

import org.saltymc.message.MessageException;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.saltymc.SaltyClaims;
import org.saltymc.claim.*;
import java.util.*;

/** Transient selection state, with bounded particle previews and no world edits. */
public final class SelectionManager implements Listener {
    public static final class Session {
        public Location first, second;
        public String mode = "basic";
        public UUID parent, resize;
        public long revision, expires;
    }
    private final SaltyClaims plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    public SelectionManager(SaltyClaims plugin) { this.plugin = plugin; }
    public Session session(Player p) {
        Session old = sessions.get(p.getUniqueId());
        if (old != null && old.expires < System.currentTimeMillis()) sessions.remove(p.getUniqueId());
        Session s = sessions.computeIfAbsent(p.getUniqueId(), ignored -> new Session());
        s.expires = System.currentTimeMillis() + plugin.settings().sessionSeconds() * 1000L;
        return s;
    }
    public void clear() { sessions.clear(); }
    public void cancel(Player p) { sessions.remove(p.getUniqueId()); }
    public void expire() { sessions.values().removeIf(s -> s.expires < System.currentTimeMillis()); }
    public Bounds bounds(Player p) {
        Session s = session(p);
        if (s.first == null || s.second == null) throw new MessageException("errors.selection-incomplete");
        if (!s.first.getWorld().equals(s.second.getWorld())) throw new MessageException("errors.selection-same-world");
        boolean full = !s.mode.equals("3d");
        return new Bounds(Math.min(s.first.getBlockX(), s.second.getBlockX()), Math.max(s.first.getBlockX(), s.second.getBlockX()),
                Math.min(s.first.getBlockZ(), s.second.getBlockZ()), Math.max(s.first.getBlockZ(), s.second.getBlockZ()),
                full ? null : Math.min(s.first.getBlockY(), s.second.getBlockY()), full ? null : Math.max(s.first.getBlockY(), s.second.getBlockY()));
    }
    public void set(Player p, boolean first, Location point) {
        Session s = session(p);
        if (first) { s.first = point.clone(); s.second = null; } else s.second = point.clone();
        plugin.messages().send(p,first?"selection.first-corner":"selection.second-corner","x",point.getBlockX(),"y",point.getBlockY(),"z",point.getBlockZ());
        if (s.first != null && s.second != null) {
            Bounds b = bounds(p); preview(p, b, s.first.getWorld());
            plugin.messages().send(p,"selection.preview","area",b.area(),"command",s.resize != null ? "/claim resize" : s.mode.equals("basic") ? "/claim create" : "/claim child create");
        }
    }
    public void preview(Player p, Bounds b, World world) {
        if (!p.getWorld().equals(world)) return;
        double y = b.fullHeight() ? p.getLocation().getY() + 0.2 : b.minY() + 0.2;
        List<Double> heights = b.fullHeight() ? List.of(y) : List.of(y, b.maxY() + 1.0);
        for (double h : heights) for (int i = 0; i <= 32; i++) {
            double t = i / 32.0;
            particle(p, world, b.minX() + (b.maxX() + 1.0 - b.minX()) * t, h, b.minZ());
            particle(p, world, b.minX() + (b.maxX() + 1.0 - b.minX()) * t, h, b.maxZ() + 1.0);
            particle(p, world, b.minX(), h, b.minZ() + (b.maxZ() + 1.0 - b.minZ()) * t);
            particle(p, world, b.maxX() + 1.0, h, b.minZ() + (b.maxZ() + 1.0 - b.minZ()) * t);
        }
    }
    private void particle(Player p, World world, double x, double y, double z) {
        Location point = new Location(world,x,y,z);
        if (point.distanceSquared(p.getLocation()) < 128 * 128) p.spawnParticle(Particle.DUST, point, 1, new Particle.DustOptions(Color.YELLOW, 1.2f));
    }
    @EventHandler(priority=EventPriority.LOWEST) public void interact(PlayerInteractEvent e) {
        if (!plugin.ready() || e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if(e.useInteractedBlock()==Event.Result.DENY&&e.useItemInHand()==Event.Result.DENY) return;
        Player p = e.getPlayer(); Material held = p.getInventory().getItemInMainHand().getType();
        if (held == plugin.settings().inspectionTool()) {
            if (!p.hasPermission("saltyclaims.command.claim.info")) return;
            e.setCancelled(true); Claim c = plugin.at(e.getClickedBlock().getLocation());
            if (c == null) plugin.messages().send(p,"selection.wilderness");
            else { plugin.messages().send(p,"selection.inspect","type",plugin.messages().text(c.root()?"labels.root":"labels.child"),"id",c.id(),"owner",plugin.state().owner(c)); preview(p,c.bounds(),p.getWorld()); }
        } else if (held == plugin.settings().selectionTool()) {
            e.setCancelled(true);
            try {
                Session s = session(p);
                Location point = e.getClickedBlock().getLocation();
                if (s.first == null || s.second != null) {
                    s.first = null; s.second = null; s.resize = null;
                    Claim c = plugin.at(point);
                    if (c != null && s.mode.equals("basic")) c = plugin.state().root(c);
                    if (c != null && plugin.state().owner(c).equals(p.getUniqueId()) && p.hasPermission("saltyclaims.command.claim.resize")
                            && (point.getBlockX() == c.bounds().minX() || point.getBlockX() == c.bounds().maxX())
                            && (point.getBlockZ() == c.bounds().minZ() || point.getBlockZ() == c.bounds().maxZ())) {
                        s.resize = c.id(); s.revision = c.revision(); s.mode = c.root()?"basic":c.bounds().fullHeight()?"full":"3d";
                        Bounds b = c.bounds();
                        s.first = new Location(point.getWorld(),point.getBlockX()==b.minX()?b.maxX():b.minX(),
                                b.fullHeight()?point.getBlockY():point.getBlockY()==b.minY()?b.maxY():b.minY(),point.getBlockZ()==b.minZ()?b.maxZ():b.minZ());
                        plugin.messages().send(p,"selection.resize"); return;
                    }
                    require(p, s.mode.equals("basic") ? "create" : "child.create");
                    set(p,true,point);
                } else {
                    require(p,s.resize != null?"resize":s.mode.equals("basic")?"create":"child.create"); set(p,false,point);
                }
            } catch (IllegalArgumentException ex) { plugin.deny(p, plugin.messages().error(ex)); }
        }
    }
    private static void require(Player p, String path) {
        if (!p.hasPermission("saltyclaims.command.claim." + path)) throw new MessageException("errors.missing-permission","permission","saltyclaims.command.claim."+path);
    }
    @EventHandler public void quit(PlayerQuitEvent e) { cancel(e.getPlayer()); plugin.forget(e.getPlayer().getUniqueId()); }
    @EventHandler public void world(PlayerChangedWorldEvent e) { cancel(e.getPlayer()); }
}
