package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllocationScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-AllocationScheduler");

    private final TerrainAllocationCoordinator coordinator;
    private final ConfigManager configManager;
    private int tickCounter;

    public AllocationScheduler(TerrainAllocationCoordinator coordinator,
                                ConfigManager configManager) {
        this.coordinator = coordinator;
        this.configManager = configManager;
        this.tickCounter = 0;
    }

    public void tick(MinecraftServer server) {
        Config config = configManager.getConfig();
        if (config == null || !config.getPlayerLandAllocation().isEnabled()) return;

        tickCounter++;

        if (tickCounter % 20 == 0) {
            coordinator.releaseExpiredReservations();
        }

        if (tickCounter % 5 == 0) {
            int processed = coordinator.processNextRequest(server);
            if (processed > 0) {
                LOGGER.debug("Processed {} allocation request step(s) this tick", processed);
            }
        }

    }
}
