package com.bigbangcraft.regions.allocation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AllocationRequestStateTest {
    @Test
    public void testValidTransitions() {
        assertTrue(AllocationRequestState.PENDING.canTransitionTo(AllocationRequestState.VIRTUAL_SEARCHING));
        assertTrue(AllocationRequestState.PENDING.canTransitionTo(AllocationRequestState.FAILED_NO_TERRAIN));
        assertTrue(AllocationRequestState.PENDING.canTransitionTo(AllocationRequestState.FAILED_VALIDATION));
        assertTrue(AllocationRequestState.PENDING.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));

        assertTrue(AllocationRequestState.VIRTUAL_SEARCHING.canTransitionTo(AllocationRequestState.VIRTUAL_VALIDATED));
        assertTrue(AllocationRequestState.VIRTUAL_SEARCHING.canTransitionTo(AllocationRequestState.FAILED_NO_TERRAIN));
        assertTrue(AllocationRequestState.VIRTUAL_SEARCHING.canTransitionTo(AllocationRequestState.FAILED_VALIDATION));
        assertTrue(AllocationRequestState.VIRTUAL_SEARCHING.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));

        assertTrue(AllocationRequestState.VIRTUAL_VALIDATED.canTransitionTo(AllocationRequestState.SLOT_RESERVED));
        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.PREPARING_CHUNKS));
        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.VIRTUAL_SEARCHING));
        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.FAILED_NO_TERRAIN));
        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.FAILED_VALIDATION));
        assertTrue(AllocationRequestState.SLOT_RESERVED.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));

        assertTrue(AllocationRequestState.PREPARING_CHUNKS.canTransitionTo(AllocationRequestState.WAITING_FOR_CHUNKS));
        assertTrue(AllocationRequestState.PREPARING_CHUNKS.canTransitionTo(AllocationRequestState.VIRTUAL_SEARCHING));
        assertTrue(AllocationRequestState.WAITING_FOR_CHUNKS.canTransitionTo(AllocationRequestState.VIRTUAL_SEARCHING));
        assertTrue(AllocationRequestState.VALIDATING_LOADED_WORLD.canTransitionTo(AllocationRequestState.VIRTUAL_SEARCHING));
        assertTrue(AllocationRequestState.WAITING_FOR_CHUNKS.canTransitionTo(AllocationRequestState.VALIDATING_LOADED_WORLD));
        assertTrue(AllocationRequestState.VALIDATING_LOADED_WORLD.canTransitionTo(AllocationRequestState.REGION_CREATING));

        assertTrue(AllocationRequestState.REGION_CREATING.canTransitionTo(AllocationRequestState.COMPLETED));
        assertTrue(AllocationRequestState.REGION_CREATING.canTransitionTo(AllocationRequestState.PAUSED_RECOVERY));
        assertTrue(AllocationRequestState.PAUSED_RECOVERY.canTransitionTo(AllocationRequestState.VIRTUAL_SEARCHING));
    }

    @Test
    public void testInvalidTransitions() {
        assertFalse(AllocationRequestState.COMPLETED.canTransitionTo(AllocationRequestState.VIRTUAL_SEARCHING));
        assertFalse(AllocationRequestState.FAILED_NO_TERRAIN.canTransitionTo(AllocationRequestState.PENDING));
        assertFalse(AllocationRequestState.FAILED_VALIDATION.canTransitionTo(AllocationRequestState.PENDING));
        assertFalse(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION.canTransitionTo(AllocationRequestState.COMPLETED));
        assertFalse(AllocationRequestState.PAUSED_RECOVERY.canTransitionTo(AllocationRequestState.COMPLETED));

        // Legacy states are terminal
        assertFalse(AllocationRequestState.PAYMENT_RESERVE_PENDING.canTransitionTo(AllocationRequestState.PAYMENT_RESERVED));
        assertFalse(AllocationRequestState.RELEASE_PENDING.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));
    }

    @Test
    public void testPreRegionCreation() {
        assertTrue(AllocationRequestState.PENDING.isPreRegionCreation());
        assertTrue(AllocationRequestState.VIRTUAL_SEARCHING.isPreRegionCreation());
        assertTrue(AllocationRequestState.VIRTUAL_VALIDATED.isPreRegionCreation());
        assertTrue(AllocationRequestState.SLOT_RESERVED.isPreRegionCreation());
        assertTrue(AllocationRequestState.PREPARING_CHUNKS.isPreRegionCreation());
        assertTrue(AllocationRequestState.WAITING_FOR_CHUNKS.isPreRegionCreation());
        assertTrue(AllocationRequestState.VALIDATING_LOADED_WORLD.isPreRegionCreation());
        assertTrue(AllocationRequestState.REGION_CREATING.isPreRegionCreation());

        assertFalse(AllocationRequestState.COMPLETED.isPreRegionCreation());
        assertFalse(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION.isPreRegionCreation());
        assertFalse(AllocationRequestState.FAILED_NO_TERRAIN.isPreRegionCreation());
    }

    @Test
    public void testTerminal() {
        assertTrue(AllocationRequestState.COMPLETED.isTerminal());
        assertTrue(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION.isTerminal());
        assertTrue(AllocationRequestState.FAILED_NO_TERRAIN.isTerminal());
        assertTrue(AllocationRequestState.FAILED_VALIDATION.isTerminal());
        assertTrue(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION.isTerminal());
        assertTrue(AllocationRequestState.FAILED.isTerminal());
        assertTrue(AllocationRequestState.CANCELLED.isTerminal());
        assertTrue(AllocationRequestState.LEGACY_REQUIRES_ADMIN_REVIEW.isTerminal());
        assertTrue(AllocationRequestState.PAUSED_RECOVERY.isTerminal());

        assertFalse(AllocationRequestState.PENDING.isTerminal());
        assertFalse(AllocationRequestState.VIRTUAL_SEARCHING.isTerminal());
        assertFalse(AllocationRequestState.VIRTUAL_VALIDATED.isTerminal());
        assertFalse(AllocationRequestState.SLOT_RESERVED.isTerminal());
        assertFalse(AllocationRequestState.PREPARING_CHUNKS.isTerminal());
        assertFalse(AllocationRequestState.WAITING_FOR_CHUNKS.isTerminal());
        assertFalse(AllocationRequestState.VALIDATING_LOADED_WORLD.isTerminal());
        assertFalse(AllocationRequestState.REGION_CREATING.isTerminal());
    }

    @Test
    public void testCanReleasePayment() {
        assertTrue(AllocationRequestState.PAYMENT_RESERVE_PENDING.canReleasePayment());
        assertTrue(AllocationRequestState.PAYMENT_RESERVED.canReleasePayment());
        assertTrue(AllocationRequestState.PAYMENT_RENEW_PENDING.canReleasePayment());
        assertTrue(AllocationRequestState.RELEASE_PENDING.canReleasePayment());
        assertTrue(AllocationRequestState.LEGACY_REQUIRES_ADMIN_REVIEW.canReleasePayment());

        assertFalse(AllocationRequestState.PENDING.canReleasePayment());
        assertFalse(AllocationRequestState.VIRTUAL_SEARCHING.canReleasePayment());
        assertFalse(AllocationRequestState.SLOT_RESERVED.canReleasePayment());
        assertFalse(AllocationRequestState.PREPARING_CHUNKS.canReleasePayment());
        assertFalse(AllocationRequestState.REGION_CREATING.canReleasePayment());
        assertFalse(AllocationRequestState.COMPLETED.canReleasePayment());
    }
}
