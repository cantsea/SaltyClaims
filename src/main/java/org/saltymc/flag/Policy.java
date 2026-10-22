package org.saltymc.flag;

import org.saltymc.claim.*;
import org.saltymc.trust.Trust;
import java.util.*;

/** Pure resolver. Wilderness is permissive unless world/default/enforced policy explicitly says otherwise. */
public final class Policy {
    public record Value(String value, String source, boolean enforced) { }
    public record Decision(boolean allowed, String reason) { }
    private Policy() { }
    public static Value resolve(ClaimState state, Claim c, Flag flag, String world,
                                Map<Flag, String> defaults, Map<Flag, String> enforced,
                                Map<String, Map<Flag, String>> worlds) {
        if (enforced.containsKey(flag)) return new Value(enforced.get(flag), "server-enforced", true);
        if (c != null) {
            if (c.flags().containsKey(flag)) return new Value(c.flags().get(flag), c.root() ? "root override" : "child override", false);
            Claim root = state.root(c);
            if (!c.root() && root.flags().containsKey(flag)) return new Value(root.flags().get(flag), "root override", false);
        }
        Map<Flag, String> worldFlags = worlds.getOrDefault(world, Map.of());
        if (worldFlags.containsKey(flag)) return new Value(worldFlags.get(flag), "world default", false);
        if (defaults.containsKey(flag)) return new Value(defaults.get(flag), "global default", false);
        return new Value(c == null ? (flag.actor() ? "public" : "allow") : flag.defaultValue(),
                c == null ? "wilderness default" : "built-in default", false);
    }
    public static Decision check(ClaimState state, Claim claim, UUID actor, Flag flag, Value value, boolean bypass) {
        if (bypass && !value.enforced()) return new Decision(true, "staff bypass");
        boolean allowed;
        if (!flag.actor()) allowed = value.value().equals("allow");
        else if (value.value().equals("public")) allowed = true;
        else if (value.value().equals("deny") || actor == null || claim == null) allowed = false;
        else allowed = state.trust(claim, actor).grants(Trust.parse(value.value()));
        return new Decision(allowed, flag.key() + "=" + value.value() + " (" + value.source() + ")");
    }
}
