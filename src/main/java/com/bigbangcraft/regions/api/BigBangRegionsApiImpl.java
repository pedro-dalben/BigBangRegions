package com.bigbangcraft.regions.api;

import com.bigbangcraft.regions.protection.ProtectionContext;
import com.bigbangcraft.regions.protection.ProtectionResult;
import com.bigbangcraft.regions.protection.RegionAction;
import com.bigbangcraft.regions.protection.ProtectionService;
import com.bigbangcraft.regions.region.RegionContainmentService;
import com.bigbangcraft.regions.region.RegionResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public class BigBangRegionsApiImpl implements BigBangRegionsApi {
    private final RegionResolver regionResolver;
    private final ProtectionService protectionService;
    private final RegionContainmentService containmentService;

    public BigBangRegionsApiImpl(RegionResolver regionResolver, ProtectionService protectionService) {
        this(regionResolver, protectionService, null);
    }

    public BigBangRegionsApiImpl(RegionResolver regionResolver, ProtectionService protectionService,
                                 RegionContainmentService containmentService) {
        this.regionResolver = regionResolver;
        this.protectionService = protectionService;
        this.containmentService = containmentService;
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

    @Override
    public boolean canCreatePlayerWarp(UUID creatorUuid, String dimension, int x, int y, int z) {
        return containmentService != null && containmentService.canCreatePlayerWarp(creatorUuid, dimension, x, y, z);
    }

    @Override
    public boolean canUsePlayerWarp(UUID warpOwnerUuid, String dimension, int x, int y, int z) {
        return containmentService != null && containmentService.canUsePlayerWarp(warpOwnerUuid, dimension, x, y, z);
    }

    @Override
    public void recordPlayerWarpArrival(UUID playerUuid, UUID warpOwnerUuid, String dimension, int x, int y, int z) {
        if (containmentService != null) {
            containmentService.recordPlayerWarpArrival(playerUuid, warpOwnerUuid, dimension, x, y, z);
        }
    }
}
