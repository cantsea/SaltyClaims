package org.saltymc.claim;

import org.saltymc.message.MessageException;

/** Inclusive block bounds. A null Y pair means full world height. */
public record Bounds(int minX, int maxX, int minZ, int maxZ, Integer minY, Integer maxY) {
    public Bounds {
        if (minX > maxX || minZ > maxZ || (minY == null) != (maxY == null)
                || minY != null && minY > maxY) throw new MessageException("errors.invalid-bounds");
    }

    public boolean fullHeight() { return minY == null; }
    public long area() { return Math.multiplyExact((long) maxX - minX + 1, (long) maxZ - minZ + 1); }
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ
                && (fullHeight() || y >= minY && y <= maxY);
    }
    public boolean contains(Bounds other) {
        return minX <= other.minX && maxX >= other.maxX && minZ <= other.minZ && maxZ >= other.maxZ
                && (fullHeight() || !other.fullHeight() && minY <= other.minY && maxY >= other.maxY);
    }
    public boolean intersects(Bounds other) {
        return minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ
                && (fullHeight() || other.fullHeight() || minY <= other.maxY && maxY >= other.minY);
    }
    public long chunks() { return ((long) (maxX >> 4) - (minX >> 4) + 1) * ((long) (maxZ >> 4) - (minZ >> 4) + 1); }
}
