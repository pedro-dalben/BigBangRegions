package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.allocation.AllocationSearchCursor;
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
            assertTrue(tables.contains("region_expansion_operations"));
            assertTrue(tables.contains("allocation_request_preparation"));
            assertTrue(tables.contains("allocation_search_cursor"));

            // 2. Verify schema version is marked as 13
            try (ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version;")) {
                assertTrue(rs.next());
                assertEquals(13, rs.getInt(1));
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
            assertTrue(cursorColumns.contains("last_rejection_reason"));
            assertTrue(cursorColumns.contains("current_anchor_y"));
            assertTrue(cursorColumns.contains("anchor_search_y_index"));
            assertTrue(cursorColumns.contains("anchor_search_ring_quart"));
            assertTrue(cursorColumns.contains("anchor_search_point_index"));
            assertTrue(cursorColumns.contains("anchor_search_interval_quart"));
        } finally {
            dbManager.close();
        }
    }

    @Test
    public void allocationSearchCursorRepositoryPersistsAllCursorColumns() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("test_cursor_repository.db"));
        dbManager.initialize();

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO player_region_allocation_requests " +
                "(id, owner_uuid, requested_biome_option, target_dimension, state, source, created_at, updated_at) " +
                "VALUES ('request-1', 'owner-1', 'cerejeira', 'minecraft:overworld', 'VIRTUAL_SEARCHING', 'TEST', 1, 1)");

            AllocationSearchCursor cursor = new AllocationSearchCursor("request-1");
            cursor.setCurrentBandId("primary");
            cursor.setSectorX(100);
            cursor.setSectorZ(-200);
            cursor.setAnchorAttempt(1300);
            cursor.setAnchorSearchYIndex(2);
            cursor.setAnchorSearchRingQuart(24);
            cursor.setAnchorSearchPointIndex(7);
            cursor.setAnchorSearchIntervalQuart(4);

            AllocationSearchCursorRepository repository = new AllocationSearchCursorRepository(dbManager);
            repository.saveOnConnection(conn, cursor);
            try (ResultSet rs = stmt.executeQuery("SELECT anchor_search_y_index, anchor_search_ring_quart, " +
                "anchor_search_point_index, anchor_search_interval_quart FROM allocation_search_cursor WHERE request_id = 'request-1'")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
                assertEquals(24, rs.getInt(2));
                assertEquals(7, rs.getInt(3));
                assertEquals(4, rs.getInt(4));
            }
        } finally {
            dbManager.close();
        }
    }
}
