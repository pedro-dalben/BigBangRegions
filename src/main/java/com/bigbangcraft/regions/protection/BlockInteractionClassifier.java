package com.bigbangcraft.regions.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

public class BlockInteractionClassifier {

    public static class ClassifiedInteraction {
        private final RegionAction action;
        private final BlockPos targetPos;

        public ClassifiedInteraction(RegionAction action, BlockPos targetPos) {
            this.action = action;
            this.targetPos = targetPos;
        }

        public RegionAction getAction() {
            return action;
        }

        public BlockPos getTargetPos() {
            return targetPos;
        }
    }

    public static ClassifiedInteraction classify(Level world, BlockPos pos, BlockState state, Player player, InteractionHand hand, BlockHitResult hitResult) {
        Block block = state.getBlock();

        // 1. Check Container
        BlockEntity be = world.getBlockEntity(pos);
        if (be != null) {
            if (be instanceof net.minecraft.world.Container || be instanceof net.minecraft.world.MenuProvider) {
                return new ClassifiedInteraction(RegionAction.CONTAINER, pos);
            }
        }

        // 2. Check Doors / Trapdoors / Gates
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
            return new ClassifiedInteraction(RegionAction.DOOR, pos);
        }

        // 3. Check Redstone interactables (Buttons, Levers)
        if (block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof DiodeBlock) {
            return new ClassifiedInteraction(RegionAction.REDSTONE, pos);
        }

        // 4. Check if they are placing blocks/buckets (BLOCK_PLACE)
        ItemStack heldItem = player.getItemInHand(hand);
        if (!heldItem.isEmpty()) {
            if (heldItem.getItem() instanceof BlockItem) {
                BlockPos placePos = pos.relative(hitResult.getDirection());
                return new ClassifiedInteraction(RegionAction.BLOCK_PLACE, placePos);
            }
            if (heldItem.getItem() instanceof BucketItem) {
                if (heldItem.is(Items.BUCKET)) {
                    FluidState fluidState = world.getFluidState(pos);
                    if (fluidState.is(Fluids.WATER)) {
                        return new ClassifiedInteraction(RegionAction.WATER_FLOW, pos);
                    }
                    if (fluidState.is(Fluids.LAVA)) {
                        return new ClassifiedInteraction(RegionAction.LAVA_FLOW, pos);
                    }
                    return new ClassifiedInteraction(RegionAction.INTERACT, pos);
                }
                if (heldItem.is(Items.WATER_BUCKET)) {
                    BlockPos placePos = pos.relative(hitResult.getDirection());
                    return new ClassifiedInteraction(RegionAction.WATER_FLOW, placePos);
                }
                if (heldItem.is(Items.LAVA_BUCKET)) {
                    BlockPos placePos = pos.relative(hitResult.getDirection());
                    return new ClassifiedInteraction(RegionAction.LAVA_FLOW, placePos);
                }
                BlockPos placePos = pos.relative(hitResult.getDirection());
                return new ClassifiedInteraction(RegionAction.BLOCK_PLACE, placePos);
            }
        }

        if (heldItem.is(Items.FLINT_AND_STEEL) || heldItem.is(Items.FIRE_CHARGE)) {
            BlockPos firePos = state.is(Blocks.TNT) ? pos : pos.relative(hitResult.getDirection());
            return new ClassifiedInteraction(RegionAction.FIRE_SPREAD, firePos);
        }

        // 5. Fallback interaction check
        return new ClassifiedInteraction(RegionAction.INTERACT, pos);
    }
}
