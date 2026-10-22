package org.saltymc.flag;

import org.saltymc.claim.*;
import org.saltymc.trust.Trust;
import org.saltymc.config.Settings;
import org.saltymc.config.WorldSettings;
import java.util.*;

/** Claim switches and world wilderness switches are independent. Enabled claim actions still require trust. */
public final class Policy {
    public record Value(String value, String source) {
        public boolean enabled() { return !value.equals("deny"); }
    }
    public record Decision(boolean allowed, String reason) { }
    private Policy() { }
    public static Value resolve(ClaimState state, Claim c, Flag flag, String world,
                                Map<Flag, Settings.ClaimFlag> defaults, Map<String, WorldSettings> worlds) {
        if(c==null) return new Value(flag.behavior(worlds.getOrDefault(world,WorldSettings.defaults()).allows(flag),true),"world wilderness");
        Settings.ClaimFlag setting=defaults.getOrDefault(flag,new Settings.ClaimFlag(flag.actor(),false));
        if(setting.playerEditable()) {
            if(c.flags().containsKey(flag)) return new Value(flag.behavior(flag.enabled(c.flags().get(flag)),false),c.root()?"root override":"child override");
            Claim root=state.root(c);
            if(!c.root()&&root.flags().containsKey(flag)) return new Value(flag.behavior(flag.enabled(root.flags().get(flag)),false),"root override");
        }
        return new Value(flag.behavior(setting.defaultValue(),false),setting.playerEditable()?"claim default":"locked claim default");
    }
    public static Decision check(ClaimState state, Claim claim, UUID actor, Flag flag, Value value, boolean bypass) {
        if (bypass) return new Decision(true, "staff bypass");
        boolean allowed;
        if (!flag.actor()) allowed = value.value().equals("allow");
        else if (value.value().equals("public")) allowed = true;
        else if (value.value().equals("deny") || actor == null || claim == null) allowed = false;
        else allowed = state.trust(claim, actor).grants(Trust.parse(value.value()));
        return new Decision(allowed, flag.key() + "=" + value.value() + " (" + value.source() + ")");
    }
}
