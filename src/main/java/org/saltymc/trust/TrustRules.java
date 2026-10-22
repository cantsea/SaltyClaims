package org.saltymc.trust;

import org.saltymc.message.MessageException;

import org.saltymc.claim.*;
import java.util.*;

/** Delegation is independent of command permissions; the caller must check both. */
public final class TrustRules {
    private TrustRules() { }
    public static Map<Subject, Trust> edit(ClaimState state, Claim claim, UUID actor, boolean staff,
                                          Subject subject, Trust level, boolean remove, boolean all) {
        boolean owner = staff || state.owner(claim).equals(actor);
        if (!owner && !state.trust(claim,actor).grants(Trust.MANAGE)) throw new MessageException("errors.manage-required");
        if(!all && subject!=null && actor!=null && actor.equals(subject.playerId())
                && state.trust(claim,actor).grants(Trust.MANAGE)) throw new MessageException("errors.self-trust");
        Map<Subject,Trust> grants = new HashMap<>(claim.trust());
        if (all) { grants.entrySet().removeIf(e -> (actor==null || !actor.equals(e.getKey().playerId()))
                && (owner || e.getValue() != Trust.MANAGE)); return Map.copyOf(grants); }
        if (!owner && grants.get(subject) == Trust.MANAGE) throw new MessageException("errors.manager-grant");
        if (remove) grants.remove(subject);
        else {
            if (level == null || !level.assignable() || subject.isPublic() && level == Trust.MANAGE || !owner && level == Trust.MANAGE)
                throw new MessageException("errors.trust-forbidden");
            grants.put(subject,level);
        }
        return Map.copyOf(grants);
    }
}
