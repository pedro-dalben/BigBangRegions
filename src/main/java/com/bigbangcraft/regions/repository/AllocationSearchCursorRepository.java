package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.allocation.AllocationSearchCursor;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AllocationSearchCursorRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-AllocationSearchCursorRepository");
    private final DatabaseManager dbManager;

    public AllocationSearchCursorRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void save(AllocationSearchCursor cursor) {
        synchronized (dbManager) {
            try (Connection conn = dbManager.getConnection()) {
                saveOnConnection(conn, cursor);
            } catch (SQLException e) {
                LOGGER.error("Failed to save allocation search cursor", e);
            }
        }
    }

    public void saveOnConnection(Connection conn, AllocationSearchCursor cursor) throws SQLException {
        String sql = "INSERT OR REPLACE INTO allocation_search_cursor (" +
            "request_id, current_band_id, current_sector_index, sector_x, sector_z, anchor_attempt, " +
            "local_candidate_index, total_sectors_checked, total_virtual_candidates_checked, total_biome_samples, " +
            "sectors_discarded, anchors_found, locate_calls_used, current_anchor_x, current_anchor_z, " +
            "current_anchor_biome_id, last_progress_at, last_rejection_reason, fallback_mode" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, cursor.getRequestId());
            pstmt.setString(2, cursor.getCurrentBandId());
            pstmt.setInt(3, cursor.getCurrentSectorIndex());
            pstmt.setInt(4, cursor.getSectorX());
            pstmt.setInt(5, cursor.getSectorZ());
            pstmt.setInt(6, cursor.getAnchorAttempt());
            pstmt.setInt(7, cursor.getLocalCandidateIndex());
            pstmt.setInt(8, cursor.getTotalSectorsChecked());
            pstmt.setInt(9, cursor.getTotalVirtualCandidatesChecked());
            pstmt.setInt(10, cursor.getTotalBiomeSamples());
            pstmt.setInt(11, cursor.getSectorsDiscarded());
            pstmt.setInt(12, cursor.getAnchorsFound());
            pstmt.setInt(13, cursor.getLocateCallsUsed());
            pstmt.setObject(14, cursor.getCurrentAnchorX());
            pstmt.setObject(15, cursor.getCurrentAnchorZ());
            pstmt.setString(16, cursor.getCurrentAnchorBiomeId());
            pstmt.setLong(17, cursor.getLastProgressAt());
            pstmt.setString(18, cursor.getLastRejectionReason());
            pstmt.setString(19, cursor.getFallbackMode());
            pstmt.executeUpdate();
        }
    }

    public AllocationSearchCursor get(String requestId) {
        synchronized (dbManager) {
            String sql = "SELECT * FROM allocation_search_cursor WHERE request_id = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, requestId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        AllocationSearchCursor cursor = new AllocationSearchCursor(requestId);
                        cursor.setCurrentBandId(rs.getString("current_band_id"));
                        cursor.setCurrentSectorIndex(rs.getInt("current_sector_index"));
                        cursor.setSectorX(rs.getInt("sector_x"));
                        cursor.setSectorZ(rs.getInt("sector_z"));
                        cursor.setAnchorAttempt(rs.getInt("anchor_attempt"));
                        cursor.setLocalCandidateIndex(rs.getInt("local_candidate_index"));
                        cursor.setTotalSectorsChecked(rs.getInt("total_sectors_checked"));
                        cursor.setTotalVirtualCandidatesChecked(rs.getInt("total_virtual_candidates_checked"));
                        cursor.setTotalBiomeSamples(rs.getInt("total_biome_samples"));
                        cursor.setSectorsDiscarded(rs.getInt("sectors_discarded"));
                        cursor.setAnchorsFound(rs.getInt("anchors_found"));
                        cursor.setLocateCallsUsed(rs.getInt("locate_calls_used"));
                        cursor.setCurrentAnchorX(rs.getObject("current_anchor_x") != null ? rs.getInt("current_anchor_x") : null);
                        cursor.setCurrentAnchorZ(rs.getObject("current_anchor_z") != null ? rs.getInt("current_anchor_z") : null);
                        cursor.setCurrentAnchorBiomeId(rs.getString("current_anchor_biome_id"));
                        cursor.setLastProgressAt(rs.getLong("last_progress_at"));
                        cursor.setLastRejectionReason(rs.getString("last_rejection_reason"));
                        cursor.setFallbackMode(rs.getString("fallback_mode"));
                        return cursor;
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load allocation search cursor {}", requestId, e);
            }
            return null;
        }
    }

    public void delete(String requestId) {
        synchronized (dbManager) {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("DELETE FROM allocation_search_cursor WHERE request_id = ?;")) {
                pstmt.setString(1, requestId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to delete allocation search cursor {}", requestId, e);
            }
        }
    }
}
