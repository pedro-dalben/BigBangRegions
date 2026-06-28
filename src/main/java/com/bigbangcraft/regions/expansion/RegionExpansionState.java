package com.bigbangcraft.regions.expansion;

public enum RegionExpansionState {
    REQUESTED,
    QUOTED,

    PAYMENT_RESERVE_PENDING,
    PAYMENT_RESERVED,
    PAYMENT_RENEW_PENDING,

    RESIZE_APPLYING,
    RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING,

    RELEASE_PENDING,
    CANCELLED_BEFORE_RESIZE,

    COMPLETED,

    FAILED_VALIDATION,
    FAILED_ECONOMY_UNAVAILABLE,
    BLOCKED_FOR_MANUAL_RECONCILIATION;

    public boolean canTransitionTo(RegionExpansionState next) {
        switch (this) {
            case REQUESTED:
                return next == QUOTED || next == FAILED_VALIDATION
                    || next == CANCELLED_BEFORE_RESIZE;
            case QUOTED:
                return next == PAYMENT_RESERVE_PENDING || next == FAILED_ECONOMY_UNAVAILABLE
                    || next == CANCELLED_BEFORE_RESIZE;

            case PAYMENT_RESERVE_PENDING:
                return next == PAYMENT_RESERVED || next == FAILED_ECONOMY_UNAVAILABLE
                    || next == BLOCKED_FOR_MANUAL_RECONCILIATION
                    || next == CANCELLED_BEFORE_RESIZE;
            case PAYMENT_RESERVED:
                return next == PAYMENT_RENEW_PENDING || next == RESIZE_APPLYING
                    || next == RELEASE_PENDING || next == CANCELLED_BEFORE_RESIZE;
            case PAYMENT_RENEW_PENDING:
                return next == PAYMENT_RESERVED || next == RELEASE_PENDING
                    || next == BLOCKED_FOR_MANUAL_RECONCILIATION
                    || next == CANCELLED_BEFORE_RESIZE;

            case RESIZE_APPLYING:
                return next == RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING
                    || next == BLOCKED_FOR_MANUAL_RECONCILIATION;
            case RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING:
                return next == COMPLETED || next == BLOCKED_FOR_MANUAL_RECONCILIATION;

            case COMPLETED:
            case RELEASE_PENDING:
            case CANCELLED_BEFORE_RESIZE:
            case FAILED_VALIDATION:
            case FAILED_ECONOMY_UNAVAILABLE:
            case BLOCKED_FOR_MANUAL_RECONCILIATION:
            default:
                return false;
        }
    }

    public boolean isPreResize() {
        return this == REQUESTED || this == QUOTED
            || this == PAYMENT_RESERVE_PENDING || this == PAYMENT_RESERVED
            || this == PAYMENT_RENEW_PENDING || this == RELEASE_PENDING;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED_BEFORE_RESIZE
            || this == FAILED_VALIDATION || this == FAILED_ECONOMY_UNAVAILABLE
            || this == BLOCKED_FOR_MANUAL_RECONCILIATION;
    }

    public boolean canReleasePayment() {
        return this == PAYMENT_RESERVED || this == PAYMENT_RENEW_PENDING
            || this == RELEASE_PENDING;
    }
}
