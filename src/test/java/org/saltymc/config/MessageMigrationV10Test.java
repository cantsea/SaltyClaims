package org.saltymc.config;

import org.junit.jupiter.api.Test;
import org.saltymc.message.Messages;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MessageMigrationV10Test {
    private YamlFileSpec.Candidate prepare(String yaml) { return ConfigFiles.messagesFile().prepare(yaml.getBytes(StandardCharsets.UTF_8)); }
    @Test void movesCustomToolInstructionsIntoHelpAndPreservesOtherEdits() {
        var candidate=prepare("prefix: 'Custom '\ncommands:\n  tool: 'Pick {selection}, divide with {child-selection}'\n  help-player: 'My claiming tips.'\nconfig-version: 9\n");
        Messages messages=Messages.from(candidate.document());
        assertEquals("Pick stick, divide with bone",messages.text("commands.help-tools","selection","stick","child-selection","bone"));
        assertEquals("My claiming tips.",messages.text("commands.help-player"));
        assertFalse(((Map<?,?>)candidate.document().get("commands")).containsKey("tool"));
        assertFalse(ConfigFiles.messagesFile().prepare(candidate.output()).changed());
    }
    @Test void conflictingRenamedDestinationIsRejectedAndEqualDestinationIsIdempotent() {
        assertThrows(ConfigException.class,()->prepare("commands:\n  tool: 'Old custom'\n  help-tools: 'Different custom'\nconfig-version: 9\n"));
        var candidate=prepare("commands:\n  tool: 'Same custom'\n  help-tools: 'Same custom'\nconfig-version: 9\n");
        assertEquals("Same custom",Messages.from(candidate.document()).text("commands.help-tools"));
    }
    @Test void stockHelpBecomesClaimingInstructions() {
        var candidate=prepare("commands:\n  help-player: 'Commands apply to the claim you are standing in.'\nconfig-version: 9\n");
        assertTrue(Messages.from(candidate.document()).text("commands.help-player").contains("two opposite corners"));
    }
}
