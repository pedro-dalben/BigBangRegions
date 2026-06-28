package com.bigbangcraft.regions.allocation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AllocationRequestStateTest {
    @Test
    public void testValidTransitions() {
        assertTrue(AllocationRequestState.PENDING.canTransitionTo(AllocationRequestState.SEARCHING));
        assertTrue(AllocationRequestState.PENDING.canTransitionTo(AllocationRequestState.FAILED_NO_TERRAIN));
        assertTrue(AllocationRequestState.PENDING.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));

        assertTrue(AllocationRequestState.SEARCHING.canTransitionTo(AllocationRequestState.SLOT_RESERVED));
        assertTrue(AllocationRequestState.SEARCHING.canTransitionTo(AllocationRequestState.FAILED_NO_TERRAIN));
        assertTrue(AllocationRequestState.SEARCHING.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));

        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.PAYMENT_RESERVE_PENDING));
        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.PREPARING));
        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.RELEASE_PENDING));
        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.FAILED_NO_TERRAIN));
        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));

        assertTrue(AllocationRequestState.PAYMENT_RESERVE_PENDING.canTransitionTo(AllocationRequestState.PAYMENT_RESERVED));
        assertTrue(AllocationRequestState.PAYMENT_RESERVE_PENDING.canTransitionTo(AllocationRequestState.RELEASE_PENDING));
        assertTrue(AllocationRequestState.PAYMENT_RESERVE_PENDING.canTransitionTo(AllocationRequestState.FAILED_ECONOMY_UNAVAILABLE));
        assertTrue(AllocationRequestState.PAYMENT_RESERVE_PENDING.canTransitionTo(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION));
        assertTrue(AllocationRequestState.PAYMENT_RESERVE_PENDING.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));

        assertTrue(AllocationRequestState.PAYMENT_RESERVED.canTransitionTo(AllocationRequestState.PREPARING));
        assertTrue(AllocationRequestState.PAYMENT_RESERVED.canTransitionTo(AllocationRequestState.RELEASE_PENDING));
        assertTrue(AllocationRequestState.PAYMENT_RESERVED.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));

        assertTrue(AllocationRequestState.PREPARING.canTransitionTo(AllocationRequestState.REGION_CREATING));
        assertTrue(AllocationRequestState.PREPARING.canTransitionTo(AllocationRequestState.RELEASE_PENDING));
        assertTrue(AllocationRequestState.PREPARING.canTransitionTo(AllocationRequestState.FAILED_NO_TERRAIN));
        assertTrue(AllocationRequestState.PREPARING.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));

        assertTrue(AllocationRequestState.REGION_CREATING.canTransitionTo(AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING));
        assertTrue(AllocationRequestState.REGION_CREATING.canTransitionTo(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION));

        assertTrue(AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING.canTransitionTo(AllocationRequestState.COMPLETED));
        assertTrue(AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING.canTransitionTo(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION));
    }

    @Test
    public void testInvalidTransitions() {
        assertFalse(AllocationRequestState.COMPLETED.canTransitionTo(AllocationRequestState.SEARCHING));
        assertFalse(AllocationRequestState.FAILED_NO_TERRAIN.canTransitionTo(AllocationRequestState.PENDING));
        assertFalse(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION.canTransitionTo(AllocationRequestState.COMPLETED));
        assertFalse(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION.canTransitionTo(AllocationRequestState.COMPLETED));
    }

    @Test
    public void testPreRegionCreation() {
        assertTrue(AllocationRequestState.PENDING.isPreRegionCreation());
        assertTrue(AllocationRequestState.SEARCHING.isPreRegionCreation());
        assertTrue(AllocationRequestState.SLOT_RESERVED.isPreRegionCreation());
        assertTrue(AllocationRequestState.PAYMENT_RESERVE_PENDING.isPreRegionCreation());
        assertTrue(AllocationRequestState.PAYMENT_RESERVED.isPreRegionCreation());
        assertTrue(AllocationRequestState.PREPARING.isPreRegionCreation());
        assertTrue(AllocationRequestState.REGION_CREATING.isPreRegionCreation());
        
        assertFalse(AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING.isPreRegionCreation());
        assertFalse(AllocationRequestState.COMPLETED.isPreRegionCreation());
        assertFalse(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION.isPreRegionCreation());
    }

    @Test
    public void testTerminal() {
        assertTrue(AllocationRequestState.COMPLETED.isTerminal());
        assertTrue(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION.isTerminal());
        assertTrue(AllocationRequestState.FAILED_NO_TERRAIN.isTerminal());
        assertTrue(AllocationRequestState.FAILED_ECONOMY_UNAVAILABLE.isTerminal());
        assertTrue(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION.isTerminal());
        
        assertFalse(AllocationRequestState.PENDING.isTerminal());
        assertFalse(AllocationRequestState.SEARCHING.isTerminal());
        assertFalse(AllocationRequestState.SLOT_RESERVED.isTerminal());
        assertFalse(AllocationRequestState.PAYMENT_RESERVE_PENDING.isTerminal());
        assertFalse(AllocationRequestState.PAYMENT_RESERVED.isTerminal());
        assertFalse(AllocationRequestState.PREPARING.isTerminal());
        assertFalse(AllocationRequestState.REGION_CREATING.isTerminal());
        assertFalse(AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING.isTerminal());
    }

    @Test
    public void testCanReleasePayment() {
        assertTrue(AllocationRequestState.PAYMENT_RESERVE_PENDING.canReleasePayment());
        assertTrue(AllocationRequestState.PAYMENT_RESERVED.canReleasePayment());
        assertTrue(AllocationRequestState.PREPARING.canReleasePayment());
        assertTrue(AllocationRequestState.RELEASE_PENDING.canReleasePayment());
        
        assertFalse(AllocationRequestState.PENDING.canReleasePayment());
        assertFalse(AllocationRequestState.SEARCHING.canReleasePayment());
        assertFalse(AllocationRequestState.SLOT_RESERVED.canReleasePayment());
        assertFalse(AllocationRequestState.REGION_CREATING.canReleasePayment());
        assertFalse(AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING.canReleasePayment());
        assertFalse(AllocationRequestState.COMPLETED.canReleasePayment());
    }
}
