package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.protection.RegionAction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LavaFluid.class)
public class LavaFluidMixin {
    @Unique
    private BlockPos bigbangregions$lavaSourcePos;

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void onRandomTickHead(Level level, BlockPos pos, FluidState fluidState, RandomSource random, CallbackInfo ci) {
        this.bigbangregions$lavaSourcePos = pos;
    }

    @Inject(method = "randomTick", at = @At("RETURN"))
    private void onRandomTickReturn(Level level, BlockPos pos, FluidState fluidState, RandomSource random, CallbackInfo ci) {
        this.bigbangregions$lavaSourcePos = null;
    }

    @Inject(method = "hasFlammableNeighbours", at = @At("HEAD"), cancellable = true)
    private void onHasFlammableNeighbours(LevelReader levelReader, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.bigbangregions$lavaSourcePos == null || !(levelReader instanceof Level level)) {
            return;
        }

        if (!BigBangRegions.canWorldAction(level, this.bigbangregions$lavaSourcePos, pos, RegionAction.FIRE_SPREAD)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isFlammable", at = @At("HEAD"), cancellable = true)
    private void onIsFlammable(LevelReader levelReader, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.bigbangregions$lavaSourcePos == null || !(levelReader instanceof Level level)) {
            return;
        }

        if (!BigBangRegions.canWorldAction(level, this.bigbangregions$lavaSourcePos, pos, RegionAction.FIRE_SPREAD)) {
            cir.setReturnValue(false);
        }
    }
}
