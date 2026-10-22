package org.saltymc.config;

import org.junit.jupiter.api.Test;
import org.saltymc.message.Messages;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MessageMigrationV7Test {
    @Test void upgradesStockViewsWhileKeepingCustomLegacyContracts() {
        String yaml="commands:\n  list-entry: '{id} area={area} world={world}'\n  bounds: '{bounds} area={area} children={children}'\n  trust-list: 'Grants on {id}: {grants}'\nconfig-version: 6\n";
        var candidate=ConfigFiles.messagesFile().prepare(yaml.getBytes(StandardCharsets.UTF_8));
        Messages messages=Messages.from(candidate.document());
        assertTrue(messages.text("commands.list-entry").contains("{corner1}"));
        assertTrue(messages.text("commands.bounds").startsWith("Size:"));
        assertEquals("",messages.text("commands.trust-list","id","A"));
        assertFalse(ConfigFiles.messagesFile().prepare(candidate.output()).changed());
        candidate=ConfigFiles.messagesFile().prepare("commands:\n  list-entry: 'My {id} {area} {world}'\n  bounds: 'My {bounds} {children}'\n  trust-list: 'My {id} {grants}'\nconfig-version: 6\n".getBytes(StandardCharsets.UTF_8));
        messages=Messages.from(candidate.document());
        assertEquals("My A B",messages.text("commands.trust-list","id","A","grants","B"));
    }
}
