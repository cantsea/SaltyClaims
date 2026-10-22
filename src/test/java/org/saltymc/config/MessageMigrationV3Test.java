package org.saltymc.config;

import org.junit.jupiter.api.Test;
import org.saltymc.message.Messages;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MessageMigrationV3Test {
    @Test void versionTwoStockPromptBecomesClaimOrChildSpecific() {
        var candidate=ConfigFiles.messagesFile().prepare(("commands:\n  confirm-delete: 'Delete {type} {id}? Within {seconds} seconds: /{command} delete confirm'\nconfig-version: 2\n").getBytes(StandardCharsets.UTF_8));
        Messages messages=Messages.from(candidate.document());
        assertEquals(2,candidate.from()); assertEquals(ConfigFiles.messagesFile().version(),candidate.to());
        assertEquals("Type /claim delete confirm to delete this claim.",messages.text("commands.confirm-delete","command","claim","type",messages.text("labels.claim")));
        assertEquals("Type /claim delete confirm to delete this child claim.",messages.text("commands.confirm-delete","command","claim","type",messages.text("labels.child-claim")));
        assertFalse(ConfigFiles.messagesFile().prepare(candidate.output()).changed());
    }
    @Test void customizedPromptRetainsItsOriginalPlaceholderContract() {
        var candidate=ConfigFiles.messagesFile().prepare(("prefix: 'Custom '\ncommands:\n  confirm-delete: 'Delete {type} {id} within {seconds}s: /{command} delete confirm {token}'\nconfig-version: 2\n").getBytes(StandardCharsets.UTF_8));
        Messages messages=Messages.from(candidate.document());
        assertEquals("Delete claim Alex within 30s: /claim delete confirm ",messages.text("commands.confirm-delete","type","claim","id","Alex","seconds",30,"command","claim","token",""));
    }
}
