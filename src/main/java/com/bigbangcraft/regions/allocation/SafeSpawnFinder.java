package com.bigbangcraft.regions.allocation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;
import java.util.Set;

public class SafeSpawnFinder {
    private static final int PLATFORM_RADIUS = 2;
    private static final int REQUIRED_HEADROOM = 3;

    public static Optional<BlockPos> findSafeSpawn(Level level, int minX, int maxX, int minZ, int maxZ) {
        return findSafeSpawn(level, minX, maxX, minZ, maxZ, null);
    }

    public static Optional<BlockPos> findSafeSpawn(Level level, int minX, int maxX, int minZ, int maxZ, Set<net.minecraft.world.level.ChunkPos> allowedChunks) {
        int centerX = minX + (maxX - minX) / 2;
        int centerZ = minZ + (maxZ - minZ) / 2;

        // Try center first
        Optional<BlockPos> spawn = checkColumn(level, centerX, centerZ, allowedChunks);
        if (spawn.isPresent()) {
            return spawn;
        }

        // Search outwards in a spiral grid within bounds
        int step = 2;
        int maxRadius = Math.min((maxX - minX) / 2, (maxZ - minZ) / 2);
        for (int r = step; r <= maxRadius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (Math.abs(dx) == r || Math.abs(dz) == r) {
                        int x = centerX + dx;
                        int z = centerZ + dz;
                        spawn = checkColumn(level, x, z, allowedChunks);
                        if (spawn.isPresent()) {
                            return spawn;
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> checkColumn(Level level, int x, int z, Set<net.minecraft.world.level.ChunkPos> allowedChunks) {
        if (allowedChunks != null && !allowedChunks.contains(new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4))) {
            return Optional.empty();
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (surfaceY <= level.getMinBuildHeight()) {
            return Optional.empty();
        }

        BlockPos floor = new BlockPos(x, surfaceY - 1, z);
        BlockPos stand = new BlockPos(x, surfaceY, z);
        BlockPos head = new BlockPos(x, surfaceY + 1, z);

        if (isUnsafePlatformColumn(level, x, z, surfaceY, allowedChunks)) {
            return Optional.empty();
        }

        if (level.canSeeSky(stand) && isSafeFloor(level, floor) && isSafeToStand(level, stand) && isSafeToStand(level, head)) {
            return Optional.of(stand);
        }
        return Optional.empty();
    }

    private static boolean isUnsafePlatformColumn(Level level, int centerX, int centerZ, int surfaceY) {
        return isUnsafePlatformColumn(level, centerX, centerZ, surfaceY, null);
    }

    private static boolean isUnsafePlatformColumn(Level level, int centerX, int centerZ, int surfaceY, Set<net.minecraft.world.level.ChunkPos> allowedChunks) {
        for (int dx = -PLATFORM_RADIUS + 1; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS + 1; dz <= PLATFORM_RADIUS; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                if (allowedChunks != null && !allowedChunks.contains(new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4))) {
                    return true;
                }
                int colSurfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (colSurfaceY <= level.getMinBuildHeight()) {
                    return true;
                }
                if (Math.abs(colSurfaceY - surfaceY) > 2) {
                    return true;
                }
                BlockPos colFloor = new BlockPos(x, colSurfaceY - 1, z);
                if (!isSafeFloor(level, colFloor)) {
                    return true;
                }
                for (int dy = 0; dy < REQUIRED_HEADROOM; dy++) {
                    BlockPos colPos = new BlockPos(x, colSurfaceY + dy, z);
                    if (!isSafeToStand(level, colPos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isSafeFloor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (state.is(Blocks.LAVA) || state.is(Blocks.WATER) || state.is(Blocks.FIRE)
            || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.POWDER_SNOW)) {
            return false;
        }
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
            || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
            || state.is(BlockTags.CROPS) || state.is(BlockTags.REPLACEABLE_BY_TREES)) {
            return false;
        }
        if (state.getBlock() instanceof BushBlock
            || state.getBlock() instanceof SugarCaneBlock) {
            return false;
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean isSafeToStand(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (state.is(Blocks.LAVA) || state.is(Blocks.WATER) || state.is(Blocks.FIRE)
            || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.POWDER_SNOW)) {
            return false;
        }
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
            return false;
        }
        return !state.isCollisionShapeFullBlock(level, pos);
    }
}
