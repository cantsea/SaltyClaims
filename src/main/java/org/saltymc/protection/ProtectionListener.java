package org.saltymc.protection;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.*;
import org.bukkit.block.data.type.Bed;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import io.papermc.paper.event.player.PlayerInsertLecternBookEvent;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.BlockProjectileSource;
import org.saltymc.SaltyClaims;
import org.saltymc.claim.Claim;
import org.saltymc.flag.Flag;
import org.saltymc.flag.Policy;
import java.util.*;

/** Paper event adapters. No storage I/O, world scans, or per-block ownership records. */
public final class ProtectionListener implements Listener {
    private final SaltyClaims plugin;
    private final NamespacedKey launchOrigin;
    private final NamespacedKey fallingOrigin;
    public ProtectionListener(SaltyClaims plugin) { this.plugin=plugin; launchOrigin=new NamespacedKey(plugin,"launch-origin"); fallingOrigin=new NamespacedKey(plugin,"falling-origin"); }
    private static final Set<Material> CROPS=Set.of(Material.WHEAT,Material.CARROTS,Material.POTATOES,Material.BEETROOTS,Material.NETHER_WART,Material.COCOA);
    private static final Set<Material> SEEDS=Set.of(Material.WHEAT_SEEDS,Material.CARROT,Material.POTATO,Material.BEETROOT_SEEDS,Material.NETHER_WART,Material.COCOA_BEANS);
    private boolean allow(Player actor, Block block, Flag flag) { return plugin.allow(actor,block.getLocation(),flag); }
    private Player actor(Entity e) {
        if(e instanceof Player p) return p;
        if(e instanceof Projectile p && p.getShooter() instanceof Player player) return player;
        if(e instanceof TNTPrimed t && t.getSource()!=null && t.getSource()!=e) return actor(t.getSource());
        if(e instanceof Tameable t && t.getOwner()!=null) return Bukkit.getPlayer(t.getOwner().getUniqueId());
        if(e instanceof AreaEffectCloud c && c.getSource() instanceof Player p) return p;
        return null;
    }
    private boolean sameRegion(Location a, Location b) {
        if(!a.getWorld().equals(b.getWorld())) return false;
        Claim x=plugin.at(a), y=plugin.at(b);
        return Objects.equals(x==null?null:x.id(), y==null?null:y.id());
    }
    private boolean crossing(Location a, Location b, Flag flag) {
        if(!plugin.ready()) return false;
        return sameRegion(a,b) || plugin.decision(null,a,flag).allowed() && plugin.decision(null,b,flag).allowed();
    }
    private boolean environment(Location l, Flag flag) { return plugin.decision(null,l,flag).allowed(); }
    private boolean decoration(Entity e) { return e instanceof Hanging || e instanceof ArmorStand; }
    private Flag entityFlag(Entity e, boolean edit) { return decoration(e)?Flag.DECORATION_EDIT:e instanceof Vehicle?(edit?Flag.VEHICLE_EDIT:Flag.VEHICLE_USE):edit?Flag.ANIMAL_DAMAGE:Flag.ANIMAL_INTERACT; }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void breakBlock(BlockBreakEvent e) {
        Flag flag=CROPS.contains(e.getBlock().getType()) && e.getBlock().getBlockData() instanceof Ageable age && age.getAge()==age.getMaximumAge()?Flag.CROP_HARVEST:Flag.BLOCK_BREAK;
        if(!allow(e.getPlayer(),e.getBlock(),flag)) e.setCancelled(true); else plugin.audit(e.getPlayer(),e.getBlock().getLocation(),"break");
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void place(BlockPlaceEvent e) {
        Flag f=CROPS.contains(e.getBlockPlaced().getType())?Flag.CROP_REPLANT:Flag.BLOCK_PLACE;
        boolean allowed=allow(e.getPlayer(),e.getBlockPlaced(),f);
        if(e instanceof BlockMultiPlaceEvent multi) for(BlockState b:multi.getReplacedBlockStates()) allowed &= allow(e.getPlayer(),b.getBlock(),f);
        if(!allowed) e.setCancelled(true); else plugin.audit(e.getPlayer(),e.getBlock().getLocation(),"place");
    }
    private Flag interaction(Block b) {
        BlockData data=b.getBlockData(); Material type=b.getType();
        if(data instanceof Door || data instanceof TrapDoor || data instanceof Gate) return Flag.DOOR_USE;
        if(data instanceof Bed) return Flag.BED_USE;
        if(data instanceof Switch || type.name().endsWith("PRESSURE_PLATE") || data instanceof AnaloguePowerable) return Flag.SWITCH_USE;
        if(b.getState() instanceof Container || type==Material.ENDER_CHEST) return Flag.CONTAINER_USE;
        if(type==Material.CRAFTING_TABLE||type==Material.ANVIL||type==Material.CHIPPED_ANVIL||type==Material.DAMAGED_ANVIL
                ||type==Material.ENCHANTING_TABLE||type==Material.GRINDSTONE||type==Material.STONECUTTER
                ||type==Material.SMITHING_TABLE||type==Material.CARTOGRAPHY_TABLE||type==Material.LOOM) return Flag.UTILITY_USE;
        if(type==Material.SWEET_BERRY_BUSH||type==Material.CAVE_VINES||type==Material.CAVE_VINES_PLANT) return Flag.CROP_HARVEST;
        return Flag.BLOCK_PLACE; // Block transformations, signs, beehives, decorations, and uncommon utilities fail closed.
    }
    @EventHandler(priority=EventPriority.HIGH) public void interact(PlayerInteractEvent e) {
        // Air interactions arrive pre-cancelled by vanilla; honor explicit block/item denial separately.
        if(e.getClickedBlock()==null || e.useInteractedBlock()==Event.Result.DENY) return;
        if(e.getAction()==Action.LEFT_CLICK_BLOCK) {
            if(e.getClickedBlock().getType()==Material.DRAGON_EGG&&!allow(e.getPlayer(),e.getClickedBlock(),Flag.BLOCK_BREAK)) e.setCancelled(true);
            return; // Actual breaking is checked by BlockBreakEvent.
        }
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK&&e.getAction()!=Action.PHYSICAL) return;
        Block block=e.getClickedBlock();
        Flag flag=e.getAction()==Action.PHYSICAL?(block.getType()==Material.FARMLAND?Flag.CROP_HARVEST:block.getType()==Material.TURTLE_EGG?Flag.BLOCK_BREAK:Flag.SWITCH_USE):interaction(block);
        if(e.getAction()==Action.RIGHT_CLICK_BLOCK&&e.getItem()!=null&&SEEDS.contains(e.getItem().getType())
                &&(block.getType()==Material.FARMLAND||block.getType()==Material.SOUL_SAND||block.getType().name().endsWith("JUNGLE_LOG"))) flag=Flag.CROP_REPLANT;
        if(!allow(e.getPlayer(),block,flag)) { e.setUseInteractedBlock(Event.Result.DENY); e.setUseItemInHand(Event.Result.DENY); }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void bucketEmpty(PlayerBucketEmptyEvent e) {
        if(!allow(e.getPlayer(),e.getBlock(),Flag.BUCKET_USE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void bucketFill(PlayerBucketFillEvent e) {
        if(!allow(e.getPlayer(),e.getBlock(),Flag.BUCKET_USE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void open(InventoryOpenEvent e) {
        if(!(e.getPlayer() instanceof Player p)) return;
        Flag flag=e.getInventory().getHolder() instanceof Container || e.getInventory().getType()==InventoryType.CHEST?Flag.CONTAINER_USE:Flag.UTILITY_USE;
        for(Location l:inventoryLocations(e.getInventory())) if(!plugin.allow(p,l,flag)) { e.setCancelled(true); return; }
    }
    private List<Location> inventoryLocations(Inventory inventory) {
        // A player's own inventory/crafting grid is not a container belonging to the land they stand on.
        if(inventory.getHolder() instanceof Player) return List.of();
        if(inventory.getHolder() instanceof DoubleChest d) {
            List<Location> result=new ArrayList<>();
            if(d.getLeftSide() instanceof BlockState b) result.add(b.getLocation());
            if(d.getRightSide() instanceof BlockState b) result.add(b.getLocation());
            return result;
        }
        if(inventory.getHolder() instanceof Entity e) return List.of(e.getLocation());
        Location l=inventory.getLocation(); return l==null?List.of():List.of(l);
    }
    private boolean inventoryAccess(Player p,Inventory inventory) {
        Flag flag=inventory.getHolder() instanceof Container || inventory.getType()==InventoryType.CHEST || inventory.getHolder() instanceof Entity?Flag.CONTAINER_USE:Flag.UTILITY_USE;
        for(Location l:inventoryLocations(inventory)) if(!plugin.allow(p,l,flag)) return false;
        return plugin.ready();
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void click(InventoryClickEvent e) {
        if(e.getWhoClicked() instanceof Player p&&!inventoryAccess(p,e.getView().getTopInventory())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void drag(InventoryDragEvent e) {
        if(e.getWhoClicked() instanceof Player p&&!inventoryAccess(p,e.getView().getTopInventory())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void interactEntity(PlayerInteractEntityEvent e) {
        if(!plugin.allow(e.getPlayer(),e.getRightClicked().getLocation(),entityFlag(e.getRightClicked(),false))) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void interactAtEntity(PlayerInteractAtEntityEvent e) {
        if(!plugin.allow(e.getPlayer(),e.getRightClicked().getLocation(),entityFlag(e.getRightClicked(),false))) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void armorStand(PlayerArmorStandManipulateEvent e) {
        if(!plugin.allow(e.getPlayer(),e.getRightClicked().getLocation(),Flag.DECORATION_EDIT)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void shear(PlayerShearEntityEvent e) {
        if(!plugin.allow(e.getPlayer(),e.getEntity().getLocation(),Flag.ANIMAL_INTERACT)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void leash(PlayerLeashEntityEvent e) {
        if(!plugin.allow(e.getPlayer(),e.getEntity().getLocation(),Flag.ANIMAL_INTERACT)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void unleash(PlayerUnleashEntityEvent e) {
        if(!plugin.allow(e.getPlayer(),e.getEntity().getLocation(),Flag.ANIMAL_INTERACT)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void fish(PlayerFishEvent e) {
        if(e.getCaught()!=null&&!plugin.allow(e.getPlayer(),e.getCaught().getLocation(),entityFlag(e.getCaught(),true))) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void entityPlace(EntityPlaceEvent e) {
        if(!plugin.allow(e.getPlayer(),e.getEntity().getLocation(),entityFlag(e.getEntity(),true))) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void hangingPlace(HangingPlaceEvent e) {
        if(!plugin.allow(e.getPlayer(),e.getEntity().getLocation(),Flag.DECORATION_EDIT)
                ||!plugin.allow(e.getPlayer(),e.getBlock().getLocation(),Flag.DECORATION_EDIT)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void hangingBreak(HangingBreakEvent e) {
        Player p=e instanceof HangingBreakByEntityEvent by?actor(by.getRemover()):null;
        if(e.getCause()==HangingBreakEvent.RemoveCause.EXPLOSION) {
            if(!environment(e.getEntity().getLocation(),Flag.EXPLOSION_ENTITY_DAMAGE)) e.setCancelled(true);
        } else if(!plugin.allow(p,e.getEntity().getLocation(),Flag.DECORATION_EDIT)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void launch(ProjectileLaunchEvent e) {
        if(!plugin.ready()) { e.setCancelled(true); return; }
        Projectile projectile=e.getEntity();
        if(projectile.getShooter() instanceof Entity source) {
            Location l=source.getLocation(); Player p=actor(source);
            if(p!=null) projectile.getPersistentDataContainer().set(launchOrigin,PersistentDataType.STRING,
                    p.getUniqueId()+","+l.getWorld().getUID()+","+l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ());
        } else if(projectile.getShooter() instanceof BlockProjectileSource source) {
            Location l=source.getBlock().getLocation();
            projectile.getPersistentDataContainer().set(launchOrigin,PersistentDataType.STRING,
                    "automation,"+l.getWorld().getUID()+","+l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ());
        }
    }
    private Location origin(Entity source) {
        if(source!=null) {
            String serialized=source.getPersistentDataContainer().get(launchOrigin,PersistentDataType.STRING);
            if(serialized!=null) try {
                String[] a=serialized.split(","); World world=Bukkit.getWorld(UUID.fromString(a[1]));
                if(world!=null) return new Location(world,Integer.parseInt(a[2]),Integer.parseInt(a[3]),Integer.parseInt(a[4]));
            } catch(RuntimeException ignored) { }
        }
        return source.getLocation();
    }
    private boolean playerSource(Entity source) {
        return actor(source)!=null || source instanceof TNTPrimed || source.getPersistentDataContainer().has(launchOrigin,PersistentDataType.STRING);
    }
    private boolean harmful(Entity source,Entity victim) {
        Player p=actor(source);
        if(victim instanceof Player) {
            if(!playerSource(source)) return plugin.ready();
            return environment(origin(source),Flag.PVP)&&environment(source.getLocation(),Flag.PVP)&&environment(victim.getLocation(),Flag.PVP);
        }
        if(victim instanceof Monster) return plugin.ready();
        return plugin.allow(p,victim.getLocation(),entityFlag(victim,true));
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void damage(EntityDamageByEntityEvent e) {
        if(!harmful(e.getDamager(),e.getEntity())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void explosionDamage(EntityDamageEvent e) {
        if(!plugin.ready()) { e.setCancelled(true); return; }
        if((e.getCause()==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION||e.getCause()==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)
                &&!environment(e.getEntity().getLocation(),Flag.EXPLOSION_ENTITY_DAMAGE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void splash(PotionSplashEvent e) {
        for(LivingEntity entity:e.getAffectedEntities()) if(!harmful(e.getPotion(),entity)) e.setIntensity(entity,0);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void cloud(AreaEffectCloudApplyEvent e) {
        e.getAffectedEntities().removeIf(entity->!harmful(e.getEntity(),entity));
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void lingering(LingeringPotionSplashEvent e) {
        String origin=e.getEntity().getPersistentDataContainer().get(launchOrigin,PersistentDataType.STRING);
        if(origin!=null) e.getAreaEffectCloud().getPersistentDataContainer().set(launchOrigin,PersistentDataType.STRING,origin);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void projectileHit(ProjectileHitEvent e) {
        if(e.getHitBlock()!=null) {
            Player p=actor(e.getEntity());
            if(!allow(p,e.getHitBlock(),Flag.SWITCH_USE)) e.setCancelled(true);
        }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void vehicleDamage(VehicleDamageEvent e) {
        if(!plugin.allow(actor(e.getAttacker()),e.getVehicle().getLocation(),Flag.VEHICLE_EDIT)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void vehicleDestroy(VehicleDestroyEvent e) {
        if(!plugin.allow(actor(e.getAttacker()),e.getVehicle().getLocation(),Flag.VEHICLE_EDIT)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void vehicleEnter(VehicleEnterEvent e) {
        if(!plugin.allow(actor(e.getEntered()),e.getVehicle().getLocation(),Flag.VEHICLE_USE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void explode(EntityExplodeEvent e) {
        e.blockList().removeIf(b->!environment(b.getLocation(),Flag.EXPLOSION_BLOCK_DAMAGE));
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void explodeBlock(BlockExplodeEvent e) {
        e.blockList().removeIf(b->!environment(b.getLocation(),Flag.EXPLOSION_BLOCK_DAMAGE));
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void ignite(BlockIgniteEvent e) {
        Player p=e.getPlayer();
        if(p!=null ? !allow(p,e.getBlock(),Flag.IGNITE) : !environment(e.getBlock().getLocation(),Flag.FIRE_SPREAD)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void burn(BlockBurnEvent e) {
        if(!environment(e.getBlock().getLocation(),Flag.FIRE_SPREAD)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void spread(BlockSpreadEvent e) {
        if(e.getNewState().getType()==Material.FIRE||e.getNewState().getType()==Material.SOUL_FIRE) {
            if(!environment(e.getSource().getLocation(),Flag.FIRE_SPREAD)||!environment(e.getBlock().getLocation(),Flag.FIRE_SPREAD)) e.setCancelled(true);
        } else if(!sameRegion(e.getSource().getLocation(),e.getBlock().getLocation())&&!environment(e.getBlock().getLocation(),Flag.MOB_BLOCK_GRIEF)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void mobChange(EntityChangeBlockEvent e) {
        if(e.getEntity() instanceof FallingBlock falling) {
            String region=falling.getPersistentDataContainer().get(fallingOrigin,PersistentDataType.STRING);
            Claim current=plugin.at(e.getBlock().getLocation()); String key=current==null?"wilderness":current.id().toString();
            if(plugin.ready()&&key.equals(region)) return;
        }
        Player p=actor(e.getEntity());
        if(p!=null ? !allow(p,e.getBlock(),Flag.BLOCK_BREAK) : !environment(e.getBlock().getLocation(),Flag.MOB_BLOCK_GRIEF)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR) public void fallingSpawn(EntitySpawnEvent e) {
        if(e.getEntity() instanceof FallingBlock falling) {
            Claim claim=plugin.at(falling.getLocation());
            falling.getPersistentDataContainer().set(fallingOrigin,PersistentDataType.STRING,claim==null?"wilderness":claim.id().toString());
        }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void entityForm(EntityBlockFormEvent e) {
        if(!plugin.allow(actor(e.getEntity()),e.getBlock().getLocation(),Flag.BLOCK_PLACE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void flow(BlockFromToEvent e) {
        if(!crossing(e.getBlock().getLocation(),e.getToBlock().getLocation(),Flag.FLUID_CROSSING)) e.setCancelled(true);
    }
    private boolean piston(Block base,List<Block> moved,org.bukkit.block.BlockFace direction, boolean retract) {
        Location start=base.getLocation();
        org.bukkit.block.BlockFace facing=base.getBlockData() instanceof Directional d?d.getFacing():direction;
        if(!crossing(start,base.getRelative(facing).getLocation(),Flag.PISTON_CROSSING)) return false;
        for(Block block:moved) {
            // Paper passes movement direction for nonempty retract events, already opposite the piston facing.
            Block dest=block.getRelative(direction);
            if(!crossing(start,block.getLocation(),Flag.PISTON_CROSSING)||!crossing(block.getLocation(),dest.getLocation(),Flag.PISTON_CROSSING)) return false;
            if(!sameRegion(block.getLocation(),dest.getLocation())&&(!allow(null,block,Flag.BLOCK_BREAK)||!allow(null,dest,Flag.BLOCK_PLACE))) return false;
        }
        return true;
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void pistonExtend(BlockPistonExtendEvent e) {
        if(!piston(e.getBlock(),e.getBlocks(),e.getDirection(),false)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void pistonRetract(BlockPistonRetractEvent e) {
        if(!piston(e.getBlock(),e.getBlocks(),e.getDirection(),true)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void moveItems(InventoryMoveItemEvent e) {
        if(!plugin.ready()) { e.setCancelled(true); return; }
        for(Location from:inventoryLocations(e.getSource())) for(Location to:inventoryLocations(e.getDestination()))
            if(!crossing(from,to,Flag.HOPPER_CROSSING)||!sameRegion(from,to)&&(!plugin.allow(null,from,Flag.CONTAINER_USE)||!plugin.allow(null,to,Flag.CONTAINER_USE))) { e.setCancelled(true); return; }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void pickup(InventoryPickupItemEvent e) {
        for(Location to:inventoryLocations(e.getInventory())) if(!crossing(e.getItem().getLocation(),to,Flag.HOPPER_CROSSING)) { e.setCancelled(true); return; }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void dispense(BlockDispenseEvent e) {
        if(!plugin.ready()) { e.setCancelled(true); return; }
        BlockData data=e.getBlock().getBlockData();
        if(data instanceof Directional facing) {
            Location from=e.getBlock().getLocation(), to=e.getBlock().getRelative(facing.getFacing()).getLocation();
            if(!sameRegion(from,to)&&!plugin.allow(null,to,Flag.BLOCK_PLACE)) e.setCancelled(true);
        }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void dispenserShear(BlockShearEntityEvent e) {
        if(!sameRegion(e.getBlock().getLocation(),e.getEntity().getLocation())&&!plugin.allow(null,e.getEntity().getLocation(),Flag.ANIMAL_INTERACT)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void prime(TNTPrimeEvent e) {
        Player p=actor(e.getPrimingEntity());
        if(p!=null) { if(!allow(p,e.getBlock(),Flag.IGNITE)) e.setCancelled(true); }
        else if(e.getPrimingBlock()!=null&&sameRegion(e.getPrimingBlock().getLocation(),e.getBlock().getLocation())&&e.getCause()==TNTPrimeEvent.PrimeCause.REDSTONE) {
            if(!plugin.ready()) e.setCancelled(true);
        } else if(!environment(e.getBlock().getLocation(),Flag.EXPLOSION_BLOCK_DAMAGE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void mount(EntityMountEvent e) {
        if(e.getEntity() instanceof Player p&&!plugin.allow(p,e.getMount().getLocation(),Flag.VEHICLE_USE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void cauldron(CauldronLevelChangeEvent e) {
        if(e.getEntity()!=null&&!plugin.allow(actor(e.getEntity()),e.getBlock().getLocation(),Flag.BUCKET_USE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void sign(SignChangeEvent e) {
        if(!allow(e.getPlayer(),e.getBlock(),Flag.BLOCK_PLACE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void signOpen(PlayerOpenSignEvent e) {
        if(!allow(e.getPlayer(),e.getSign().getBlock(),Flag.BLOCK_PLACE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void lectern(PlayerTakeLecternBookEvent e) {
        if(!allow(e.getPlayer(),e.getLectern().getBlock(),Flag.CONTAINER_USE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void insertLectern(PlayerInsertLecternBookEvent e) {
        if(!allow(e.getPlayer(),e.getBlock(),Flag.CONTAINER_USE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void shearBlock(PlayerShearBlockEvent e) {
        if(!allow(e.getPlayer(),e.getBlock(),Flag.BLOCK_PLACE)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void fertilize(BlockFertilizeEvent e) {
        for(BlockState b:e.getBlocks()) if(!plugin.allow(e.getPlayer(),b.getLocation(),Flag.BLOCK_PLACE)) { e.setCancelled(true); return; }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void grow(StructureGrowEvent e) {
        if(!plugin.ready()) { e.setCancelled(true); return; }
        for(BlockState b:e.getBlocks()) if(!sameRegion(e.getLocation(),b.getLocation())&&!plugin.allow(e.getPlayer(),b.getLocation(),Flag.BLOCK_PLACE)) { e.setCancelled(true); return; }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void sponge(SpongeAbsorbEvent e) {
        for(BlockState b:e.getBlocks()) if(!crossing(e.getBlock().getLocation(),b.getLocation(),Flag.FLUID_CROSSING)) { e.setCancelled(true); return; }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH) public void portal(PortalCreateEvent e) {
        Player p=actor(e.getEntity());
        for(BlockState b:e.getBlocks()) if(!plugin.allow(p,b.getLocation(),Flag.BLOCK_PLACE)) { e.setCancelled(true); return; }
    }
}
