package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.protection.RegionAction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public class EndermanTakeBlockGoalMixin {
    // Redirect the exact mutation instead of capturing tick()'s LVT, which differs
    // between named development mappings and intermediary production mappings.
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"
            )
    )
    private boolean onRemoveBlock(@Coerce Object levelObject, @Coerce Object posObject, boolean moving) {
        Level level = (Level) levelObject;
        BlockPos pos = (BlockPos) posObject;
        if (!BigBangRegions.canWorldAction(level, pos, RegionAction.MOB_GRIEFING)) {
            return false;
        }
        return level.removeBlock(pos, moving);
    }
}
