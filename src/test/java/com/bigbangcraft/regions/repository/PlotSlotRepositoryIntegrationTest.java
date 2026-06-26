package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.allocation.PlotSlot;
import com.bigbangcraft.regions.allocation.PlotSlotState;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PlotSlotRepositoryIntegrationTest {
    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;
    private PlotSlotRepository repository;

    @BeforeEach
    public void setUp() throws Exception {
        dbManager = new DatabaseManager(tempDir.resolve("test_slots.db"));
        dbManager.initialize();
        repository = new PlotSlotRepository(dbManager);
    }

    @AfterEach
    public void tearDown() {
        dbManager.close();
    }

    @Test
    public void testSaveAndGetSlot() {
        PlotSlot slot = new PlotSlot("slot1", "minecraft:overworld", 10, -5, 2560, -1280, 256,
                PlotSlotState.RELEASED, null, null, null, null, null, null,
                System.currentTimeMillis(), System.currentTimeMillis());

        repository.save(slot);

        PlotSlot loaded = repository.get("slot1");
        assertNotNull(loaded);
        assertEquals("minecraft:overworld", loaded.getDimensionKey());
        assertEquals(10, loaded.getGridX());
        assertEquals(-5, loaded.getGridZ());
        assertEquals(PlotSlotState.RELEASED, loaded.getState());
        assertNull(loaded.getReservedForUuid());

        // Reserve it
        UUID player = UUID.randomUUID();
        slot.reserve(player, "planicies", 300000);
        repository.save(slot);

        loaded = repository.getByGrid("minecraft:overworld", 10, -5);
        assertNotNull(loaded);
        assertEquals(PlotSlotState.RESERVED, loaded.getState());
        assertEquals(player, loaded.getReservedForUuid());
        assertEquals("planicies", loaded.getBiomeOptionKey());

        // Allocate it
        slot.allocate("meu_claim");
        repository.save(slot);

        loaded = repository.getByRegionId("meu_claim");
        assertNotNull(loaded);
        assertEquals(PlotSlotState.ALLOCATED, loaded.getState());
        assertEquals("meu_claim", loaded.getRegionId());
    }

    @Test
    public void testExpiredReservations() throws Exception {
        PlotSlot slot = new PlotSlot("slot2", "minecraft:overworld", 1, 1, 256, 256, 256,
                PlotSlotState.RESERVED, UUID.randomUUID(), null, "floresta",
                System.currentTimeMillis() - 10000, System.currentTimeMillis() - 5000, null,
                System.currentTimeMillis(), System.currentTimeMillis());
        repository.save(slot);

        List<PlotSlot> expired = repository.getExpiredReservations();
        assertEquals(1, expired.size());
        assertEquals("slot2", expired.get(0).getId());
    }
}
