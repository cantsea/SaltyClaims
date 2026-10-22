package org.saltymc.config;

import org.junit.jupiter.api.Test;
import org.saltymc.claim.Claim;
import org.saltymc.command.ClaimView;
import org.saltymc.message.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class MessageMigrationV8Test {
    @Test void oldStockMessagesBecomeShortWithoutTechnicalStateOrRepeatedLocations() {
        var candidate=ConfigFiles.messagesFile().prepare(ConfigFiles.resource("config-migrations/messages-v7.yml"));
        Messages messages=Messages.from(candidate.document());
        assertEquals(7,candidate.from()); assertEquals(ConfigFiles.messagesFile().version(),candidate.to());
        assertEquals("Claim",messages.text("labels.root"));
        assertEquals("Claim created.",messages.text("success.created","type","Claim","id","ignored"));
        assertEquals("Claim owned by Alex",messages.text("commands.info","type","Claim","owner","Alex","id","ignored","parent","ignored"));
        assertEquals("This land is owned by Alex",messages.text("selection.inspect","owner","Alex"));
        assertTrue(messages.lines(Notice.of("commands.local-flags","flags","debug map")).isEmpty());
        assertTrue(messages.lines(Notice.of("selection.preview","area",25)).isEmpty());
        assertFalse(ConfigFiles.messagesFile().prepare(candidate.output()).changed());
    }
    @Test void customWordingPrefixAndEveryLegacySlotRemainValid() {
        String yaml="prefix: '[Mine] '\nselection:\n  inspect: 'This land is owned by {owner}'\ncommands:\n  info: 'My {type} {id} {owner} {parent}'\n  list-entry: 'My {width} {length} {height} {world} {area} {corner1} {corner2}'\n  bounds: 'My {bounds} {children} {height}'\n  flag-entry: 'My {flag} {value} {source}'\nerrors:\n  missing-permission: 'Ask for {permission}'\nlabels:\n  claim-reference: '{owner} {world} {x} {y} {z} {max-x} {max-y} {max-z}'\nconfig-version: 7\n";
        var candidate=ConfigFiles.messagesFile().prepare(yaml.getBytes(StandardCharsets.UTF_8));
        Messages messages=Messages.from(candidate.document());
        assertEquals("My Claim A Alex none",messages.text("commands.info","type","Claim","id","A","owner","Alex","parent","none"));
        assertEquals(List.of("[Mine] This land is owned by Alex"),messages.lines(Notice.of("selection.inspect","owner","Alex")));
        assertEquals("Ask for node",messages.text("errors.missing-permission","permission","node"));
    }
    @Test void ordinaryDefaultMessagesAvoidInternalTerminologyAndPreserveUsefulErrors() {
        Map<String,Object> document=YamlData.parse(ConfigFiles.resource("messages.yml"),"test");
        for(String section:List.of("commands","selection","success","errors","labels"))
            for(Object value:YamlData.map(document.get(section),"test",section).values()) {
                String text=value.toString().toLowerCase(Locale.ROOT);
                assertFalse(text.matches("(?s).*\\broot\\b.*"),text);
                assertFalse(text.contains("owner=")||text.contains("parent=")||text.contains("effective trust="),text);
            }
        assertEquals("Not enough claim blocks.",Messages.defaults().text("errors.insufficient-blocks"));
        assertEquals("That area overlaps another claim.",Messages.defaults().text("errors.overlap"));
    }
    @Test void compactListStillIncludesBothCornersAndSizeWithOnePrefixPerLine() {
        Claim claim=root(0,9);
        List<String> lines=Messages.defaults().lines(ClaimView.details("commands.list-entry",claim,"world","full height","Claim",0));
        assertEquals(2,lines.size()); assertEquals("[SaltyClaims] Claim in world: 10 x 10 (100 blocks)",lines.getFirst());
        assertEquals("[SaltyClaims]   X 0, Z 0 to X 9, Z 9",lines.getLast());
    }
}
