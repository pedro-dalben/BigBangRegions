package com.bigbangcraft.regions.allocation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;

public class SafeSpawnFinder {
    public static Optional<BlockPos> findSafeSpawn(Level level, int minX, int maxX, int minZ, int maxZ) {
        int centerX = minX + (maxX - minX) / 2;
        int centerZ = minZ + (maxZ - minZ) / 2;

        // Try center first
        Optional<BlockPos> spawn = checkColumn(level, centerX, centerZ);
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
                        spawn = checkColumn(level, x, z);
                        if (spawn.isPresent()) {
                            return spawn;
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> checkColumn(Level level, int x, int z) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (surfaceY <= level.getMinBuildHeight()) {
            return Optional.empty();
        }

        BlockPos floor = new BlockPos(x, surfaceY - 1, z);
        BlockPos stand = floor.above();
        BlockPos head = stand.above();

        if (level.canSeeSky(stand) && isSafeFloor(level, floor) && isSafeToStand(level, stand) && isSafeToStand(level, head)) {
            return Optional.of(stand);
        }
        return Optional.empty();
    }

    private static boolean isSafeFloor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (state.is(Blocks.LAVA) || state.is(Blocks.WATER) || state.is(Blocks.FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)) {
            return false;
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean isSafeToStand(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (state.is(Blocks.LAVA) || state.is(Blocks.WATER) || state.is(Blocks.FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)) {
            return false;
        }
        return !state.isCollisionShapeFullBlock(level, pos);
    }
}
