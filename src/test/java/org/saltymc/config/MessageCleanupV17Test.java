package org.saltymc.config;

import org.junit.jupiter.api.Test;
import org.saltymc.message.Messages;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MessageCleanupV17Test {
    private void assertRemoved(Map<String,Object> document) {
        for(var entry:Map.of("commands",List.of("cancelled","inherit-trust","root-flags"),
                "success",List.of("restricted"),"labels",List.of("inherited"),
                "errors",List.of("admin-target","root-required","child-required")).entrySet()) {
            Map<String,Object> section=YamlData.map(document.get(entry.getKey()),"messages.yml",entry.getKey());
            for(String key:entry.getValue()) assertFalse(section.containsKey(key),entry.getKey()+"."+key);
        }
    }
    @Test void prunesOnlyObsoleteTemplatesAndPreservesCustomizedLiveMessages() {
        String yaml="prefix: '[Mine] '\ncommands:\n  cancelled: 'Old cancel'\n  inherit-trust: 'Old inheritance'\n  root-flags: 'Old parent flags'\n  claimblocks: 'Balance: {available}'\nsuccess:\n  restricted: 'Old restriction'\nlabels:\n  inherited: 'Old label'\nerrors:\n  admin-target: 'Old target'\n  root-required: 'Old root'\n  child-required: 'Old child'\n  self-trust: 'Do not target yourself.'\nconfig-version: 16\n";
        var candidate=ConfigFiles.messagesFile().prepare(yaml.getBytes(StandardCharsets.UTF_8));
        assertRemoved(candidate.document());
        Messages messages=Messages.from(candidate.document());
        assertEquals("Balance: 12",messages.text("commands.claimblocks","available",12));
        assertEquals("Do not target yourself.",messages.text("errors.self-trust"));
        assertEquals(17,candidate.to()); assertFalse(ConfigFiles.messagesFile().prepare(candidate.output()).changed());
    }
    @Test void frozenHistoricalSnapshotsStillUpgradeAfterRuntimeMessageRemoval() {
        for(String resource:List.of("config-migrations/messages-v7.yml","config-migrations/messages-v8.yml")) {
            byte[] historical=ConfigFiles.resource(resource);
            assertTrue(YamlData.map(YamlData.parse(historical,resource).get("commands"),resource,"commands").containsKey("inherit-trust"));
            var upgraded=ConfigFiles.messagesFile().prepare(historical);
            assertRemoved(upgraded.document());
            assertDoesNotThrow(()->Messages.from(upgraded.document()));
        }
    }
}
