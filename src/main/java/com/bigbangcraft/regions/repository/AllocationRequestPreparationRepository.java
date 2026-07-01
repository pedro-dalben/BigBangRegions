package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.allocation.AllocationRequestPreparation;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AllocationRequestPreparationRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-AllocationRequestPreparationRepository");

    private final DatabaseManager dbManager;

    public AllocationRequestPreparationRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void save(AllocationRequestPreparation preparation) {
        synchronized (dbManager) {
            try (Connection conn = dbManager.getConnection()) {
                saveOnConnection(conn, preparation);
            } catch (SQLException e) {
                LOGGER.error("Failed to save allocation preparation", e);
            }
        }
    }

    public void saveOnConnection(Connection conn, AllocationRequestPreparation preparation) throws SQLException {
        String sql = "INSERT OR REPLACE INTO allocation_request_preparation (" +
            "allocation_request_id, preparation_attempt, started_at, timeout_at, candidate_id, chunk_plan_json, " +
            "last_error_code, last_error_message, ticket_state, cleanup_required, updated_at" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, preparation.getAllocationRequestId());
            pstmt.setInt(2, preparation.getPreparationAttempt());
            pstmt.setLong(3, preparation.getStartedAt());
            pstmt.setLong(4, preparation.getTimeoutAt());
            pstmt.setString(5, preparation.getCandidateId());
            pstmt.setString(6, preparation.getChunkPlanJson());
            pstmt.setString(7, preparation.getLastErrorCode());
            pstmt.setString(8, preparation.getLastErrorMessage());
            pstmt.setString(9, preparation.getTicketState());
            pstmt.setInt(10, preparation.isCleanupRequired() ? 1 : 0);
            pstmt.setLong(11, preparation.getUpdatedAt());
            pstmt.executeUpdate();
        }
    }

    public AllocationRequestPreparation get(String requestId) {
        synchronized (dbManager) {
            String sql = "SELECT * FROM allocation_request_preparation WHERE allocation_request_id = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, requestId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load allocation preparation", e);
            }
            return null;
        }
    }

    public List<AllocationRequestPreparation> listCleanupRequired() {
        synchronized (dbManager) {
            List<AllocationRequestPreparation> results = new ArrayList<>();
            String sql = "SELECT * FROM allocation_request_preparation WHERE cleanup_required = 1;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to list cleanup-required allocation preparations", e);
            }
            return results;
        }
    }

    public void delete(String requestId) {
        synchronized (dbManager) {
            String sql = "DELETE FROM allocation_request_preparation WHERE allocation_request_id = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, requestId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to delete allocation preparation {}", requestId, e);
            }
        }
    }

    private AllocationRequestPreparation mapRow(ResultSet rs) throws SQLException {
        return new AllocationRequestPreparation(
            rs.getString("allocation_request_id"),
            rs.getInt("preparation_attempt"),
            rs.getLong("started_at"),
            rs.getLong("timeout_at"),
            rs.getString("candidate_id"),
            rs.getString("chunk_plan_json"),
            rs.getString("last_error_code"),
            rs.getString("last_error_message"),
            rs.getString("ticket_state"),
            rs.getInt("cleanup_required") != 0,
            rs.getLong("updated_at")
        );
    }
}
