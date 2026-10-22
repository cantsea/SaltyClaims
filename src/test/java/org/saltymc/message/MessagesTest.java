package org.saltymc.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    @TempDir Path directory;
    private Messages load(String yaml) throws Exception {
        Path file=directory.resolve("messages.yml"); Files.writeString(file,yaml); return Messages.load(file);
    }
    @Test void customPrefixAppliesToResponsesAndDefaultFallbacks() throws Exception {
        Messages messages=load("prefix: '[Land] '\ncommands:\n  allowance: '{used} of {total}'\n");
        assertEquals(List.of("[Land] 10 of 20"),messages.lines(Notice.of("commands.allowance","used",10,"total",20)));
        assertEquals(List.of("[Land] This land is not claimed."),messages.lines(Notice.of("selection.wilderness")));
    }
    @Test void emptyPrefixAndSuppressedMessages() throws Exception {
        Messages messages=load("prefix: ''\nselection:\n  wilderness: ''\n");
        assertEquals(List.of(),messages.lines(Notice.of("selection.wilderness")));
        assertEquals(List.of("Settings reloaded."),messages.lines(Notice.of("commands.reloaded")));
    }
    @Test void everyLineGetsExactlyOnePrefix() throws Exception {
        Messages messages=load("prefix: '[C] '\ncommands:\n  help-player: |\n    First line\n    Second line\n");
        assertEquals(List.of("[C] First line","[C] Second line","[C] "),messages.lines(Notice.of("commands.help-player")));
    }
    @Test void ampersandColorsOnlyApplyToConfiguredTemplates() throws Exception {
        Messages messages=load("prefix: '&6[C] &r'\ncommands:\n  help-line: '&a{usage}'\n");
        assertEquals(List.of("\u00a76[C] \u00a7r\u00a7a/claim &c"),messages.lines(Notice.of("commands.help-line","usage","/claim &c")));
    }
    @Test void placeholderDataIsLiteralAndNeverExpandedAgain() throws Exception {
        Messages messages=load("prefix: ''\ncommands:\n  allowance: '{used} / {total}'\n");
        assertEquals("{total} $1 \\ / 5",messages.text("commands.allowance","used","{total} $1 \\","total",5));
    }
    @Test void typedAndStructuralErrorsRejectReplacementAndRetainOldSnapshot() throws Exception {
        Messages old=load("prefix: '[Old] '\n");
        assertThrows(IllegalArgumentException.class,()->load("prefix: 123\n"));
        assertThrows(IllegalArgumentException.class,()->load("prefix:\n  nested: bad\n"));
        assertThrows(IllegalArgumentException.class,()->load("commands: bad\n"));
        assertThrows(Exception.class,()->load("prefix: [unterminated\n"));
        assertEquals(List.of("[Old] This land is not claimed."),old.lines(Notice.of("selection.wilderness")));
    }
    @Test void validationExceptionsUseConfiguredMessagesAndOnePrefix() throws Exception {
        Messages messages=load("prefix: '[C] '\nerrors:\n  missing-permission: 'Need {permission}'\n");
        MessageException error=new MessageException("errors.missing-permission","permission","node.name");
        assertEquals("Need node.name",messages.error(error));
        assertEquals(List.of("[C] Need node.name"),messages.lines(error.notice()));
        assertEquals("Enter a whole number.",messages.error(new NumberFormatException("internal detail")));
    }
    @Test void noticesCopyCallerDataBeforeAsyncUse() {
        Map<String,String> values=new HashMap<>(); values.put("id","before"); Notice notice=new Notice("success.deleted",values); values.put("id","after");
        assertEquals("before",notice.values().get("id")); assertThrows(UnsupportedOperationException.class,()->notice.values().put("id","new"));
    }
    @Test void customizedUnclaimAllSuccessCanIncludeRemovedCount() throws Exception {
        Messages messages=load("success:\n  unclaimed-all: 'Removed {count} claims.'\n");
        assertEquals(List.of("[SaltyClaims] Removed 3 claims."),messages.lines(Notice.of("success.unclaimed-all","count",3)));
    }
}
