package org.saltymc.selection;

import org.saltymc.message.MessageException;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.saltymc.claim.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.saltymc.config.Settings;
import org.saltymc.message.Messages;

/** Transient selection state, with bounded particle previews and no world edits. */
public final class SelectionManager implements Listener {
    public interface Port {
        boolean ready(); Settings settings(); Messages messages(); ClaimState state(); Claim at(Location location);
        void deny(Player player,String reason); void forget(UUID player);
        void showBounds(Player player,Bounds bounds,World world,boolean child);
        String playerName(UUID player);
    }
    public static final class Session {
        public Location first, second;
        public String mode = "basic";
        public UUID parent, resize;
        public long revision, expires;
    }
    private final Port plugin;
    private final Consumer<Player> complete;
    private final LongSupplier clock;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private record Preview(Player player,Bounds bounds,World world,long expires,Particle.DustOptions dust) { }
    private final Map<UUID,Preview> previews=new HashMap<>();
    public SelectionManager(Port plugin,Consumer<Player> complete) { this(plugin,complete,System::currentTimeMillis); }
    public SelectionManager(Port plugin,Consumer<Player> complete,LongSupplier clock) { this.plugin=plugin; this.complete=complete; this.clock=clock; }
    public Session session(Player p) {
        Session old = sessions.get(p.getUniqueId());
        if (old != null && old.expires < System.currentTimeMillis()) sessions.remove(p.getUniqueId());
        Session s = sessions.computeIfAbsent(p.getUniqueId(), ignored -> new Session());
        s.expires = System.currentTimeMillis() + plugin.settings().sessionSeconds() * 1000L;
        return s;
    }
    public void clear() { sessions.clear(); previews.clear(); }
    public void finish(Player p) { sessions.remove(p.getUniqueId()); }
    public void cancel(Player p) { finish(p); previews.remove(p.getUniqueId()); }
    public void expire() { sessions.values().removeIf(s -> s.expires < System.currentTimeMillis()); }
    public Bounds bounds(Player p) {
        Session s = session(p);
        if (s.first == null || s.second == null) throw new MessageException("errors.selection-incomplete");
        if (!s.first.getWorld().equals(s.second.getWorld())) throw new MessageException("errors.selection-same-world");
        boolean full = s.mode.equals("basic") || s.first.getBlockY()==s.second.getBlockY();
        return new Bounds(Math.min(s.first.getBlockX(), s.second.getBlockX()), Math.max(s.first.getBlockX(), s.second.getBlockX()),
                Math.min(s.first.getBlockZ(), s.second.getBlockZ()), Math.max(s.first.getBlockZ(), s.second.getBlockZ()),
                full ? null : Math.min(s.first.getBlockY(), s.second.getBlockY()), full ? null : Math.max(s.first.getBlockY(), s.second.getBlockY()));
    }
    public void set(Player p, boolean first, Location point) {
        Session s = session(p);
        if (first) { s.first = point.clone(); s.second = null; } else s.second = point.clone();
        plugin.messages().send(p,first?"selection.first-corner":"selection.second-corner","x",point.getBlockX(),"y",point.getBlockY(),"z",point.getBlockZ());
        if(first) showSelection(p,s);
        if (s.first != null && s.second != null) {
            Bounds b = bounds(p);
            if(s.resize==null) plugin.showBounds(p,b,s.first.getWorld(),!s.mode.equals("basic"));
            plugin.messages().send(p,"selection.preview","area",b.area(),"command","");
        }
    }
    private void showFirst(Player p,Location point,boolean child) {
        plugin.showBounds(p,new Bounds(point.getBlockX(),point.getBlockX(),point.getBlockZ(),point.getBlockZ(),point.getBlockY(),point.getBlockY()),point.getWorld(),child);
    }
    private void showSelection(Player player,Session selection) {
        Claim resizing=selection.resize==null?null:plugin.state().claims().get(selection.resize);
        if(resizing!=null) plugin.showBounds(player,resizing.bounds(),selection.first.getWorld(),!resizing.root());
        else if(selection.first!=null) showFirst(player,selection.first,!selection.mode.equals("basic"));
    }
    private Session toolSession(Player player,boolean child) {
        Session selection=session(player);
        if(child==selection.mode.equals("basic")) {
            cancel(player); selection=session(player); selection.mode=child?"child":"basic";
        }
        return selection;
    }
    private void selectParent(Player player,Session selection,Claim clicked) {
        if(!selection.mode.equals("basic")&&(selection.first==null||selection.second!=null)) {
            if(clicked==null) throw new MessageException("errors.select-root");
            if(plugin.state().owner(clicked).equals(player.getUniqueId())) selection.parent=plugin.state().root(clicked).id();
        }
    }
    /** Replace the sender's previous border and refresh its configured lifetime. */
    public void preview(Player p, Bounds b, World world) {
        preview(p,b,world,false);
    }
    public void preview(Player p, Bounds b, World world,boolean child) {
        previews.remove(p.getUniqueId());
        int seconds=plugin.settings().previewSeconds();
        if(seconds==0||!p.getWorld().equals(world)) return;
        Particle.DustOptions dust=new Particle.DustOptions(child?plugin.settings().childBorderColor():plugin.settings().parentBorderColor(),1.2f);
        previews.put(p.getUniqueId(),new Preview(p,b,world,clock.getAsLong()+seconds*1000L,dust));
        render(p,b,world,dust);
    }
    /** One shared server task repeats active borders; no task is created per click. */
    public void tickPreviews() {
        long now=clock.getAsLong();
        for(var iterator=previews.values().iterator();iterator.hasNext();) {
            Preview preview=iterator.next(); Player player=preview.player();
            if(now>=preview.expires()||!player.isOnline()||!player.getWorld().equals(preview.world())) iterator.remove();
            else render(player,preview.bounds(),preview.world(),preview.dust());
        }
    }
    private void render(Player p, Bounds b, World world,Particle.DustOptions dust) {
        if (!p.getWorld().equals(world)) return;
        double y = b.fullHeight() ? p.getLocation().getY() + 0.2 : b.minY() + 0.2;
        List<Double> heights = b.fullHeight() ? List.of(y) : List.of(y, b.maxY() + 1.0);
        for (double h : heights) for (int i = 0; i <= 32; i++) {
            double t = i / 32.0;
            particle(p, world, b.minX() + (b.maxX() + 1.0 - b.minX()) * t, h, b.minZ(),dust);
            particle(p, world, b.minX() + (b.maxX() + 1.0 - b.minX()) * t, h, b.maxZ() + 1.0,dust);
            particle(p, world, b.minX(), h, b.minZ() + (b.maxZ() + 1.0 - b.minZ()) * t,dust);
            particle(p, world, b.maxX() + 1.0, h, b.minZ() + (b.maxZ() + 1.0 - b.minZ()) * t,dust);
        }
        if(!b.fullHeight()) for(int i=0;i<=16;i++) {
            double h=b.minY()+(b.maxY()+1.0-b.minY())*i/16.0;
            for(double x:new double[]{b.minX(),b.maxX()+1.0}) for(double z:new double[]{b.minZ(),b.maxZ()+1.0}) particle(p,world,x,h,z,dust);
        }
    }
    private void particle(Player p, World world, double x, double y, double z,Particle.DustOptions dust) {
        Location point = new Location(world,x,y,z);
        if (point.distanceSquared(p.getLocation()) < 128 * 128) p.spawnParticle(Particle.DUST, point, 1, dust);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void interact(PlayerInteractEvent e) {
        if (!plugin.ready() || e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if(e.useInteractedBlock()==Event.Result.DENY&&e.useItemInHand()==Event.Result.DENY) return;
        Player p = e.getPlayer(); Material held = p.getInventory().getItemInMainHand().getType();
        if (held == plugin.settings().inspectionTool()) {
            if (!p.hasPermission("saltyclaims.command.claim.info")) return;
            e.setCancelled(true); Claim c = plugin.at(e.getClickedBlock().getLocation());
            if (c == null) plugin.messages().send(p,"selection.wilderness");
            else { plugin.messages().send(p,"selection.inspect","type",plugin.messages().text(c.root()?"labels.root":"labels.child"),"id",c.id(),"owner",plugin.state().owner(c)); preview(p,c.bounds(),p.getWorld(),!c.root()); }
        } else if (held == plugin.settings().selectionTool()||held == plugin.settings().childSelectionTool()) {
            e.setCancelled(true);
            selectCorner(p,e.getClickedBlock().getLocation(),held==plugin.settings().childSelectionTool());
        }
    }
    /** Both tool clicks enter here; no chat command or token is required to commit a complete selection. */
    public void selectCorner(Player p,Location point) {
        selectCorner(p,point,false);
    }
    public void selectCorner(Player p,Location point,boolean childTool) {
        if(!plugin.ready()) return;
        Session s=toolSession(p,childTool);
            try {
                Claim clicked=plugin.at(point);
                selectParent(p,s,clicked);
                if(clicked!=null) {
                    Claim target=s.mode.equals("basic")?plugin.state().root(clicked):clicked;
                    boolean beginning=s.first==null||s.second!=null;
                    boolean resize=beginning&&resizeCorner(p,s,target,point);
                    boolean own=plugin.state().owner(target).equals(p.getUniqueId());
                    boolean currentResize=!beginning&&own&&target.id().equals(s.resize);
                    boolean selectedParent=!s.mode.equals("basic")&&own&&target.root()&&target.id().equals(s.parent);
                    if(!resize&&!currentResize&&!selectedParent) {
                        cancel(p);
                        plugin.showBounds(p,clicked.bounds(),point.getWorld(),!clicked.root());
                        plugin.deny(p,plugin.messages().text("selection.already-claimed","owner",plugin.playerName(plugin.state().owner(clicked))));
                        return;
                    }
                }
                if (s.first == null || s.second != null) {
                    s.first = null; s.second = null; s.resize = null;
                    Claim c = plugin.at(point);
                    if (c != null && s.mode.equals("basic")) c = plugin.state().root(c);
                    if (c != null && resizeCorner(p,s,c,point)) {
                        s.resize = c.id(); s.revision = c.revision(); s.mode = c.root()?"basic":"child";
                        Bounds b = c.bounds();
                        s.first = new Location(point.getWorld(),point.getBlockX()==b.minX()?b.maxX():b.minX(),
                                b.fullHeight()?point.getBlockY():point.getBlockY()==b.minY()?b.maxY():b.minY(),point.getBlockZ()==b.minZ()?b.maxZ():b.minZ());
                        showSelection(p,s); plugin.messages().send(p,"selection.resize"); return;
                    }
                    require(p, s.mode.equals("basic") ? "create" : "child.create");
                    set(p,true,point);
                } else {
                    require(p,s.resize != null?"resize":s.mode.equals("basic")?"create":"child.create"); set(p,false,point);
                    complete.accept(p);
                }
            } catch (IllegalArgumentException|ArithmeticException ex) {
                cancel(p);
                plugin.deny(p,plugin.messages().error(ex));
            }
    }
    private boolean resizeCorner(Player player,Session selection,Claim claim,Location point) {
        Bounds bounds=claim.bounds();
        return plugin.state().owner(claim).equals(player.getUniqueId())&&player.hasPermission("saltyclaims.command.claim.resize")
                &&(selection.mode.equals("basic")?claim.root():!claim.root())
                &&(point.getBlockX()==bounds.minX()||point.getBlockX()==bounds.maxX())
                &&(point.getBlockZ()==bounds.minZ()||point.getBlockZ()==bounds.maxZ())
                &&(bounds.fullHeight()||point.getBlockY()==bounds.minY()||point.getBlockY()==bounds.maxY());
    }
    private static void require(Player p, String path) {
        if (!p.hasPermission("saltyclaims.command.claim." + path)) throw new MessageException("errors.missing-permission","permission","saltyclaims.command.claim."+path);
    }
    private boolean selectionTool(ItemStack item) {
        return item!=null&&(item.getType()==plugin.settings().selectionTool()||item.getType()==plugin.settings().childSelectionTool());
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void held(PlayerItemHeldEvent event) {
        if(event.isCancelled()||!plugin.ready()) return;
        Player player=event.getPlayer();
        if(selectionTool(player.getInventory().getItem(event.getPreviousSlot()))) cancel(player);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void swap(PlayerSwapHandItemsEvent event) {
        if(event.isCancelled()||!plugin.ready()) return;
        Player player=event.getPlayer();
        if(selectionTool(player.getInventory().getItemInMainHand())) cancel(player);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void drop(PlayerDropItemEvent event) {
        if(event.isCancelled()||!plugin.ready()) return;
        Player player=event.getPlayer(); ItemStack dropped=event.getItemDrop().getItemStack();
        if(selectionTool(dropped)&&player.getInventory().getItemInMainHand().getType()!=dropped.getType()) cancel(player);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void inventoryClick(InventoryClickEvent event) {
        if(event.isCancelled()||!plugin.ready()||!(event.getWhoClicked() instanceof Player player)) return;
        int held=player.getInventory().getHeldItemSlot();
        if(selectionTool(player.getInventory().getItemInMainHand())
                &&(event.getClickedInventory()==player.getInventory()&&event.getSlot()==held||event.getHotbarButton()==held)) cancel(player);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void inventoryDrag(InventoryDragEvent event) {
        if(event.isCancelled()||!plugin.ready()||!(event.getWhoClicked() instanceof Player player)||!selectionTool(player.getInventory().getItemInMainHand())) return;
        for(int raw:event.getRawSlots()) if(event.getView().getInventory(raw)==player.getInventory()
                &&event.getView().convertSlot(raw)==player.getInventory().getHeldItemSlot()) { cancel(player); return; }
    }
    @EventHandler public void quit(PlayerQuitEvent e) { cancel(e.getPlayer()); plugin.forget(e.getPlayer().getUniqueId()); }
    @EventHandler public void world(PlayerChangedWorldEvent e) { cancel(e.getPlayer()); }
}
