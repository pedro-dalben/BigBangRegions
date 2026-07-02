package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PlotSlotEligibilityTest {
    private ConfigManager configManager;
    private PlotSlotRepository plotSlotRepository;
    private RegionCache regionCache;
    private PlotSlotService service;

    @BeforeEach
    public void setUp() {
        configManager = mock(ConfigManager.class);
        Config config = new Config();
        when(configManager.getConfig()).thenReturn(config);

        plotSlotRepository = mock(PlotSlotRepository.class);
        regionCache = new RegionCache();
        service = new PlotSlotService(configManager, plotSlotRepository, regionCache);
    }

    @Test
    public void testExclusionZoneIntersects() {
        // Exclusion: -2000 to 2000, safety buffer: 0 => effective exclusion: -2000 to 2000
        // The last 512x512 slot that still overlaps the exclusion starts at 1536 and ends at 2047.
        assertFalse(service.isSlotEligible(1536, 1536, 512));

        // The first eligible slot starts at 2048 and no longer intersects the exclusion zone.
        assertTrue(service.isSlotEligible(2048, 2048, 512));
    }

    @Test
    public void testExistingRegionIntersects() {
        // Add existing region to cache at [ 22000, 22100 ]
        Region region = new Region(
                "existing", "Existing", RegionType.ADMIN_REGION,
                new RegionBounds("minecraft:overworld", 22000, 0, 22000, 22100, 100, 22100),
                100, null, UUID.randomUUID(), 0, 0, "ACTIVE"
        );
        regionCache.add(region);

        // Slot at [ 21900, 22412 ] intersects existing region => ineligible
        assertFalse(service.isSlotEligible(21900, 21900, 512));

        // Slot at [ 22500, 23012 ] does not intersect => eligible
        assertTrue(service.isSlotEligible(22500, 22500, 512));
    }
}
