package com.bigbangcraft.regions.allocation;

public final class ChunkAccessGuard {
    private ChunkAccessGuard() {
    }

    public static void assertAllowed(AllocationPhase phase) {
        if (phase == AllocationPhase.VIRTUAL_SEARCHING
            || phase == AllocationPhase.VIRTUAL_VALIDATED
            || phase == AllocationPhase.SLOT_RESERVED) {
            AllocationMetrics.increment("bigbangregions_forbidden_chunk_access_total");
            throw new IllegalStateException("Chunk access forbidden during " + phase);
        }
    }
}
