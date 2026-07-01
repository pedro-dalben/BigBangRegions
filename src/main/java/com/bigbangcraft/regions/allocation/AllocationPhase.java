package com.bigbangcraft.regions.allocation;

public enum AllocationPhase {
    VIRTUAL_SEARCHING,
    VIRTUAL_VALIDATED,
    SLOT_RESERVED,
    PREPARING_CHUNKS,
    WAITING_FOR_CHUNKS,
    VALIDATING_LOADED_WORLD,
    REGION_CREATING
}
