package com.bigbangcraft.regions.allocation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record WorldgenSearchContext(
    ResourceKey<Level> dimensionKey,
    long worldSeed,
    ChunkGenerator chunkGenerator,
    BiomeSource biomeSource,
    Climate.Sampler noiseSampler,
    WorldgenFingerprint fingerprint,
    int sampleBlockY,
    List<Integer> sampleBlockYs
) {
    public WorldgenSearchContext {
        Objects.requireNonNull(dimensionKey, "dimensionKey");
        Objects.requireNonNull(chunkGenerator, "chunkGenerator");
        Objects.requireNonNull(biomeSource, "biomeSource");
        Objects.requireNonNull(noiseSampler, "noiseSampler");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(sampleBlockYs, "sampleBlockYs");
    }

    public int sampleQuartY() {
        return BiomeCoordinateMath.blockToQuart(sampleBlockY);
    }

    public List<Integer> getEffectiveSampleBlockYs() {
        if (!sampleBlockYs.isEmpty()) {
            return sampleBlockYs;
        }
        return Collections.singletonList(sampleBlockY);
    }
}
