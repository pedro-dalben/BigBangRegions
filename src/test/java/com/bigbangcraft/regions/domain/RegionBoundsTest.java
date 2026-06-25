package com.bigbangcraft.regions.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RegionBoundsTest {

    @Test
    public void testInclusiveness() {
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 10, 10, 10, 20, 20, 20);

        // Inside
        assertTrue(bounds.contains("minecraft:overworld", 15, 15, 15));

        // Exact corners (Inclusive test)
        assertTrue(bounds.contains("minecraft:overworld", 10, 10, 10));
        assertTrue(bounds.contains("minecraft:overworld", 20, 20, 20));

        // Outside by one block
        assertFalse(bounds.contains("minecraft:overworld", 9, 15, 15));
        assertFalse(bounds.contains("minecraft:overworld", 15, 21, 15));
        assertFalse(bounds.contains("minecraft:overworld", 15, 15, 9));
        assertFalse(bounds.contains("minecraft:overworld", 21, 15, 15));
    }

    @Test
    public void testDimensionChecking() {
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 10, 10, 10);
        assertTrue(bounds.contains("minecraft:overworld", 5, 5, 5));
        assertFalse(bounds.contains("minecraft:the_nether", 5, 5, 5));
    }

    @Test
    public void testIntersections() {
        RegionBounds boundsA = new RegionBounds("minecraft:overworld", 0, 0, 0, 10, 10, 10);
        RegionBounds boundsB = new RegionBounds("minecraft:overworld", 5, 5, 5, 15, 15, 15);
        RegionBounds boundsC = new RegionBounds("minecraft:overworld", 11, 11, 11, 20, 20, 20);
        RegionBounds boundsNether = new RegionBounds("minecraft:the_nether", 0, 0, 0, 10, 10, 10);

        // A and B intersect
        assertTrue(boundsA.intersects(boundsB));
        assertTrue(boundsB.intersects(boundsA));

        // A and C do not intersect
        assertFalse(boundsA.intersects(boundsC));

        // Same coordinates but different dimensions do not intersect
        assertFalse(boundsA.intersects(boundsNether));
    }

    @Test
    public void testVolume() {
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 2, 2, 2);
        // (2 - 0 + 1) * (2 - 0 + 1) * (2 - 0 + 1) = 3 * 3 * 3 = 27
        assertEquals(27, bounds.volume());
    }
}
