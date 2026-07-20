package com.bigbangcraft.regions.cache;

import com.bigbangcraft.regions.domain.Region;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RegionCache {
    private final Map<String, Region> regionsById = new ConcurrentHashMap<>();
    private final ChunkSpatialIndex spatialIndex = new ChunkSpatialIndex();

    public void add(Region region) {
        regionsById.put(region.getId().toLowerCase(), region);
        spatialIndex.add(region);
    }

    public synchronized void replaceAll(Collection<Region> regions) {
        clear();
        for (Region region : regions) {
            add(region);
        }
    }

    public void remove(String id) {
        if (id == null) return;
        regionsById.remove(id.toLowerCase());
        spatialIndex.remove(id);
    }

    public Region get(String id) {
        if (id == null) return null;
        return regionsById.get(id.toLowerCase());
    }

    public Collection<Region> getAll() {
        return Collections.unmodifiableCollection(regionsById.values());
    }

    public List<Region> getRegionsAt(String dimension, int x, int y, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        Set<String> candidateIds = spatialIndex.getRegionIdsInChunk(dimension, chunkX, chunkZ);
        if (candidateIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Region> matching = new ArrayList<>();
        for (String id : candidateIds) {
            Region region = regionsById.get(id.toLowerCase());
            if (region != null && region.contains(dimension, x, y, z)) {
                matching.add(region);
            }
        }
        return matching;
    }

    public void clear() {
        regionsById.clear();
        spatialIndex.clear();
    }
}
