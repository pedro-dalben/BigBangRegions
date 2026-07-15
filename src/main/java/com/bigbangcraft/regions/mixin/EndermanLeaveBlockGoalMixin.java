package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.protection.RegionAction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public class EndermanLeaveBlockGoalMixin {
    @Inject(method = "canPlaceBlock", at = @At("HEAD"), cancellable = true)
    // The nested goal is private, so it must be selected by name. Using Object here
    // keeps the callback descriptor mapping-neutral; Mixin coerces the target args.
    private void onCanPlaceBlock(
            @Coerce Object levelObject,
            @Coerce Object posObject,
            @Coerce Object carriedState,
            @Coerce Object currentState,
            @Coerce Object belowState,
            @Coerce Object belowPosObject,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Level level = (Level) levelObject;
        BlockPos pos = (BlockPos) posObject;
        if (!BigBangRegions.canWorldAction(level, pos, RegionAction.MOB_GRIEFING)) {
            cir.setReturnValue(false);
        }
    }
}
