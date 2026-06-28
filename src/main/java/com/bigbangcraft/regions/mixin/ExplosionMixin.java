package com.bigbangcraft.regions.mixin;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.cache.RegionCache;
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
    @Shadow @Final private List<BlockPos> toBlow;

    @Inject(method = "explode", at = @At("RETURN"))
    private void onExplodeReturn(CallbackInfo ci) {
        if (level.isClientSide) return;

        String dimension = level.dimension().location().toString();
        RegionCache cache = BigBangRegions.getRegionCache();
        if (cache == null) return;

        // Remove any block position that is a boundary block or inside a protected region
        toBlow.removeIf(pos -> {
            if (BigBangRegions.isBoundaryBlock(dimension, pos)) {
                return true;
            }

            List<Region> regions = cache.getRegionsAt(dimension, pos.getX(), pos.getY(), pos.getZ());
            return !regions.isEmpty();
        });
    }
}
