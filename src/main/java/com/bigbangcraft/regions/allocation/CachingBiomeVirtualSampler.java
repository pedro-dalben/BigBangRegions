package com.bigbangcraft.regions.allocation;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CachingBiomeVirtualSampler implements BiomeVirtualSampler {
    private final int maxEntries;
    private final long ttlMillis;
    private final ConcurrentMap<String, CacheBucket> buckets = new ConcurrentHashMap<>();

    public CachingBiomeVirtualSampler(int maxEntries, long ttlMillis) {
        this.maxEntries = Math.max(16, maxEntries);
        this.ttlMillis = Math.max(1_000L, ttlMillis);
    }

    @Override
    public ResourceKey<Biome> sampleAtBlock(WorldgenSearchContext context, int blockX, int blockY, int blockZ) {
        long packedQuart = BiomeCoordinateMath.packQuart(
            BiomeCoordinateMath.blockToQuart(blockX),
            BiomeCoordinateMath.blockToQuart(blockY),
            BiomeCoordinateMath.blockToQuart(blockZ)
        );

        CacheBucket bucket = buckets.computeIfAbsent(context.fingerprint().hash(), ignored -> new CacheBucket(maxEntries, ttlMillis));
        Optional<ResourceKey<Biome>> cached = bucket.get(packedQuart);
        if (cached != null) {
            return cached.orElse(null);
        }

        Holder<Biome> holder = context.biomeSource().getNoiseBiome(
            BiomeCoordinateMath.blockToQuart(blockX),
            BiomeCoordinateMath.blockToQuart(blockY),
            BiomeCoordinateMath.blockToQuart(blockZ),
            context.noiseSampler()
        );
        Optional<ResourceKey<Biome>> result = holder.unwrapKey();
        bucket.put(packedQuart, result);
        return result.orElse(null);
    }

    public void clear() {
        buckets.clear();
    }

    private static final class CacheBucket {
        private final int maxEntries;
        private final long ttlMillis;
        private final Map<Long, CacheEntry> entries;

        private CacheBucket(int maxEntries, long ttlMillis) {
            this.maxEntries = maxEntries;
            this.ttlMillis = ttlMillis;
            this.entries = new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, CacheEntry> eldest) {
                    return size() > CacheBucket.this.maxEntries;
                }
            };
        }

        private synchronized Optional<ResourceKey<Biome>> get(long packedQuart) {
            CacheEntry entry = entries.get(packedQuart);
            if (entry == null) {
                return null;
            }
            long now = System.currentTimeMillis();
            if (entry.isExpired(now)) {
                entries.remove(packedQuart);
                return null;
            }
            return entry.biomeKey();
        }

        private synchronized void put(long packedQuart, Optional<ResourceKey<Biome>> biomeKey) {
            entries.put(packedQuart, new CacheEntry(biomeKey, System.currentTimeMillis() + ttlMillis));
        }
    }

    private record CacheEntry(Optional<ResourceKey<Biome>> biomeKey, long expiresAt) {
        private boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }
}
