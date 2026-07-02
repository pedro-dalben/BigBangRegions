package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.protection.RegionAction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public class EndermanLeaveBlockGoalMixin {
    @Inject(method = "canPlaceBlock", at = @At("HEAD"), cancellable = true)
    private void onCanPlaceBlock(Level level, BlockPos pos, BlockState carriedState, BlockState currentState, BlockState belowState, BlockPos belowPos, CallbackInfoReturnable<Boolean> cir) {
        if (!BigBangRegions.canWorldAction(level, pos, RegionAction.MOB_GRIEFING)) {
            cir.setReturnValue(false);
        }
    }
}
