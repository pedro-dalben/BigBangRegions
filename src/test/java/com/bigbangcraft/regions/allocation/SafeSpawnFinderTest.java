package com.bigbangcraft.regions.allocation;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SafeSpawnFinderTest {

    @BeforeAll
    public static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testSafeSpawnSuccess() {
        Level level = mock(Level.class);
        when(level.getMinBuildHeight()).thenReturn(0);
        when(level.getMaxBuildHeight()).thenReturn(256);
        when(level.getHeight(eq(Heightmap.Types.WORLD_SURFACE), anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(1);
            int z = invocation.getArgument(2);
            return (x == 25 && z == 25) ? 65 : 0;
        });
        when(level.canSeeSky(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return pos.getX() == 25 && pos.getZ() == 25;
        });

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();

        when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            if (pos.getX() == 25 && pos.getZ() == 25) {
                if (pos.getY() == 64) {
                    return stone;
                } else if (pos.getY() > 64) {
                    return air;
                }
            }
            return air;
        });

        Optional<BlockPos> spawn = SafeSpawnFinder.findSafeSpawn(level, 0, 50, 0, 50);
        assertTrue(spawn.isPresent());
        assertEquals(25, spawn.get().getX());
        assertEquals(65, spawn.get().getY());
        assertEquals(25, spawn.get().getZ());
    }

    @Test
    public void testRejectsCaveSpawn() {
        Level level = mock(Level.class);
        when(level.getMinBuildHeight()).thenReturn(0);
        when(level.getMaxBuildHeight()).thenReturn(256);
        when(level.getHeight(eq(Heightmap.Types.WORLD_SURFACE), anyInt(), anyInt())).thenReturn(65);
        when(level.canSeeSky(any(BlockPos.class))).thenReturn(false);

        when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            if (pos.getY() == 64) {
                return Blocks.STONE.defaultBlockState();
            }
            return Blocks.AIR.defaultBlockState();
        });

        Optional<BlockPos> spawn = SafeSpawnFinder.findSafeSpawn(level, 0, 50, 0, 50);
        assertTrue(spawn.isEmpty());
    }
}
