package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.protection.RegionAction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IceBlock.class)
public class IceBlockMixin {

    @Inject(method = "melt", at = @At("HEAD"), cancellable = true)
    private void onMelt(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (!BigBangRegions.canWorldAction(level, pos, RegionAction.ICE_MELT)) {
            ci.cancel();
        }
    }
}
