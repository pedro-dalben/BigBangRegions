package com.bigbangcraft.regions.virtualpasture;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VirtualPastureStateTest {
    @BeforeAll
    static void bootMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void onlyBottomPartOfConfiguredTwoPartBlockConsumesQuota() {
        var blockId = BuiltInRegistries.BLOCK.getKey(Blocks.SUNFLOWER);
        BlockState bottom = pastureState("bottom");
        BlockState top = pastureState("top");

        assertTrue(VirtualPastureService.isCountedVirtualPasture(bottom, blockId));
        assertFalse(VirtualPastureService.isCountedVirtualPasture(top, blockId));
        assertFalse(VirtualPastureService.isCountedVirtualPasture(Blocks.DIRT.defaultBlockState(), blockId));
    }

    @Test
    void bothConfiguredBlockIdsShareTheBaseRule() {
        var first = BuiltInRegistries.BLOCK.getKey(Blocks.SUNFLOWER);
        var second = BuiltInRegistries.BLOCK.getKey(Blocks.DANDELION);
        assertTrue(VirtualPastureService.isCountedVirtualPasture(pastureState(Blocks.SUNFLOWER, "bottom"), Set.of(first, second)));
        assertTrue(VirtualPastureService.isCountedVirtualPasture(pastureState(Blocks.DANDELION, "bottom"), Set.of(first, second)));
        assertFalse(VirtualPastureService.isCountedVirtualPasture(pastureState(Blocks.DANDELION, "top"), Set.of(first, second)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState pastureState(String part) {
        return pastureState(Blocks.SUNFLOWER, part);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState pastureState(net.minecraft.world.level.block.Block block, String part) {
        BlockState state = mock(BlockState.class);
        Property property = mock(Property.class);
        when(state.getBlock()).thenReturn(block);
        when(state.getProperties()).thenReturn(java.util.List.of(property));
        when(state.getValue(property)).thenReturn(part);
        when(property.getName()).thenReturn("part");
        when(property.getName(part)).thenReturn(part);
        return state;
    }
}
