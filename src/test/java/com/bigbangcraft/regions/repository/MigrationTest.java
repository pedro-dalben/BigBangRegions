package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.storage.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class MigrationTest {

    @TempDir
    Path tempDir;

    @Test
    public void testDatabaseSchemaInitialization() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("test_migration.db"));
        
        // This will run the migration
        dbManager.initialize();

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Verify table existence
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table';")) {
                while (rs.next()) {
                    tables.add(rs.getString("name"));
                }
            }

            assertTrue(tables.contains("schema_version"));
            assertTrue(tables.contains("regions"));
            assertTrue(tables.contains("region_members"));
            assertTrue(tables.contains("region_flags"));
            assertTrue(tables.contains("region_audit_logs"));
            assertTrue(tables.contains("player_region_allocation_requests"));
            assertTrue(tables.contains("plot_slots"));
            assertTrue(tables.contains("player_region_homes"));

            // 2. Verify schema version is marked as 5
            try (ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version;")) {
                assertTrue(rs.next());
                assertEquals(5, rs.getInt(1));
            }

            // Verify columns added in V2
            List<String> memberColumns = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(region_members);")) {
                while (rs.next()) {
                    memberColumns.add(rs.getString("name"));
                }
            }
            assertTrue(memberColumns.contains("addedByUuid"));
            assertTrue(memberColumns.contains("createdAt"));
            assertTrue(memberColumns.contains("updatedAt"));

            // 3. Verify indexes exist
            List<String> indexes = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index';")) {
                while (rs.next()) {
                    indexes.add(rs.getString("name"));
                }
            }

            assertTrue(indexes.contains("idx_regions_dimension"));
            assertTrue(indexes.contains("idx_regions_type"));
            assertTrue(indexes.contains("idx_regions_priority"));
            assertTrue(indexes.contains("idx_regions_bounds"));
            assertTrue(indexes.contains("idx_audit_regionId"));
            assertTrue(indexes.contains("idx_audit_createdAt"));
        } finally {
            dbManager.close();
        }
    }
}
