package com.bigbangcraft.regions.allocation;

public record PlotFootprint(int minX, int maxX, int minZ, int maxZ) {
    public PlotFootprint {
        if (minX > maxX) {
            throw new IllegalArgumentException("minX must be <= maxX");
        }
        if (minZ > maxZ) {
            throw new IllegalArgumentException("minZ must be <= maxZ");
        }
    }

    public int centerX() {
        return minX + (width() / 2);
    }

    public int centerZ() {
        return minZ + (depth() / 2);
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    public int largestRadiusBlocks() {
        return Math.max(maxX - minX, maxZ - minZ) / 2;
    }
}
