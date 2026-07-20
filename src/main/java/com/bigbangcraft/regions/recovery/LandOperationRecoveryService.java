package com.bigbangcraft.regions.recovery;

import com.bigbangcraft.regions.allocation.AllocationRequest;
import com.bigbangcraft.regions.allocation.AllocationRequestState;
import com.bigbangcraft.regions.allocation.PlotSlot;
import com.bigbangcraft.regions.allocation.PlotSlotState;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.repository.AllocationRequestRepository;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LandOperationRecoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-LandOperationRecoveryService");

    private final AllocationRequestRepository requestRepository;
    private final PlotSlotRepository slotRepository;
    private final RegionRepository regionRepository;
    private final RegionCache regionCache;
    private final RegionMembershipCache membershipCache;

    public LandOperationRecoveryService(AllocationRequestRepository requestRepository,
                                        PlotSlotRepository slotRepository,
                                        RegionRepository regionRepository,
                                        RegionCache regionCache,
                                        RegionMembershipCache membershipCache) {
        this.requestRepository = requestRepository;
        this.slotRepository = slotRepository;
        this.regionRepository = regionRepository;
        this.regionCache = regionCache;
        this.membershipCache = membershipCache;
    }

    public void recover() {
        LOGGER.info("Starting land operation recovery...");

        reloadRegionsFromDb();

        List<AllocationRequest> legacy = requestRepository.getLegacyRequests();
        if (!legacy.isEmpty()) {
            LOGGER.warn("Found {} legacy allocation requests with payment data. Moving to LEGACY_REQUIRES_ADMIN_REVIEW.", legacy.size());
            for (AllocationRequest request : legacy) {
                if (request.getState().isLegacyPaymentState() && request.getState() != AllocationRequestState.LEGACY_REQUIRES_ADMIN_REVIEW) {
                    request.forceTransitionTo(AllocationRequestState.LEGACY_REQUIRES_ADMIN_REVIEW);
                    request.setFailureReason("Operacao legada com pagamento detectada na recuperacao. Revisao administrativa necessaria.");
                    requestRepository.save(request);
                    LOGGER.info("Legacy request {} moved to LEGACY_REQUIRES_ADMIN_REVIEW (state was {})",
                        request.getId(), request.getState());
                }
            }
        }

        List<AllocationRequest> active = requestRepository.getActiveRequests();
        int recovered = 0;

        for (AllocationRequest request : active) {
            AllocationRequestState state = request.getState();
            boolean changed = false;

            if (state == AllocationRequestState.PREPARING_CHUNKS
                || state == AllocationRequestState.WAITING_FOR_CHUNKS
                || state == AllocationRequestState.VALIDATING_LOADED_WORLD) {
                request.forceTransitionTo(AllocationRequestState.PAUSED_RECOVERY);
                request.setFailureReason("Recuperacao: preparacao fisica interrompida. Recovery explicito necessario.");
                requestRepository.save(request);
                changed = true;
            } else if (state == AllocationRequestState.REGION_CREATING) {
                changed = recoverRegionCreating(request);
            } else if (state.isLegacyPaymentState() && state != AllocationRequestState.LEGACY_REQUIRES_ADMIN_REVIEW) {
                request.forceTransitionTo(AllocationRequestState.LEGACY_REQUIRES_ADMIN_REVIEW);
                request.setFailureReason("Operacao legada com pagamento encontrada na recuperacao.");
                requestRepository.save(request);
                changed = true;
            }

            if (changed) recovered++;
        }

        LOGGER.info("Land operation recovery completed. Recovered {} operations.", recovered);
    }

    private void reloadRegionsFromDb() {
        try {
            regionRepository.reloadCaches(regionCache, membershipCache);
            LOGGER.info("Recovery: reloaded regions and persisted members from database.");
        } catch (Exception e) {
            LOGGER.error("Recovery: failed to reload regions and members from database; existing caches were preserved.", e);
        }
    }

    private boolean recoverRegionCreating(AllocationRequest request) {
        String regionId = request.getRegionId();
        if (regionId == null) {
            LOGGER.warn("RECOVERY: Request {} in REGION_CREATING has no regionId. Moving to PAUSED_RECOVERY.", request.getId());
            request.forceTransitionTo(AllocationRequestState.PAUSED_RECOVERY);
            request.setFailureReason("Recuperacao: REGION_CREATING sem region_id.");
            requestRepository.save(request);
            return true;
        }

        boolean existsInDb = regionExistsInDb(regionId);

        if (existsInDb) {
            LOGGER.info("RECOVERY: Request {} region {} exists in DB. Marking complete.", request.getId(), regionId);
            request.forceTransitionTo(AllocationRequestState.COMPLETED);
            requestRepository.save(request);
            return true;
        }

        String slotId = request.getPlotSlotId();
        if (slotId != null) {
            PlotSlot slot = slotRepository.get(slotId);
            if (slot != null && slot.getState() == PlotSlotState.RESERVED) {
                LOGGER.info("RECOVERY: Request {} region not created, slot still RESERVED. Moving to PAUSED_RECOVERY.", request.getId());
                request.forceTransitionTo(AllocationRequestState.PAUSED_RECOVERY);
                request.setFailureReason("Recuperacao: criacao fisica interrompida antes da conclusao.");
                requestRepository.save(request);
                return true;
            }
        }

        LOGGER.warn("RECOVERY: Request {} cannot recover automatically. Moving to PAUSED_RECOVERY.", request.getId());
        request.forceTransitionTo(AllocationRequestState.PAUSED_RECOVERY);
        request.setFailureReason("Recuperacao: falha na criacao da regiao.");
        requestRepository.save(request);
        return true;
    }

    private boolean regionExistsInDb(String regionId) {
        List<Region> allRegions = regionRepository.loadAll();
        return allRegions.stream().anyMatch(r -> r.getId().equals(regionId));
    }
}
