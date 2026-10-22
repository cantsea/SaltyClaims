package org.saltymc.config;

import org.junit.jupiter.api.Test;
import org.saltymc.message.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MessageMigrationV2Test {
    private YamlFileSpec.Candidate prepare(String yaml) { return ConfigFiles.messagesFile().prepare(yaml.getBytes(StandardCharsets.UTF_8)); }
    @Test void upgradesStockInstructionsAndKeepsIndependentVersions() {
        var candidate=prepare("prefix: '[Custom] '\ncommands:\n  tool: 'Select with {selection}, inspect with {inspection}. Right-click two corners, then /claim create. Click an owned corner to resize.'\n  confirm-delete: 'Delete {type} {id}? Within {seconds} seconds: /{command} confirm {token}'\nselection:\n  preview: 'Area {area}; use {command} to save.'\nconfig-version: 1\n");
        Messages messages=Messages.from(candidate.document());
        assertEquals(1,candidate.from()); assertEquals(ConfigFiles.messagesFile().version(),candidate.to()); assertEquals(7,ConfigFiles.settingsFile().version());
        assertTrue(messages.text("commands.help-player").contains("two opposite corners"));
        assertEquals("",messages.text("selection.preview","area",25));
        String confirmation=messages.text("commands.confirm-delete","type","Root","id","Alex","seconds",30,"command","claim","token","");
        assertTrue(confirmation.equals("Type /claim delete confirm to delete this Root.")); assertTrue(messages.lines(Notice.of("selection.wilderness")).getFirst().startsWith("[Custom] "));
        assertFalse(ConfigFiles.messagesFile().prepare(candidate.output()).changed());
    }
    @Test void preservesCustomMessagesAndMigratesOnlyExactRemovedSyntax() {
        var candidate=prepare("commands:\n  tool: 'Use your special shovel.'\n  confirm-delete: 'Please run /{command} confirm {token} for {id}.'\nselection:\n  preview: 'Size {area}, custom wording {command}'\nconfig-version: 1\n");
        Messages messages=Messages.from(candidate.document());
        assertEquals("Use your special shovel.",messages.text("commands.help-tools"));
        assertEquals("Please run /claim delete confirm for Alex.",messages.text("commands.confirm-delete","command","claim","token","","id","Alex"));
        assertEquals("Size 25, custom wording ",messages.text("selection.preview","area",25,"command",""));
    }
    @Test void unversionedMessagesRunBothStepsWithoutResettingPrefix() {
        var candidate=prepare("prefix: '&6Custom '\nerrors:\n  no-claim: 'No claim here. Use --id <uuid> to target one.'\n");
        assertEquals(0,candidate.from()); assertEquals(ConfigFiles.messagesFile().version(),candidate.to());
        assertEquals("Stand inside a claim first.",Messages.from(candidate.document()).text("errors.no-claim"));
    }
}
