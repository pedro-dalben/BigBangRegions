package com.bigbangcraft.regions.bigmoncraft;

import com.bigbangcraft.regions.config.Config;
import com.pedrodalben.bigmoncraft.api.ServerMapSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BigMonCraftRegionMapStyleTest {
    @Test
    void rendersOnlyTheConfiguredBorder() {
        ServerMapSnapshot.Style style = BigMonCraftRegionMapIntegration.toStyle(
            new Config.JourneyMapConfig.RegionStyle(0x123456, 0x654321, 0.8f, 0.65f));

        assertEquals(0.0f, style.fillOpacity());
        assertEquals(0x123456, style.fillColor());
        assertEquals(0x654321, style.strokeColor());
        assertEquals(2.0f, style.strokeWidth());
        assertEquals(0.65f, style.strokeOpacity());
    }
}
