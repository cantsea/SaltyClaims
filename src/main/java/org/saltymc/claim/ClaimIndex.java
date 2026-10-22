package org.saltymc.claim;

import java.util.*;
import org.saltymc.message.MessageException;

/** World/chunk buckets, with exact 3D tests for children; never loads world chunks. */
public final class ClaimIndex {
    private record Chunk(UUID world, int x, int z) { }
    private final Map<Chunk, List<Claim>> buckets = new HashMap<>();
    public ClaimIndex(ClaimState state) {
        for (Claim c : state.claims().values()) {
            Bounds b = c.bounds();
            if (b.chunks() > 65536) throw new MessageException("errors.chunk-limit","id",c.id());
            for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++)
                for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++)
                    buckets.computeIfAbsent(new Chunk(c.world(), x, z), ignored -> new ArrayList<>()).add(c);
        }
    }
    public Claim at(UUID world, int x, int y, int z) {
        Claim root = null;
        for (Claim c : buckets.getOrDefault(new Chunk(world, x >> 4, z >> 4), List.of())) {
            if (c.bounds().contains(x, y, z)) { if (!c.root()) return c; root = c; }
        }
        return root;
    }
    public Set<Claim> candidates(UUID world, Bounds b) {
        Set<Claim> result = new HashSet<>();
        for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++)
            for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++)
                result.addAll(buckets.getOrDefault(new Chunk(world, x, z), List.of()));
        return result;
    }
}
