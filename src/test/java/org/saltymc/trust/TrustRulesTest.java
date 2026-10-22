package org.saltymc.trust;

import org.junit.jupiter.api.Test;
import org.saltymc.claim.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class TrustRulesTest {
    @Test void ownersAndManagersCannotTrustOrUntrustThemselvesInRootsOrChildren() {
        Claim root=root(0,9);
        root=root.change(root.bounds(),true,Map.of(new Subject(FRIEND),Trust.MANAGE),Map.of());
        Claim child=child(root,1,5);
        child=child.change(child.bounds(),false,Map.of(new Subject(FRIEND),Trust.MANAGE),Map.of());
        ClaimState state=state(root,child);
        for(Claim claim:List.of(root,child)) for(UUID actor:List.of(OWNER,FRIEND)) for(boolean staff:List.of(false,true)) {
            for(Trust level:List.of(Trust.ACCESS,Trust.BUILD,Trust.MANAGE)) {
                var error=assertThrows(org.saltymc.message.MessageException.class,
                        ()->TrustRules.edit(state,claim,actor,staff,new Subject(actor),level,false,false));
                assertEquals("errors.self-trust",error.notice().key());
            }
            var error=assertThrows(org.saltymc.message.MessageException.class,
                    ()->TrustRules.edit(state,claim,actor,staff,new Subject(actor),null,true,false));
            assertEquals("errors.self-trust",error.notice().key());
        }
    }
    @Test void untrustAllKeepsTheActorsOwnGrant() {
        Claim root=root(0,9);
        root=root.change(root.bounds(),true,Map.of(new Subject(OWNER),Trust.ACCESS,new Subject(FRIEND),Trust.MANAGE,
                new Subject(STRANGER),Trust.BUILD,Subject.PUBLIC,Trust.ACCESS),Map.of());
        ClaimState state=state(root);
        var ownerCleared=TrustRules.edit(state,root,OWNER,false,null,null,true,true);
        assertEquals(Map.of(new Subject(OWNER),Trust.ACCESS),ownerCleared);
        var managerCleared=TrustRules.edit(state,root,FRIEND,false,null,null,true,true);
        assertEquals(Map.of(new Subject(FRIEND),Trust.MANAGE),managerCleared);
    }
    @Test void cumulativeTrustAndOwnerAuthority() {
        for(Trust t:Trust.values()) assertTrue(t.grants(Trust.VISITOR));
        assertTrue(Trust.MANAGE.grants(Trust.BUILD)); assertTrue(Trust.BUILD.grants(Trust.CONTAINER)); assertTrue(Trust.CONTAINER.grants(Trust.ACCESS));
        assertFalse(Trust.BUILD.grants(Trust.MANAGE)); Claim root=root(0,9); assertEquals(Trust.OWNER,state(root).trust(root,OWNER));
    }
    @Test void childrenUseOnlyLocalTrustEvenWhenLegacyInheritanceIsEnabled() {
        Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(new Subject(FRIEND),Trust.BUILD,Subject.PUBLIC,Trust.ACCESS),Map.of());
        Claim child=child(root,1,5); child=child.change(child.bounds(),true,Map.of(new Subject(FRIEND),Trust.ACCESS),Map.of());
        ClaimState s=state(root,child);
        assertFalse(child.inheritTrust());
        assertEquals(Trust.ACCESS,s.trust(child,FRIEND)); assertEquals(Trust.VISITOR,s.trust(child,STRANGER)); assertEquals(Trust.OWNER,s.trust(child,OWNER));
    }
    @Test void managersCannotPromoteOrRemoveManagers() {
        Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(new Subject(FRIEND),Trust.MANAGE,new Subject(STRANGER),Trust.MANAGE),Map.of());
        Claim claim=root; ClaimState s=state(root);
        assertThrows(IllegalArgumentException.class,()->TrustRules.edit(s,claim,FRIEND,false,new Subject(STRANGER),Trust.BUILD,false,false));
        assertThrows(IllegalArgumentException.class,()->TrustRules.edit(s,claim,FRIEND,false,new Subject(STRANGER),null,true,false));
        assertThrows(IllegalArgumentException.class,()->TrustRules.edit(s,claim,FRIEND,false,new Subject(UUID.randomUUID()),Trust.MANAGE,false,false));
        Map<Subject,Trust> cleared=TrustRules.edit(s,claim,FRIEND,false,null,null,true,true); assertEquals(2,cleared.size());
    }
    @Test void managerMayDelegateLowerLevelsAndOwnerMayDelegateManage() {
        Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(new Subject(FRIEND),Trust.MANAGE),Map.of()); ClaimState s=state(root);
        assertEquals(Trust.BUILD,TrustRules.edit(s,root,FRIEND,false,new Subject(STRANGER),Trust.BUILD,false,false).get(new Subject(STRANGER)));
        assertEquals(Trust.MANAGE,TrustRules.edit(s,root,OWNER,false,new Subject(STRANGER),Trust.MANAGE,false,false).get(new Subject(STRANGER)));
    }
    @Test void publicCanNeverManageAndVisitorsCannotDelegate() {
        Claim root=root(0,9); ClaimState s=state(root);
        assertThrows(IllegalArgumentException.class,()->TrustRules.edit(s,root,OWNER,false,Subject.PUBLIC,Trust.MANAGE,false,false));
        assertThrows(IllegalArgumentException.class,()->TrustRules.edit(s,root,FRIEND,false,new Subject(FRIEND),Trust.BUILD,false,false));
    }
    @Test void removingChildTrustDoesNotAffectParentTrustOrLeaveInheritedAccess() {
        Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(new Subject(FRIEND),Trust.BUILD),Map.of()); Claim child=child(root,1,5);
        child=child.change(child.bounds(),true,Map.of(new Subject(FRIEND),Trust.BUILD),Map.of());
        ClaimState s=state(root,child); Map<Subject,Trust> grants=TrustRules.edit(s,child,OWNER,false,new Subject(FRIEND),null,true,false);
        Claim updated=child.change(child.bounds(),true,grants,Map.of()); s=state(root,updated);
        assertTrue(grants.isEmpty()); assertEquals(Trust.VISITOR,s.trust(updated,FRIEND)); assertEquals(Trust.BUILD,s.trust(root,FRIEND));
    }
}
