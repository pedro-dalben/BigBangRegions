package com.bigbangcraft.regions.domain;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.region.RegionResolver;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class RegionPriorityTest {

    @Test
    public void testPriorityResolutionCascade() {
        RegionCache cache = new RegionCache();
        RegionResolver resolver = new RegionResolver(cache);
        UUID creator = UUID.randomUUID();

        // 1. High priority wins over low priority
        Region regLow = new Region("reg_low", "Low Priority", RegionType.PLAYER_REGION,
                new RegionBounds("overworld", 0, 0, 0, 10, 10, 10), 100, UUID.randomUUID(), creator, 0, 0, "ACTIVE");

        Region regHigh = new Region("reg_high", "High Priority", RegionType.ADMIN_REGION,
                new RegionBounds("overworld", 0, 0, 0, 10, 10, 10), 1000, null, creator, 0, 0, "ACTIVE");

        cache.add(regLow);
        cache.add(regHigh);

        Optional<Region> resolved = resolver.resolveRegionAt("overworld", 5, 5, 5);
        assertTrue(resolved.isPresent());
        assertEquals("reg_high", resolved.get().getId());

        // 2. Same priority: smaller volume wins
        cache.clear();
        Region regLarge = new Region("reg_large", "Large Region", RegionType.ADMIN_REGION,
                new RegionBounds("overworld", 0, 0, 0, 10, 10, 10), 500, null, creator, 0, 0, "ACTIVE"); // Vol: 1331

        Region regSmall = new Region("reg_small", "Small Region", RegionType.ADMIN_REGION,
                new RegionBounds("overworld", 2, 2, 2, 4, 4, 4), 500, null, creator, 0, 0, "ACTIVE"); // Vol: 27

        cache.add(regLarge);
        cache.add(regSmall);

        resolved = resolver.resolveRegionAt("overworld", 3, 3, 3);
        assertTrue(resolved.isPresent());
        assertEquals("reg_small", resolved.get().getId());

        // 3. Same priority, same volume: alphabetical ID wins
        cache.clear();
        Region regB = new Region("regB", "B Region", RegionType.ADMIN_REGION,
                new RegionBounds("overworld", 0, 0, 0, 5, 5, 5), 500, null, creator, 0, 0, "ACTIVE");

        Region regA = new Region("regA", "A Region", RegionType.ADMIN_REGION,
                new RegionBounds("overworld", 0, 0, 0, 5, 5, 5), 500, null, creator, 0, 0, "ACTIVE");

        cache.add(regB);
        cache.add(regA);

        resolved = resolver.resolveRegionAt("overworld", 1, 1, 1);
        assertTrue(resolved.isPresent());
        assertEquals("regA", resolved.get().getId()); // 'regA' comes before 'regB'
    }
}
