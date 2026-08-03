package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.storage.DatabaseManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
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
    private RegionRepository regions;

    @BeforeEach
    void setUp() throws Exception {
        database = new DatabaseManager(tempDir.resolve("pastures.db"));
        database.initialize();
        repository = new VirtualPastureRepository(database);
        regions = new RegionRepository(database);
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

    @Test
    void ownerTransferRollsBackRegionAndPastureIndexTogether() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Region original = region("r-transfer", first);
        regions.save(original);
        repository.upsert(new VirtualPastureRecord("minecraft:overworld", 7L, original.getId(), first, 0, 0,
            VirtualPastureRecord.State.ACTIVE, null));
        try (var statement = database.getConnection().createStatement()) {
            statement.execute("CREATE TRIGGER reject_pasture_owner_transfer BEFORE UPDATE OF owner_uuid ON virtual_pastures "
                + "BEGIN SELECT RAISE(ABORT, 'simulated pasture failure'); END");
        }

        assertThrows(IllegalStateException.class, () -> regions.transferOwnership(region(original.getId(), second)));

        assertEquals(first, regions.loadAll().stream().filter(region -> region.getId().equals(original.getId())).findFirst().orElseThrow().getOwnerUuid());
        assertEquals(first, repository.loadAll().getFirst().ownerUuid());
    }

    private static Region region(String id, UUID owner) {
        return new Region(id, "Player Region", RegionType.PLAYER_REGION,
            new RegionBounds("minecraft:overworld", 0, -64, 0, 10, 320, 10), 100,
            owner, owner, 1L, 1L, "ACTIVE");
    }
}
