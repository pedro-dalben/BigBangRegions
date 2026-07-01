package com.bigbangcraft.regions.allocation;

public enum PlotSlotState {
    AVAILABLE,
    STALE,
    PLAYER_RESERVED,
    RESERVED,
    ALLOCATED,
    OCCUPIED,
    RELEASED,
    RETIRED,
    CONSUMED,
    INVALIDATED,
    PREPARING,
    FAILED;

    public boolean canTransitionTo(PlotSlotState next) {
        switch (this) {
            case AVAILABLE:
                return next == STALE || next == PLAYER_RESERVED || next == INVALIDATED;
            case STALE:
                return next == AVAILABLE || next == INVALIDATED;
            case PLAYER_RESERVED:
                return next == PREPARING || next == RELEASED || next == FAILED;
            case PREPARING:
                return next == CONSUMED || next == FAILED || next == RELEASED;
            case RESERVED:
                return next == OCCUPIED || next == RELEASED;
            case ALLOCATED:
                return next == OCCUPIED || next == RETIRED;
            case OCCUPIED:
                return next == RETIRED;
            case RELEASED:
                return next == RESERVED || next == AVAILABLE;
            case RETIRED:
                return next == RELEASED;
            case CONSUMED:
                return false;
            case INVALIDATED:
                return next == RELEASED;
            case FAILED:
                return next == RELEASED || next == INVALIDATED;
            default:
                return false;
        }
    }

    public boolean isTerminal() {
        return this == CONSUMED || this == INVALIDATED;
    }

    public boolean isOccupied() {
        return this == RESERVED || this == PLAYER_RESERVED || this == PREPARING
            || this == ALLOCATED || this == OCCUPIED || this == CONSUMED;
    }

    public boolean isAvailable() {
        return this == AVAILABLE;
    }

    public boolean isStale() {
        return this == STALE;
    }
}
