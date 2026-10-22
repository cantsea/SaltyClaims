package org.saltymc.api;

import org.saltymc.claim.*;
import org.saltymc.config.Settings;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Service adapter; writes use the exact serialized mutation path used by commands. */
public final class DefaultSaltyClaimsApi implements SaltyClaimsApi {
    public interface Port {
        boolean primaryThread(); Settings settings();
        void validateWorld(UUID world,Bounds bounds);
        void mutate(Function<ClaimState,ClaimState> operation,Runnable committed,Runnable failed);
    }
    private final Port port;
    private final Set<CompletableFuture<Claim>> pending=new HashSet<>();
    private boolean closed;
    public DefaultSaltyClaimsApi(Port port) { this.port=Objects.requireNonNull(port); }
    @Override public CompletionStage<Claim> createClaim(UUID owner,UUID world,Bounds bounds) {
        if(!port.primaryThread()) return CompletableFuture.failedStage(new IllegalStateException("Call SaltyClaimsApi on the server thread"));
        if(closed) return CompletableFuture.failedStage(new IllegalStateException("SaltyClaims is disabled"));
        CompletableFuture<Claim> result=new CompletableFuture<>();
        try {
            Objects.requireNonNull(owner,"owner"); Objects.requireNonNull(world,"world"); Objects.requireNonNull(bounds,"bounds");
            Claim claim=new Claim(UUID.randomUUID(),world,owner,null,bounds,true,System.currentTimeMillis(),0,Map.of(),Map.of());
            port.validateWorld(world,bounds);
            pending.add(result); result.whenComplete((saved,error)->pending.remove(result));
            port.mutate(state->ClaimService.put(state,claim,port.settings().limits(),true),
                    ()->result.complete(claim),()->result.completeExceptionally(new IllegalStateException("Claim storage failed; check the server log")));
        } catch(Exception error) { result.completeExceptionally(error); }
        return result.minimalCompletionStage();
    }
    /** Called on the server thread during disable so callers never wait indefinitely. */
    public void close() {
        closed=true;
        for(var result:List.copyOf(pending)) result.completeExceptionally(new IllegalStateException("SaltyClaims stopped before publishing the claim"));
    }
}
