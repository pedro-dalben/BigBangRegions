package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.allocation.PlotSlot;
import com.bigbangcraft.regions.allocation.PlotSlotState;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlotSlotRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-PlotSlotRepository");
    private final DatabaseManager dbManager;

    public PlotSlotRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void save(PlotSlot slot) {
        synchronized (dbManager) {
            try (Connection conn = dbManager.getConnection()) {
                saveOnConnection(conn, slot);
            } catch (SQLException e) {
                LOGGER.error("Failed to save plot slot: ", e);
            }
        }
    }

    public void saveOnConnection(Connection conn, PlotSlot slot) throws SQLException {
        String sql = "INSERT OR REPLACE INTO plot_slots (" +
                "id, dimension_key, grid_x, grid_z, min_x, min_z, max_x, max_z, slot_size, state, " +
                "reserved_for_uuid, region_id, biome_option_key, allocation_request_id, " +
                "reserved_at, lease_expires_at, allocated_at, consumed_at, invalidated_at, " +
                "invalidation_reason, validation_schema_version, validated_at, " +
                "created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, slot.getId());
            pstmt.setString(2, slot.getDimensionKey());
            pstmt.setInt(3, slot.getGridX());
            pstmt.setInt(4, slot.getGridZ());
            pstmt.setInt(5, slot.getMinX());
            pstmt.setInt(6, slot.getMinZ());
            pstmt.setInt(7, slot.getMaxX());
            pstmt.setInt(8, slot.getMaxZ());
            pstmt.setInt(9, slot.getSlotSize());
            pstmt.setString(10, slot.getState().name());
            pstmt.setString(11, slot.getReservedForUuid() != null ? slot.getReservedForUuid().toString() : null);
            pstmt.setString(12, slot.getRegionId());
            pstmt.setString(13, slot.getBiomeOptionKey());
            pstmt.setString(14, slot.getAllocationRequestId());
            pstmt.setObject(15, slot.getReservedAt());
            pstmt.setObject(16, slot.getLeaseExpiresAt());
            pstmt.setObject(17, slot.getAllocatedAt());
            pstmt.setObject(18, slot.getConsumedAt());
            pstmt.setObject(19, slot.getInvalidatedAt());
            pstmt.setString(20, slot.getInvalidationReason());
            pstmt.setInt(21, slot.getValidationSchemaVersion());
            pstmt.setObject(22, slot.getValidatedAt());
            pstmt.setLong(23, slot.getCreatedAt());
            pstmt.setLong(24, slot.getUpdatedAt());
            pstmt.executeUpdate();
        }
    }

    public PlotSlot get(String id) {
        synchronized (dbManager) {
            String sql = "SELECT * FROM plot_slots WHERE id = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSet(rs);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to get plot slot by ID: ", e);
            }
            return null;
        }
    }

    public PlotSlot getByGrid(String dimensionKey, int gridX, int gridZ) {
        synchronized (dbManager) {
            String sql = "SELECT * FROM plot_slots WHERE dimension_key = ? AND grid_x = ? AND grid_z = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, dimensionKey);
                pstmt.setInt(2, gridX);
                pstmt.setInt(3, gridZ);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSet(rs);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to get plot slot by grid: ", e);
            }
            return null;
        }
    }

    public PlotSlot getByRegionId(String regionId) {
        synchronized (dbManager) {
            String sql = "SELECT * FROM plot_slots WHERE region_id = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, regionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSet(rs);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to get plot slot by region ID: ", e);
            }
            return null;
        }
    }

    public List<PlotSlot> getExpiredReservations() {
        synchronized (dbManager) {
            List<PlotSlot> list = new ArrayList<>();
            long now = System.currentTimeMillis();
            String sql = "SELECT * FROM plot_slots WHERE state = 'RESERVED' AND lease_expires_at < ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, now);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSet(rs));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to list expired slot reservations: ", e);
            }
            return list;
        }
    }

    public List<PlotSlot> getAvailableByDimension(String dimensionKey) {
        synchronized (dbManager) {
            List<PlotSlot> list = new ArrayList<>();
            String sql = "SELECT * FROM plot_slots WHERE dimension_key = ? AND state = 'AVAILABLE' ORDER BY validated_at ASC;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, dimensionKey);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSet(rs));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to list available plot slots: ", e);
            }
            return list;
        }
    }

    private PlotSlot mapResultSet(ResultSet rs) throws SQLException {
        String resStr = rs.getString("reserved_for_uuid");
        UUID reservedForUuid = resStr != null ? UUID.fromString(resStr) : null;

        Long reservedAt = rs.getObject("reserved_at") != null ? rs.getLong("reserved_at") : null;
        Long leaseExpiresAt = rs.getObject("lease_expires_at") != null ? rs.getLong("lease_expires_at") : null;
        Long allocatedAt = rs.getObject("allocated_at") != null ? rs.getLong("allocated_at") : null;
        Long consumedAt = rs.getObject("consumed_at") != null ? rs.getLong("consumed_at") : null;
        Long invalidatedAt = rs.getObject("invalidated_at") != null ? rs.getLong("invalidated_at") : null;
        Long validatedAt = rs.getObject("validated_at") != null ? rs.getLong("validated_at") : null;

        return new PlotSlot(
                rs.getString("id"),
                rs.getString("dimension_key"),
                rs.getInt("grid_x"),
                rs.getInt("grid_z"),
                rs.getInt("min_x"),
                rs.getInt("min_z"),
                rs.getInt("max_x"),
                rs.getInt("max_z"),
                rs.getInt("slot_size"),
                PlotSlotState.valueOf(rs.getString("state")),
                reservedForUuid,
                rs.getString("region_id"),
                rs.getString("biome_option_key"),
                rs.getString("allocation_request_id"),
                reservedAt,
                leaseExpiresAt,
                allocatedAt,
                consumedAt,
                invalidatedAt,
                rs.getString("invalidation_reason"),
                rs.getInt("validation_schema_version"),
                validatedAt,
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
