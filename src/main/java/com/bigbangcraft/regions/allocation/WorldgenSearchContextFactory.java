package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class WorldgenSearchContextFactory {
    private final ConcurrentMap<ResourceKey<Level>, WorldgenSearchContext> contexts = new ConcurrentHashMap<>();

    public WorldgenSearchContext getOrCreate(ServerLevel level, Config config) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(config, "config");

        Config.WorldgenSearchConfig worldgen = config.getPlayerLandAllocation().getWorldgenSearch();
        int sampleBlockY = worldgen.getSampleBlockY();
        List<Integer> sampleBlockYs = worldgen.getSampleBlockYs();
        WorldgenFingerprint fingerprint = WorldgenFingerprint.capture(level, config, sampleBlockY);
        ResourceKey<Level> dimensionKey = level.dimension();

        return contexts.compute(dimensionKey, (key, existing) -> {
            if (existing != null && existing.fingerprint().hash().equals(fingerprint.hash())) {
                return existing;
            }
            ChunkGenerator chunkGenerator = level.getChunkSource().getGenerator();
            BiomeSource biomeSource = chunkGenerator.getBiomeSource();
            Climate.Sampler noiseSampler = level.getChunkSource().randomState().sampler();
            return new WorldgenSearchContext(dimensionKey, level.getSeed(), chunkGenerator, biomeSource, noiseSampler, fingerprint, sampleBlockY, sampleBlockYs);
        });
    }

    public void clear() {
        contexts.clear();
    }
}
