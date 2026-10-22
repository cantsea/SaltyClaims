package org.saltymc.config;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ToolMigrationV4Test {
    @Test void oldStockSetsIncludingPreChildVersionsUpgradeTogether() {
        for(String yaml:new String[]{"tools:\n  selection: GOLDEN_SHOVEL\n  child-selection: IRON_SHOVEL\n  inspection: STICK\nconfig-version: 3\n",
                "tools:\n  selection: GOLDEN_SHOVEL\n  inspection: STICK\nconfig-version: 1\n",""}) {
            var candidate=ConfigFiles.settingsFile().prepare(yaml.getBytes(StandardCharsets.UTF_8));
            Settings settings=Settings.from(candidate.document());
            assertEquals(Material.STICK,settings.selectionTool()); assertEquals(Material.BONE,settings.childSelectionTool()); assertEquals(Material.FEATHER,settings.inspectionTool());
            assertFalse(ConfigFiles.settingsFile().prepare(candidate.output()).changed());
        }
    }
    @Test void customizedSetIsPreservedIncludingFormerDefaultsWithinIt() {
        var candidate=ConfigFiles.settingsFile().prepare("tools:\n  selection: DIAMOND_SHOVEL\n  child-selection: IRON_SHOVEL\n  inspection: STICK\nconfig-version: 3\n".getBytes(StandardCharsets.UTF_8));
        Settings settings=Settings.from(candidate.document());
        assertEquals(Material.DIAMOND_SHOVEL,settings.selectionTool()); assertEquals(Material.IRON_SHOVEL,settings.childSelectionTool()); assertEquals(Material.STICK,settings.inspectionTool());
    }
    @Test void currentVersionExplicitlyConfiguredOldSetIsNotReset() {
        var candidate=ConfigFiles.settingsFile().prepare("tools:\n  selection: GOLDEN_SHOVEL\n  child-selection: IRON_SHOVEL\n  inspection: STICK\nconfig-version: 4\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(Material.GOLDEN_SHOVEL,Settings.from(candidate.document()).selectionTool());
    }
}
