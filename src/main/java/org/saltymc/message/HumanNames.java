package org.saltymc.message;

import org.saltymc.claim.*;
import java.util.*;
import java.util.function.*;
import java.util.regex.*;

/** Friendly display only. Identity/ownership always remains UUID-based internally. */
public final class HumanNames {
    private static final Pattern UUID_TEXT=Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private final Supplier<ClaimState> state;
    private final Function<UUID,String> players,worlds;
    private final Function<Notice,String> labels;
    public HumanNames(Supplier<ClaimState> state,Function<UUID,String> players,Function<UUID,String> worlds,Function<Notice,String> labels) {
        this.state=state; this.players=players; this.worlds=worlds; this.labels=labels;
    }
    public static boolean validPlayerName(String name) {
        return name!=null&&!name.isBlank()&&name.length()<=32&&!UUID_TEXT.matcher(name).find()
                &&name.chars().noneMatch(c->Character.isWhitespace(c)||Character.isISOControl(c));
    }
    public String player(UUID id) {
        String name=players.apply(id);
        return name==null||!validPlayerName(name)?labels.apply(Notice.of("labels.unknown-player")):name;
    }
    public String world(UUID id) {
        String name=worlds.apply(id); return name==null?labels.apply(Notice.of("labels.unloaded-world")):name;
    }
    public String claim(Claim claim) {
        return labels.apply(Notice.of("labels.claim-reference","owner",player(state.get().owner(claim)),"world",world(claim.world()),
                "x",claim.bounds().minX(),"z",claim.bounds().minZ(),"y",claim.bounds().fullHeight()?labels.apply(Notice.of("labels.full-height")):claim.bounds().minY(),
                "max-x",claim.bounds().maxX(),"max-z",claim.bounds().maxZ(),"max-y",claim.bounds().fullHeight()?labels.apply(Notice.of("labels.full-height")):claim.bounds().maxY()));
    }
    public String replaceIdentifiers(String text) {
        Matcher matcher=UUID_TEXT.matcher(text); StringBuilder result=new StringBuilder();
        while(matcher.find()) {
            UUID id=UUID.fromString(matcher.group()); Claim claim=state.get().claims().get(id);
            String replacement=claim!=null?claim(claim):worlds.apply(id)!=null?world(id):player(id);
            matcher.appendReplacement(result,Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result); return result.toString();
    }
}
