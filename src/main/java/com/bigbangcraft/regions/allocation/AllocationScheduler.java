package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
            ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(config.getPlayerLandAllocation().getTargetDimension()));
            ServerLevel level = server.getLevel(dimensionKey);
            if (level == null) return;

            // The coordinator already owns the per-request candidate budget.
            int processed = coordinator.processNextRequest(level);
            if (processed > 0) {
                LOGGER.debug("Processed {} allocation request step(s) this tick", processed);
            }
        }

    }
}
