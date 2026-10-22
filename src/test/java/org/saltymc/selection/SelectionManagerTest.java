package org.saltymc.selection;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.Test;
import org.saltymc.claim.*;
import org.saltymc.config.Settings;
import org.saltymc.message.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class SelectionManagerTest {
    /** Only material is needed here; avoid item registries supplied by a running Paper server. */
    private static ItemStack item(Material material) { return new ItemStack() { @Override public Material getType() { return material; } }; }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type,java.util.function.BiFunction<String,Object[],Object> handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(object,method,args)->switch(method.getName()) {
            case "equals"->object==args[0]; case "hashCode"->System.identityHashCode(object); case "toString"->type.getSimpleName();
            default->handler.apply(method.getName(),args);
        });
    }
    private static World world(UUID id) { return proxy(World.class,(name,args)->switch(name) { case "getUID"->id; case "getName"->"world"; default->null; }); }
    private static final class Harness implements SelectionManager.Port {
        ClaimState state=ClaimState.empty();
        final World world=world(WORLD);
        final Set<String> permissions=new HashSet<>(Set.of("saltyclaims.command.claim.create","saltyclaims.command.claim.resize","saltyclaims.command.claim.child.create"));
        final List<String> feedback=new ArrayList<>(),denials=new ArrayList<>();
        final List<Bounds> borders=new ArrayList<>();
        final List<Color> colors=new ArrayList<>();
        final Color parentColor=Color.fromRGB(0x123456),childColor=Color.fromRGB(0xABCDEF);
        long now; int duration=10,particles;
        Material held=Material.STICK;
        final PlayerInventory inventory=proxy(PlayerInventory.class,(name,args)->switch(name) {
            case "getItemInMainHand"->item(held);
            case "getItem"->item((int)args[0]==0?held:Material.AIR);
            case "getHeldItemSlot"->0;
            default->null;
        });
        final Player player=proxy(Player.class,(name,args)->switch(name) {
            case "getUniqueId"->OWNER; case "getName"->"Alex"; case "getWorld"->world; case "getLocation"->point(0,64,0);
            case "isOnline"->true;
            case "getInventory"->inventory;
            case "spawnParticle"->{ particles++; for(Object arg:args) if(arg instanceof Particle.DustOptions dust) colors.add(dust.getColor()); yield null; }
            case "hasPermission"->permissions.contains(args[0]);
            case "sendMessage"->{ if(args[0] instanceof String text) feedback.add(text); yield null; }
            default->null;
        });
        final SelectionManager manager=new SelectionManager(this,this::commit,()->now);
        int attempts;
        boolean storageBusy;
        Location point(int x,int y,int z) { return new Location(world,x,y,z); }
        void click(int x,int y,int z) { manager.selectCorner(player,point(x,y,z)); }
        void childClick(int x,int y,int z) { manager.selectCorner(player,point(x,y,z),true); }
        void commit(Player player) {
            attempts++;
            if(storageBusy) throw new MessageException("errors.storage-busy");
            var selection=manager.session(player); Bounds bounds=manager.bounds(player); Claim claim;
            if(selection.resize!=null) {
                Claim old=state.require(selection.resize);
                if(old.revision()!=selection.revision) throw new MessageException("errors.resize-changed");
                claim=old.change(bounds,old.inheritTrust(),old.trust(),old.flags());
            } else {
                boolean root=selection.mode.equals("basic");
                claim=new Claim(UUID.randomUUID(),selection.first.getWorld().getUID(),root?OWNER:null,root?null:selection.parent,bounds,true,1,0,Map.of(),Map.of());
            }
            state=ClaimService.put(state,claim,settings().limits(),false);
            if(selection.resize!=null) showBounds(player,claim.bounds(),world,!claim.root());
            manager.finish(player);
        }
        public boolean ready() { return true; }
        public Settings settings() { return new Settings(new ClaimService.Limits(100,3,20,100,1000000),Material.STICK,Material.BONE,Material.FEATHER,120,duration,parentColor,childColor,Set.of(),Map.of(),Map.of(),Map.of()); }
        public Messages messages() { return Messages.defaults(); }
        public ClaimState state() { return state; }
        public Claim at(Location location) { return new ClaimIndex(state).at(location.getWorld().getUID(),location.getBlockX(),location.getBlockY(),location.getBlockZ()); }
        public void deny(Player player,String reason) { denials.add(reason); }
        public void forget(UUID player) { }
        public String playerName(UUID player) { return player.equals(OWNER)?"Alex":"Sam"; }
        public void showBounds(Player player,Bounds bounds,World world,boolean child) { borders.add(bounds); manager.preview(player,bounds,world,child); }
        void assertSelectionCleared() {
            var selection=manager.session(player);
            assertNull(selection.first); assertNull(selection.second); assertNull(selection.parent); assertNull(selection.resize);
        }
    }
    @Test void secondToolClickImmediatelyCreatesWithoutChatAction() {
        Harness h=new Harness(); h.click(0,64,0); assertEquals(0,h.attempts); h.click(9,64,9);
        assertEquals(1,h.attempts); assertEquals(1,h.state.claims().size()); assertEquals(100,h.state.used(OWNER)); assertTrue(h.denials.isEmpty());
        assertTrue(h.feedback.stream().noneMatch(text->text.contains("/claim create")||text.contains("confirm")));
    }
    @Test void insufficientBlocksRejectAndResetBothCornersForFreshSelection() {
        Harness h=new Harness(); h.click(0,64,0); h.click(10,64,10);
        assertTrue(h.state.claims().isEmpty()); assertEquals(List.of("Not enough claim blocks."),h.denials);
        h.assertSelectionCleared(); int particles=h.particles; h.manager.tickPreviews(); assertEquals(particles,h.particles);
        h.click(0,64,0); assertEquals(1,h.attempts);
        h.click(9,64,9); assertEquals(2,h.attempts); assertEquals(100,h.state.used(OWNER));
    }
    @Test void overlappingSelectionIsRejectedAndRequiresBothCornersAgain() {
        Harness h=new Harness(); Claim other=new Claim(UUID.randomUUID(),WORLD,STRANGER,null,new Bounds(5,10,5,10,null,null),true,1,0,Map.of(),Map.of()); h.state=state(other);
        h.click(0,64,0); h.click(12,64,12);
        assertEquals(Set.of(other.id()),h.state.claims().keySet()); assertTrue(h.denials.getFirst().contains("overlaps"));
        h.assertSelectionCleared(); h.click(0,64,0); h.click(3,64,3); assertEquals(2,h.state.claims().size());
    }
    @Test void permissionIsRecheckedAtSecondClick() {
        Harness h=new Harness(); h.click(0,64,0); h.permissions.clear(); h.click(9,64,9);
        assertEquals(0,h.attempts); assertTrue(h.state.claims().isEmpty()); assertTrue(h.denials.getFirst().contains("permission"));
        h.assertSelectionCleared();
    }
    @Test void cornerResizeCommitsOnNextClickWithoutChat() {
        Harness h=new Harness(); Claim original=root(0,9); h.state=state(original);
        h.click(0,64,0); assertEquals(original.id(),h.manager.session(h.player).resize); assertEquals(0,h.attempts);
        h.click(2,64,2); assertEquals(new Bounds(2,9,2,9,null,null),h.state.require(original.id()).bounds()); assertEquals(1,h.attempts);
    }
    @Test void threeDimensionalChildSelectionAutoCreates() {
        Harness h=new Harness(); Claim root=root(0,9); h.state=state(root);
        h.childClick(1,10,1); h.childClick(3,12,3); Claim child=h.state.children(root.id()).getFirst();
        assertEquals(new Bounds(1,3,1,3,10,12),child.bounds()); assertEquals(100,h.state.used(OWNER));
    }
    @Test void rootCornerInChildModeDoesNotResizeParent() {
        Harness h=new Harness(); Claim root=root(0,9); h.state=state(root);
        h.childClick(0,64,0); assertNull(h.manager.session(h.player).resize); h.childClick(2,64,2);
        assertEquals(root,h.state.require(root.id())); assertEquals(1,h.state.children(root.id()).size());
    }
    @Test void crossWorldSecondCornerIsRejectedAndClearsSelection() {
        Harness h=new Harness(); h.click(0,64,0);
        h.manager.selectCorner(h.player,new Location(world(UUID.randomUUID()),9,64,9));
        assertEquals(0,h.attempts); h.assertSelectionCleared();
    }
    @Test void firstBlockBorderExpandsToRootRectangleRegardlessOfY() {
        Harness h=new Harness(); h.click(0,64,0);
        assertEquals(new Bounds(0,0,0,0,64,64),h.borders.getLast());
        h.click(9,70,9); assertEquals(new Bounds(0,9,0,9,null,null),h.borders.getLast());
        assertTrue(h.state.claims().values().iterator().next().bounds().fullHeight());
    }
    @Test void childToolInfersHeightWithoutModeCommands() {
        for(boolean sameY:List.of(true,false)) {
            Harness h=new Harness(); Claim root=root(0,9); h.state=state(root);
            h.childClick(1,20,1); h.childClick(3,sameY?20:22,3);
            Bounds bounds=h.state.children(root.id()).getFirst().bounds();
            assertEquals(sameY,bounds.fullHeight());
            if(!sameY) { assertEquals(20,bounds.minY()); assertEquals(22,bounds.maxY()); }
        }
    }
    @Test void previewRepeatsUntilConfiguredExpiryAndSurvivesCommit() {
        Harness h=new Harness(); h.click(0,64,0); h.click(9,64,9);
        int initial=h.particles; assertTrue(initial>0);
        h.now=9999; h.manager.tickPreviews(); assertTrue(h.particles>initial);
        int repeated=h.particles; h.now=10000; h.manager.tickPreviews(); assertEquals(repeated,h.particles);
        h.now=11000; h.manager.tickPreviews(); assertEquals(repeated,h.particles);
    }
    @Test void replacementRefreshesExpiryAndCancelOrReloadClearsParticles() {
        Harness h=new Harness(); h.manager.set(h.player,true,h.point(0,64,0));
        h.now=9000; h.manager.set(h.player,false,h.point(9,64,9));
        h.now=10000; int before=h.particles; h.manager.tickPreviews(); assertTrue(h.particles>before);
        h.manager.cancel(h.player); before=h.particles; h.manager.tickPreviews(); assertEquals(before,h.particles);
        h.manager.preview(h.player,new Bounds(0,9,0,9,null,null),h.world); h.manager.clear();
        before=h.particles; h.manager.tickPreviews(); assertEquals(before,h.particles);
    }
    @Test void zeroDurationDisablesParticles() {
        Harness h=new Harness(); h.duration=0; h.click(0,64,0); h.click(9,64,9); h.manager.tickPreviews();
        assertEquals(0,h.particles); assertEquals(1,h.state.claims().size());
    }
    @Test void claimedFirstCornerShowsOwnerAndBordersWithoutSettingPosition() {
        for(UUID owner:List.of(OWNER,STRANGER)) {
            Harness h=new Harness(); Claim claim=new Claim(UUID.randomUUID(),WORLD,owner,null,new Bounds(0,9,0,9,null,null),true,1,0,Map.of(),Map.of()); h.state=state(claim);
            h.click(3,64,3);
            assertNull(h.manager.session(h.player).first); assertNull(h.manager.session(h.player).second);
            assertEquals(0,h.attempts); assertEquals(claim.bounds(),h.borders.getLast());
            assertEquals(List.of("This land is already claimed by "+(owner.equals(OWNER)?"Alex":"Sam")+"."),h.denials);
        }
    }
    @Test void claimedSecondCornerClearsSelectionAndShowsExistingClaimInstead() {
        Harness h=new Harness(); Claim claim=new Claim(UUID.randomUUID(),WORLD,STRANGER,null,new Bounds(5,10,5,10,null,null),true,1,0,Map.of(),Map.of()); h.state=state(claim);
        h.click(0,64,0); h.click(6,64,6);
        h.assertSelectionCleared();
        assertEquals(0,h.attempts); assertEquals(claim.bounds(),h.borders.getLast());
        assertEquals(List.of("This land is already claimed by Sam."),h.denials);
        h.click(0,64,0); h.click(3,64,3); assertEquals(2,h.state.claims().size());
    }
    @Test void anotherPlayersCornerCannotStartResize() {
        Harness h=new Harness(); Claim claim=new Claim(UUID.randomUUID(),WORLD,STRANGER,null,new Bounds(0,9,0,9,null,null),true,1,0,Map.of(),Map.of()); h.state=state(claim);
        h.click(0,64,0); assertNull(h.manager.session(h.player).resize); assertNull(h.manager.session(h.player).first);
        assertEquals(claim.bounds(),h.borders.getLast());
    }
    @Test void childSelectionRejectsExistingChildInterior() {
        Harness h=new Harness(); Claim parent=root(0,9);
        Claim child=new Claim(UUID.randomUUID(),WORLD,null,parent.id(),new Bounds(1,5,1,5,null,null),true,1,0,Map.of(),Map.of()); h.state=state(parent,child);
        h.childClick(3,64,3); assertNull(h.manager.session(h.player).first); assertEquals(child.bounds(),h.borders.getLast());
        assertEquals(List.of("This land is already claimed by Alex."),h.denials);
    }
    @Test void childToolInWildernessCannotStartSelection() {
        Harness h=new Harness(); h.childClick(1,64,1);
        assertNull(h.manager.session(h.player).first); assertEquals(0,h.attempts);
        assertEquals(List.of("Use the child claim tool inside your own claim."),h.denials);
    }
    @Test void changingToolsResetsPartialSelectionAndChoosesParentFromFirstClick() {
        Harness h=new Harness(); Claim parent=root(0,9); h.state=state(parent);
        h.click(20,64,20); h.childClick(1,64,1);
        assertEquals(h.point(1,64,1),h.manager.session(h.player).first);
        assertEquals(parent.id(),h.manager.session(h.player).parent);
        h.click(30,64,30); assertEquals("basic",h.manager.session(h.player).mode);
        assertNull(h.manager.session(h.player).parent); assertEquals(h.point(30,64,30),h.manager.session(h.player).first);
    }
    @Test void invalidResizeClearsItsTargetAndPreviewBeforeFreshResize() {
        Harness h=new Harness(); Claim original=root(0,9); h.state=state(original);
        h.click(0,64,0); assertEquals(original.bounds(),h.borders.getLast());
        h.click(-1,64,-1); assertEquals(original.bounds(),h.borders.getLast());
        assertEquals(original.bounds(),h.state.require(original.id()).bounds());
        h.assertSelectionCleared(); int particles=h.particles; h.manager.tickPreviews(); assertEquals(particles,h.particles);
        h.click(0,64,0); assertEquals(original.id(),h.manager.session(h.player).resize);
        h.click(2,64,2); assertEquals(new Bounds(2,9,2,9,null,null),h.borders.getLast());
    }
    @Test void childToolResizesExistingChildWhileShowingOldCuboid() {
        Harness h=new Harness(); Claim parent=root(0,9),child=child(parent,10,20); h.state=state(parent,child);
        h.childClick(1,10,1); assertEquals(child.id(),h.manager.session(h.player).resize);
        assertEquals(child.bounds(),h.borders.getLast());
        h.childClick(0,9,0); assertEquals(new Bounds(0,4,0,4,9,20),h.state.require(child.id()).bounds());
        assertEquals(h.state.require(child.id()).bounds(),h.borders.getLast());
    }
    @Test void switchingHotbarSlotClearsRegularSelectionAndStopsItsParticles() {
        Harness h=new Harness(); h.click(0,64,0);
        h.manager.held(new PlayerItemHeldEvent(h.player,0,1));
        assertNull(h.manager.session(h.player).first); int particles=h.particles;
        h.manager.tickPreviews(); assertEquals(particles,h.particles);
        h.click(9,64,9); assertEquals(0,h.attempts); assertEquals(h.point(9,64,9),h.manager.session(h.player).first);
    }
    @Test void switchingAwayFromChildToolClearsParentAndResizeState() {
        Harness h=new Harness(); Claim root=root(0,9),child=child(root,10,20); h.state=state(root,child); h.held=Material.BONE;
        h.childClick(1,10,1); assertEquals(child.id(),h.manager.session(h.player).resize);
        h.manager.held(new PlayerItemHeldEvent(h.player,0,1));
        var selection=h.manager.session(h.player);
        assertNull(selection.first); assertNull(selection.second); assertNull(selection.parent); assertNull(selection.resize);
    }
    @Test void swappingToolToOffhandClearsSelection() {
        Harness h=new Harness(); h.click(0,64,0);
        h.manager.swap(new PlayerSwapHandItemsEvent(h.player,item(Material.AIR),item(Material.STICK)));
        assertNull(h.manager.session(h.player).first);
    }
    @Test void cancelledSlotChangePreservesSelectionAndUnrelatedItemsDoNotClearManualSelection() {
        Harness h=new Harness(); h.click(0,64,0);
        PlayerItemHeldEvent event=new PlayerItemHeldEvent(h.player,0,1); event.setCancelled(true); h.manager.held(event);
        assertEquals(h.point(0,64,0),h.manager.session(h.player).first);
        h.held=Material.DIRT; h.manager.held(new PlayerItemHeldEvent(h.player,0,1));
        assertEquals(h.point(0,64,0),h.manager.session(h.player).first);
    }
    @Test void droppingLastToolClearsSelectionButDroppingOneFromStackDoesNot() {
        Harness h=new Harness(); h.click(0,64,0);
        org.bukkit.entity.Item dropped=proxy(org.bukkit.entity.Item.class,(name,args)->name.equals("getItemStack")?item(Material.STICK):null);
        h.manager.drop(new PlayerDropItemEvent(h.player,dropped));
        assertEquals(h.point(0,64,0),h.manager.session(h.player).first);
        h.held=Material.AIR; h.manager.drop(new PlayerDropItemEvent(h.player,dropped));
        assertNull(h.manager.session(h.player).first);
    }
    @Test void regularFirstBlockAndFinalBorderUseConfiguredParentColor() {
        Harness h=new Harness(); h.click(0,64,0);
        assertEquals(h.parentColor,h.colors.getLast());
        h.click(9,64,9); h.manager.tickPreviews();
        assertTrue(h.colors.stream().allMatch(h.parentColor::equals));
    }
    @Test void fullHeightChildAndItsFirstBlockUseChildColor() {
        Harness h=new Harness(); Claim parent=root(0,9); h.state=state(parent);
        h.childClick(1,64,1); assertEquals(h.childColor,h.colors.getLast());
        h.childClick(3,64,3); assertTrue(h.state.children(parent.id()).getFirst().bounds().fullHeight());
        h.manager.tickPreviews(); assertTrue(h.colors.stream().allMatch(h.childColor::equals));
    }
    @Test void childInspectionAndBlockedClaimUseClaimTypeRatherThanToolOrHeight() {
        Harness h=new Harness(); Claim parent=root(0,9),child=child(parent,60,70); h.state=state(parent,child);
        h.click(2,64,2); assertEquals(h.childColor,h.colors.getLast());
        h.manager.preview(h.player,parent.bounds(),h.world,false); assertEquals(h.parentColor,h.colors.getLast());
        h.manager.preview(h.player,child.bounds(),h.world,true); assertEquals(h.childColor,h.colors.getLast());
        h.childClick(1,60,1); assertEquals(h.childColor,h.colors.getLast());
    }
    @Test void invalidChildCreationClearsSelectedParentAndBothCorners() {
        Harness h=new Harness(); Claim parent=root(0,9); h.state=state(parent);
        h.childClick(1,64,1); h.childClick(11,64,11);
        assertEquals(1,h.state.claims().size()); h.assertSelectionCleared();
        h.childClick(2,64,2); assertEquals(1,h.attempts); assertEquals(parent.id(),h.manager.session(h.player).parent);
    }
    @Test void resizeThatExcludesChildClearsSelectionWithoutChangingEitherClaim() {
        Harness h=new Harness(); Claim parent=root(0,9),child=child(parent,10,20); h.state=state(parent,child);
        h.click(0,64,0); h.click(5,64,5);
        h.assertSelectionCleared(); assertEquals(parent,h.state.require(parent.id())); assertEquals(child,h.state.require(child.id()));
    }
    @Test void busyStorageSubmissionAlsoResetsSelection() {
        Harness h=new Harness(); h.storageBusy=true; h.click(0,64,0); h.click(9,64,9);
        h.assertSelectionCleared(); assertTrue(h.state.claims().isEmpty());
        assertEquals(List.of("Still saving. Please try again in a moment."),h.denials);
    }
}
