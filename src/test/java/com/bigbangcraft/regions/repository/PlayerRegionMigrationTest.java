package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.storage.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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

    @Test
    public void testPragmaOutputs() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("test_pragma_outputs.db"));
        dbManager.initialize();

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {

            System.out.println("=== PRAGMA foreign_keys ===");
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                while (rs.next()) {
                    System.out.println("foreign_keys: " + rs.getInt(1));
                }
            }

            System.out.println("=== PRAGMA table_info(region_members) ===");
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(region_members);")) {
                while (rs.next()) {
                    System.out.println(String.format("cid: %d, name: %s, type: %s, notnull: %d, dflt_value: %s, pk: %d",
                            rs.getInt("cid"), rs.getString("name"), rs.getString("type"),
                            rs.getInt("notnull"), rs.getString("dflt_value"), rs.getInt("pk")));
                }
            }

            System.out.println("=== PRAGMA foreign_key_list(region_members) ===");
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_list(region_members);")) {
                while (rs.next()) {
                    System.out.println(String.format("id: %d, seq: %d, table: %s, from: %s, to: %s, on_update: %s, on_delete: %s",
                            rs.getInt("id"), rs.getInt("seq"), rs.getString("table"),
                            rs.getString("from"), rs.getString("to"), rs.getString("on_update"),
                            rs.getString("on_delete")));
                }
            }

            System.out.println("=== PRAGMA index_list(region_members) ===");
            try (ResultSet rs = stmt.executeQuery("PRAGMA index_list(region_members);")) {
                while (rs.next()) {
                    System.out.println(String.format("seq: %d, name: %s, unique: %d, origin: %s, partial: %d",
                            rs.getInt("seq"), rs.getString("name"), rs.getInt("unique"),
                            rs.getString("origin"), rs.getInt("partial")));
                }
            }
        } finally {
            dbManager.close();
        }
    }

    @Test
    public void testOnDeleteCascade() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("test_cascade.db"));
        dbManager.initialize();

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Insert parent region
            stmt.execute("INSERT INTO regions (id, name, type, dimensionKey, minX, minY, minZ, maxX, maxY, maxZ, priority, ownerUuid, createdByUuid, createdAt, updatedAt, status) " +
                    "VALUES ('reg1', 'Region 1', 'PLAYER_REGION', 'minecraft:overworld', 0, 0, 0, 10, 10, 10, 100, 'owner-uuid', 'creator-uuid', 1000, 1000, 'ACTIVE');");

            // 2. Insert member referencing parent
            stmt.execute("INSERT INTO region_members (regionId, uuid, role, addedByUuid, createdAt, updatedAt) " +
                    "VALUES ('reg1', 'member-uuid', 'MEMBER', 'owner-uuid', 1000, 1000);");

            // Verify both exist
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM regions;")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM region_members;")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }

            // 3. Delete parent region
            stmt.execute("DELETE FROM regions WHERE id = 'reg1';");

            // Verify cascade delete worked (region_members should be empty)
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM region_members;")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        } finally {
            dbManager.close();
        }
    }

    @Test
    public void testMigrationV1ToV2() throws Exception {
        Path dbPath = tempDir.resolve("test_v1_to_v2.db");
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();

        // 1. Create a pure V1 database manually
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE schema_version (version INTEGER PRIMARY KEY, appliedAt INTEGER NOT NULL);");
            stmt.execute("INSERT INTO schema_version (version, appliedAt) VALUES (1, " + System.currentTimeMillis() + ");");

            stmt.execute("CREATE TABLE regions (" +
                    "id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, dimensionKey TEXT NOT NULL, " +
                    "minX INTEGER NOT NULL, minY INTEGER NOT NULL, minZ INTEGER NOT NULL, " +
                    "maxX INTEGER NOT NULL, maxY INTEGER NOT NULL, maxZ INTEGER NOT NULL, " +
                    "priority INTEGER NOT NULL, ownerUuid TEXT, createdByUuid TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, status TEXT NOT NULL);");

            stmt.execute("CREATE TABLE region_members (regionId TEXT NOT NULL, uuid TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY (regionId, uuid), FOREIGN KEY (regionId) REFERENCES regions (id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE region_flags (regionId TEXT NOT NULL, flag TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY (regionId, flag), FOREIGN KEY (regionId) REFERENCES regions (id) ON DELETE CASCADE);");

            stmt.execute("CREATE TABLE region_audit_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, regionId TEXT, actorUuid TEXT, action TEXT NOT NULL, beforeValue TEXT, afterValue TEXT, createdAt INTEGER NOT NULL, metadataJson TEXT);");

            // Insert dummy V1 data
            stmt.execute("INSERT INTO regions (id, name, type, dimensionKey, minX, minY, minZ, maxX, maxY, maxZ, priority, ownerUuid, createdByUuid, createdAt, updatedAt, status) " +
                    "VALUES ('r1', 'R1', 'PLAYER_REGION', 'minecraft:overworld', 0, 0, 0, 5, 5, 5, 10, 'owner-uuid', 'creator-uuid', 500, 500, 'ACTIVE');");
            stmt.execute("INSERT INTO region_members (regionId, uuid, role) VALUES ('r1', 'member-uuid', 'MEMBER');");
        }

        // 2. Initialize DatabaseManager on this DB (should trigger migration V2)
        DatabaseManager dbManager = new DatabaseManager(dbPath);
        dbManager.initialize();

        // 3. Verify that V2 was applied and columns exist
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check schema version is 9 (V1+...+V9)
            try (ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version;")) {
                assertTrue(rs.next());
                assertEquals(9, rs.getInt(1));
            }

            // Verify V3 tables exist
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table';")) {
                while (rs.next()) {
                    tables.add(rs.getString("name"));
                }
            }
            assertTrue(tables.contains("player_region_allocation_requests"));
            assertTrue(tables.contains("plot_slots"));
            assertTrue(tables.contains("player_region_homes"));
            assertTrue(tables.contains("allocation_request_preparation"));
            assertTrue(tables.contains("allocation_search_cursor"));

            // Verify columns added in V2
            List<String> columns = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(region_members);")) {
                while (rs.next()) {
                    columns.add(rs.getString("name"));
                }
            }
            assertTrue(columns.contains("addedByUuid"));
            assertTrue(columns.contains("createdAt"));
            assertTrue(columns.contains("updatedAt"));

            List<String> allocationColumns = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(player_region_allocation_requests);")) {
                while (rs.next()) {
                    allocationColumns.add(rs.getString("name"));
                }
            }
            assertTrue(allocationColumns.contains("preparation_attempt"));

            List<String> cursorColumns = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(allocation_search_cursor);")) {
                while (rs.next()) {
                    cursorColumns.add(rs.getString("name"));
                }
            }
            assertTrue(cursorColumns.contains("current_band_id"));
            assertTrue(cursorColumns.contains("current_sector_index"));

            // Verify dummy V1 data is preserved and new fields have default values / are readable
            try (ResultSet rs = stmt.executeQuery("SELECT regionId, uuid, role, addedByUuid, createdAt, updatedAt FROM region_members WHERE regionId = 'r1';")) {
                assertTrue(rs.next());
                assertEquals("r1", rs.getString("regionId"));
                assertEquals("member-uuid", rs.getString("uuid"));
                assertEquals("MEMBER", rs.getString("role"));
                assertNull(rs.getString("addedByUuid")); // Defaults to NULL or default value in ALTER
                assertEquals(0, rs.getLong("createdAt")); // DEFAULT 0 in alter table
            }
        } finally {
            dbManager.close();
        }
    }
}
