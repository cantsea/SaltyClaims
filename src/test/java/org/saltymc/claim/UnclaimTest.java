package org.saltymc.claim;

import org.junit.jupiter.api.Test;
import org.saltymc.message.MessageException;
import org.saltymc.trust.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class UnclaimTest {
    @Test void serverManagedModeProtectsParentsButAllowsOwnedChildRemoval() {
        Claim parent=root(0,9),child=child(parent,1,5); ClaimState before=state(parent,child);
        assertThrows(MessageException.class,()->ClaimService.unclaim(before,OWNER,parent.id(),true));
        assertThrows(MessageException.class,()->ClaimService.unclaimAll(before,OWNER,true));
        assertThrows(MessageException.class,()->ClaimService.unclaim(before,FRIEND,child.id(),true));
        assertEquals(Map.of(parent.id(),parent),ClaimService.unclaim(before,OWNER,child.id(),true).claims());
        assertTrue(ClaimService.unclaimAll(before,OWNER,false).claims().isEmpty());
    }
    @Test void singleUnclaimSelectsChildFirstAndParentDeletionIncludesChildren() {
        Claim parent=root(0,9),child=child(parent,1,5); ClaimState before=state(parent,child);
        Claim selected=new ClaimIndex(before).at(WORLD,2,3,2);
        ClaimState after=ClaimService.unclaim(before,OWNER,selected.id());
        assertEquals(Map.of(parent.id(),parent),after.claims());
        assertTrue(ClaimService.unclaim(before,OWNER,parent.id()).claims().isEmpty());
        assertEquals(2,before.claims().size());
    }
    @Test void unclaimAllRemovesOnlyOwnedTreesAndPreservesBalancesAndOtherOwners() {
        Claim parent=root(0,9),child=child(parent,1,5);
        Claim other=new Claim(UUID.randomUUID(),WORLD,STRANGER,null,new Bounds(20,29,20,29,null,null),true,1,0,Map.of(new Subject(OWNER),Trust.MANAGE),Map.of());
        ClaimState before=new ClaimState(state(parent,child,other).claims(),Map.of(OWNER,new ClaimState.Account(25,50),STRANGER,new ClaimState.Account(5,10)));
        ClaimState after=ClaimService.unclaimAll(before,OWNER);
        assertEquals(Map.of(other.id(),other),after.claims()); assertEquals(before.accounts(),after.accounts());
        assertEquals(0,after.used(OWNER)); assertEquals(100,after.used(STRANGER));
        assertThrows(MessageException.class,()->ClaimService.unclaim(before,OWNER,other.id()));
        assertThrows(MessageException.class,()->ClaimService.unclaimAll(before,FRIEND));
    }
}
