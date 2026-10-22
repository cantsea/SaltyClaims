package org.saltymc.flag;

import org.junit.jupiter.api.Test;
import org.saltymc.claim.*;
import org.saltymc.trust.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class PolicyTest {
    private Policy.Value value(ClaimState state,Claim c,Flag f) { return Policy.resolve(state,c,f,"world",Map.of(),Map.of(),Map.of()); }
    @Test void defaultClaimProtectionAndPermissiveWilderness() {
        Claim root=root(0,9); ClaimState s=state(root);
        assertFalse(Policy.check(s,root,STRANGER,Flag.BLOCK_BREAK,value(s,root,Flag.BLOCK_BREAK),false).allowed());
        assertTrue(Policy.check(s,root,OWNER,Flag.BLOCK_BREAK,value(s,root,Flag.BLOCK_BREAK),false).allowed());
        assertTrue(Policy.check(s,null,STRANGER,Flag.BLOCK_BREAK,value(s,null,Flag.BLOCK_BREAK),false).allowed());
    }
    @Test void childRootWorldGlobalAndEnforcedPrecedence() {
        Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(),Map.of(Flag.PVP,"allow")); Claim child=child(root,1,5);
        ClaimState s=state(root,child);
        assertEquals("allow",Policy.resolve(s,child,Flag.PVP,"world",Map.of(Flag.PVP,"deny"),Map.of(),Map.of()).value());
        child=child.change(child.bounds(),false,Map.of(),Map.of(Flag.PVP,"deny")); s=state(root,child);
        assertEquals("child override",value(s,child,Flag.PVP).source());
        assertTrue(Policy.resolve(s,child,Flag.PVP,"world",Map.of(),Map.of(Flag.PVP,"allow"),Map.of()).enforced());
        assertEquals("world default",Policy.resolve(s,null,Flag.PVP,"world",Map.of(Flag.PVP,"deny"),Map.of(),Map.of("world",Map.of(Flag.PVP,"allow"))).source());
    }
    @Test void restrictingTrustDoesNotResetFlags() {
        Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(),Map.of(Flag.PVP,"allow"));
        Claim child=child(root,1,5); child=child.change(child.bounds(),false,Map.of(),Map.of());
        assertEquals("allow",value(state(root,child),child,Flag.PVP).value());
    }
    @Test void explicitDenyBlocksOwnerButUnenforcedBypassWorks() {
        Claim root=root(0,9); ClaimState s=state(root); Policy.Value deny=new Policy.Value("deny","root",false);
        assertFalse(Policy.check(s,root,OWNER,Flag.BLOCK_PLACE,deny,false).allowed());
        assertTrue(Policy.check(s,root,STRANGER,Flag.BLOCK_PLACE,deny,true).allowed());
        assertFalse(Policy.check(s,root,OWNER,Flag.BLOCK_PLACE,new Policy.Value("deny","server",true),true).allowed());
    }
    @Test void actorlessActionsNeverInheritOwnerOrTrust() {
        Claim root=root(0,9); ClaimState s=state(root);
        assertFalse(Policy.check(s,root,null,Flag.CONTAINER_USE,value(s,root,Flag.CONTAINER_USE),false).allowed());
        assertTrue(Policy.check(s,root,null,Flag.CONTAINER_USE,new Policy.Value("public","root",false),false).allowed());
    }
    @Test void typedFlagsRejectIncompatibleValues() {
        assertThrows(IllegalArgumentException.class,()->Flag.PVP.validate("build"));
        assertThrows(IllegalArgumentException.class,()->Flag.BLOCK_BREAK.validate("allow"));
        assertThrows(IllegalArgumentException.class,()->Flag.parse("made-up"));
        assertEquals("build",Flag.BLOCK_BREAK.validate("BUILD"));
    }
}
