package com.bigbangcraft.regions.virtualpasture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VirtualPastureLimitPolicyTest {
    @Test
    void allowsPlacementBelowEveryLimit() {
        assertTrue(VirtualPastureLimitPolicy.check(new VirtualPastureLimitPolicy.Counts(1, 1, 0), 2, 2, 1, true, false).allowed());
    }

    @Test
    void blocksRegionLimit() {
        var result = VirtualPastureLimitPolicy.check(new VirtualPastureLimitPolicy.Counts(2, 0, 0), 2, 2, 1, true, false);
        assertFalse(result.allowed());
        assertEquals("região", result.scope());
    }

    @Test
    void blocksOwnerLimitIncludingMembers() {
        var result = VirtualPastureLimitPolicy.check(new VirtualPastureLimitPolicy.Counts(0, 2, 0), 3, 2, 1, true, false);
        assertFalse(result.allowed());
        assertEquals("proprietário", result.scope());
    }

    @Test
    void blocksChunkLimit() {
        var result = VirtualPastureLimitPolicy.check(new VirtualPastureLimitPolicy.Counts(0, 0, 1), 2, 2, 1, true, false);
        assertFalse(result.allowed());
        assertEquals("chunk", result.scope());
    }

    @Test
    void bypassIgnoresAllLimits() {
        assertTrue(VirtualPastureLimitPolicy.check(new VirtualPastureLimitPolicy.Counts(99, 99, 99), 1, 1, 1, true, true).allowed());
    }

    @Test
    void ownerLimitIsNotAppliedToOwnerlessAdminRegions() {
        assertTrue(VirtualPastureLimitPolicy.check(new VirtualPastureLimitPolicy.Counts(0, 99, 0), 1, 1, 1, false, false).allowed());
    }
}
