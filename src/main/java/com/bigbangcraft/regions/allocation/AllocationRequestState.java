package com.bigbangcraft.regions.allocation;

public enum AllocationRequestState {
    PENDING,
    VIRTUAL_SEARCHING,
    VIRTUAL_VALIDATED,
    SLOT_RESERVED,

    PREPARING_CHUNKS,
    WAITING_FOR_CHUNKS,
    VALIDATING_LOADED_WORLD,
    REGION_CREATING,

    COMPLETED,
    PAUSED_RECOVERY,

    CANCELLED_BEFORE_REGION_CREATION,

    FAILED_NO_TERRAIN,
    FAILED_VALIDATION,
    BLOCKED_FOR_MANUAL_RECONCILIATION,

    // Legacy states retained for backward compatibility only.
    // New allocations never use these.
    PAYMENT_RESERVE_PENDING,
    PAYMENT_RESERVED,
    PAYMENT_RENEW_PENDING,
    REGION_CREATED_PAYMENT_CAPTURE_PENDING,
    RELEASE_PENDING,
    FAILED_ECONOMY_UNAVAILABLE,
    FAILED,
    CANCELLED,
    LEGACY_REQUIRES_ADMIN_REVIEW;

    public boolean canTransitionTo(AllocationRequestState next) {
        switch (this) {
            case PENDING:
                return next == VIRTUAL_SEARCHING || next == FAILED_NO_TERRAIN
                    || next == FAILED_VALIDATION
                    || next == CANCELLED_BEFORE_REGION_CREATION
                    || next == CANCELLED;
            case VIRTUAL_SEARCHING:
                return next == VIRTUAL_VALIDATED || next == FAILED_NO_TERRAIN
                    || next == FAILED_VALIDATION
                    || next == CANCELLED_BEFORE_REGION_CREATION
                    || next == CANCELLED;
            case VIRTUAL_VALIDATED:
                return next == SLOT_RESERVED || next == FAILED_NO_TERRAIN
                    || next == FAILED_VALIDATION
                    || next == CANCELLED_BEFORE_REGION_CREATION
                    || next == CANCELLED;
            case SLOT_RESERVED:
                return next == PREPARING_CHUNKS || next == FAILED_NO_TERRAIN
                    || next == FAILED_VALIDATION
                    || next == CANCELLED_BEFORE_REGION_CREATION
                    || next == CANCELLED;

            case PREPARING_CHUNKS:
                return next == WAITING_FOR_CHUNKS || next == FAILED_NO_TERRAIN
                    || next == FAILED_VALIDATION
                    || next == PAUSED_RECOVERY
                    || next == CANCELLED_BEFORE_REGION_CREATION
                    || next == CANCELLED;
            case WAITING_FOR_CHUNKS:
                return next == VALIDATING_LOADED_WORLD || next == FAILED_NO_TERRAIN
                    || next == FAILED_VALIDATION
                    || next == PAUSED_RECOVERY
                    || next == CANCELLED_BEFORE_REGION_CREATION
                    || next == CANCELLED;
            case VALIDATING_LOADED_WORLD:
                return next == REGION_CREATING || next == FAILED_NO_TERRAIN
                    || next == FAILED_VALIDATION
                    || next == PAUSED_RECOVERY
                    || next == CANCELLED_BEFORE_REGION_CREATION
                    || next == CANCELLED;
            case REGION_CREATING:
                return next == COMPLETED || next == BLOCKED_FOR_MANUAL_RECONCILIATION
                    || next == PAUSED_RECOVERY
                    || next == FAILED
                    || next == FAILED_NO_TERRAIN
                    || next == FAILED_VALIDATION;

            case COMPLETED:
            case PAUSED_RECOVERY:
            case CANCELLED_BEFORE_REGION_CREATION:
            case FAILED_NO_TERRAIN:
            case FAILED_VALIDATION:
            case BLOCKED_FOR_MANUAL_RECONCILIATION:

            // Legacy states: terminal
            case PAYMENT_RESERVE_PENDING:
            case PAYMENT_RESERVED:
            case PAYMENT_RENEW_PENDING:
            case REGION_CREATED_PAYMENT_CAPTURE_PENDING:
            case RELEASE_PENDING:
            case FAILED_ECONOMY_UNAVAILABLE:
            case FAILED:
            case CANCELLED:
            case LEGACY_REQUIRES_ADMIN_REVIEW:
            default:
                return false;
        }
    }

    public boolean isPreRegionCreation() {
        return this == PENDING || this == VIRTUAL_SEARCHING || this == VIRTUAL_VALIDATED
                || this == SLOT_RESERVED || this == PREPARING_CHUNKS
                || this == WAITING_FOR_CHUNKS || this == VALIDATING_LOADED_WORLD
                || this == REGION_CREATING || this == PAUSED_RECOVERY;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED_BEFORE_REGION_CREATION
                || this == FAILED_NO_TERRAIN || this == FAILED_VALIDATION
                || this == BLOCKED_FOR_MANUAL_RECONCILIATION
                || this == PAUSED_RECOVERY
                || this == FAILED || this == CANCELLED
                || this == FAILED_ECONOMY_UNAVAILABLE
                || this == LEGACY_REQUIRES_ADMIN_REVIEW;
    }

    public boolean isLegacyPaymentState() {
        return this == PAYMENT_RESERVE_PENDING || this == PAYMENT_RESERVED
                || this == PAYMENT_RENEW_PENDING
                || this == REGION_CREATED_PAYMENT_CAPTURE_PENDING
                || this == RELEASE_PENDING
                || this == FAILED_ECONOMY_UNAVAILABLE
                || this == LEGACY_REQUIRES_ADMIN_REVIEW;
    }

    public boolean canReleasePayment() {
        return isLegacyPaymentState();
    }
}
