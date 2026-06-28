package com.bigbangcraft.regions.allocation;

public enum PlotSlotState {
    RESERVED,
    ALLOCATED,
    OCCUPIED,
    RELEASED,
    RETIRED;
    
    public boolean canTransitionTo(PlotSlotState next) {
        switch (this) {
            case RESERVED:
                return next == OCCUPIED || next == RELEASED;
            case ALLOCATED:
                return next == OCCUPIED || next == RETIRED;
            case OCCUPIED:
                return next == RETIRED;
            case RELEASED:
                return next == RESERVED;
            case RETIRED:
                return false;
            default:
                return false;
        }
    }
}
