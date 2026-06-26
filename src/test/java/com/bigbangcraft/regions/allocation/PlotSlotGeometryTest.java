package com.bigbangcraft.regions.allocation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PlotSlotGeometryTest {
    @Test
    public void testSlotCenteringMath() {
        int slotMinX = 1000;
        int slotMinZ = 2000;
        int slotSize = 256;
        int initialClaimSize = 50;

        int offset = (slotSize - initialClaimSize) / 2;
        assertEquals(103, offset);

        int claimMinX = slotMinX + offset;
        int claimMinZ = slotMinZ + offset;
        int claimMaxX = claimMinX + initialClaimSize - 1;
        int claimMaxZ = claimMinZ + initialClaimSize - 1;

        assertEquals(1103, claimMinX);
        assertEquals(2103, claimMinZ);
        assertEquals(1152, claimMaxX);
        assertEquals(2152, claimMaxZ);

        // Verify dimensions: (1152 - 1103 + 1) = 50
        assertEquals(50, claimMaxX - claimMinX + 1);
        assertEquals(50, claimMaxZ - claimMinZ + 1);
    }
}
