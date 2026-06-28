package com.bigbangcraft.regions.allocation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ClaimGeometryTest {
    @Test
    public void testClaimCentering() {
        int slotSize = 256;
        int initialClaimSize = 50;
        int claimOffset = (slotSize - initialClaimSize) / 2;

        assertEquals(103, claimOffset, "Offset must be 103 for slotSize=256 and claimSize=50");
    }

    @Test
    public void testClaimBoundsWithinSlot() {
        int slotSize = 256;
        int initialClaimSize = 50;
        int claimOffset = (slotSize - initialClaimSize) / 2;

        int slotMinX = 0;
        int slotMinZ = 0;
        int slotMaxX = slotMinX + slotSize - 1;
        int slotMaxZ = slotMinZ + slotSize - 1;

        int claimMinX = slotMinX + claimOffset;
        int claimMinZ = slotMinZ + claimOffset;
        int claimMaxX = claimMinX + initialClaimSize - 1;
        int claimMaxZ = claimMinZ + initialClaimSize - 1;

        assertEquals(103, claimMinX, "Claim must start at offset 103");
        assertEquals(103, claimMinZ, "Claim must start at offset 103");
        assertEquals(152, claimMaxX, "Claim must end at minX + 49 = 152");
        assertEquals(152, claimMaxZ, "Claim must end at minZ + 49 = 152");

        assertTrue(claimMinX >= slotMinX, "Claim must not start before slot");
        assertTrue(claimMinZ >= slotMinZ, "Claim must not start before slot");
        assertTrue(claimMaxX <= slotMaxX, "Claim must not exceed slot bounds");
        assertTrue(claimMaxZ <= slotMaxZ, "Claim must not exceed slot bounds");

        int claimSize = claimMaxX - claimMinX + 1;
        assertEquals(50, claimSize, "Claim must be exactly 50 blocks wide");
    }

    @Test
    public void testExpansionMargins() {
        int slotSize = 256;
        int maxExpansionSize = 240;

        int expansionOffset = (slotSize - maxExpansionSize) / 2;
        int margin = expansionOffset;

        assertEquals(8, margin, "Margin must be at least 8 blocks for 240 expansion in 256 slot");
    }

    @Test
    public void testFutureExpansionCentering() {
        int slotSize = 256;
        int futureSize = 240;

        int offset = (slotSize - futureSize) / 2;
        int minX = offset;
        int maxX = minX + futureSize - 1;

        assertEquals(8, offset, "Expansion must start at offset 8 for 240 size");
        assertEquals(247, maxX, "240x240 claim must end at 247");
        assertTrue(maxX < slotSize, "Expansion must stay within slot");
    }

    @Test
    public void testMultipleExpansionSizes() {
        int slotSize = 256;
        int[][] testCases = {
            {50, 103, 152},
            {75, 90, 164},
            {100, 78, 177},
            {125, 65, 189},
            {150, 53, 202},
            {175, 40, 214},
            {200, 28, 227},
            {225, 15, 239},
            {240, 8, 247}
        };

        for (int[] tc : testCases) {
            int size = tc[0];
            int expectedOffset = tc[1];
            int expectedMax = tc[2];
            int offset = (slotSize - size) / 2;
            int maxX = offset + size - 1;

            assertEquals(expectedOffset, offset,
                "Offset for " + size + "x" + size + " must be " + expectedOffset);
            assertEquals(expectedMax, maxX,
                "Max X for " + size + "x" + size + " must be " + expectedMax);
        }
    }
}
