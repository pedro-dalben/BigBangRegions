package com.bigbangcraft.regions.allocation;

public enum AllocationRequestState {
    PENDING,
    SEARCHING,
    SLOT_RESERVED,
    PREPARING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean canTransitionTo(AllocationRequestState next) {
        switch (this) {
            case PENDING:
                return next == SEARCHING || next == FAILED || next == CANCELLED;
            case SEARCHING:
                return next == SLOT_RESERVED || next == FAILED || next == CANCELLED;
            case SLOT_RESERVED:
                return next == PREPARING || next == FAILED || next == CANCELLED;
            case PREPARING:
                return next == COMPLETED || next == FAILED || next == CANCELLED;
            case COMPLETED:
            case FAILED:
            case CANCELLED:
            default:
                return false;
        }
    }
}
