package com.bigbangcraft.regions.region;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class RegionResolver {
    private final RegionCache regionCache;

    private static int getTypePriority(com.bigbangcraft.regions.domain.RegionType type) {
        if (type == com.bigbangcraft.regions.domain.RegionType.SYSTEM_REGION) return 3;
        if (type == com.bigbangcraft.regions.domain.RegionType.ADMIN_REGION) return 2;
        if (type == com.bigbangcraft.regions.domain.RegionType.PLAYER_REGION) return 1;
        return 0;
    }

    public static final Comparator<Region> REGION_PRIORITY_COMPARATOR = (r1, r2) -> {
        // 1. Compare type priority (SYSTEM > ADMIN > PLAYER)
        int typeCompare = Integer.compare(getTypePriority(r2.getType()), getTypePriority(r1.getType()));
        if (typeCompare != 0) {
            return typeCompare;
        }
        // 2. Highest priority wins
        int priorityCompare = Integer.compare(r2.getPriority(), r1.getPriority());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        // 3. Smaller volume wins on tie
        int volumeCompare = Long.compare(r1.getBounds().volume(), r2.getBounds().volume());
        if (volumeCompare != 0) {
            return volumeCompare;
        }
        // 4. Alphabetical / smaller ID wins on tie (deterministic fallback)
        return r1.getId().compareTo(r2.getId());
    };

    public RegionResolver(RegionCache regionCache) {
        this.regionCache = regionCache;
    }

    public Optional<Region> resolveRegionAt(String dimension, int x, int y, int z) {
        List<Region> candidates = regionCache.getRegionsAt(dimension, x, y, z);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Return the first element after sorting by priority rules
        candidates.sort(REGION_PRIORITY_COMPARATOR);
        return Optional.of(candidates.get(0));
    }

    public boolean checkOverlap(RegionBounds bounds, String excludeRegionId) {
        for (Region region : regionCache.getAll()) {
            if (region.getId().equalsIgnoreCase(excludeRegionId)) {
                continue;
            }
            if (region.getBounds().intersects(bounds)) {
                // Check if they are both player regions (overlap not allowed)
                // "1. Regiões de jogador não podem se sobrepor entre si."
                // "2. Regiões administrativas podem se sobrepor a regiões de jogador."
                // We should enforce this check when creating player regions or admin regions.
                // If it's a player region intersecting another player region, overlap is invalid.
                return true;
            }
        }
        return false;
    }
}
