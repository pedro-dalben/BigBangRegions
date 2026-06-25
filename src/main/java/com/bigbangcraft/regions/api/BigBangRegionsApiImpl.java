package com.bigbangcraft.regions.api;

import com.bigbangcraft.regions.protection.ProtectionContext;
import com.bigbangcraft.regions.protection.ProtectionResult;
import com.bigbangcraft.regions.protection.RegionAction;
import com.bigbangcraft.regions.protection.ProtectionService;
import com.bigbangcraft.regions.region.RegionResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class BigBangRegionsApiImpl implements BigBangRegionsApi {
    private final RegionResolver regionResolver;
    private final ProtectionService protectionService;

    public BigBangRegionsApiImpl(RegionResolver regionResolver, ProtectionService protectionService) {
        this.regionResolver = regionResolver;
        this.protectionService = protectionService;
    }

    @Override
    public Optional<RegionView> getRegionAt(ServerLevel world, BlockPos pos) {
        String dimension = world.dimension().location().toString();
        return regionResolver.resolveRegionAt(dimension, pos.getX(), pos.getY(), pos.getZ())
                .map(RegionView::from);
    }

    @Override
    public ProtectionResult check(ProtectionContext context) {
        return protectionService.check(context);
    }

    @Override
    public boolean canPlayer(ServerPlayer player, BlockPos pos, RegionAction action) {
        return protectionService.canPlayer(player, pos, action);
    }
}
