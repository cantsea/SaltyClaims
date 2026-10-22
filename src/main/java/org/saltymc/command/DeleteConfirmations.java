package org.saltymc.command;

import org.saltymc.claim.*;
import org.saltymc.message.MessageException;
import java.util.*;
import java.util.function.LongSupplier;

/** One pending deletion per sender; confirmation never reselects from the sender's current location. */
public final class DeleteConfirmations {
    public enum Action { DELETE, RESET_ALL }
    public record Pending(UUID claim,Map<UUID,Claim> expected,long expires,String permission,boolean admin,Action action) {
        public Pending { expected=Map.copyOf(expected); }
    }
    private final Map<String,Pending> pending=new HashMap<>();
    private final LongSupplier clock;
    public DeleteConfirmations() { this(System::currentTimeMillis); }
    public DeleteConfirmations(LongSupplier clock) { this.clock=clock; }
    public void request(String sender,ClaimState state,Claim claim,String permission,boolean admin) {
        pending.put(sender,new Pending(claim.id(),tree(state,claim),clock.getAsLong()+30000,permission,admin,Action.DELETE));
    }
    public void requestReset(String sender,ClaimState state,String permission) {
        pending.put(sender,new Pending(null,state.claims(),clock.getAsLong()+30000,permission,true,Action.RESET_ALL));
    }
    public Pending requireReset(String sender,ClaimState state) {
        Pending confirmation=valid(sender,true,Action.RESET_ALL);
        if(!state.claims().equals(confirmation.expected())) throw new MessageException("errors.reset-changed");
        return confirmation;
    }
    public Pending require(String sender,ClaimState state,boolean admin) {
        Pending confirmation=valid(sender,admin,Action.DELETE);
        Claim current=state.claims().get(confirmation.claim());
        if(current==null||!tree(state,current).equals(confirmation.expected())) throw new MessageException("errors.tree-changed");
        return confirmation;
    }
    private Pending valid(String sender,boolean admin,Action action) {
        Pending confirmation=pending.get(sender);
        if(confirmation==null||confirmation.expires()<=clock.getAsLong()||confirmation.admin()!=admin||confirmation.action()!=action)
            throw new MessageException("errors.confirmation");
        return confirmation;
    }
    public void clear(String sender) { pending.remove(sender); }
    public void expire() { pending.values().removeIf(c->c.expires()<=clock.getAsLong()); }
    private static Map<UUID,Claim> tree(ClaimState state,Claim claim) {
        Map<UUID,Claim> result=new HashMap<>(); result.put(claim.id(),claim);
        if(claim.root()) for(Claim child:state.children(claim.id())) result.put(child.id(),child);
        return result;
    }
}
