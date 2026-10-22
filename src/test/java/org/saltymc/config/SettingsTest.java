package org.saltymc.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saltymc.flag.Flag;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @TempDir Path directory;
    private Settings load(String yaml) throws Exception {
        Path path=directory.resolve("config.yml"); Files.writeString(path,yaml); return Settings.load(path.toFile());
    }
    @Test void defaultsAndTypedWorldPolicies() throws Exception {
        Settings settings=load("flags:\n  enforced:\n    pvp: deny\nworlds:\n  world:\n    container-use: public\n");
        assertEquals(10000,settings.limits().starting()); assertEquals("deny",settings.enforced().get(Flag.PVP));
        assertEquals("public",settings.worlds().get("world").get(Flag.CONTAINER_USE));
    }
    @Test void malformedAndUnknownPoliciesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->load("flags:\n  defaults:\n    imaginary-flag: allow\n"));
        assertThrows(IllegalArgumentException.class,()->load("flags:\n  defaults:\n    pvp: build\n"));
        assertThrows(IllegalArgumentException.class,()->load("flags:\n  defaults: deny\n"));
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
        for(Flag flag:Flag.values()) assertTrue(yaml.contains("#   "+flag.key()+" ["),flag.key());
        for(String level:new String[]{"visitor","access","container","build","manage","owner"}) assertTrue(yaml.contains("#   "+level+":"),level);
        assertEquals(12345,Settings.from(candidate.document()).limits().starting());
    }
}
