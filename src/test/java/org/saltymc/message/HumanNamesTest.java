package org.saltymc.message;

import org.junit.jupiter.api.Test;
import org.saltymc.claim.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class HumanNamesTest {
    private HumanNames names(ClaimState state) {
        return new HumanNames(()->state,id->id.equals(OWNER)?"Alex":null,id->id.equals(WORLD)?"world":null,Messages.defaults()::text);
    }
    @Test void ownerWorldAndClaimReferencesAreHumanReadable() {
        Claim root=root(0,9); HumanNames names=names(state(root));
        String output=names.replaceIdentifiers("owner="+OWNER+" world="+WORLD+" claim="+root.id());
        assertTrue(output.contains("owner=Alex")); assertTrue(output.contains("world=world")); assertTrue(output.contains("Alex at 0, 0 (world)"));
        assertFalse(output.contains(OWNER.toString())); assertFalse(output.contains(root.id().toString()));
    }
    @Test void missingPlayerUsesFriendlyFallbackWithoutExposingIdentifier() {
        assertEquals("Unknown player",names(ClaimState.empty()).replaceIdentifiers(STRANGER.toString()));
    }
    @Test void playerArgumentsAcceptNamesButRejectUuidAndWhitespace() {
        assertTrue(HumanNames.validPlayerName("Alex_123")); assertTrue(HumanNames.validPlayerName(".Bedrock"));
        assertFalse(HumanNames.validPlayerName(OWNER.toString())); assertFalse(HumanNames.validPlayerName("Alex Smith"));
    }
    @Test void messageDeliveryFormatsAllIdentifiersIncludingErrorAndCustomTemplates() {
        Messages messages=Messages.defaults().withDisplay(names(ClaimState.empty())::replaceIdentifiers);
        String line=messages.lines(Notice.of("errors.invalid-input","reason","Player "+OWNER+" is here")).getFirst();
        assertEquals("[SaltyClaims] Player Alex is here",line);
    }
    @Test void capturedDeletedClaimDescriptionSurvivesRemoval() {
        Claim root=root(0,9); String description=names(state(root)).claim(root);
        Messages messages=Messages.from(org.saltymc.config.ConfigFiles.messagesFile().prepare("success:\n  deleted: 'Deleted {id}'\nconfig-version: 8\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)).document()).withDisplay(names(ClaimState.empty())::replaceIdentifiers);
        assertTrue(messages.lines(Notice.of("success.deleted","id",description)).getFirst().contains("Alex at 0, 0 (world)"));
    }
}
