package com.bigbangcraft.regions.allocation;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class PlotSlotStateMachineTest {

    private PlotSlot createSlot() {
        return new PlotSlot("test:0:0", "minecraft:overworld", 0, 0,
            20000, 20000, 256, PlotSlotState.RESERVED,
            UUID.randomUUID(), null, "planicies",
            System.currentTimeMillis(), System.currentTimeMillis() + 300000L,
            null, System.currentTimeMillis(), System.currentTimeMillis());
    }

    @Test
    public void testReserveRejectsRetired() {
        PlotSlot slot = createSlot();
        slot.allocate("region_123");
        slot.retire();
        assertThrows(IllegalStateException.class, () ->
            slot.reserve(UUID.randomUUID(), "planicies", 300000L)
        );
    }

    @Test
    public void testReserveAfterRecycleAllowed() {
        PlotSlot slot = createSlot();
        slot.allocate("region_123");
        slot.retire();
        slot.recycle();
        slot.reserve(UUID.randomUUID(), "planicies", 300000L);
        assertEquals(PlotSlotState.RESERVED, slot.getState());
    }

    @Test
    public void testRetireFromAllocated() {
        PlotSlot slot = createSlot();
        slot.allocate("region_123");
        slot.retire();
        assertEquals(PlotSlotState.RETIRED, slot.getState());
    }

    @Test
    public void testRetireFailsFromReserved() {
        PlotSlot slot = createSlot();
        assertThrows(IllegalStateException.class, slot::retire);
    }

    @Test
    public void testRetireFailsFromReleased() {
        PlotSlot slot = createSlot();
        slot.release();
        assertThrows(IllegalStateException.class, slot::retire);
    }

    @Test
    public void testRecycleFromRetired() {
        PlotSlot slot = createSlot();
        slot.allocate("region_123");
        slot.retire();
        assertEquals(PlotSlotState.RETIRED, slot.getState());

        slot.recycle();
        assertEquals(PlotSlotState.RELEASED, slot.getState());
    }

    @Test
    public void testRecycleFailsFromAllocated() {
        PlotSlot slot = createSlot();
        slot.allocate("region_123");
        assertThrows(IllegalStateException.class, slot::recycle);
    }

}
