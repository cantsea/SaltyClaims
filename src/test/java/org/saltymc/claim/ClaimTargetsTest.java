package org.saltymc.claim;

import org.junit.jupiter.api.Test;
import org.saltymc.message.MessageException;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class ClaimTargetsTest {
    @Test void singleOwnedRootCanBeSelectedByOwnerWithoutLocation() {
        Claim root=root(0,9); assertEquals(root,ClaimTargets.select(state(root),OWNER,null,false,false));
    }
    @Test void multipleOwnedRootsRequireContextRatherThanGuessing() {
        Claim a=root(0,9),b=root(20,29); ClaimState state=state(a,b);
        assertThrows(MessageException.class,()->ClaimTargets.select(state,OWNER,null,false,false));
        assertEquals(b,ClaimTargets.select(state,OWNER,b,false,false));
    }
    @Test void explicitLocationMustMatchNamedOwner() {
        Claim root=root(0,9); assertThrows(MessageException.class,()->ClaimTargets.select(state(root),STRANGER,root,true,false));
        assertThrows(MessageException.class,()->ClaimTargets.select(state(root),OWNER,null,true,false));
    }
    @Test void contextualChildAndExplicitRootScopeAreDifferent() {
        Claim root=root(0,9),child=child(root,1,5); ClaimState state=state(root,child);
        assertEquals(child,ClaimTargets.select(state,OWNER,child,true,false)); assertEquals(root,ClaimTargets.select(state,OWNER,child,true,true));
    }
    @Test void missingContextOrOwnerClaimsIsRejected() {
        assertThrows(MessageException.class,()->ClaimTargets.select(ClaimState.empty(),null,null,false,false));
        assertThrows(MessageException.class,()->ClaimTargets.select(ClaimState.empty(),OWNER,null,false,false));
    }
}
