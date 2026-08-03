package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.storage.DatabaseManager;
import com.bigbangcraft.regions.virtualpasture.VirtualPastureRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VirtualPastureRepositoryIntegrationTest {
    @TempDir Path tempDir;
    private DatabaseManager database;
    private VirtualPastureRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(tempDir.resolve("pastures.db"));
        database.initialize();
        repository = new VirtualPastureRepository(database);
    }

    @AfterEach
    void tearDown() { database.close(); }

    @Test
    void survivesRestartAndUsesOneRecordPerWorldPosition() throws Exception {
        UUID owner = UUID.randomUUID();
        VirtualPastureRecord pending = new VirtualPastureRecord("minecraft:overworld", 42L, "r1", owner, 0, 0,
            VirtualPastureRecord.State.PENDING, System.currentTimeMillis() + 30_000);
        repository.upsert(pending);
        repository.upsert(new VirtualPastureRecord("minecraft:overworld", 42L, "r1", owner, 0, 0,
            VirtualPastureRecord.State.ACTIVE, null));
        assertEquals(1, repository.loadAll().size());
        assertEquals(VirtualPastureRecord.State.ACTIVE, repository.loadAll().getFirst().state());

        database.close();
        database = new DatabaseManager(tempDir.resolve("pastures.db"));
        database.initialize();
        repository = new VirtualPastureRepository(database);
        assertEquals(1, repository.loadAll().size());
    }

    @Test
    void transferDeleteAndExpiredReservationDoNotLeaveDuplicates() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.upsert(new VirtualPastureRecord("minecraft:overworld", 1L, "r1", first, 0, 0,
            VirtualPastureRecord.State.ACTIVE, null));
        repository.upsert(new VirtualPastureRecord("minecraft:overworld", 2L, "r1", first, 0, 0,
            VirtualPastureRecord.State.PENDING, 1L));
        repository.transferOwner("r1", second);
        repository.deleteExpiredPending(System.currentTimeMillis());
        assertEquals(1, repository.loadAll().size());
        assertEquals(second, repository.loadAll().getFirst().ownerUuid());
        repository.deleteRegion("r1");
        assertTrue(repository.loadAll().isEmpty());
    }

    @Test
    void failedWriteLeavesTheDurableIndexUnchanged() {
        UUID owner = UUID.randomUUID();
        VirtualPastureRecord first = new VirtualPastureRecord("minecraft:overworld", 1L, "r1", owner, 0, 0,
            VirtualPastureRecord.State.ACTIVE, null);
        repository.upsert(first);

        DatabaseManager unavailable = new DatabaseManager(tempDir.resolve("pastures.db")) {
            @Override public synchronized Connection getConnection() throws SQLException {
                throw new SQLException("simulated database failure");
            }
        };
        VirtualPastureRepository failingRepository = new VirtualPastureRepository(unavailable);
        assertThrows(IllegalStateException.class, () -> failingRepository.upsert(new VirtualPastureRecord(
            "minecraft:overworld", 2L, "r1", owner, 0, 0, VirtualPastureRecord.State.ACTIVE, null
        )));
        assertEquals(java.util.List.of(first), repository.loadAll());
    }
}
