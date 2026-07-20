package com.bigbangcraft.regions.expansion;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.payment.api.*;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class RegionExpansionRecoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-RegionExpansionRecoveryService");

    private final RegionExpansionOperationRepository expansionRepository;
    private final RegionRepository regionRepository;
    private final PlotSlotRepository slotRepository;
    private final RegionCache regionCache;
    private final RegionMembershipCache membershipCache;
    private final LandPaymentGateway paymentGateway;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;

    public RegionExpansionRecoveryService(RegionExpansionOperationRepository expansionRepository,
                                           RegionRepository regionRepository,
                                           PlotSlotRepository slotRepository,
                                           RegionCache regionCache,
                                           RegionMembershipCache membershipCache,
                                           LandPaymentGateway paymentGateway,
                                           ConfigManager configManager,
                                           DatabaseManager databaseManager) {
        this.expansionRepository = expansionRepository;
        this.regionRepository = regionRepository;
        this.slotRepository = slotRepository;
        this.regionCache = regionCache;
        this.membershipCache = membershipCache;
        this.paymentGateway = paymentGateway;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
    }

    public void recover() {
        LOGGER.info("Starting region expansion recovery...");
        reloadRegionsFromDb();

        List<RegionExpansionOperation> active = expansionRepository.getActiveOperations();
        int recovered = 0;

        for (RegionExpansionOperation op : active) {
            boolean changed = false;
            switch (op.getState()) {
                case PAYMENT_RESERVE_PENDING:
                    changed = recoverReservePending(op);
                    break;
                case PAYMENT_RESERVED:
                    changed = recoverReserved(op);
                    break;
                case PAYMENT_RENEW_PENDING:
                    changed = recoverRenewPending(op);
                    break;
                case RELEASE_PENDING:
                    changed = recoverReleasePending(op);
                    break;
                case RESIZE_APPLYING:
                    changed = recoverResizeApplying(op);
                    break;
                case RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING:
                    changed = recoverCapturePending(op);
                    break;
                default:
                    break;
            }
            if (changed) recovered++;
        }

        LOGGER.info("Region expansion recovery completed. Recovered {} operations.", recovered);
    }

    private void reloadRegionsFromDb() {
        try {
            regionRepository.reloadCaches(regionCache, membershipCache);
        } catch (Exception e) {
            LOGGER.error("Expansion recovery: failed to reload regions and members; existing caches were preserved.", e);
        }
    }

    private boolean recoverReservePending(RegionExpansionOperation op) {
        if (op.getReserveIdempotencyKey() == null) {
            String key = generateIdempotencyKey(op.getOperationId(), "expand_reserve");
            op.setReserveIdempotencyKey(key);
            expansionRepository.save(op);
        }
        scheduleRetry(op);
        return true;
    }

    private boolean recoverReserved(RegionExpansionOperation op) {
        Config.RegionExpansionConfig ec = configManager.getConfig().getRegionExpansion();
        if (op.isReservationExpired()) {
            LOGGER.warn("Expansion recovery: reservation expired for op={}", op.getOperationId());
            beginReleaseBeforeResize(op, "RESERVATION_EXPIRED", "Recuperacao: reserva expirada.");
            return true;
        }
        long renewThreshold = ec.getRenewBeforeExpirySeconds() * 1000L;
        if (op.getReservationLeaseExpiresAt() != null
            && System.currentTimeMillis() + renewThreshold > op.getReservationLeaseExpiresAt()) {
            op.forceTransitionTo(RegionExpansionState.PAYMENT_RENEW_PENDING);
            expansionRepository.save(op);
            return true;
        }
        scheduleRetry(op);
        return true;
    }

    private boolean recoverRenewPending(RegionExpansionOperation op) {
        if (op.getRenewIdempotencyKey() == null) {
            String key = generateIdempotencyKey(op.getOperationId(), "expand_renew_" + op.getRenewSequence());
            op.setRenewIdempotencyKey(key);
            expansionRepository.save(op);
        }
        scheduleRetry(op);
        return true;
    }

    private boolean recoverReleasePending(RegionExpansionOperation op) {
        String reservationId = op.getGemsReservationId();
        if (reservationId == null) {
            op.forceTransitionTo(RegionExpansionState.CANCELLED_BEFORE_RESIZE);
            expansionRepository.save(op);
            return true;
        }

        String releaseKey = op.getReleaseIdempotencyKey();
        if (releaseKey == null) {
            releaseKey = generateIdempotencyKey(op.getOperationId(), "expand_release");
            op.setReleaseIdempotencyKey(releaseKey);
            expansionRepository.save(op);
        }

        LandPaymentReleaseRequest req = new LandPaymentReleaseRequest(
            op.getPaymentOperationUuid(),
            reservationId,
            releaseKey
        );
        LandPaymentOperationResult result = paymentGateway.release(req);
        if (result.isSuccess() || result.getFailure() == LandPaymentFailure.ALREADY_RELEASED) {
            op.setGemsReservationId(null);
            op.forceTransitionTo(RegionExpansionState.CANCELLED_BEFORE_RESIZE);
            op.setFailureDetail("Recuperacao: release confirmado.");
            expansionRepository.save(op);
            LOGGER.info("Expansion recovery: released reservation {} for op {}", reservationId, op.getOperationId());
        } else {
            LOGGER.warn("Expansion recovery: failed to release reservation {} for op {}: {}. Staying in RELEASE_PENDING.",
                reservationId, op.getOperationId(), result.getFailure());
            scheduleRetry(op);
        }
        return true;
    }

    private boolean recoverResizeApplying(RegionExpansionOperation op) {
        Region region = regionCache.get(op.getRegionId());
        if (region == null) {
            reloadRegionsFromDb();
            region = regionCache.get(op.getRegionId());
        }

        if (region != null) {
            RegionBounds b = region.getBounds();
            if (b.getMinX() == op.getTargetMinX() && b.getMinZ() == op.getTargetMinZ()
                && b.getMaxX() == op.getTargetMaxX() && b.getMaxZ() == op.getTargetMaxZ()) {
                op.forceTransitionTo(RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING);
                op.setResizeAppliedAt(System.currentTimeMillis());
                expansionRepository.save(op);
                return true;
            }
            if (b.getMinX() == op.getOldMinX() && b.getMinZ() == op.getOldMinZ()
                && b.getMaxX() == op.getOldMaxX() && b.getMaxZ() == op.getOldMaxZ()) {
                try {
                    applyResizeRecovery(op);
                    return true;
                } catch (Exception e) {
                    LOGGER.error("Expansion recovery: failed to apply resize for op={}", op.getOperationId(), e);
                }
            }
        }

        op.forceTransitionTo(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION);
        op.setFailureCode("RECOVERY_BOUNDS_MISMATCH");
        op.setFailureDetail("Recuperacao: bounds nao correspondem a old nem target.");
        expansionRepository.save(op);
        return true;
    }

    private void applyResizeRecovery(RegionExpansionOperation op) throws SQLException {
        Region region = regionCache.get(op.getRegionId());
        if (region == null) {
            regionRepository.reloadCaches(regionCache, membershipCache);
            region = regionCache.get(op.getRegionId());
        }
        if (region == null) {
            throw new IllegalStateException("Region not found: " + op.getRegionId());
        }

        synchronized (databaseManager) {
            Connection conn = databaseManager.getConnection();
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                RegionBounds newBounds = new RegionBounds(
                    op.getDimensionKey(),
                    op.getTargetMinX(), region.getBounds().getMinY(), op.getTargetMinZ(),
                    op.getTargetMaxX(), region.getBounds().getMaxY(), op.getTargetMaxZ()
                );
                region.setBounds(newBounds);
                regionRepository.saveOnConnection(conn, region);

                op.setResizeAppliedAt(System.currentTimeMillis());
                op.forceTransitionTo(RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING);
                expansionRepository.saveOnConnection(conn, op);

                conn.commit();
                regionCache.add(region);
                membershipCache.loadFromRegion(region);

                LOGGER.info("Expansion recovery: resize applied for op={}", op.getOperationId());
            } catch (Exception e) {
                conn.rollback();
                throw (e instanceof SQLException) ? (SQLException) e : new SQLException(e);
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        }
    }

    private boolean recoverCapturePending(RegionExpansionOperation op) {
        if (op.getCaptureIdempotencyKey() == null) {
            String key = generateIdempotencyKey(op.getOperationId(), "expand_capture");
            op.setCaptureIdempotencyKey(key);
            expansionRepository.save(op);
        }
        scheduleRetry(op);
        return true;
    }

    private void beginReleaseBeforeResize(RegionExpansionOperation op, String code, String detail) {
        String releaseKey = generateIdempotencyKey(op.getOperationId(), "expand_release");
        op.setReleaseIdempotencyKey(releaseKey);
        op.forceTransitionTo(RegionExpansionState.RELEASE_PENDING);
        op.setFailureCode(code);
        op.setFailureDetail(detail);
        expansionRepository.save(op);

        LandPaymentReleaseRequest req = new LandPaymentReleaseRequest(
            op.getPaymentOperationUuid(),
            op.getGemsReservationId(),
            releaseKey
        );
        LandPaymentOperationResult result = paymentGateway.release(req);
        if (result.isSuccess() || result.getFailure() == LandPaymentFailure.ALREADY_RELEASED) {
            op.setGemsReservationId(null);
            op.forceTransitionTo(RegionExpansionState.CANCELLED_BEFORE_RESIZE);
            expansionRepository.save(op);
        } else {
            scheduleRetry(op);
        }
    }

    private void scheduleRetry(RegionExpansionOperation op) {
        op.incrementRetryCount();
        op.setNextRetryAt(System.currentTimeMillis() + 5000);
        expansionRepository.save(op);
    }

    private String generateIdempotencyKey(String operationId, String operation) {
        String compactId = operationId.replace("-", "");
        return "regions_" + compactId + "_" + operation;
    }
}
