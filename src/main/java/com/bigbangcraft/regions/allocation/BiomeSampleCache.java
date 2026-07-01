package com.bigbangcraft.regions.allocation;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BiomeSampleCache {
    public record Key(String dimensionId, int quartX, int quartY, int quartZ, int validationSchemaVersion) {
        public Key {
            Objects.requireNonNull(dimensionId, "dimensionId");
        }
    }

    public static final class Sample {
        private final ResourceLocation biomeId;
        private final boolean chunkUnavailable;
        private final long expiresAt;

        public Sample(ResourceLocation biomeId, boolean chunkUnavailable, long expiresAt) {
            this.biomeId = biomeId;
            this.chunkUnavailable = chunkUnavailable;
            this.expiresAt = expiresAt;
        }

        public Optional<ResourceLocation> getBiomeId() {
            return Optional.ofNullable(biomeId);
        }

        public boolean isChunkUnavailable() {
            return chunkUnavailable;
        }

        public boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }

    private final int maxEntries;
    private final long ttlMillis;
    private final Map<Key, Sample> cache;

    public BiomeSampleCache(int maxEntries, long ttlMillis) {
        this.maxEntries = Math.max(16, maxEntries);
        this.ttlMillis = Math.max(1_000L, ttlMillis);
        this.cache = new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, Sample> eldest) {
                return size() > BiomeSampleCache.this.maxEntries;
            }
        };
    }

    public synchronized Sample get(Key key) {
        Sample sample = cache.get(key);
        if (sample == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (sample.isExpired(now)) {
            cache.remove(key);
            return null;
        }
        return sample;
    }

    public synchronized void put(Key key, ResourceLocation biomeId, boolean chunkUnavailable) {
        cache.put(key, new Sample(biomeId, chunkUnavailable, System.currentTimeMillis() + ttlMillis));
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }
}
