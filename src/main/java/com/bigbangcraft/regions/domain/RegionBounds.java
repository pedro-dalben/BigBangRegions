package com.bigbangcraft.regions.domain;

import java.util.Objects;

public class RegionBounds {
    private final String dimension;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public RegionBounds(String dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.dimension = Objects.requireNonNull(dimension, "Dimension cannot be null");
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public String getDimension() {
        return dimension;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public boolean contains(String dim, int x, int y, int z) {
        if (!this.dimension.equals(dim)) {
            return false;
        }
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }

    public boolean intersects(RegionBounds other) {
        if (!this.dimension.equals(other.dimension)) {
            return false;
        }
        return this.minX <= other.maxX && this.maxX >= other.minX &&
               this.minY <= other.maxY && this.maxY >= other.minY &&
               this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }

    public long volume() {
        long width = (long) maxX - minX + 1;
        long height = (long) maxY - minY + 1;
        long depth = (long) maxZ - minZ + 1;
        return width * height * depth;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegionBounds that = (RegionBounds) o;
        return minX == that.minX && minY == that.minY && minZ == that.minZ &&
               maxX == that.maxX && maxY == that.maxY && maxZ == that.maxZ &&
               dimension.equals(that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public String toString() {
        return "RegionBounds{" +
                "dimension='" + dimension + '\'' +
                ", min=(" + minX + "," + minY + "," + minZ + ")" +
                ", max=(" + maxX + "," + maxY + "," + maxZ + ")" +
                '}';
    }
}
