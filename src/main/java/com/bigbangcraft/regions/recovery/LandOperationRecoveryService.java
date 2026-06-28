package com.bigbangcraft.regions.recovery;

import com.bigbangcraft.regions.allocation.AllocationRequest;
import com.bigbangcraft.regions.allocation.AllocationRequestState;
import com.bigbangcraft.regions.allocation.PlotSlot;
import com.bigbangcraft.regions.allocation.PlotSlotState;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.payment.api.LandPaymentGateway;
import com.bigbangcraft.regions.payment.api.LandPaymentOperationResult;
import com.bigbangcraft.regions.payment.api.LandPaymentReleaseRequest;
import com.bigbangcraft.regions.repository.AllocationRequestRepository;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class LandOperationRecoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-LandOperationRecoveryService");

    private final AllocationRequestRepository requestRepository;
    private final PlotSlotRepository slotRepository;
    private final RegionCache regionCache;
    private final LandPaymentGateway paymentGateway;
    private final ConfigManager configManager;

    public LandOperationRecoveryService(AllocationRequestRepository requestRepository,
                                        PlotSlotRepository slotRepository,
                                        RegionCache regionCache,
                                        LandPaymentGateway paymentGateway,
                                        ConfigManager configManager) {
        this.requestRepository = requestRepository;
        this.slotRepository = slotRepository;
        this.regionCache = regionCache;
        this.paymentGateway = paymentGateway;
        this.configManager = configManager;
    }

    public void recover() {
        LOGGER.info("Starting land operation recovery...");
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

    private boolean recoverRegionCreating(AllocationRequest request) {
        String regionId = request.getRegionId();
        if (regionId == null) {
            LOGGER.warn("RECOVERY: Request {} in REGION_CREATING has no regionId. Moving to BLOCKED.", request.getId());
            request.forceTransitionTo(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION);
            request.setFailureReason("Recuperacao: REGION_CREATING sem region_id.");
            requestRepository.save(request);
            return true;
        }

        // Check if the region was already created
        if (regionCache.get(regionId) != null) {
            LOGGER.info("RECOVERY: Request {} region {} exists. Advancing to capture pending.", request.getId(), regionId);
            request.transitionTo(AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING);
            requestRepository.save(request);
            return true;
        }

        // Region was not created. Release the slot and payment, cancel the request.
        releaseSlotIfReserved(request);
        releasePaymentIfReserved(request);

        // If payment was reserved, mark as cancelled
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

    private boolean recoverCapturePending(AllocationRequest request) {
        String regionId = request.getRegionId();
        if (regionId == null) {
            LOGGER.warn("RECOVERY: Request {} in REGION_CREATED_PAYMENT_CAPTURE_PENDING has no regionId. Moving to BLOCKED.", request.getId());
            request.forceTransitionTo(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION);
            request.setFailureReason("Recuperacao: CAPTURE_PENDING sem region_id.");
            requestRepository.save(request);
            return true;
        }

        // Check if region exists; if not, we have an inconsistency
        if (regionCache.get(regionId) == null) {
            LOGGER.warn("RECOVERY: Request {} region {} missing. Moving to BLOCKED.", request.getId(), regionId);
            request.forceTransitionTo(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION);
            request.setFailureReason("Recuperacao: regiao perdida apos criacao.");
            requestRepository.save(request);
            return true;
        }

        // Check if the reservation was already captured via idempotency key
        if (request.getCaptureIdempotencyKey() != null) {
            var existingReservation = paymentGateway.getReservationByIdempotencyKey(request.getCaptureIdempotencyKey());
            if (existingReservation.isEmpty()) {
                // Reservation was already captured or doesn't exist. Set retry so processNextRequest can retry.
                LOGGER.info("RECOVERY: Request {} in capture pending. Setting retry.", request.getId());
                scheduleRetry(request);
                return true;
            }
        }

        scheduleRetry(request);
        return true;
    }

    private boolean recoverReleasePending(AllocationRequest request) {
        String reservationId = request.getGemsReservationId();
        if (reservationId == null) {
            // Nothing to release, move to cancelled
            LOGGER.info("RECOVERY: Request {} in RELEASE_PENDING has no reservation. Moving to CANCELLED.", request.getId());
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
        } else {
            LOGGER.warn("RECOVERY: Failed to release reservation for request={}: {}", request.getId(), result.getFailure());
        }

        releaseSlotIfReserved(request);
        request.forceTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION);
        requestRepository.save(request);
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
