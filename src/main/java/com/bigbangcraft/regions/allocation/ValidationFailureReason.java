package com.bigbangcraft.regions.allocation;

public enum ValidationFailureReason {
    NONE,
    NO_CONTEXT,
    EMPTY_ACCEPTED_BIOMES,
    NO_ACCEPTED_BIOMES_IN_AREA,
    BORDER_MISMATCH,
    BUDGET_EXHAUSTED,
    INTERIOR_THRESHOLD_NOT_MET
}
