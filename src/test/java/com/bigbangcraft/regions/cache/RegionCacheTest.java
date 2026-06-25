package com.bigbangcraft.regions.cache;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class RegionCacheTest {
    private RegionCache cache;
    private UUID creator;

    @BeforeEach
    public void setUp() {
        cache = new RegionCache();
        creator = UUID.randomUUID();
    }

    @Test
    public void testCacheAddGetRemove() {
        RegionBounds bounds = new RegionBounds("overworld", 0, 0, 0, 10, 10, 10);
        Region region = new Region("regA", "Region A", RegionType.ADMIN_REGION, bounds, 1000, null, creator, 0, 0, "ACTIVE");

        cache.add(region);
        assertEquals(region, cache.get("regA"));
        assertEquals(region, cache.get("REGA")); // Case insensitivity

        cache.remove("regA");
        assertNull(cache.get("regA"));
    }

    @Test
    public void testGetRegionsAtCoordinates() {
        RegionBounds boundsA = new RegionBounds("overworld", 0, 0, 0, 10, 10, 10);
        Region regA = new Region("regA", "Region A", RegionType.ADMIN_REGION, boundsA, 1000, null, creator, 0, 0, "ACTIVE");

        RegionBounds boundsB = new RegionBounds("overworld", 5, 5, 5, 15, 15, 15);
        Region regB = new Region("regB", "Region B", RegionType.ADMIN_REGION, boundsB, 500, null, creator, 0, 0, "ACTIVE");

        cache.add(regA);
        cache.add(regB);

        // Position 3,3,3 is only in A
        List<Region> list = cache.getRegionsAt("overworld", 3, 3, 3);
        assertEquals(1, list.size());
        assertTrue(list.contains(regA));

        // Position 7,7,7 is in both A and B
        list = cache.getRegionsAt("overworld", 7, 7, 7);
        assertEquals(2, list.size());
        assertTrue(list.contains(regA));
        assertTrue(list.contains(regB));

        // Position 12,12,12 is only in B
        list = cache.getRegionsAt("overworld", 12, 12, 12);
        assertEquals(1, list.size());
        assertTrue(list.contains(regB));

        // Position 20,20,20 is in none
        list = cache.getRegionsAt("overworld", 20, 20, 20);
        assertTrue(list.isEmpty());
    }
}
