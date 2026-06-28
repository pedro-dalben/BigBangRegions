package com.bigbangcraft.regions.allocation;

public enum AllocationRequestState {
    PENDING,
    SEARCHING,
    SLOT_RESERVED,
    
    PAYMENT_RESERVE_PENDING,
    PAYMENT_RESERVED,
    
    PREPARING,
    REGION_CREATING,
    REGION_CREATED_PAYMENT_CAPTURE_PENDING,
    
    COMPLETED,
    
    RELEASE_PENDING,
    CANCELLED_BEFORE_REGION_CREATION,
    
    FAILED_NO_TERRAIN,
    FAILED_ECONOMY_UNAVAILABLE,
    BLOCKED_FOR_MANUAL_RECONCILIATION,
    
    // Legacy states for backward compatibility
    FAILED,
    CANCELLED;

    public boolean canTransitionTo(AllocationRequestState next) {
        switch (this) {
            case PENDING:
                return next == SEARCHING || next == FAILED_NO_TERRAIN || next == CANCELLED_BEFORE_REGION_CREATION;
            case SEARCHING:
                return next == SLOT_RESERVED || next == FAILED_NO_TERRAIN || next == CANCELLED_BEFORE_REGION_CREATION;
            case SLOT_RESERVED:
                return next == PAYMENT_RESERVE_PENDING || next == PREPARING || next == RELEASE_PENDING || next == FAILED_NO_TERRAIN || next == CANCELLED_BEFORE_REGION_CREATION;
            
            case PAYMENT_RESERVE_PENDING:
                return next == PAYMENT_RESERVED || next == RELEASE_PENDING || next == FAILED_ECONOMY_UNAVAILABLE || next == BLOCKED_FOR_MANUAL_RECONCILIATION || next == CANCELLED_BEFORE_REGION_CREATION;
            case PAYMENT_RESERVED:
                return next == PREPARING || next == RELEASE_PENDING || next == CANCELLED_BEFORE_REGION_CREATION;
            
            case PREPARING:
                return next == REGION_CREATING || next == RELEASE_PENDING || next == FAILED_NO_TERRAIN || next == CANCELLED_BEFORE_REGION_CREATION;
            case REGION_CREATING:
                return next == REGION_CREATED_PAYMENT_CAPTURE_PENDING || next == BLOCKED_FOR_MANUAL_RECONCILIATION;
            case REGION_CREATED_PAYMENT_CAPTURE_PENDING:
                return next == COMPLETED || next == BLOCKED_FOR_MANUAL_RECONCILIATION;
            
            case COMPLETED:
            case RELEASE_PENDING:
            case CANCELLED_BEFORE_REGION_CREATION:
            case FAILED_NO_TERRAIN:
            case FAILED_ECONOMY_UNAVAILABLE:
            case BLOCKED_FOR_MANUAL_RECONCILIATION:
            
            // Legacy states
            case FAILED:
            case CANCELLED:
            default:
                return false;
        }
    }
    
    public boolean isPreRegionCreation() {
        return this == PENDING || this == SEARCHING || this == SLOT_RESERVED ||
               this == PAYMENT_RESERVE_PENDING || this == PAYMENT_RESERVED ||
               this == PREPARING || this == REGION_CREATING;
    }
    
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED_BEFORE_REGION_CREATION || 
               this == FAILED_NO_TERRAIN || this == FAILED_ECONOMY_UNAVAILABLE ||
               this == BLOCKED_FOR_MANUAL_RECONCILIATION || this == FAILED || this == CANCELLED;
    }
    
    public boolean canReleasePayment() {
        return this == PAYMENT_RESERVE_PENDING || this == PAYMENT_RESERVED || 
               this == PREPARING || this == RELEASE_PENDING;
    }
}
