package com.bigbangcraft.regions.allocation;

public enum LoadedWorldFailureReason {
    NONE,
    CHUNK_NOT_READY,
    VIRTUAL_PHYSICAL_BIOME_MISMATCH,
    EDGE_BIOME_MISMATCH,
    INTERIOR_BIOME_MISMATCH,
    TERRAIN_UNSAFE,
    SAFE_SPAWN_NOT_FOUND,
    REGION_INTERSECTION,
    RESERVATION_LOST
}
