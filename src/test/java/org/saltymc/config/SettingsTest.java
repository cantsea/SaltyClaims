package org.saltymc.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saltymc.flag.Flag;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @Test void serverManagedModeDefaultsOffAndRequiresBooleanValues() throws Exception {
        assertFalse(load("config-version: 8\n").serverManagesClaims());
        assertTrue(load("server-manages-claims: true\n").serverManagesClaims());
        for(String value:List.of("null","'true'","1","[]"))
            assertThrows(ConfigException.class,()->load("server-manages-claims: "+value+"\n"));
    }
    @TempDir Path directory;
    private Settings load(String yaml) throws Exception {
        Path path=directory.resolve("config.yml"); Files.writeString(path,yaml); return Settings.load(path.toFile());
    }
    @Test void booleanClaimDefaultsAndEditability() throws Exception {
        Settings settings=load("claim-flags:\n  pvp:\n    default: true\n    player-editable: true\n");
        assertEquals(10000,settings.limits().starting());
        assertTrue(settings.claimFlag(Flag.PVP).defaultValue());
        assertTrue(settings.claimFlag(Flag.PVP).playerEditable());
        assertTrue(settings.claimFlag(Flag.BLOCK_BREAK).defaultValue());
        assertFalse(settings.claimFlag(Flag.BLOCK_BREAK).playerEditable());
    }
    @Test void malformedAndUnknownPoliciesAreRejected() {
        for(String yaml:new String[]{"claim-flags:\n  imaginary-flag: {default: false, player-editable: false}\n",
                "claim-flags:\n  pvp: {default: allow}\n","claim-flags: deny\n",
                "claim-flags:\n  pvp: {default: false, player-editable: 'true'}\n",
                "claim-flags: null\n", "claim-flags:\n  pvp: null\n", "claim-flags:\n  pvp: {default: null}\n"})
            assertThrows(IllegalArgumentException.class,()->load(yaml));
    }
    @Test void invalidLimitsAndTypesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->load("claims:\n  starting-blocks: -1\n"));
        assertThrows(IllegalArgumentException.class,()->load("claims:\n  starting-blocks: bananas\n"));
        assertThrows(IllegalArgumentException.class,()->load("claims:\n  minimum-width: 1.5\n"));
        assertThrows(IllegalArgumentException.class,()->load("disabled-worlds: world\n"));
    }
    @Test void failedReplacementDoesNotMutateExistingSettings() throws Exception {
        Settings original=load("claims:\n  starting-blocks: 1234\n");
        assertThrows(IllegalArgumentException.class,()->load("tools:\n  selection: STICK\n  inspection: STICK\n"));
        assertEquals(1234,original.limits().starting());
    }
    @Test void particleDurationIsStrictAndPreservedDuringUpgrade() throws Exception {
        assertEquals(10,load("config-version: 1\n").previewSeconds());
        assertEquals(45,load("particle-duration-seconds: 45\nconfig-version: 1\n").previewSeconds());
        assertEquals(0,load("particle-duration-seconds: 0\n").previewSeconds());
        for(String value:new String[]{"-1","301","1.5","'10'","null"})
            assertThrows(IllegalArgumentException.class,()->load("particle-duration-seconds: "+value+"\n"),value);
    }
    @Test void distinctChildToolIsAddedAndCustomizedItemsArePreserved() throws Exception {
        Settings settings=load("tools:\n  selection: DIAMOND_SHOVEL\nconfig-version: 2\n");
        assertEquals(org.bukkit.Material.DIAMOND_SHOVEL,settings.selectionTool());
        assertEquals(org.bukkit.Material.BONE,settings.childSelectionTool());
        assertEquals(org.bukkit.Material.WOODEN_SHOVEL,load("tools:\n  child-selection: WOODEN_SHOVEL\n").childSelectionTool());
        for(String item:new String[]{"STICK","FEATHER","AIR","nonsense"})
            assertThrows(IllegalArgumentException.class,()->load("tools:\n  child-selection: "+item+"\n"),item);
    }
    @Test void upgradedConfigIncludesEveryFlagAndTrustReferenceWithoutChangingSettings() {
        var candidate=ConfigFiles.settingsFile().prepare("claims:\n  starting-blocks: 12345\nconfig-version: 4\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String yaml=new String(candidate.output(),java.nio.charset.StandardCharsets.UTF_8);
        for(Flag flag:Flag.values()) assertTrue(yaml.contains("  "+flag.key()+":"),flag.key());
        assertTrue(yaml.contains("player-editable: false"));
        assertTrue(yaml.contains("Requires Container trust"));
        assertEquals(12345,Settings.from(candidate.document()).limits().starting());
    }
    @Test void borderColorsAdoptDefaultsAndPreserveConfiguredHexValues() throws Exception {
        Settings defaults=load("config-version: 5\n");
        assertEquals(0xFFFF00,defaults.parentBorderColor().asRGB()); assertEquals(0x00FFFF,defaults.childBorderColor().asRGB());
        Settings custom=load("border-colors:\n  parent: '#123456'\n  child: '#aBcDeF'\nconfig-version: 5\n");
        assertEquals(0x123456,custom.parentBorderColor().asRGB()); assertEquals(0xABCDEF,custom.childBorderColor().asRGB());
        for(String value:new String[]{"red","'#FFF'","'#GG0000'","'#12345678'","123456","null","[]"})
            assertThrows(IllegalArgumentException.class,()->load("border-colors:\n  child: "+value+"\n"),value);
    }
}
