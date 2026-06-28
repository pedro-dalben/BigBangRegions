package com.bigbangcraft.regions.recovery;

import com.bigbangcraft.regions.allocation.AllocationRequest;
import com.bigbangcraft.regions.allocation.AllocationRequestState;
import com.bigbangcraft.regions.allocation.PlotSlot;
import com.bigbangcraft.regions.allocation.PlotSlotState;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.payment.api.LandPaymentGateway;
import com.bigbangcraft.regions.payment.api.LandPaymentOperationResult;
import com.bigbangcraft.regions.payment.api.LandPaymentReleaseRequest;
import com.bigbangcraft.regions.repository.AllocationRequestRepository;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class LandOperationRecoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-LandOperationRecoveryService");

    private final AllocationRequestRepository requestRepository;
    private final PlotSlotRepository slotRepository;
    private final RegionRepository regionRepository;
    private final RegionCache regionCache;
    private final RegionMembershipCache membershipCache;
    private final LandPaymentGateway paymentGateway;
    private final ConfigManager configManager;

    public LandOperationRecoveryService(AllocationRequestRepository requestRepository,
                                        PlotSlotRepository slotRepository,
                                        RegionRepository regionRepository,
                                        RegionCache regionCache,
                                        RegionMembershipCache membershipCache,
                                        LandPaymentGateway paymentGateway,
                                        ConfigManager configManager) {
        this.requestRepository = requestRepository;
        this.slotRepository = slotRepository;
        this.regionRepository = regionRepository;
        this.regionCache = regionCache;
        this.membershipCache = membershipCache;
        this.paymentGateway = paymentGateway;
        this.configManager = configManager;
    }

    public void recover() {
        LOGGER.info("Starting land operation recovery...");
        
        // Reload regions from DB first to ensure cache consistency
        reloadRegionsFromDb();
        
        List<AllocationRequest> active = requestRepository.getActiveRequests();
        int recovered = 0;

        for (AllocationRequest request : active) {
            AllocationRequestState state = request.getState();
            boolean changed = false;

            if (state == AllocationRequestState.REGION_CREATING) {
                changed = recoverRegionCreating(request);
            } else if (state == AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING) {
                changed = recoverCapturePending(request);
            } else if (state == AllocationRequestState.RELEASE_PENDING) {
                changed = recoverReleasePending(request);
            } else if (state == AllocationRequestState.PAYMENT_RESERVE_PENDING) {
                changed = recoverPaymentReservePending(request);
            } else if (state == AllocationRequestState.PAYMENT_RENEW_PENDING) {
                changed = recoverPaymentRenewPending(request);
            }

            if (changed) {
                recovered++;
            }
        }

        LOGGER.info("Land operation recovery completed. Recovered {} operations.", recovered);
    }

    private void reloadRegionsFromDb() {
        try {
            List<Region> regions = regionRepository.loadAll();
            for (Region r : regions) {
                regionCache.add(r);
                membershipCache.loadFromRegion(r);
            }
            LOGGER.info("Recovery: reloaded {} regions from database.", regions.size());
        } catch (Exception e) {
            LOGGER.error("Recovery: failed to reload regions from database.", e);
        }
    }

    /**
     * Query SQLite directly for region existence. Never trust cache during recovery.
     * If region exists, advance to capture pending. If not, release and cancel.
     */
    private boolean recoverRegionCreating(AllocationRequest request) {
        String regionId = request.getRegionId();
        if (regionId == null) {
            LOGGER.warn("RECOVERY: Request {} in REGION_CREATING has no regionId. Moving to BLOCKED.", request.getId());
            request.forceTransitionTo(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION);
            request.setFailureReason("Recuperacao: REGION_CREATING sem region_id.");
            requestRepository.save(request);
            return true;
        }

        // Query SQLite directly, not cache
        boolean existsInDb = regionExistsInDb(regionId);
        
        if (existsInDb) {
            LOGGER.info("RECOVERY: Request {} region {} exists in DB. Advancing to capture pending.", request.getId(), regionId);
            request.transitionTo(AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING);
            requestRepository.save(request);
            return true;
        }

        // Region was not created (crashed before world ops). Release slot and payment.
        LOGGER.info("RECOVERY: Request {} region {} not found in DB. Releasing and cancelling.", request.getId(), regionId);
        releaseSlotIfReserved(request);
        releasePaymentIfReserved(request);

        if (request.getGemsReservationId() != null) {
            request.forceTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION);
            request.setFailureReason("Recuperacao: regiao nao criada antes do crash.");
        } else {
            request.forceTransitionTo(AllocationRequestState.FAILED_NO_TERRAIN);
            request.setFailureReason("Recuperacao: regiao nao criada (sem pagamento).");
        }
        requestRepository.save(request);
        return true;
    }

    /**
     * Query SQLite directly for region existence. The coordinator's REGION_CREATING handler
     * also checks DB directly, so cache is never authoritative for recovery.
     */
    private boolean recoverCapturePending(AllocationRequest request) {
        String regionId = request.getRegionId();
        if (regionId == null) {
            LOGGER.warn("RECOVERY: Request {} in REGION_CREATED_PAYMENT_CAPTURE_PENDING has no regionId. Moving to BLOCKED.", request.getId());
            request.forceTransitionTo(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION);
            request.setFailureReason("Recuperacao: CAPTURE_PENDING sem region_id.");
            requestRepository.save(request);
            return true;
        }

        if (!regionExistsInDb(regionId)) {
            LOGGER.warn("RECOVERY: Request {} region {} missing from DB. Moving to BLOCKED.", request.getId(), regionId);
            request.forceTransitionTo(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION);
            request.setFailureReason("Recuperacao: regiao perdida apos criacao.");
            requestRepository.save(request);
            return true;
        }

        if (request.getCaptureIdempotencyKey() != null) {
            var existingReservation = paymentGateway.getReservationByIdempotencyKey(request.getCaptureIdempotencyKey());
            if (existingReservation.isEmpty()) {
                scheduleRetry(request);
                return true;
            }
        }

        scheduleRetry(request);
        return true;
    }

    /**
     * RELEASE_PENDING: only release slot and cancel after release is confirmed.
     * On failure, preserve reservationId, slot, and RELEASE_PENDING state for retry.
     */
    private boolean recoverReleasePending(AllocationRequest request) {
        String reservationId = request.getGemsReservationId();
        if (reservationId == null) {
            // Nothing to release anymore
            LOGGER.info("RECOVERY: Request {} in RELEASE_PENDING has no reservation. Cancelling.", request.getId());
            releaseSlotIfReserved(request);
            request.forceTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION);
            requestRepository.save(request);
            return true;
        }

        // Try to release via idempotency key
        String releaseKey = generateIdempotencyKey(request.getId(), "release");
        request.setReleaseIdempotencyKey(releaseKey);
        requestRepository.save(request);

        LandPaymentReleaseRequest releaseReq = new LandPaymentReleaseRequest(
            UUID.fromString(request.getId()),
            reservationId,
            releaseKey
        );
        LandPaymentOperationResult result = paymentGateway.release(releaseReq);
        if (result.isSuccess()) {
            request.setGemsReservationId(null);
            requestRepository.save(request);
            LOGGER.info("RECOVERY: Successfully released reservation {} for request {}", reservationId, request.getId());
            
            // Only now, after confirmed release, release the slot and cancel
            releaseSlotIfReserved(request);
            request.forceTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION);
            requestRepository.save(request);
        } else {
            // Release failed - preserve reservationId, slot, and state (RELEASE_PENDING)
            // So it can be retried
            LOGGER.warn("RECOVERY: Failed to release reservation {} for request {}: {}. Staying in RELEASE_PENDING.",
                reservationId, request.getId(), result.getFailure());
            scheduleRetry(request);
        }
        return true;
    }

    private boolean recoverPaymentReservePending(AllocationRequest request) {
        scheduleRetry(request);
        return true;
    }

    private boolean recoverPaymentRenewPending(AllocationRequest request) {
        scheduleRetry(request);
        return true;
    }

    private boolean regionExistsInDb(String regionId) {
        List<Region> allRegions = regionRepository.loadAll();
        return allRegions.stream().anyMatch(r -> r.getId().equals(regionId));
    }

    private void releaseSlotIfReserved(AllocationRequest request) {
        String slotId = request.getPlotSlotId();
        if (slotId != null) {
            PlotSlot slot = slotRepository.get(slotId);
            if (slot != null && slot.getState() == PlotSlotState.RESERVED) {
                slot.release();
                slotRepository.save(slot);
                LOGGER.info("RECOVERY: Released slot {} for request {}", slotId, request.getId());
            }
        }
    }

    private void releasePaymentIfReserved(AllocationRequest request) {
        String reservationId = request.getGemsReservationId();
        if (reservationId != null) {
            String releaseKey = generateIdempotencyKey(request.getId(), "release");
            LandPaymentReleaseRequest releaseReq = new LandPaymentReleaseRequest(
                UUID.fromString(request.getId()),
                reservationId,
                releaseKey
            );
            LandPaymentOperationResult result = paymentGateway.release(releaseReq);
            if (result.isSuccess()) {
                request.setGemsReservationId(null);
                LOGGER.info("RECOVERY: Released payment {} for request {}", reservationId, request.getId());
            } else {
                LOGGER.warn("RECOVERY: Failed to release payment {} for request {}: {}", reservationId, request.getId(), result.getFailure());
            }
        }
    }

    private void scheduleRetry(AllocationRequest request) {
        request.incrementRetryCount();
        long retryAt = System.currentTimeMillis() + 5000;
        request.setNextRetryAt(retryAt);
        requestRepository.save(request);
        LOGGER.info("RECOVERY: Scheduled retry for request {} at {}", request.getId(), retryAt);
    }

    private String generateIdempotencyKey(String operationId, String operation) {
        String compactId = operationId.replace("-", "");
        return "regions_" + compactId + "_" + operation;
    }
}
