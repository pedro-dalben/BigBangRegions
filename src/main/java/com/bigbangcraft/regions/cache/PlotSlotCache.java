package com.bigbangcraft.regions.cache;

import com.bigbangcraft.regions.allocation.PlotSlot;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of PlotSlots indexed by (dimension, gridX, gridZ) and by regionId.
 * Thread-safe: byGrid and byRegionId are ConcurrentHashMap. Upsert/remove are synchronized
 * to keep both maps consistent during composite mutations. Readers (get) are unsynchronized
 * because ConcurrentHashMap guarantees visibility of writes published by upsert/remove.
 */
public class PlotSlotCache {
    private final Map<String, PlotSlot> byGrid = new ConcurrentHashMap<>();
    private final Map<String, PlotSlot> byRegionId = new ConcurrentHashMap<>();

    private static String gridKey(String dimension, int gridX, int gridZ) {
        return dimension + ":" + gridX + ":" + gridZ;
    }

    public synchronized void reload(Collection<PlotSlot> slots) {
        byGrid.clear();
        byRegionId.clear();
        if (slots == null) return;
        for (PlotSlot slot : slots) {
            upsert(slot);
        }
    }

    public synchronized void upsert(PlotSlot slot) {
        if (slot == null) return;
        String gridKey = gridKey(slot.getDimensionKey(), slot.getGridX(), slot.getGridZ());
        // Remove previous slot indexed under old regionId mapping if any.
        PlotSlot previous = byGrid.get(gridKey);
        if (previous != null && previous.getRegionId() != null) {
            byRegionId.remove(previous.getRegionId());
        }
        byGrid.put(gridKey, slot);
        if (slot.getRegionId() != null) {
            byRegionId.put(slot.getRegionId(), slot);
        }
    }

    public synchronized void removeByRegionId(String regionId) {
        if (regionId == null) return;
        PlotSlot slot = byRegionId.remove(regionId);
        if (slot != null) {
            byGrid.remove(gridKey(slot.getDimensionKey(), slot.getGridX(), slot.getGridZ()));
        }
    }

    public PlotSlot get(String dimension, int gridX, int gridZ) {
        return byGrid.get(gridKey(dimension, gridX, gridZ));
    }

    public PlotSlot getByRegionId(String regionId) {
        if (regionId == null) return null;
        return byRegionId.get(regionId);
    }

    public Collection<PlotSlot> getAll() {
        return java.util.Collections.unmodifiableCollection(byGrid.values());
    }
}