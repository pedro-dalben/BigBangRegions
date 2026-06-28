package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.allocation.AllocationRequest;
import com.bigbangcraft.regions.allocation.AllocationRequestState;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AllocationRequestRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-AllocationRequestRepository");
    private final DatabaseManager dbManager;

    public AllocationRequestRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void save(AllocationRequest request) {
        synchronized (dbManager) {
            try (Connection conn = dbManager.getConnection()) {
                saveOnConnection(conn, request);
            } catch (SQLException e) {
                LOGGER.error("Failed to save allocation request: ", e);
            }
        }
    }

    public void saveOnConnection(Connection conn, AllocationRequest request) throws SQLException {
        String sql = "INSERT OR REPLACE INTO player_region_allocation_requests (" +
                "id, owner_uuid, requested_biome_option, target_dimension, state, source, " +
                "requested_by_uuid, region_id, plot_slot_id, failure_reason, attempts, created_at, updated_at, " +
                "completed_at, cancelled_at, " +
                "price_gems, payment_required, gems_reservation_id, reserve_idempotency_key, " +
                "renew_idempotency_key, renew_sequence, capture_idempotency_key, release_idempotency_key, " +
                "reservation_lease_expires_at, payment_captured_at, retry_count, next_retry_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, request.getId());
            pstmt.setString(2, request.getOwnerUuid().toString());
            pstmt.setString(3, request.getRequestedBiomeOption());
            pstmt.setString(4, request.getTargetDimension());
            pstmt.setString(5, request.getState().name());
            pstmt.setString(6, request.getSource());
            pstmt.setString(7, request.getRequestedByUuid() != null ? request.getRequestedByUuid().toString() : null);
            pstmt.setString(8, request.getRegionId());
            pstmt.setString(9, request.getPlotSlotId());
            pstmt.setString(10, request.getFailureReason());
            pstmt.setInt(11, request.getAttempts());
            pstmt.setLong(12, request.getCreatedAt());
            pstmt.setLong(13, request.getUpdatedAt());
            pstmt.setObject(14, request.getCompletedAt());
            pstmt.setObject(15, request.getCancelledAt());
            pstmt.setLong(16, request.getPriceGems());
            pstmt.setInt(17, request.isPaymentRequired() ? 1 : 0);
            pstmt.setString(18, request.getGemsReservationId());
            pstmt.setString(19, request.getReserveIdempotencyKey());
            pstmt.setString(20, request.getRenewIdempotencyKey());
            pstmt.setLong(21, request.getRenewSequence());
            pstmt.setString(22, request.getCaptureIdempotencyKey());
            pstmt.setString(23, request.getReleaseIdempotencyKey());
            pstmt.setObject(24, request.getReservationLeaseExpiresAt());
            pstmt.setObject(25, request.getPaymentCapturedAt());
            pstmt.setInt(26, request.getRetryCount());
            pstmt.setObject(27, request.getNextRetryAt());
            pstmt.executeUpdate();
        }
    }

    public AllocationRequest get(String id) {
        synchronized (dbManager) {
            String sql = "SELECT * FROM player_region_allocation_requests WHERE id = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSet(rs);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to get allocation request: ", e);
            }
            return null;
        }
    }

    public AllocationRequest getActiveRequestByOwner(UUID ownerUuid) {
        synchronized (dbManager) {
            String sql = "SELECT * FROM player_region_allocation_requests WHERE owner_uuid = ? AND state IN (" +
                    "'PENDING', 'SEARCHING', 'SLOT_RESERVED', 'PAYMENT_RESERVE_PENDING', 'PAYMENT_RESERVED', " +
                    "'PAYMENT_RENEW_PENDING', 'PREPARING', 'REGION_CREATING', " +
                    "'REGION_CREATED_PAYMENT_CAPTURE_PENDING', 'RELEASE_PENDING');";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, ownerUuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSet(rs);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to get active allocation request by owner: ", e);
            }
            return null;
        }
    }

    public List<AllocationRequest> getActiveRequests() {
        synchronized (dbManager) {
            List<AllocationRequest> list = new ArrayList<>();
            String sql = "SELECT * FROM player_region_allocation_requests WHERE state IN (" +
                    "'PENDING', 'SEARCHING', 'SLOT_RESERVED', 'PAYMENT_RESERVE_PENDING', 'PAYMENT_RESERVED', " +
                    "'PAYMENT_RENEW_PENDING', 'PREPARING', 'REGION_CREATING', " +
                    "'REGION_CREATED_PAYMENT_CAPTURE_PENDING', 'RELEASE_PENDING');";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to list active allocation requests: ", e);
            }
            return list;
        }
    }

    private AllocationRequest mapResultSet(ResultSet rs) throws SQLException {
        String regionId = rs.getString("region_id");
        String plotSlotId = rs.getString("plot_slot_id");
        String failureReason = rs.getString("failure_reason");

        Long completedAt = rs.getObject("completed_at") != null ? rs.getLong("completed_at") : null;
        Long cancelledAt = rs.getObject("cancelled_at") != null ? rs.getLong("cancelled_at") : null;

        String reqByStr = rs.getString("requested_by_uuid");
        UUID requestedByUuid = reqByStr != null ? UUID.fromString(reqByStr) : null;

        AllocationRequest request = new AllocationRequest(
                rs.getString("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("requested_biome_option"),
                rs.getString("target_dimension"),
                AllocationRequestState.valueOf(rs.getString("state")),
                rs.getString("source"),
                requestedByUuid,
                regionId,
                plotSlotId,
                failureReason,
                rs.getInt("attempts"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                completedAt,
                cancelledAt
        );
        
        // Map payment fields
        request.setPriceGems(rs.getLong("price_gems"));
        request.setPaymentRequired(rs.getInt("payment_required") == 1);
        request.setGemsReservationId(rs.getString("gems_reservation_id"));
        request.setReserveIdempotencyKey(rs.getString("reserve_idempotency_key"));
        request.setRenewIdempotencyKey(rs.getString("renew_idempotency_key"));
        request.setRenewSequence(rs.getLong("renew_sequence"));
        request.setCaptureIdempotencyKey(rs.getString("capture_idempotency_key"));
        request.setReleaseIdempotencyKey(rs.getString("release_idempotency_key"));
        
        Long reservationLeaseExpiresAt = rs.getObject("reservation_lease_expires_at") != null ? rs.getLong("reservation_lease_expires_at") : null;
        request.setReservationLeaseExpiresAt(reservationLeaseExpiresAt);
        
        Long paymentCapturedAt = rs.getObject("payment_captured_at") != null ? rs.getLong("payment_captured_at") : null;
        request.setPaymentCapturedAt(paymentCapturedAt);
        
        request.setRetryCount(rs.getInt("retry_count"));
        
        Long nextRetryAt = rs.getObject("next_retry_at") != null ? rs.getLong("next_retry_at") : null;
        request.setNextRetryAt(nextRetryAt);
        
        return request;
    }
}
