package org.saltymc.claim;

import org.saltymc.message.MessageException;

import org.saltymc.flag.Flag;
import org.saltymc.trust.*;
import java.util.*;

/** Immutable committed claim. Children derive ownership from their root. */
public record Claim(UUID id, UUID world, UUID owner, UUID parent, Bounds bounds,
                    boolean inheritTrust, long created, long revision,
                    Map<Subject, Trust> trust, Map<Flag, String> flags) {
    public Claim {
        Objects.requireNonNull(id); Objects.requireNonNull(world); Objects.requireNonNull(bounds);
        if ((parent == null) != (owner != null)) throw new MessageException("errors.root-owner");
        if (parent == null && !bounds.fullHeight()) throw new MessageException("errors.root-height");
        if (id.equals(parent)) throw new MessageException("errors.self-parent");
        // Legacy storage may enable inheritance; children always use local grants now.
        if (parent != null) inheritTrust = false;
        trust = Map.copyOf(trust);
        Map<Flag,String> normalized = new EnumMap<>(Flag.class);
        flags.forEach((flag,value) -> normalized.put(flag,flag.validate(value))); flags = Map.copyOf(normalized);
        trust.forEach((subject, level) -> {
            if (!level.assignable() || subject.isPublic() && level == Trust.MANAGE)
                throw new MessageException("errors.invalid-trust");
        });
    }
    public boolean root() { return parent == null; }
    public Claim change(Bounds box, boolean inherit, Map<Subject, Trust> grants, Map<Flag, String> overrides) {
        return new Claim(id, world, owner, parent, box, inherit, created, revision + 1, grants, overrides);
    }
}
