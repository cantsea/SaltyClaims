package org.saltymc.claim;

import org.saltymc.message.MessageException;

import java.util.*;

/** Pure mutation rules. The plugin serializes pending mutations before calling storage. */
public final class ClaimService {
    public record Limits(long starting, int minWidth, int maxRoots, int maxChildren, long maxArea) {
        public Limits {
            if (starting < 0 || minWidth < 1 || maxRoots < 1 || maxChildren < 0 || maxArea < 1)
                throw new MessageException("errors.invalid-limits");
        }
    }
    private ClaimService() { }
    public static ClaimState put(ClaimState state, Claim claim, Limits limits, boolean staff) {
        Bounds b = claim.bounds();
        if (b.chunks() > 65536 || b.area() > limits.maxArea()) throw new MessageException("errors.claim-large");
        if ((long) b.maxX() - b.minX() + 1 < limits.minWidth() || (long) b.maxZ() - b.minZ() + 1 < limits.minWidth())
            throw new MessageException("errors.minimum-width","width",limits.minWidth());
        Map<UUID, Claim> claims = new HashMap<>(state.claims());
        claims.put(claim.id(), claim);
        ClaimState next = new ClaimState(claims, state.accounts());
        next.validate();
        if (claim.root()) {
            long roots = claims.values().stream().filter(c -> c.root() && c.owner().equals(claim.owner())).count();
            if (!staff && !state.claims().containsKey(claim.id()) && roots > limits.maxRoots()) throw new MessageException("errors.maximum-roots");
            if (!staff && next.used(claim.owner()) > state.used(claim.owner()) && next.used(claim.owner()) > next.accounts().getOrDefault(claim.owner(), new ClaimState.Account(0, 0)).total(limits.starting()))
                throw new MessageException("errors.insufficient-blocks");
        } else if (!state.claims().containsKey(claim.id()) && next.children(claim.parent()).size() > limits.maxChildren()) throw new MessageException("errors.maximum-children");
        return next;
    }
    public static ClaimState replace(ClaimState state, Claim claim) {
        state.require(claim.id());
        Map<UUID, Claim> claims = new HashMap<>(state.claims()); claims.put(claim.id(), claim);
        return new ClaimState(claims, state.accounts());
    }
    public static ClaimState delete(ClaimState state, UUID id) {
        state.require(id);
        Map<UUID, Claim> claims = new HashMap<>(state.claims());
        claims.values().removeIf(c -> id.equals(c.parent())); claims.remove(id);
        return new ClaimState(claims, state.accounts());
    }
}
