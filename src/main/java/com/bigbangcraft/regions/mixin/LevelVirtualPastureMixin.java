package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tracks direct commands and mod APIs as well as normal player placement/breaking. */
@Mixin(Level.class)
public abstract class LevelVirtualPastureMixin {
    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void bigbangregions$reserveVirtualPastureBeforeChange(BlockPos pos, BlockState state, int flags,
                                                                    CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (!(level instanceof ServerLevel serverLevel)
            || BigBangRegions.getVirtualPastureService() == null
            || !BigBangRegions.getVirtualPastureService().isVirtualPasture(state)
            || BigBangRegions.getVirtualPastureService().isVirtualPasture(level.getBlockState(pos))) {
            return;
        }
        if (!BigBangRegions.reserveVirtualPasturePlacement(serverLevel, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setBlock", at = @At("RETURN"))
    private void bigbangregions$trackVirtualPastureChange(BlockPos pos, BlockState state, int flags,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        Level level = (Level) (Object) this;
        if (level instanceof ServerLevel serverLevel && BigBangRegions.shouldTrackVirtualPastureChange(serverLevel, pos, state)) {
            BigBangRegions.recordVirtualPastureChange(serverLevel, pos, state);
        }
    }
}
