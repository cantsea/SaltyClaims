package org.saltymc.claim;

import org.junit.jupiter.api.Test;
import org.saltymc.Fixtures;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class ClaimRulesTest {
    private final ClaimService.Limits limits=new ClaimService.Limits(100,1,20,10,1000000);
    @Test void fullHeightAndInclusiveCoordinates() {
        Bounds b=new Bounds(-16,-1,-16,-1,null,null);
        assertEquals(256,b.area()); assertEquals(1,b.chunks()); assertTrue(b.contains(-1,-10000,-1));
        assertTrue(b.contains(-16,10000,-16)); assertFalse(b.contains(0,0,0));
        assertThrows(IllegalArgumentException.class,()->new Bounds(0,1,0,1,1,null));
    }
    @Test void checkedAreaCannotOverflow() {
        assertThrows(ArithmeticException.class,()->new Bounds(Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,null,null).area());
    }
    @Test void rootsRejectOverlapsIncludingSharedEdge() {
        Claim a=root(0,4), b=root(4,8); assertThrows(IllegalArgumentException.class,()->state(a,b).validate());
        state(a,root(5,8)).validate();
    }
    @Test void differentWorldsDoNotOverlap() {
        Claim a=root(0,4), b=new Claim(UUID.randomUUID(),UUID.randomUUID(),OWNER,null,a.bounds(),true,1,0,Map.of(),Map.of());
        assertDoesNotThrow(()->state(a,b).validate());
    }
    @Test void childrenMayStackButNeverIntersect() {
        Claim root=root(0,9), a=child(root,-64,0), b=child(root,1,30);
        ClaimState state=state(root,a,b); state.validate(); ClaimIndex index=new ClaimIndex(state);
        assertEquals(a,index.at(WORLD,2,-1,2)); assertEquals(b,index.at(WORLD,2,1,2)); assertEquals(root,index.at(WORLD,2,31,2));
        assertThrows(IllegalArgumentException.class,()->state(root,a,child(root,0,30)).validate());
    }
    @Test void fullHeightChildConflictsWithAnyStackedSibling() {
        Claim root=root(0,9), bounded=child(root,10,20);
        Claim full=new Claim(UUID.randomUUID(),WORLD,null,root.id(),new Bounds(1,4,1,4,null,null),true,1,0,Map.of(),Map.of());
        assertThrows(IllegalArgumentException.class,()->state(root,bounded,full).validate());
    }
    @Test void childCanNeverParentAnotherChild() {
        Claim root=root(0,9), child=child(root,0,30), grandchild=child(child,1,2);
        assertThrows(IllegalArgumentException.class,()->state(root,child,grandchild).validate());
    }
    @Test void shrinkingRootCannotExcludeChild() {
        Claim root=root(0,9), child=child(root,0,30);
        Claim replacement=root.change(new Bounds(5,9,5,9,null,null),true,Map.of(),Map.of());
        assertThrows(IllegalArgumentException.class,()->ClaimService.put(state(root,child),replacement,limits,false));
    }
    @Test void budgetUsesRootAreaAndChildrenAreFree() {
        Claim root=root(0,9); ClaimState created=ClaimService.put(ClaimState.empty(),root,limits,false);
        ClaimState withChild=ClaimService.put(created,child(root,1,5),limits,false);
        assertEquals(100,withChild.used(OWNER));
        assertThrows(IllegalArgumentException.class,()->ClaimService.put(withChild,root(20,21),limits,false));
    }
    @Test void deletingChildPreservesRootAndDeletingRootRemovesChildren() {
        Claim root=root(0,9), child=child(root,1,5); ClaimState s=state(root,child);
        assertEquals(Set.of(root.id()),ClaimService.delete(s,child.id()).claims().keySet());
        assertTrue(ClaimService.delete(s,root.id()).claims().isEmpty());
        assertEquals(0,ClaimService.delete(s,root.id()).used(OWNER));
    }
    @Test void shrinkStillAllowedAfterAllowanceReduction() {
        Claim root=root(0,19); ClaimState s=state(root);
        Claim smaller=root.change(new Bounds(0,14,0,14,null,null),true,Map.of(),Map.of());
        assertDoesNotThrow(()->ClaimService.put(s,smaller,limits,false));
    }
    @Test void negativeChunkBoundariesLookupCorrectly() {
        Claim c=root(-17,-16); ClaimIndex index=new ClaimIndex(state(c));
        assertEquals(c,index.at(WORLD,-17,0,-17)); assertEquals(c,index.at(WORLD,-16,0,-16)); assertNull(index.at(WORLD,-15,0,-16));
    }
    @Test void largeDatasetLookupSmokeBenchmark() {
        Map<UUID,Claim> claims=new HashMap<>();
        for(int i=0;i<10000;i++) { Claim c=new Claim(UUID.randomUUID(),WORLD,OWNER,null,new Bounds(i*32,i*32+3,0,3,null,null),true,1,0,Map.of(),Map.of()); claims.put(c.id(),c); }
        ClaimState s=new ClaimState(claims,Map.of()); s.validate(); long begin=System.nanoTime(); ClaimIndex index=new ClaimIndex(s);
        for(int i=0;i<100000;i++) assertNotNull(index.at(WORLD,(i%10000)*32,0,0));
        System.out.println("10,000 roots / 100,000 indexed lookups + construction: "+(System.nanoTime()-begin)/1000000+"ms");
    }
}
