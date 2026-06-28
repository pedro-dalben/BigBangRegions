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
        // Exclusion: -10000 to 10000, safety buffer: 1000 => effective exclusion: -11000 to 11000
        // A slot at 10000 to 10384 intersects [ -11000, 11000 ] => should be ineligible
        assertFalse(service.isSlotEligible(10000, 10000, 384));

        // A slot at 11500 to 11884 does not intersect => should be eligible
        assertTrue(service.isSlotEligible(11500, 11500, 384));
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

        // Slot at [ 21900, 22284 ] intersects existing region => ineligible
        assertFalse(service.isSlotEligible(21900, 21900, 384));

        // Slot at [ 22500, 22884 ] does not intersect => eligible
        assertTrue(service.isSlotEligible(22500, 22500, 384));
    }
}
