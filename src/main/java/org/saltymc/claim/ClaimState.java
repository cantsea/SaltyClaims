package org.saltymc.claim;

import org.saltymc.message.MessageException;

import org.saltymc.trust.*;
import java.util.*;

/** Snapshot shared with the storage worker; all collections are immutable. */
public record ClaimState(Map<UUID, Claim> claims, Map<UUID, Account> accounts) {
    public record Account(long earned, long bonus) {
        public Account { if (earned < 0 || bonus < 0) throw new MessageException("errors.negative-allowance"); }
        public long total(long starting) { return Math.addExact(starting, Math.addExact(earned, bonus)); }
    }
    public ClaimState { claims = Map.copyOf(claims); accounts = Map.copyOf(accounts); }
    public static ClaimState empty() { return new ClaimState(Map.of(), Map.of()); }
    public Claim require(UUID id) {
        Claim c = claims.get(id);
        if (c == null) throw new MessageException("errors.claim-missing","id",id);
        return c;
    }
    public Claim root(Claim c) { return c.root() ? c : require(c.parent()); }
    public UUID owner(Claim c) { return root(c).owner(); }
    public List<Claim> children(UUID root) { return claims.values().stream().filter(c -> root.equals(c.parent())).toList(); }
    public long used(UUID owner) {
        return claims.values().stream().filter(c -> c.root() && c.owner().equals(owner)).mapToLong(c -> c.bounds().area()).reduce(0L, Math::addExact);
    }
    public Trust trust(Claim c, UUID player) {
        if (owner(c).equals(player)) return Trust.OWNER;
        Trust local = Trust.highest(c.trust().getOrDefault(Subject.PUBLIC, Trust.VISITOR),
                c.trust().getOrDefault(new Subject(player), Trust.VISITOR));
        return local;
    }
    public void validate() {
        for (Claim c : claims.values()) {
            if (!c.root()) {
                Claim parent = require(c.parent());
                if (!parent.root() || !parent.world().equals(c.world()) || !parent.bounds().contains(c.bounds()))
                    throw new MessageException("errors.invalid-parent","id",c.id());
            }
        }
        // Startup/mutation validation uses the same spatial index as gameplay lookups.
        ClaimIndex index = new ClaimIndex(this);
        for (Claim c : claims.values()) for (Claim other : index.candidates(c.world(), c.bounds())) {
            if (c.id().compareTo(other.id()) >= 0) continue;
            if (Objects.equals(c.parent(), other.parent()) && c.bounds().intersects(other.bounds()))
                throw new MessageException("errors.overlap","id",c.id(),"other",other.id());
        }
    }
}
