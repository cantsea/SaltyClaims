package org.saltymc.config;

import org.junit.jupiter.api.Test;
import org.saltymc.message.Messages;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MessageMigrationV6Test {
    @Test void upgradesStockToolInstructionsAndRemovesOnlyObsoleteModePaths() {
        String yaml="commands:\n  tool: 'Select with {selection}, inspect with {inspection}. Right-click two corners to claim immediately. Click an owned corner, then its new position, to resize.'\n  mode-basic: 'custom old instruction'\n  mode-child: 'custom old instruction'\nerrors:\n  select-root: 'Select a root with /claim mode child.'\n  child-mode: 'old'\nconfig-version: 5\n";
        var candidate=ConfigFiles.messagesFile().prepare(yaml.getBytes(StandardCharsets.UTF_8));
        Messages messages=Messages.from(candidate.document());
        assertTrue(messages.text("commands.tool","selection","GOLDEN_SHOVEL","child-selection","IRON_SHOVEL","inspection","STICK").contains("subdivide with IRON_SHOVEL"));
        assertEquals("Use the child claim tool inside your parent claim.",messages.text("errors.select-root"));
        assertFalse(((Map<?,?>)candidate.document().get("commands")).containsKey("mode-child"));
        assertFalse(((Map<?,?>)candidate.document().get("commands")).containsKey("mode-basic"));
        assertFalse(((Map<?,?>)candidate.document().get("errors")).containsKey("child-mode"));
        assertFalse(ConfigFiles.messagesFile().prepare(candidate.output()).changed());
    }
    @Test void customToolInstructionsAndPrefixArePreserved() {
        var candidate=ConfigFiles.messagesFile().prepare("prefix: 'Custom '\ncommands:\n  tool: 'My {selection} and {inspection} tools'\nconfig-version: 5\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("My GOLD and STICK tools",Messages.from(candidate.document()).text("commands.tool","selection","GOLD","inspection","STICK"));
        assertEquals("Custom ",candidate.document().get("prefix"));
    }
}
