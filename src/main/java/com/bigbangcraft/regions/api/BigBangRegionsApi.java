package com.bigbangcraft.regions.api;

import com.bigbangcraft.regions.protection.ProtectionContext;
import com.bigbangcraft.regions.protection.ProtectionResult;
import com.bigbangcraft.regions.protection.RegionAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public interface BigBangRegionsApi {
    Optional<RegionView> getRegionAt(ServerLevel world, BlockPos pos);

    ProtectionResult check(ProtectionContext context);

    boolean canPlayer(ServerPlayer player, BlockPos pos, RegionAction action);
}
