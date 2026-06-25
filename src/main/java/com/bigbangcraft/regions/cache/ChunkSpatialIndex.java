package com.bigbangcraft.regions.cache;

import com.bigbangcraft.regions.domain.Region;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkSpatialIndex {
    public static record ChunkKey(String dimension, int chunkX, int chunkZ) {}

    private final Map<ChunkKey, Set<String>> chunkToRegions = new ConcurrentHashMap<>();
    private final Map<String, Set<ChunkKey>> regionToChunks = new ConcurrentHashMap<>();

    public synchronized void add(Region region) {
        // Remove old mapping if present
        remove(region.getId());

        String dim = region.getBounds().getDimension();
        int minChunkX = region.getBounds().getMinX() >> 4;
        int maxChunkX = region.getBounds().getMaxX() >> 4;
        int minChunkZ = region.getBounds().getMinZ() >> 4;
        int maxChunkZ = region.getBounds().getMaxZ() >> 4;

        Set<ChunkKey> chunks = new HashSet<>();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                ChunkKey key = new ChunkKey(dim, cx, cz);
                chunks.add(key);
                chunkToRegions.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(region.getId());
            }
        }

        regionToChunks.put(region.getId(), chunks);
    }

    public synchronized void remove(String regionId) {
        Set<ChunkKey> chunks = regionToChunks.remove(regionId);
        if (chunks != null) {
            for (ChunkKey chunk : chunks) {
                Set<String> regions = chunkToRegions.get(chunk);
                if (regions != null) {
                    regions.remove(regionId);
                    if (regions.isEmpty()) {
                        chunkToRegions.remove(chunk);
                    }
                }
            }
        }
    }

    public Set<String> getRegionIdsInChunk(String dimension, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
        Set<String> ids = chunkToRegions.get(key);
        if (ids == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(ids);
    }

    public synchronized void clear() {
        chunkToRegions.clear();
        regionToChunks.clear();
    }
}
