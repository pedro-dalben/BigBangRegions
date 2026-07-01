package com.bigbangcraft.regions.allocation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ChunkAccessGuardTest {
    @Test
    void forbidsChunkAccessDuringVirtualSearchPhases() {
        assertThrows(IllegalStateException.class, () -> ChunkAccessGuard.assertAllowed(AllocationPhase.VIRTUAL_SEARCHING));
        assertThrows(IllegalStateException.class, () -> ChunkAccessGuard.assertAllowed(AllocationPhase.VIRTUAL_VALIDATED));
        assertThrows(IllegalStateException.class, () -> ChunkAccessGuard.assertAllowed(AllocationPhase.SLOT_RESERVED));
    }

    @Test
    void allowsChunkAccessDuringPhysicalPhases() {
        assertDoesNotThrow(() -> ChunkAccessGuard.assertAllowed(AllocationPhase.PREPARING_CHUNKS));
        assertDoesNotThrow(() -> ChunkAccessGuard.assertAllowed(AllocationPhase.WAITING_FOR_CHUNKS));
        assertDoesNotThrow(() -> ChunkAccessGuard.assertAllowed(AllocationPhase.VALIDATING_LOADED_WORLD));
        assertDoesNotThrow(() -> ChunkAccessGuard.assertAllowed(AllocationPhase.REGION_CREATING));
    }
}
