package com.bigbangcraft.regions.allocation;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class BiomeSampler {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-BiomeSampler");
    private static final int SAMPLE_Y = 64;
    private static final Map<BiomeSampleCache.Key, CompletableFuture<ChunkResult<ChunkAccess>>> pendingChunkLoads = new ConcurrentHashMap<>();

    public enum SampleState {
        READY,
        PENDING,
        UNAVAILABLE
    }

    public static final class SampleRead {
        private final SampleState state;
        private final ResourceKey<Biome> biomeKey;

        private SampleRead(SampleState state, ResourceKey<Biome> biomeKey) {
            this.state = state;
            this.biomeKey = biomeKey;
        }

        public static SampleRead ready(ResourceKey<Biome> biomeKey) {
            return new SampleRead(SampleState.READY, biomeKey);
        }

        public static SampleRead pending() {
            return new SampleRead(SampleState.PENDING, null);
        }

        public static SampleRead unavailable() {
            return new SampleRead(SampleState.UNAVAILABLE, null);
        }

        public SampleState getState() {
            return state;
        }

        public boolean isPending() {
            return state == SampleState.PENDING;
        }

        public boolean isAvailable() {
            return biomeKey != null;
        }

        public ResourceKey<Biome> getBiomeKey() {
            return biomeKey;
        }
    }

    // Use RegistryKey<Biome> instead of strings for performance
    public static ResourceKey<Biome> readBiomeKey(ServerLevel level, int blockX, int blockZ) {
        return readBiomeKey(level, blockX, blockZ, 0, null);
    }

    public static ResourceKey<Biome> readBiomeKey(ServerLevel level, int blockX, int blockZ, int validationSchemaVersion,
                                                  BiomeSampleCache cache) {
        SampleRead sample = readBiomeSample(level, blockX, blockZ, validationSchemaVersion, cache);
        return sample.getBiomeKey();
    }

    public static SampleRead readBiomeSample(ServerLevel level, int blockX, int blockZ, int validationSchemaVersion,
                                             BiomeSampleCache cache) {
        try {
            int cx = blockX >> 4;
            int cz = blockZ >> 4;
            int biomeY = SAMPLE_Y >> 2;
            BiomeSampleCache.Key key = new BiomeSampleCache.Key(
                level.dimension().location().toString(),
                blockX >> 2,
                biomeY,
                blockZ >> 2,
                validationSchemaVersion
            );
            if (cache != null) {
                BiomeSampleCache.Sample cached = cache.get(key);
                if (cached != null) {
                    if (cached.isChunkUnavailable()) {
                        return SampleRead.unavailable();
                    }
                    Optional<ResourceLocation> biomeId = cached.getBiomeId();
                    if (biomeId.isPresent()) {
                        return SampleRead.ready(ResourceKey.create(Registries.BIOME, biomeId.get()));
                    }
                    return SampleRead.unavailable();
                }
            }

            ChunkAccess chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {
                // Best-effort biome access: request BIOMES asynchronously and never block the server tick.
                CompletableFuture<ChunkResult<ChunkAccess>> future = pendingChunkLoads.computeIfAbsent(
                    key,
                    ignored -> level.getChunkSource().getChunkFuture(cx, cz, ChunkStatus.BIOMES, true)
                );
                if (!future.isDone()) {
                    return SampleRead.pending();
                }
                pendingChunkLoads.remove(key, future);
                try {
                    ChunkResult<ChunkAccess> chunkResult = future.getNow(null);
                    if (chunkResult == null) {
                        if (cache != null) {
                            cache.put(key, null, true);
                        }
                        return SampleRead.unavailable();
                    }
                    chunk = chunkResult.orElse(null);
                } catch (RuntimeException e) {
                    pendingChunkLoads.remove(key, future);
                    if (cache != null) {
                        cache.put(key, null, true);
                    }
                    return SampleRead.unavailable();
                }
            }
            if (chunk == null) {
                if (cache != null) {
                    cache.put(key, null, true);
                }
                return SampleRead.unavailable();
            }
            pendingChunkLoads.remove(key);
            Holder<Biome> holder = chunk.getNoiseBiome(blockX >> 2, biomeY, blockZ >> 2);
            ResourceLocation biomeId = holder.unwrapKey().map(ResourceKey::location).orElse(null);
            if (cache != null) {
                cache.put(key, biomeId, false);
            }
            return biomeId != null ? SampleRead.ready(ResourceKey.create(Registries.BIOME, biomeId)) : SampleRead.unavailable();
        } catch (Exception e) {
            LOGGER.debug("Failed to read biome at ({},{},{}): {}", blockX, SAMPLE_Y, blockZ, e.getMessage());
            return SampleRead.unavailable();
        }
    }

    // Check if a specific position matches the accepted biomes (using RegistryKey set for speed)
    public static boolean matches(ResourceKey<Biome> biomeKey, Set<ResourceKey<Biome>> acceptedKeys) {
        return biomeKey != null && acceptedKeys.contains(biomeKey);
    }
}
