package org.saltymc.selection;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.saltymc.claim.*;
import org.saltymc.config.Settings;
import org.saltymc.message.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class SelectionManagerTest {
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
        long now; int duration=10,particles;
        final Player player=proxy(Player.class,(name,args)->switch(name) {
            case "getUniqueId"->OWNER; case "getName"->"Alex"; case "getWorld"->world; case "getLocation"->point(0,64,0);
            case "isOnline"->true;
            case "spawnParticle"->{ particles++; yield null; }
            case "hasPermission"->permissions.contains(args[0]);
            case "sendMessage"->{ if(args[0] instanceof String text) feedback.add(text); yield null; }
            default->null;
        });
        final SelectionManager manager=new SelectionManager(this,this::commit,()->now);
        int attempts;
        Location point(int x,int y,int z) { return new Location(world,x,y,z); }
        void click(int x,int y,int z) { manager.selectCorner(player,point(x,y,z)); }
        void commit(Player player) {
            attempts++;
            var selection=manager.session(player); Bounds bounds=manager.bounds(player); Claim claim;
            if(selection.resize!=null) {
                Claim old=state.require(selection.resize);
                if(old.revision()!=selection.revision) throw new MessageException("errors.resize-changed");
                claim=old.change(bounds,old.inheritTrust(),old.trust(),old.flags());
            } else {
                boolean root=selection.mode.equals("basic");
                claim=new Claim(UUID.randomUUID(),selection.first.getWorld().getUID(),root?OWNER:null,root?null:selection.parent,bounds,true,1,0,Map.of(),Map.of());
            }
            state=ClaimService.put(state,claim,settings().limits(),false); manager.finish(player);
        }
        public boolean ready() { return true; }
        public Settings settings() { return new Settings(new ClaimService.Limits(100,3,20,100,1000000),Material.GOLDEN_SHOVEL,Material.STICK,120,duration,Set.of(),Map.of(),Map.of(),Map.of()); }
        public Messages messages() { return Messages.defaults(); }
        public ClaimState state() { return state; }
        public Claim at(Location location) { return new ClaimIndex(state).at(location.getWorld().getUID(),location.getBlockX(),location.getBlockY(),location.getBlockZ()); }
        public void deny(Player player,String reason) { denials.add(reason); }
        public void forget(UUID player) { }
        public String playerName(UUID player) { return player.equals(OWNER)?"Alex":"Sam"; }
        public void showBounds(Player player,Bounds bounds,World world) { borders.add(bounds); manager.preview(player,bounds,world); }
    }
    @Test void secondToolClickImmediatelyCreatesWithoutChatAction() {
        Harness h=new Harness(); h.click(0,64,0); assertEquals(0,h.attempts); h.click(9,64,9);
        assertEquals(1,h.attempts); assertEquals(1,h.state.claims().size()); assertEquals(100,h.state.used(OWNER)); assertTrue(h.denials.isEmpty());
        assertTrue(h.feedback.stream().noneMatch(text->text.contains("/claim create")||text.contains("confirm")));
    }
    @Test void insufficientBlocksRejectAndRetainFirstCornerForRetry() {
        Harness h=new Harness(); h.click(0,64,0); h.click(10,64,10);
        assertTrue(h.state.claims().isEmpty()); assertEquals(List.of("Not enough claim blocks."),h.denials);
        assertEquals(h.point(0,64,0),h.manager.session(h.player).first); assertNull(h.manager.session(h.player).second);
        h.click(9,64,9); assertEquals(2,h.attempts); assertEquals(100,h.state.used(OWNER));
    }
    @Test void overlappingSelectionIsRejectedAndCorrectedSecondCornerCanSucceed() {
        Harness h=new Harness(); Claim other=new Claim(UUID.randomUUID(),WORLD,STRANGER,null,new Bounds(5,10,5,10,null,null),true,1,0,Map.of(),Map.of()); h.state=state(other);
        h.click(0,64,0); h.click(12,64,12);
        assertEquals(Set.of(other.id()),h.state.claims().keySet()); assertTrue(h.denials.getFirst().contains("overlaps"));
        h.click(3,64,3); assertEquals(2,h.state.claims().size());
    }
    @Test void permissionIsRecheckedAtSecondClick() {
        Harness h=new Harness(); h.click(0,64,0); h.permissions.clear(); h.click(9,64,9);
        assertEquals(0,h.attempts); assertTrue(h.state.claims().isEmpty()); assertTrue(h.denials.getFirst().contains("Missing permission"));
    }
    @Test void cornerResizeCommitsOnNextClickWithoutChat() {
        Harness h=new Harness(); Claim original=root(0,9); h.state=state(original);
        h.click(0,64,0); assertEquals(original.id(),h.manager.session(h.player).resize); assertEquals(0,h.attempts);
        h.click(2,64,2); assertEquals(new Bounds(2,9,2,9,null,null),h.state.require(original.id()).bounds()); assertEquals(1,h.attempts);
    }
    @Test void threeDimensionalChildSelectionAutoCreates() {
        Harness h=new Harness(); Claim root=root(0,9); h.state=state(root); var selection=h.manager.session(h.player); selection.mode="3d"; selection.parent=root.id();
        h.click(1,10,1); h.click(3,12,3); Claim child=h.state.children(root.id()).getFirst();
        assertEquals(new Bounds(1,3,1,3,10,12),child.bounds()); assertEquals(100,h.state.used(OWNER));
    }
    @Test void rootCornerInChildModeDoesNotResizeParent() {
        Harness h=new Harness(); Claim root=root(0,9); h.state=state(root); var selection=h.manager.session(h.player); selection.mode="full"; selection.parent=root.id();
        h.click(0,64,0); assertNull(h.manager.session(h.player).resize); h.click(2,64,2);
        assertEquals(root,h.state.require(root.id())); assertEquals(1,h.state.children(root.id()).size());
    }
    @Test void crossWorldSecondCornerIsRejectedWithoutLosingFirst() {
        Harness h=new Harness(); h.click(0,64,0);
        h.manager.selectCorner(h.player,new Location(world(UUID.randomUUID()),9,64,9));
        assertEquals(0,h.attempts); assertNull(h.manager.session(h.player).second); assertEquals(h.point(0,64,0),h.manager.session(h.player).first);
    }
    @Test void firstBlockBorderExpandsToRootRectangleRegardlessOfY() {
        Harness h=new Harness(); h.click(0,64,0);
        assertEquals(new Bounds(0,0,0,0,64,64),h.borders.getLast());
        h.click(9,70,9); assertEquals(new Bounds(0,9,0,9,null,null),h.borders.getLast());
        assertTrue(h.state.claims().values().iterator().next().bounds().fullHeight());
    }
    @Test void childHeightIsInferredEvenWithLegacyModeValues() {
        for(String mode:List.of("child","full","3d")) for(boolean sameY:List.of(true,false)) {
            Harness h=new Harness(); Claim root=root(0,9); h.state=state(root);
            var selection=h.manager.session(h.player); selection.mode=mode; selection.parent=root.id();
            h.click(1,20,1); h.click(3,sameY?20:22,3);
            Bounds bounds=h.state.children(root.id()).getFirst().bounds();
            assertEquals(sameY,bounds.fullHeight(),mode);
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
    @Test void claimedSecondCornerKeepsOriginalSelectionAndShowsClaimInstead() {
        Harness h=new Harness(); Claim claim=new Claim(UUID.randomUUID(),WORLD,STRANGER,null,new Bounds(5,10,5,10,null,null),true,1,0,Map.of(),Map.of()); h.state=state(claim);
        h.click(0,64,0); h.click(6,64,6);
        assertEquals(h.point(0,64,0),h.manager.session(h.player).first); assertNull(h.manager.session(h.player).second);
        assertEquals(0,h.attempts); assertEquals(claim.bounds(),h.borders.getLast());
        assertEquals(List.of("This land is already claimed by Sam."),h.denials);
        h.click(3,64,3); assertEquals(2,h.state.claims().size());
    }
    @Test void anotherPlayersCornerCannotStartResize() {
        Harness h=new Harness(); Claim claim=new Claim(UUID.randomUUID(),WORLD,STRANGER,null,new Bounds(0,9,0,9,null,null),true,1,0,Map.of(),Map.of()); h.state=state(claim);
        h.click(0,64,0); assertNull(h.manager.session(h.player).resize); assertNull(h.manager.session(h.player).first);
        assertEquals(claim.bounds(),h.borders.getLast());
    }
    @Test void childSelectionRejectsExistingChildInterior() {
        Harness h=new Harness(); Claim parent=root(0,9);
        Claim child=new Claim(UUID.randomUUID(),WORLD,null,parent.id(),new Bounds(1,5,1,5,null,null),true,1,0,Map.of(),Map.of()); h.state=state(parent,child);
        var selection=h.manager.session(h.player); selection.mode="child"; selection.parent=parent.id();
        h.click(3,64,3); assertNull(selection.first); assertEquals(child.bounds(),h.borders.getLast());
        assertEquals(List.of("This land is already claimed by Alex."),h.denials);
    }
}
