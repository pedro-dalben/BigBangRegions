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

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void onResolve(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || level.isClientSide) {
            return;
        }

        PistonStructureResolver resolver = (PistonStructureResolver) (Object) this;
        Direction pushDirection = resolver.getPushDirection();

        if (containsBlockedPosition(resolver.getToPush(), pushDirection) ||
            containsBlockedPosition(resolver.getToDestroy(), pushDirection)) {
            cir.setReturnValue(false);
        }
    }

    private boolean containsBlockedPosition(List<BlockPos> positions, Direction pushDirection) {
        for (BlockPos pos : positions) {
            if (!BigBangRegions.isPistonAllowed(level, pos, pushDirection)) {
                return true;
            }
        }
        return false;
    }
}
