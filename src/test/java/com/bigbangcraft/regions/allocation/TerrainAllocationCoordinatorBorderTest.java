package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.domain.RegionBounds;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TerrainAllocationCoordinatorBorderTest {
    @BeforeAll
    static void bootMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void placesGlassFromSurfaceToWorldCeiling() {
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 2, 70, 2);
        Map<Long, BlockState> states = new HashMap<>();
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                if (x == 0 || x == 2 || z == 0 || z == 2) {
                    states.put(new BlockPos(x, 64, z).asLong(), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos solidSurface = new BlockPos(0, 64, 0);
        states.put(solidSurface.asLong(), Blocks.DIRT.defaultBlockState());
        BlockPos tallGrass = new BlockPos(1, 63, 0);
        states.put(tallGrass.asLong(), Blocks.TALL_GRASS.defaultBlockState());

        ServerLevel level = mock(ServerLevel.class);
        when(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0)).thenReturn(64);
        when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(64);
        when(level.getMaxBuildHeight()).thenReturn(71);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return states.getOrDefault(pos.asLong(), pos.getY() >= 64 && pos.getY() <= 70
                ? Blocks.AIR.defaultBlockState() : Blocks.DIRT.defaultBlockState());
        });
        when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            states.put(pos.asLong(), invocation.getArgument(1));
            return true;
        });

        TerrainAllocationCoordinator.generateGlassBorder(level, bounds, "minecraft:glass", false);

        assertEquals(Blocks.DIRT.defaultBlockState(), states.get(solidSurface.asLong()));
        assertEquals(Blocks.GLASS.defaultBlockState(), states.get(new BlockPos(1, 64, 0).asLong()));
        assertEquals(Blocks.GLASS.defaultBlockState(), states.get(tallGrass.asLong()));
        assertTrue(states.entrySet().stream()
            .filter(entry -> entry.getValue().equals(Blocks.GLASS.defaultBlockState()))
            .allMatch(entry -> BlockPos.of(entry.getKey()).getY() >= 63 && BlockPos.of(entry.getKey()).getY() <= 70));
    }

    @Test
    void fillsWaterColumnBeforeRisingToCeiling() {
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 2, 70, 2);
        Map<Long, BlockState> states = new HashMap<>();
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                if (x == 0 || x == 2 || z == 0 || z == 2) {
                    for (int y = 60; y <= 63; y++) {
                        states.put(new BlockPos(x, y, z).asLong(), Blocks.WATER.defaultBlockState());
                    }
                }
            }
        }

        ServerLevel level = mock(ServerLevel.class);
        when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(64);
        when(level.getMaxBuildHeight()).thenReturn(71);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return states.getOrDefault(pos.asLong(), pos.getY() >= 64 && pos.getY() <= 70
                ? Blocks.AIR.defaultBlockState() : Blocks.DIRT.defaultBlockState());
        });
        when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            states.put(pos.asLong(), invocation.getArgument(1));
            return true;
        });

        TerrainAllocationCoordinator.generateGlassBorder(level, bounds, "minecraft:glass", false);

        assertEquals(Blocks.DIRT.defaultBlockState(), level.getBlockState(new BlockPos(0, 59, 0)));
        assertEquals(88, states.values().stream().filter(state -> state.equals(Blocks.GLASS.defaultBlockState())).count());
        assertTrue(states.entrySet().stream()
            .filter(entry -> entry.getValue().equals(Blocks.GLASS.defaultBlockState()))
            .allMatch(entry -> BlockPos.of(entry.getKey()).getY() >= 60 && BlockPos.of(entry.getKey()).getY() <= 70));
    }

    @Test
    void clearsLegacyWallWhenTheTerrainExpands() {
        RegionBounds oldBounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 2, 2, 2);
        Map<Long, BlockState> states = new HashMap<>();
        for (int x = oldBounds.getMinX(); x <= oldBounds.getMaxX(); x++) {
            for (int y = oldBounds.getMinY(); y <= oldBounds.getMaxY(); y++) {
                for (int z = oldBounds.getMinZ(); z <= oldBounds.getMaxZ(); z++) {
                    if (x == oldBounds.getMinX() || x == oldBounds.getMaxX()
                        || z == oldBounds.getMinZ() || z == oldBounds.getMaxZ()) {
                        states.put(new BlockPos(x, y, z).asLong(), Blocks.GLASS.defaultBlockState());
                    }
                }
            }
        }

        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return states.getOrDefault(pos.asLong(), Blocks.DIRT.defaultBlockState());
        });
        when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            states.put(pos.asLong(), invocation.getArgument(1));
            return true;
        });

        TerrainAllocationCoordinator.clearLegacyExpansionBorder(
            level, oldBounds, Blocks.GLASS.defaultBlockState());

        assertTrue(states.values().stream().noneMatch(state -> state.equals(Blocks.GLASS.defaultBlockState())));
    }

    @Test
    void clearsVerticalSurfaceWallWhenTheTerrainExpands() {
        RegionBounds oldBounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 2, 70, 2);
        Map<Long, BlockState> states = new HashMap<>();
        for (int x = oldBounds.getMinX(); x <= oldBounds.getMaxX(); x++) {
            for (int z = oldBounds.getMinZ(); z <= oldBounds.getMaxZ(); z++) {
                if (x == oldBounds.getMinX() || x == oldBounds.getMaxX()
                    || z == oldBounds.getMinZ() || z == oldBounds.getMaxZ()) {
                    for (int y = 20; y <= 40; y++) {
                        states.put(new BlockPos(x, y, z).asLong(), Blocks.GLASS.defaultBlockState());
                    }
                }
            }
        }

        ServerLevel level = mock(ServerLevel.class);
        when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(100);
        when(level.getMaxBuildHeight()).thenReturn(71);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return states.getOrDefault(pos.asLong(), Blocks.DIRT.defaultBlockState());
        });
        when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            states.put(pos.asLong(), invocation.getArgument(1));
            return true;
        });

        TerrainAllocationCoordinator.clearSurfaceExpansionBorder(
            level, oldBounds, Blocks.GLASS.defaultBlockState());

        assertTrue(states.values().stream().noneMatch(state -> state.equals(Blocks.GLASS.defaultBlockState())));
    }

    @Test
    void rebuildsOldWallThatIsStillTheTargetBoundary() {
        RegionBounds oldBounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 2, 10, 2);
        RegionBounds targetBounds = new RegionBounds("minecraft:overworld", 0, 0, -1, 2, 10, 3);
        Map<Long, BlockState> states = new HashMap<>();
        for (int x = oldBounds.getMinX(); x <= oldBounds.getMaxX(); x++) {
            for (int z = oldBounds.getMinZ(); z <= oldBounds.getMaxZ(); z++) {
                if (x == oldBounds.getMinX() || x == oldBounds.getMaxX()
                    || z == oldBounds.getMinZ() || z == oldBounds.getMaxZ()) {
                    for (int y = oldBounds.getMinY(); y <= oldBounds.getMaxY(); y++) {
                        states.put(new BlockPos(x, y, z).asLong(), Blocks.GLASS.defaultBlockState());
                    }
                }
            }
        }

        ServerLevel level = mock(ServerLevel.class);
        when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(0);
        when(level.getMaxBuildHeight()).thenReturn(11);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return states.getOrDefault(pos.asLong(), pos.getY() >= 0 && pos.getY() <= 10
                ? Blocks.AIR.defaultBlockState() : Blocks.DIRT.defaultBlockState());
        });
        when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            states.put(pos.asLong(), invocation.getArgument(1));
            return true;
        });

        TerrainAllocationCoordinator.clearSurfaceExpansionBorder(
            level, oldBounds, Blocks.GLASS.defaultBlockState());

        assertEquals(Blocks.AIR.defaultBlockState(), states.get(new BlockPos(0, 5, 1).asLong()));
        assertEquals(Blocks.AIR.defaultBlockState(), states.get(new BlockPos(2, 5, 1).asLong()));

        TerrainAllocationCoordinator.generateGlassBorder(level, targetBounds, "minecraft:glass", false);

        assertEquals(Blocks.GLASS.defaultBlockState(), states.get(new BlockPos(0, 5, 1).asLong()));
        assertEquals(Blocks.GLASS.defaultBlockState(), states.get(new BlockPos(2, 5, 1).asLong()));
        assertEquals(Blocks.AIR.defaultBlockState(), states.get(new BlockPos(1, 5, 0).asLong()));
        assertEquals(Blocks.AIR.defaultBlockState(), states.get(new BlockPos(1, 5, 2).asLong()));
        assertEquals(Blocks.GLASS.defaultBlockState(), states.get(new BlockPos(1, 5, -1).asLong()));
        assertEquals(Blocks.GLASS.defaultBlockState(), states.get(new BlockPos(1, 5, 3).asLong()));
    }

    @Test
    void allSidesExpansionByTenDoesNotLeaveOldCornerPillars() {
        RegionBounds oldBounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 2, 10, 2);
        RegionBounds targetBounds = new RegionBounds("minecraft:overworld", -10, 0, -10, 12, 10, 12);
        Map<Long, BlockState> states = new HashMap<>();
        for (int y = oldBounds.getMinY(); y <= oldBounds.getMaxY(); y++) {
            states.put(new BlockPos(0, y, 0).asLong(), Blocks.GLASS.defaultBlockState());
            states.put(new BlockPos(0, y, 2).asLong(), Blocks.GLASS.defaultBlockState());
            states.put(new BlockPos(2, y, 0).asLong(), Blocks.GLASS.defaultBlockState());
            states.put(new BlockPos(2, y, 2).asLong(), Blocks.GLASS.defaultBlockState());
        }

        ServerLevel level = mock(ServerLevel.class);
        when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(0);
        when(level.getMaxBuildHeight()).thenReturn(11);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return states.getOrDefault(pos.asLong(), pos.getY() >= 0 && pos.getY() <= 10
                ? Blocks.AIR.defaultBlockState() : Blocks.DIRT.defaultBlockState());
        });
        when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            states.put(pos.asLong(), invocation.getArgument(1));
            return true;
        });

        TerrainAllocationCoordinator.clearLegacyExpansionBorder(
            level, oldBounds, Blocks.GLASS.defaultBlockState());
        TerrainAllocationCoordinator.generateGlassBorder(level, targetBounds, "minecraft:glass", false);

        assertEquals(Blocks.AIR.defaultBlockState(), states.get(new BlockPos(0, 5, 0).asLong()));
        assertEquals(Blocks.AIR.defaultBlockState(), states.get(new BlockPos(0, 5, 2).asLong()));
        assertEquals(Blocks.AIR.defaultBlockState(), states.get(new BlockPos(2, 5, 0).asLong()));
        assertEquals(Blocks.AIR.defaultBlockState(), states.get(new BlockPos(2, 5, 2).asLong()));
        assertEquals(Blocks.GLASS.defaultBlockState(), states.get(new BlockPos(-10, 5, -10).asLong()));
        assertEquals(Blocks.GLASS.defaultBlockState(), states.get(new BlockPos(12, 5, 12).asLong()));
    }
}
