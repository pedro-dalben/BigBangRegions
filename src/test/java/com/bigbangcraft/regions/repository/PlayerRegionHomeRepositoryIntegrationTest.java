package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.allocation.PlayerRegionHome;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerRegionHomeRepositoryIntegrationTest {
    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;
    private PlayerRegionHomeRepository repository;

    @BeforeEach
    public void setUp() throws Exception {
        dbManager = new DatabaseManager(tempDir.resolve("test_homes.db"));
        dbManager.initialize();
        repository = new PlayerRegionHomeRepository(dbManager);

        // Insert a minimal region so the FK constraint in player_region_homes is satisfied
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO regions (id, name, type, dimensionKey, " +
                    "minX, minY, minZ, maxX, maxY, maxZ, priority, " +
                    "ownerUuid, createdByUuid, createdAt, updatedAt, status) " +
                    "VALUES ('reg1', 'Test Region', 'PLAYER_REGION', 'minecraft:overworld', " +
                    "0, -64, 0, 50, 320, 50, 100, " +
                    "'00000000-0000-0000-0000-000000000001', " +
                    "'00000000-0000-0000-0000-000000000002', " +
                    "1000, 1000, 'ACTIVE')");
        }
    }

    @AfterEach
    public void tearDown() {
        dbManager.close();
    }

    @Test
    public void testSaveGetDeleteHome() {
        PlayerRegionHome home = new PlayerRegionHome(
                "reg1", "minecraft:overworld", 100.5, 64.0, -200.5, 90.0f, 0.0f,
                System.currentTimeMillis(), System.currentTimeMillis()
        );

        repository.save(home);

        PlayerRegionHome loaded = repository.get("reg1");
        assertNotNull(loaded);
        assertEquals("minecraft:overworld", loaded.getDimensionKey());
        assertEquals(100.5, loaded.getX());
        assertEquals(64.0, loaded.getY());
        assertEquals(-200.5, loaded.getZ());
        assertEquals(90.0f, loaded.getYaw());
        assertEquals(0.0f, loaded.getPitch());

        repository.delete("reg1");
        assertNull(repository.get("reg1"));
    }
}
