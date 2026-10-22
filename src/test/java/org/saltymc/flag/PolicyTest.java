package org.saltymc.flag;

import org.junit.jupiter.api.Test;
import org.saltymc.claim.*;
import org.saltymc.config.*;
import org.saltymc.trust.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class PolicyTest {
    private Policy.Value value(ClaimState s,Claim c,Flag f) { return Policy.resolve(s,c,f,"world",editableDefaults(),Map.of()); }
    private Map<Flag,Settings.ClaimFlag> editableDefaults() {
        Map<Flag,Settings.ClaimFlag> flags=new EnumMap<>(Flag.class);
        for(Flag flag:Flag.values()) flags.put(flag,new Settings.ClaimFlag(flag.actor(),true));
        return flags;
    }
    @Test void defaultClaimTrustAndAllowedWildernessAreIndependent() {
        Claim root=root(0,9); ClaimState s=state(root);
        assertFalse(Policy.check(s,root,STRANGER,Flag.BLOCK_BREAK,value(s,root,Flag.BLOCK_BREAK),false).allowed());
        assertTrue(Policy.check(s,root,OWNER,Flag.BLOCK_BREAK,value(s,root,Flag.BLOCK_BREAK),false).allowed());
        assertTrue(Policy.check(s,null,STRANGER,Flag.BLOCK_BREAK,value(s,null,Flag.BLOCK_BREAK),false).allowed());
        var world=new WorldSettings(false,Map.of(Flag.BLOCK_BREAK,false));
        var wilderness=Policy.resolve(s,null,Flag.BLOCK_BREAK,"world",editableDefaults(),Map.of("world",world));
        assertFalse(Policy.check(s,null,STRANGER,Flag.BLOCK_BREAK,wilderness,false).allowed());
        assertFalse(world.claiming());
    }
    @Test void childUsesDefaultsInsteadOfParentOverridesAndCanSetItsOwnFlags() {
        Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(),Map.of(Flag.PVP,"true")); Claim child=child(root,1,5);
        ClaimState s=state(root,child);
        assertEquals("allow",value(s,root,Flag.PVP).value());
        assertEquals("deny",value(s,child,Flag.PVP).value());
        assertEquals("claim default",value(s,child,Flag.PVP).source());
        assertEquals("deny",Policy.resolve(s,null,Flag.PVP,"world",editableDefaults(),Map.of("world",new WorldSettings(false,Map.of(Flag.PVP,false)))).value());
        child=child.change(child.bounds(),false,Map.of(),Map.of(Flag.PVP,"true")); s=state(root,child);
        assertEquals("child override",value(s,child,Flag.PVP).source());
        assertEquals("allow",value(s,child,Flag.PVP).value());
    }
    @Test void lockedDefaultsOverrideSavedRootAndChildFlags() {
        Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(),Map.of(Flag.PVP,"allow"));
        Claim child=child(root,1,5); child=child.change(child.bounds(),true,Map.of(),Map.of(Flag.PVP,"true"));
        for(Claim c:List.of(root,child)) {
            var v=Policy.resolve(state(root,child),c,Flag.PVP,"world",Map.of(Flag.PVP,new Settings.ClaimFlag(false,false)),Map.of());
            assertEquals("deny",v.value()); assertEquals("locked claim default",v.source());
        }
    }
    @Test void childUsesConfiguredDefaultEvenWhenParentDisablesBehavior() {
        Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(),Map.of(Flag.PVP,"allow"));
        Claim child=child(root,1,5); child=child.change(child.bounds(),false,Map.of(),Map.of());
        assertEquals("deny",value(state(root,child),child,Flag.PVP).value());
        var enabled=Map.of(Flag.PVP,new Settings.ClaimFlag(true,true));
        root=root.change(root.bounds(),true,Map.of(),Map.of(Flag.PVP,"false"));
        assertEquals("allow",Policy.resolve(state(root,child),child,Flag.PVP,"world",enabled,Map.of()).value());
    }
    @Test void disabledActionBlocksOwnerAndStaffBypassStillWorks() {
        Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(),Map.of(Flag.BLOCK_PLACE,"false"));
        ClaimState s=state(root); var v=value(s,root,Flag.BLOCK_PLACE);
        assertFalse(Policy.check(s,root,OWNER,Flag.BLOCK_PLACE,v,false).allowed());
        assertTrue(Policy.check(s,root,STRANGER,Flag.BLOCK_PLACE,v,true).allowed());
    }
    @Test void enablingActionKeepsTrustRequirementsEvenForLegacyOverrides() {
        for(String enabled:List.of("true","public","access","owner")) {
            Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(new Subject(FRIEND),Trust.BUILD),Map.of(Flag.BLOCK_BREAK,enabled));
            ClaimState s=state(root); var v=value(s,root,Flag.BLOCK_BREAK);
            assertFalse(Policy.check(s,root,STRANGER,Flag.BLOCK_BREAK,v,false).allowed());
            assertTrue(Policy.check(s,root,FRIEND,Flag.BLOCK_BREAK,v,false).allowed());
            assertFalse(Policy.check(s,root,null,Flag.BLOCK_BREAK,v,false).allowed());
        }
    }
    @Test void perWorldWildernessRulesDoNotLeakBetweenWorlds() {
        var worlds=Map.of("open",new WorldSettings(false,Map.of(Flag.PVP,true)),"closed",new WorldSettings(false,Map.of(Flag.PVP,false)));
        assertEquals("allow",Policy.resolve(ClaimState.empty(),null,Flag.PVP,"open",Map.of(),worlds).value());
        assertEquals("deny",Policy.resolve(ClaimState.empty(),null,Flag.PVP,"closed",Map.of(),worlds).value());
        assertEquals("allow",Policy.resolve(ClaimState.empty(),null,Flag.PVP,"unknown",Map.of(),worlds).value());
    }
    @Test void flagsAcceptBooleanStorageAndKeepLegacyValuesReadable() {
        assertEquals("true",Flag.BLOCK_BREAK.validate("true"));
        assertEquals("false",Flag.PVP.validate("false"));
        assertThrows(IllegalArgumentException.class,()->Flag.PVP.validate("build"));
        assertThrows(IllegalArgumentException.class,()->Flag.parse("made-up"));
        assertEquals("build",Flag.BLOCK_BREAK.validate("BUILD"));
    }
}
