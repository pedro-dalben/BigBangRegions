package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.protection.RegionAction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Explosion.class)
public class ExplosionMixin {
    @Shadow @Final private Level level;

    @Inject(method = "explode", at = @At("RETURN"))
    private void onExplodeReturn(CallbackInfo ci) {
        if (level.isClientSide) {
            return;
        }

        Explosion explosion = (Explosion) (Object) this;
        List<BlockPos> affectedBlocks = explosion.getToBlow();
        if (affectedBlocks.isEmpty()) {
            return;
        }

        String dimension = level.dimension().location().toString();
        affectedBlocks.removeIf(pos ->
                BigBangRegions.isBoundaryBlock(dimension, pos) ||
                !BigBangRegions.canWorldAction(level, pos, RegionAction.EXPLOSION_BLOCK_DAMAGE));
    }
}
