package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllocationConfigValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-AllocationConfigValidator");

    public static boolean isValid(Config config) {
        if (config == null) return false;
        Config.PlayerLandAllocationConfig lac = config.getPlayerLandAllocation();
        if (lac == null) return false;

        if (lac.getTargetDimension() == null || lac.getTargetDimension().trim().isEmpty()) {
            LOGGER.error("Allocation config invalid: targetDimension is missing or empty.");
            return false;
        }

        if (lac.getInitialClaimSize() < 1) {
            LOGGER.error("Allocation config invalid: initialClaimSize must be >= 1.");
            return false;
        }

        if (lac.getSlotSize() <= lac.getInitialClaimSize()) {
            LOGGER.error("Allocation config invalid: slotSize must be > initialClaimSize.");
            return false;
        }

        if (lac.getFutureMaximumClaimSize() < lac.getInitialClaimSize()) {
            LOGGER.error("Allocation config invalid: futureMaximumClaimSize must be >= initialClaimSize.");
            return false;
        }

        if (lac.getFutureMaximumClaimSize() + 2 * lac.getSlotInternalMargin() > lac.getSlotSize()) {
            LOGGER.error("Allocation config invalid: futureMaximumClaimSize + 2 * slotInternalMargin must be <= slotSize.");
            return false;
        }

        Config.BiomeSearchConfig bsc = lac.getBiomeSearch();
        if (bsc == null) return false;

        if (bsc.getMinimumMatchPercentage() < 1 || bsc.getMinimumMatchPercentage() > 100) {
            LOGGER.error("Allocation config invalid: minimumMatchPercentage must be between 1 and 100.");
            return false;
        }

        if (bsc.getSampleGridSize() < 3 || bsc.getSampleGridSize() % 2 == 0) {
            LOGGER.error("Allocation config invalid: sampleGridSize must be odd and >= 3.");
            return false;
        }

        if (bsc.getMaximumCandidateSlots() <= 0) {
            LOGGER.error("Allocation config invalid: maximumCandidateSlots must be > 0.");
            return false;
        }

        Config.SchedulerConfig sc = lac.getScheduler();
        if (sc == null) return false;

        if (sc.getCreationCooldownSeconds() < 0 || sc.getHomeTeleportCooldownSeconds() < 0) {
            LOGGER.error("Allocation config invalid: cooldowns must be non-negative.");
            return false;
        }

        if (sc.getRequestTimeoutSeconds() <= 0 || sc.getReservationLeaseSeconds() <= 0) {
            LOGGER.error("Allocation config invalid: timeout and lease duration must be positive.");
            return false;
        }

        return true;
    }
}
