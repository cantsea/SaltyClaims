package org.saltymc.claim;

import org.junit.jupiter.api.Test;
import org.saltymc.trust.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class ChildCreationTest {
    private final Bounds bounds = new Bounds(1,4,1,4,10,20);

    private Claim parent() {
        Claim root = root(0,9);
        return root.change(root.bounds(),true,Map.of(new Subject(FRIEND),Trust.MANAGE,
                new Subject(STRANGER),Trust.BUILD,Subject.PUBLIC,Trust.ACCESS),Map.of());
    }

    @Test void ownerCreatedChildExcludesAllParentGrants() {
        Claim root = parent();
        Claim child = ClaimService.newChild(state(root),root.id(),OWNER,bounds,1);
        ClaimState state = state(root,child);
        assertFalse(child.inheritTrust());
        assertTrue(child.trust().isEmpty());
        assertEquals(Trust.OWNER,state.trust(child,OWNER));
        assertEquals(Trust.VISITOR,state.trust(child,FRIEND));
        assertEquals(Trust.VISITOR,state.trust(child,STRANGER));
        assertEquals(Trust.VISITOR,state.trust(child,UUID.randomUUID()));
    }

    @Test void managerCreatedChildRetainsOnlyCreatorAndOwner() {
        Claim root = parent();
        Claim child = ClaimService.newChild(state(root),root.id(),FRIEND,bounds,1);
        ClaimState state = state(root,child);
        assertFalse(child.inheritTrust());
        assertEquals(Map.of(new Subject(FRIEND),Trust.MANAGE),child.trust());
        assertEquals(Trust.OWNER,state.trust(child,OWNER));
        assertEquals(Trust.MANAGE,state.trust(child,FRIEND));
        assertEquals(Trust.VISITOR,state.trust(child,STRANGER));
        assertEquals(Trust.VISITOR,state.trust(child,UUID.randomUUID()));
        Claim changed = root.change(root.bounds(),true,Map.of(new Subject(STRANGER),Trust.MANAGE),Map.of());
        assertEquals(Trust.VISITOR,state(changed,child).trust(child,STRANGER));
        assertEquals(Trust.MANAGE,state(changed,child).trust(child,FRIEND));
    }

    @Test void onlyOwnerOrManagerCanCreateAndChildrenCannotBeParents() {
        for (Trust level : List.of(Trust.ACCESS,Trust.CONTAINER,Trust.BUILD)) {
            Claim root = root(0,9);
            Claim parent = root.change(root.bounds(),true,Map.of(new Subject(FRIEND),level),Map.of());
            assertFalse(ClaimService.canCreateChild(state(parent),parent,FRIEND));
            assertThrows(IllegalArgumentException.class,()->ClaimService.newChild(state(parent),parent.id(),FRIEND,bounds,1));
        }
        Claim root = parent();
        assertThrows(IllegalArgumentException.class,()->ClaimService.newChild(state(root),root.id(),UUID.randomUUID(),bounds,1));
        Claim child = ClaimService.newChild(state(root),root.id(),FRIEND,bounds,1);
        assertFalse(ClaimService.canCreateChild(state(root,child),child,OWNER));
    }
}
