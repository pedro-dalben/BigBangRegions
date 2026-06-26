package com.bigbangcraft.regions.allocation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AllocationRequestStateTest {
    @Test
    public void testValidTransitions() {
        assertTrue(AllocationRequestState.PENDING.canTransitionTo(AllocationRequestState.SEARCHING));
        assertTrue(AllocationRequestState.PENDING.canTransitionTo(AllocationRequestState.FAILED));
        assertTrue(AllocationRequestState.PENDING.canTransitionTo(AllocationRequestState.CANCELLED));

        assertTrue(AllocationRequestState.SEARCHING.canTransitionTo(AllocationRequestState.SLOT_RESERVED));
        assertTrue(AllocationRequestState.SEARCHING.canTransitionTo(AllocationRequestState.FAILED));
        assertTrue(AllocationRequestState.SEARCHING.canTransitionTo(AllocationRequestState.CANCELLED));

        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.PREPARING));
        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.FAILED));
        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.CANCELLED));

        assertTrue(AllocationRequestState.PREPARING.canTransitionTo(AllocationRequestState.COMPLETED));
        assertTrue(AllocationRequestState.PREPARING.canTransitionTo(AllocationRequestState.FAILED));
        assertTrue(AllocationRequestState.PREPARING.canTransitionTo(AllocationRequestState.CANCELLED));
    }

    @Test
    public void testInvalidTransitions() {
        assertFalse(AllocationRequestState.COMPLETED.canTransitionTo(AllocationRequestState.SEARCHING));
        assertFalse(AllocationRequestState.FAILED.canTransitionTo(AllocationRequestState.PENDING));
        assertFalse(AllocationRequestState.CANCELLED.canTransitionTo(AllocationRequestState.COMPLETED));
    }
}
