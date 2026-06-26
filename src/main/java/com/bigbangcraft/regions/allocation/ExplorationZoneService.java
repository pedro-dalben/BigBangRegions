package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExplorationZoneService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-ExplorationZoneService");

    private final ConfigManager configManager;

    public ExplorationZoneService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public Config.ExplorationExclusionConfig getExclusion() {
        return configManager.getConfig().getPlayerLandAllocation().getExplorationExclusion();
    }

    public boolean isInsideExplorationZone(String dimension, int x, int z) {
        Config.ExplorationExclusionConfig ex = getExclusion();
        return dimension.equals(configManager.getConfig().getPlayerLandAllocation().getTargetDimension())
            && x >= ex.getMinX() && x <= ex.getMaxX()
            && z >= ex.getMinZ() && z <= ex.getMaxZ();
    }

    public boolean teleportToExplorationZone(ServerPlayer player) {
        Config.PlayerLandAllocationConfig lac = configManager.getConfig().getPlayerLandAllocation();
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(lac.getTargetDimension()));
        ServerLevel targetLevel = player.getServer().getLevel(dimensionKey);
        if (targetLevel == null) {
            throw new IllegalStateException("Dimensao alvo nao encontrada: " + lac.getTargetDimension());
        }

        int centerX = (getExclusion().getMinX() + getExclusion().getMaxX()) / 2;
        int centerZ = (getExclusion().getMinZ() + getExclusion().getMaxZ()) / 2;
        int topY = targetLevel.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);
        if (topY <= -64) topY = 64;

        BlockPos spawnPos = new BlockPos(centerX, topY + 1, centerZ);
        player.teleportTo(targetLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
        LOGGER.info("Player {} teleported to exploration zone at ({}, {}, {})", player.getGameProfile().getName(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
        return true;
    }
}
