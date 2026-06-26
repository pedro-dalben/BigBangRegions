package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.storage.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;

public class PlayerRegionMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    public void testDatabaseMigrationConstraints() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("test_migration_constraints.db"));
        dbManager.initialize();

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        } finally {
            dbManager.close();
        }
    }
}
