package org.saltymc.claim;

import org.saltymc.message.MessageException;
import java.util.*;

/** Owner-name lookup is resolved to UUID before this pure, unambiguous claim selection. */
public final class ClaimTargets {
    private ClaimTargets() { }
    public static Claim select(ClaimState state,UUID owner,Claim contextual,boolean explicitLocation,boolean root) {
        Claim selected=contextual;
        if(owner!=null) {
            if(selected==null||!state.owner(selected).equals(owner)) {
                if(explicitLocation) throw new MessageException("errors.owner-location");
                List<Claim> roots=state.claims().values().stream().filter(c->c.root()&&owner.equals(c.owner())).toList();
                if(roots.isEmpty()) throw new MessageException("errors.no-owner-claims");
                if(roots.size()!=1) throw new MessageException("errors.ambiguous-claim");
                selected=roots.getFirst();
            }
        }
        if(selected==null) throw new MessageException("errors.no-claim");
        return root?state.root(selected):selected;
    }
}
