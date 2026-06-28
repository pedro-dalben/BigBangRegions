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

        Config.ExplorationExclusionConfig ex = getExclusion();
        BlockPos spawnPos = findSafeSurfaceSpawn(targetLevel, ex.getMinX(), ex.getMaxX(), ex.getMinZ(), ex.getMaxZ());
        if (spawnPos == null) {
            int centerX = (ex.getMinX() + ex.getMaxX()) / 2;
            int centerZ = (ex.getMinZ() + ex.getMaxZ()) / 2;
            int topY = targetLevel.getHeight(Heightmap.Types.WORLD_SURFACE, centerX, centerZ);
            if (topY <= targetLevel.getMinBuildHeight()) {
                topY = 64;
            }
            spawnPos = new BlockPos(centerX, topY + 1, centerZ);
        }

        player.teleportTo(targetLevel, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
        LOGGER.info("Player {} teleported to exploration zone at ({}, {}, {})", player.getGameProfile().getName(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
        return true;
    }

    private BlockPos findSafeSurfaceSpawn(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int maxRadius = Math.max((maxX - minX) / 2, (maxZ - minZ) / 2);

        for (int radius = 0; radius <= maxRadius; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (x < minX || x > maxX || z < minZ || z > maxZ) {
                        continue;
                    }
                    int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    if (surfaceY <= level.getMinBuildHeight()) {
                        continue;
                    }
                    BlockPos floor = new BlockPos(x, surfaceY - 1, z);
                    BlockPos stand = floor.above();
                    BlockPos head = stand.above();
                    if (level.canSeeSky(stand) && isSafeFloor(level, floor) && isSafeToStand(level, stand) && isSafeToStand(level, head)) {
                        return stand;
                    }
                }
            }
        }

        return null;
    }

    private boolean isSafeFloor(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (state.is(net.minecraft.world.level.block.Blocks.LAVA) || state.is(net.minecraft.world.level.block.Blocks.WATER)
            || state.is(net.minecraft.world.level.block.Blocks.FIRE) || state.is(net.minecraft.world.level.block.Blocks.CACTUS)
            || state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
            return false;
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }

    private boolean isSafeToStand(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (state.is(net.minecraft.world.level.block.Blocks.LAVA) || state.is(net.minecraft.world.level.block.Blocks.WATER)
            || state.is(net.minecraft.world.level.block.Blocks.FIRE) || state.is(net.minecraft.world.level.block.Blocks.CACTUS)
            || state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
            return false;
        }
        return !state.isCollisionShapeFullBlock(level, pos);
    }
}
