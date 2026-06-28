package com.bigbangcraft.regions.expansion;

import com.bigbangcraft.regions.storage.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RegionExpansionOperationRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-RegionExpansionOpRepo");
    private final DatabaseManager dbManager;

    public RegionExpansionOperationRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void save(RegionExpansionOperation op) {
        synchronized (dbManager) {
            try (Connection conn = dbManager.getConnection()) {
                saveOnConnection(conn, op);
            } catch (SQLException e) {
                LOGGER.error("Failed to save region expansion operation: ", e);
            }
        }
    }

    public void saveOnConnection(Connection conn, RegionExpansionOperation op) throws SQLException {
        String sql = "INSERT OR REPLACE INTO region_expansion_operations (" +
                "operation_id, region_id, owner_uuid, plot_slot_id, dimension_key, " +
                "current_size, target_size, " +
                "old_min_x, old_min_z, old_max_x, old_max_z, " +
                "target_min_x, target_min_z, target_max_x, target_max_z, " +
                "price_gems, pricing_policy_version, state, " +
                "gems_reservation_id, " +
                "reserve_idempotency_key, renew_idempotency_key, renew_sequence, " +
                "capture_idempotency_key, release_idempotency_key, " +
                "reservation_lease_expires_at, retry_count, next_retry_at, " +
                "requested_at, updated_at, resize_applied_at, payment_captured_at, " +
                "failure_code, failure_detail" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, op.getOperationId());
            pstmt.setString(2, op.getRegionId());
            pstmt.setString(3, op.getOwnerUuid().toString());
            pstmt.setString(4, op.getPlotSlotId());
            pstmt.setString(5, op.getDimensionKey());
            pstmt.setInt(6, op.getCurrentSize());
            pstmt.setInt(7, op.getTargetSize());
            pstmt.setInt(8, op.getOldMinX());
            pstmt.setInt(9, op.getOldMinZ());
            pstmt.setInt(10, op.getOldMaxX());
            pstmt.setInt(11, op.getOldMaxZ());
            pstmt.setInt(12, op.getTargetMinX());
            pstmt.setInt(13, op.getTargetMinZ());
            pstmt.setInt(14, op.getTargetMaxX());
            pstmt.setInt(15, op.getTargetMaxZ());
            pstmt.setLong(16, op.getPriceGems());
            pstmt.setInt(17, op.getPricingPolicyVersion());
            pstmt.setString(18, op.getState().name());
            pstmt.setString(19, op.getGemsReservationId());
            pstmt.setString(20, op.getReserveIdempotencyKey());
            pstmt.setString(21, op.getRenewIdempotencyKey());
            pstmt.setLong(22, op.getRenewSequence());
            pstmt.setString(23, op.getCaptureIdempotencyKey());
            pstmt.setString(24, op.getReleaseIdempotencyKey());
            pstmt.setObject(25, op.getReservationLeaseExpiresAt());
            pstmt.setInt(26, op.getRetryCount());
            pstmt.setObject(27, op.getNextRetryAt());
            pstmt.setLong(28, op.getRequestedAt());
            pstmt.setLong(29, op.getUpdatedAt());
            pstmt.setObject(30, op.getResizeAppliedAt());
            pstmt.setObject(31, op.getPaymentCapturedAt());
            pstmt.setString(32, op.getFailureCode());
            pstmt.setString(33, op.getFailureDetail());
            pstmt.executeUpdate();
        }
    }

    public RegionExpansionOperation get(String operationId) {
        synchronized (dbManager) {
            String sql = "SELECT * FROM region_expansion_operations WHERE operation_id = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, operationId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return mapResultSet(rs);
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to get expansion operation: ", e);
            }
            return null;
        }
    }

    public RegionExpansionOperation getActiveByRegion(String regionId) {
        synchronized (dbManager) {
            String sql = "SELECT * FROM region_expansion_operations WHERE region_id = ? AND state IN (" +
                    "'REQUESTED', 'QUOTED', " +
                    "'PAYMENT_RESERVE_PENDING', 'PAYMENT_RESERVED', 'PAYMENT_RENEW_PENDING', " +
                    "'RESIZE_APPLYING', 'RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING', " +
                    "'RELEASE_PENDING');";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, regionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return mapResultSet(rs);
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to get active expansion by region: ", e);
            }
            return null;
        }
    }

    public List<RegionExpansionOperation> getActiveOperations() {
        synchronized (dbManager) {
            List<RegionExpansionOperation> list = new ArrayList<>();
            String sql = "SELECT * FROM region_expansion_operations WHERE state IN (" +
                    "'REQUESTED', 'QUOTED', " +
                    "'PAYMENT_RESERVE_PENDING', 'PAYMENT_RESERVED', 'PAYMENT_RENEW_PENDING', " +
                    "'RESIZE_APPLYING', 'RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING', " +
                    "'RELEASE_PENDING');";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            } catch (SQLException e) {
                LOGGER.error("Failed to list active expansion operations: ", e);
            }
            return list;
        }
    }

    private RegionExpansionOperation mapResultSet(ResultSet rs) throws SQLException {
        RegionExpansionOperation op = new RegionExpansionOperation(
            rs.getString("operation_id"),
            rs.getString("region_id"),
            UUID.fromString(rs.getString("owner_uuid")),
            rs.getString("plot_slot_id"),
            rs.getString("dimension_key"),
            rs.getInt("current_size"),
            rs.getInt("target_size"),
            rs.getInt("old_min_x"),
            rs.getInt("old_min_z"),
            rs.getInt("old_max_x"),
            rs.getInt("old_max_z"),
            rs.getInt("target_min_x"),
            rs.getInt("target_min_z"),
            rs.getInt("target_max_x"),
            rs.getInt("target_max_z"),
            rs.getLong("price_gems"),
            rs.getInt("pricing_policy_version"),
            RegionExpansionState.valueOf(rs.getString("state")),
            rs.getLong("requested_at")
        );

        op.setGemsReservationId(rs.getString("gems_reservation_id"));
        op.setReserveIdempotencyKey(rs.getString("reserve_idempotency_key"));
        op.setRenewIdempotencyKey(rs.getString("renew_idempotency_key"));
        op.setCaptureIdempotencyKey(rs.getString("capture_idempotency_key"));
        op.setReleaseIdempotencyKey(rs.getString("release_idempotency_key"));

        // renewSequence is immutable via increment only; set via force
        Long rle = rs.getObject("reservation_lease_expires_at") != null ? rs.getLong("reservation_lease_expires_at") : null;
        op.setReservationLeaseExpiresAt(rle);

        // retryCount set via increment; force via set
        Long nextRetry = rs.getObject("next_retry_at") != null ? rs.getLong("next_retry_at") : null;
        op.setNextRetryAt(nextRetry);

        Long resizeApplied = rs.getObject("resize_applied_at") != null ? rs.getLong("resize_applied_at") : null;
        op.setResizeAppliedAt(resizeApplied);

        Long paymentCaptured = rs.getObject("payment_captured_at") != null ? rs.getLong("payment_captured_at") : null;
        op.setPaymentCapturedAt(paymentCaptured);

        op.setFailureCode(rs.getString("failure_code"));
        op.setFailureDetail(rs.getString("failure_detail"));

        return op;
    }
}
