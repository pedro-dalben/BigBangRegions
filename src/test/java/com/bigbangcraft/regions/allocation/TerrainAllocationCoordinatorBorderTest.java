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
    void placesGlassOnlyAtAirSurface() {
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

        ServerLevel level = mock(ServerLevel.class);
        when(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0)).thenReturn(64);
        when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(64);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return states.getOrDefault(pos.asLong(), Blocks.DIRT.defaultBlockState());
        });
        when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            states.put(pos.asLong(), invocation.getArgument(1));
            return true;
        });

        TerrainAllocationCoordinator.generateGlassBorder(level, bounds, "minecraft:glass", false);

        assertEquals(Blocks.DIRT.defaultBlockState(), states.get(solidSurface.asLong()));
        assertEquals(Blocks.GLASS.defaultBlockState(), states.get(new BlockPos(1, 64, 0).asLong()));
        assertEquals(7, states.values().stream().filter(state -> state.equals(Blocks.GLASS.defaultBlockState())).count());
        assertTrue(states.entrySet().stream()
            .filter(entry -> entry.getValue().equals(Blocks.GLASS.defaultBlockState()))
            .allMatch(entry -> BlockPos.of(entry.getKey()).getY() == 64));
    }

    @Test
    void clearsLegacyWallWhenTheTerrainExpands() {
        RegionBounds oldBounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 2, 2, 2);
        RegionBounds targetBounds = new RegionBounds("minecraft:overworld", -1, 0, -1, 3, 2, 3);
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
            level, oldBounds, Blocks.GLASS.defaultBlockState(), targetBounds);

        assertTrue(states.values().stream().noneMatch(state -> state.equals(Blocks.GLASS.defaultBlockState())));
    }
}
