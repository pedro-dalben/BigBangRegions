package com.bigbangcraft.regions.expansion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RegionExpansionStateTest {
    @Test
    public void testValidTransitions() {
        assertTrue(RegionExpansionState.REQUESTED.canTransitionTo(RegionExpansionState.QUOTED));
        assertTrue(RegionExpansionState.REQUESTED.canTransitionTo(RegionExpansionState.FAILED_VALIDATION));
        assertTrue(RegionExpansionState.REQUESTED.canTransitionTo(RegionExpansionState.CANCELLED_BEFORE_RESIZE));

        assertTrue(RegionExpansionState.QUOTED.canTransitionTo(RegionExpansionState.PAYMENT_RESERVE_PENDING));
        assertTrue(RegionExpansionState.QUOTED.canTransitionTo(RegionExpansionState.FAILED_ECONOMY_UNAVAILABLE));
        assertTrue(RegionExpansionState.QUOTED.canTransitionTo(RegionExpansionState.CANCELLED_BEFORE_RESIZE));

        assertTrue(RegionExpansionState.PAYMENT_RESERVE_PENDING.canTransitionTo(RegionExpansionState.PAYMENT_RESERVED));
        assertTrue(RegionExpansionState.PAYMENT_RESERVE_PENDING.canTransitionTo(RegionExpansionState.FAILED_ECONOMY_UNAVAILABLE));
        assertTrue(RegionExpansionState.PAYMENT_RESERVE_PENDING.canTransitionTo(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION));
        assertTrue(RegionExpansionState.PAYMENT_RESERVE_PENDING.canTransitionTo(RegionExpansionState.CANCELLED_BEFORE_RESIZE));

        assertTrue(RegionExpansionState.PAYMENT_RESERVED.canTransitionTo(RegionExpansionState.PAYMENT_RENEW_PENDING));
        assertTrue(RegionExpansionState.PAYMENT_RESERVED.canTransitionTo(RegionExpansionState.RESIZE_APPLYING));
        assertTrue(RegionExpansionState.PAYMENT_RESERVED.canTransitionTo(RegionExpansionState.RELEASE_PENDING));
        assertTrue(RegionExpansionState.PAYMENT_RESERVED.canTransitionTo(RegionExpansionState.CANCELLED_BEFORE_RESIZE));

        assertTrue(RegionExpansionState.PAYMENT_RENEW_PENDING.canTransitionTo(RegionExpansionState.PAYMENT_RESERVED));
        assertTrue(RegionExpansionState.PAYMENT_RENEW_PENDING.canTransitionTo(RegionExpansionState.RELEASE_PENDING));
        assertTrue(RegionExpansionState.PAYMENT_RENEW_PENDING.canTransitionTo(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION));
        assertTrue(RegionExpansionState.PAYMENT_RENEW_PENDING.canTransitionTo(RegionExpansionState.CANCELLED_BEFORE_RESIZE));

        assertTrue(RegionExpansionState.RESIZE_APPLYING.canTransitionTo(RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING));
        assertTrue(RegionExpansionState.RESIZE_APPLYING.canTransitionTo(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION));

        assertTrue(RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING.canTransitionTo(RegionExpansionState.COMPLETED));
        assertTrue(RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING.canTransitionTo(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION));
    }

    @Test
    public void testInvalidTransitions() {
        assertFalse(RegionExpansionState.REQUESTED.canTransitionTo(RegionExpansionState.COMPLETED));
        assertFalse(RegionExpansionState.QUOTED.canTransitionTo(RegionExpansionState.RESIZE_APPLYING));
        assertFalse(RegionExpansionState.COMPLETED.canTransitionTo(RegionExpansionState.REQUESTED));
        assertFalse(RegionExpansionState.CANCELLED_BEFORE_RESIZE.canTransitionTo(RegionExpansionState.REQUESTED));
        assertFalse(RegionExpansionState.FAILED_VALIDATION.canTransitionTo(RegionExpansionState.QUOTED));
        assertFalse(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION.canTransitionTo(RegionExpansionState.COMPLETED));
        assertFalse(RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING.canTransitionTo(RegionExpansionState.CANCELLED_BEFORE_RESIZE));
    }

    @Test
    public void testIsPreResize() {
        assertTrue(RegionExpansionState.REQUESTED.isPreResize());
        assertTrue(RegionExpansionState.QUOTED.isPreResize());
        assertTrue(RegionExpansionState.PAYMENT_RESERVE_PENDING.isPreResize());
        assertTrue(RegionExpansionState.PAYMENT_RESERVED.isPreResize());
        assertTrue(RegionExpansionState.PAYMENT_RENEW_PENDING.isPreResize());
        assertTrue(RegionExpansionState.RELEASE_PENDING.isPreResize());

        assertFalse(RegionExpansionState.RESIZE_APPLYING.isPreResize());
        assertFalse(RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING.isPreResize());
        assertFalse(RegionExpansionState.COMPLETED.isPreResize());
        assertFalse(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION.isPreResize());
    }

    @Test
    public void testIsTerminal() {
        assertTrue(RegionExpansionState.COMPLETED.isTerminal());
        assertTrue(RegionExpansionState.CANCELLED_BEFORE_RESIZE.isTerminal());
        assertTrue(RegionExpansionState.FAILED_VALIDATION.isTerminal());
        assertTrue(RegionExpansionState.FAILED_ECONOMY_UNAVAILABLE.isTerminal());
        assertTrue(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION.isTerminal());

        assertFalse(RegionExpansionState.REQUESTED.isTerminal());
        assertFalse(RegionExpansionState.PAYMENT_RESERVE_PENDING.isTerminal());
        assertFalse(RegionExpansionState.PAYMENT_RESERVED.isTerminal());
        assertFalse(RegionExpansionState.RESIZE_APPLYING.isTerminal());
        assertFalse(RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING.isTerminal());
    }

    @Test
    public void testCanReleasePayment() {
        assertTrue(RegionExpansionState.PAYMENT_RESERVED.canReleasePayment());
        assertTrue(RegionExpansionState.PAYMENT_RENEW_PENDING.canReleasePayment());
        assertTrue(RegionExpansionState.RELEASE_PENDING.canReleasePayment());

        assertFalse(RegionExpansionState.REQUESTED.canReleasePayment());
        assertFalse(RegionExpansionState.QUOTED.canReleasePayment());
        assertFalse(RegionExpansionState.PAYMENT_RESERVE_PENDING.canReleasePayment());
        assertFalse(RegionExpansionState.RESIZE_APPLYING.canReleasePayment());
        assertFalse(RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING.canReleasePayment());
        assertFalse(RegionExpansionState.COMPLETED.canReleasePayment());
    }
}
