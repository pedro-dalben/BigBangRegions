package com.bigbangcraft.regions.allocation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegionPreparationQueueTest {
    @Test
    void startsOnlyOnePreparationAtATime() {
        RegionPreparationQueue queue = new RegionPreparationQueue();
        assertTrue(queue.enqueue("interactive", RegionPreparationPriority.INTERACTIVE));
        assertTrue(queue.enqueue("admin", RegionPreparationPriority.ADMIN));

        assertEquals("interactive", queue.tryStartNext().orElseThrow());
        assertTrue(queue.tryStartNext().isEmpty());

        queue.complete("interactive");
        assertEquals("admin", queue.tryStartNext().orElseThrow());
    }

    @Test
    void rejectsDuplicateEnqueue() {
        RegionPreparationQueue queue = new RegionPreparationQueue();
        assertTrue(queue.enqueue("same", RegionPreparationPriority.INTERACTIVE));
        assertFalse(queue.enqueue("same", RegionPreparationPriority.ADMIN));
    }
}
