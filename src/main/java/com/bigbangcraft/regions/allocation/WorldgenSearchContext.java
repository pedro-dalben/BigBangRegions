package com.bigbangcraft.regions.allocation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.Objects;

public record WorldgenSearchContext(
    ResourceKey<Level> dimensionKey,
    long worldSeed,
    ChunkGenerator chunkGenerator,
    BiomeSource biomeSource,
    Climate.Sampler noiseSampler,
    WorldgenFingerprint fingerprint,
    int sampleBlockY
) {
    public WorldgenSearchContext {
        Objects.requireNonNull(dimensionKey, "dimensionKey");
        Objects.requireNonNull(chunkGenerator, "chunkGenerator");
        Objects.requireNonNull(biomeSource, "biomeSource");
        Objects.requireNonNull(noiseSampler, "noiseSampler");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }

    public int sampleQuartY() {
        return BiomeCoordinateMath.blockToQuart(sampleBlockY);
    }
}
