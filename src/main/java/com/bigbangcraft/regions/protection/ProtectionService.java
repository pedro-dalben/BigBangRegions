package com.bigbangcraft.regions.protection;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.flag.EffectiveRegionPolicy;
import com.bigbangcraft.regions.flag.FlagResolver;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.region.RegionResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Optional;
public class ProtectionService {
    private final RegionResolver regionResolver;
    private final PermissionManager permissionManager;
    private final RegionAccessService accessService;

    public ProtectionService(RegionResolver regionResolver, PermissionManager permissionManager,
                             RegionAccessService accessService) {
        this.regionResolver = regionResolver;
        this.permissionManager = permissionManager;
        this.accessService = accessService;
    }

    public ProtectionResult check(ProtectionContext context) {
        String dimension = context.getWorld().dimension().location().toString();
        BlockPos pos = context.getTargetPosition();
        RegionAction action = context.getAction();
        String flagId = action.getFlagId();
        ServerPlayer player = context.getPlayer();

        // 2. Resolve region
        Optional<Region> optRegion = regionResolver.resolveRegionAt(dimension, pos.getX(), pos.getY(), pos.getZ());
        
        // 3. No region -> NO_REGION
        if (optRegion.isEmpty()) {
            return new ProtectionResult(ProtectionDecision.NO_REGION, "No region at position", null, flagId);
        }

        Region region = optRegion.get();

        // 4. Administrative bypass
        if (player != null && permissionManager.hasBypass(player, flagId)) {
            return new ProtectionResult(ProtectionDecision.BYPASS, "ALLOW_REASON_BYPASS", region, flagId);
        }

        // Handle UNKNOWN actor type
        ActorType actorType = context.getActorType();
        if (actorType == ActorType.UNKNOWN && player == null) {
            if (action == RegionAction.BLOCK_BREAK || action == RegionAction.BLOCK_PLACE || action == RegionAction.PVP) {
                return new ProtectionResult(ProtectionDecision.DENY, "Unknown actor performing destructive action", region, flagId);
            }
        }

        // 5 & 6. Evaluate access based on type and roles/flags via RegionAccessService
        java.util.UUID playerUuid = player != null ? player.getUUID() : null;
        return accessService.checkAccess(region, playerUuid, action);
    }

    public boolean canPlayer(ServerPlayer player, BlockPos pos, RegionAction action) {
        if (player == null) return false;
        ProtectionContext context = new ProtectionContext.Builder(action, player.level(), pos)
                .player(player)
                .build();
        return check(context).isAllowed();
    }
}
