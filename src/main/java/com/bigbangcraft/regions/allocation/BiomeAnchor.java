package com.bigbangcraft.regions.allocation;

public record BiomeAnchor(int blockX, int blockY, int blockZ, String biomeId) {
    public BiomeAnchor(int blockX, int blockZ, String biomeId) {
        this(blockX, 64, blockZ, biomeId);
    }
}
