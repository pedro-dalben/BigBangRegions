package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlotSlotService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-PlotSlotService");

    private final ConfigManager configManager;
    private final PlotSlotRepository plotSlotRepository;
    private final RegionCache regionCache;

    public PlotSlotService(ConfigManager configManager, PlotSlotRepository plotSlotRepository, RegionCache regionCache) {
        this.configManager = configManager;
        this.plotSlotRepository = plotSlotRepository;
        this.regionCache = regionCache;
    }

    public boolean isSlotEligible(int minX, int minZ, int slotSize) {
        Config config = configManager.getConfig();
        Config.PlayerLandAllocationConfig lac = config.getPlayerLandAllocation();
        Config.ExplorationExclusionConfig ex = lac.getExplorationExclusion();

        int safetyBuffer = ex.getSafetyBuffer();
        int exMinX = ex.getMinX() - safetyBuffer;
        int exMaxX = ex.getMaxX() + safetyBuffer;
        int exMinZ = ex.getMinZ() - safetyBuffer;
        int exMaxZ = ex.getMaxZ() + safetyBuffer;

        int maxX = minX + slotSize - 1;
        int maxZ = minZ + slotSize - 1;

        // Check intersection with exclusion zone
        boolean overlapX = (minX <= exMaxX && maxX >= exMinX);
        boolean overlapZ = (minZ <= exMaxZ && maxZ >= exMinZ);
        if (overlapX && overlapZ) {
            return false;
        }

        // Check intersection with any existing regions in cache
        RegionBounds slotBounds = new RegionBounds(lac.getTargetDimension(), minX, -64, minZ, maxX, 320, maxZ);
        for (Region region : regionCache.getAll()) {
            if (region.getBounds().intersects(slotBounds)) {
                return false;
            }
        }

        return true;
    }

    public List<PlotSlotCandidate> getCandidates(UUID ownerUuid, int offset, int limit) {
        Config config = configManager.getConfig();
        Config.PlayerLandAllocationConfig lac = config.getPlayerLandAllocation();
        int slotSize = lac.getSlotSize();

        List<PlotSlotCandidate> candidates = new ArrayList<>();

        // Start search outside exclusion zone
        int minRadius = Math.max(Math.abs(lac.getExplorationExclusion().getMaxX()), Math.abs(lac.getExplorationExclusion().getMinX())) + lac.getExplorationExclusion().getSafetyBuffer();
        int startRing = minRadius / slotSize + 1;
        if (startRing < 1) startRing = 1;

        int ring = startRing;
        int count = 0;
        int targetAmount = offset + limit;

        while (count < targetAmount && ring < startRing + 1000) {
            for (int dx = -ring; dx <= ring && count < targetAmount; dx++) {
                if (dx == -ring || dx == ring) {
                    for (int dz = -ring; dz <= ring && count < targetAmount; dz++) {
                        int gridX = dx;
                        int gridZ = dz;
                        int minX = gridX * slotSize;
                        int minZ = gridZ * slotSize;
                        if (isSlotEligible(minX, minZ, slotSize)) {
                            candidates.add(new PlotSlotCandidate(gridX, gridZ, minX, minZ));
                            count++;
                        }
                    }
                } else {
                    int dz1 = -ring;
                    int minX = dx * slotSize;
                    int minZ1 = dz1 * slotSize;
                    if (isSlotEligible(minX, minZ1, slotSize)) {
                        candidates.add(new PlotSlotCandidate(dx, dz1, minX, minZ1));
                        count++;
                    }

                    int dz2 = ring;
                    int minZ2 = dz2 * slotSize;
                    if (isSlotEligible(minX, minZ2, slotSize) && count < targetAmount) {
                        candidates.add(new PlotSlotCandidate(dx, dz2, minX, minZ2));
                        count++;
                    }
                }
            }
            ring++;
        }

        // Shuffle pseudo-deterministically using ownerUuid
        java.util.Random rnd = new java.util.Random(ownerUuid.getMostSignificantBits() ^ ownerUuid.getLeastSignificantBits());
        java.util.Collections.shuffle(candidates, rnd);

        if (offset >= candidates.size()) {
            return java.util.Collections.emptyList();
        }
        return candidates.subList(offset, Math.min(offset + limit, candidates.size()));
    }

    public static class PlotSlotCandidate {
        public final int gridX;
        public final int gridZ;
        public final int minX;
        public final int minZ;

        public PlotSlotCandidate(int gridX, int gridZ, int minX, int minZ) {
            this.gridX = gridX;
            this.gridZ = gridZ;
            this.minX = minX;
            this.minZ = minZ;
        }
    }
}
