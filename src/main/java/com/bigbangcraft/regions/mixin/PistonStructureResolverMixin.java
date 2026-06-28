package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonStructureResolver.class)
public class PistonStructureResolverMixin {

    @Shadow @Final private Level level;
    @Shadow @Final private Direction pushDirection;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private List<BlockPos> toDestroy;

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void onResolve(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !level.isClientSide()) {
            // Check all blocks to push
            for (BlockPos pos : toPush) {
                if (!BigBangRegions.isPistonAllowed(level, pos, pushDirection)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
            // Check all blocks to destroy
            for (BlockPos pos : toDestroy) {
                String dimension = level.dimension().location().toString();
                if (BigBangRegions.isBoundaryBlock(dimension, pos)) {
                    cir.setReturnValue(false);
                    return;
                }
                
                var cache = BigBangRegions.getRegionCache();
                if (cache != null) {
                    var sourceRegions = cache.getRegionsAt(dimension, pos.getX(), pos.getY(), pos.getZ());
                    if (!sourceRegions.isEmpty()) {
                        cir.setReturnValue(false);
                        return;
                    }
                }
            }
        }
    }
}
