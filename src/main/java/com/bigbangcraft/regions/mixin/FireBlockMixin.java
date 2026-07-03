package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.protection.RegionAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FireBlock.class)
public class FireBlockMixin {
    @Unique
    private BlockPos bigbangregions$fireSourcePos;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        this.bigbangregions$fireSourcePos = pos;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void onTickReturn(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        this.bigbangregions$fireSourcePos = null;
    }

    @Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private void onGetIgniteOdds(LevelReader levelReader, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (this.bigbangregions$fireSourcePos == null || !(levelReader instanceof Level level)) {
            return;
        }

        if (!BigBangRegions.canWorldAction(level, this.bigbangregions$fireSourcePos, pos, RegionAction.FIRE_SPREAD)) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void onCheckBurnOutHead(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
        if (this.bigbangregions$fireSourcePos == null) {
            return;
        }

        if (!BigBangRegions.canWorldAction(level, this.bigbangregions$fireSourcePos, pos, RegionAction.FIRE_SPREAD)) {
            ci.cancel();
        }
    }

    @Inject(method = "checkBurnOut", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"), cancellable = true)
    private void onCheckBurnOutRemoveBlock(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
        if (this.bigbangregions$fireSourcePos == null) {
            return;
        }

        if (!BigBangRegions.canWorldAction(level, this.bigbangregions$fireSourcePos, pos, RegionAction.FIRE_BLOCK_DAMAGE)) {
            ci.cancel();
        }
    }
}
