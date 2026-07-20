package com.bigbangcraft.regions.expansion;

import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.config.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RegionExpansionDirectionalTest {
    private static RegionBounds base() { return new RegionBounds("minecraft:overworld", 100, 0, 200, 179, 100, 279); }

    @Test public void expandsOnlySelectedSide() {
        RegionBounds east = RegionExpansionCoordinator.directionalBounds(base(), ExpansionDirection.EAST, 5);
        assertEquals(100, east.getMinX()); assertEquals(184, east.getMaxX());
        assertEquals(200, east.getMinZ()); assertEquals(279, east.getMaxZ());
    }

    @Test public void allSidesAddsFourStrips() {
        RegionBounds all = RegionExpansionCoordinator.directionalBounds(base(), ExpansionDirection.ALL, 5);
        assertEquals(95, all.getMinX()); assertEquals(184, all.getMaxX());
        assertEquals(195, all.getMinZ()); assertEquals(284, all.getMaxZ());
        assertEquals(1700, area(all) - area(base()));
    }

    @Test public void priceUsesNewAreaForOneSide() {
        Config.RegionExpansionConfig config = new Config.RegionExpansionConfig();
        config.setPricePerAddedBlock(3);
        RegionExpansionPricingPolicy policy = new RegionExpansionPricingPolicy(config);
        RegionExpansionQuote quote = policy.calculateQuote(base(), RegionExpansionCoordinator.directionalBounds(base(), ExpansionDirection.EAST, 5));
        assertEquals(400, quote.getAdditionalBlocks());
        assertEquals(1200, quote.getPriceGems());
    }

    private static long area(RegionBounds b) { return (long) (b.getMaxX() - b.getMinX() + 1) * (b.getMaxZ() - b.getMinZ() + 1); }
}
