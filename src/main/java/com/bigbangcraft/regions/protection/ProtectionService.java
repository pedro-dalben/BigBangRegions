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
    private final FlagResolver flagResolver;
    private final PermissionManager permissionManager;
    private final ConfigManager configManager;

    public ProtectionService(RegionResolver regionResolver, FlagResolver flagResolver,
                             PermissionManager permissionManager, ConfigManager configManager) {
        this.regionResolver = regionResolver;
        this.flagResolver = flagResolver;
        this.permissionManager = permissionManager;
        this.configManager = configManager;
    }

    public ProtectionResult check(ProtectionContext context) {
        String dimension = context.getWorld().dimension().location().toString();
        BlockPos pos = context.getTargetPosition();
        RegionAction action = context.getAction();
        String flagId = action.getFlagId();

        // 1. Check administrative bypass
        ServerPlayer player = context.getPlayer();
        if (player != null && permissionManager.hasBypass(player, flagId)) {
            return new ProtectionResult(ProtectionDecision.BYPASS, "Player has bypass permission", null, flagId);
        }

        // 2. Resolve region
        Optional<Region> optRegion = regionResolver.resolveRegionAt(dimension, pos.getX(), pos.getY(), pos.getZ());
        if (optRegion.isEmpty()) {
            return new ProtectionResult(ProtectionDecision.NO_REGION, "No region at position", null, flagId);
        }

        Region region = optRegion.get();

        // 3. Handle actor rules
        ActorType actorType = context.getActorType();
        if (actorType == ActorType.UNKNOWN) {
            // "UNKNOWN -> negar ações destrutivas dentro de regiões protegidas quando a origem não puder ser validada."
            if (action == RegionAction.BLOCK_BREAK || action == RegionAction.BLOCK_PLACE || action == RegionAction.PVP) {
                return new ProtectionResult(ProtectionDecision.DENY, "Unknown actor performing destructive action", region, flagId);
            }
        }

        // 4. If actor is player (or fake player acting as player), check region membership
        if (player != null && isMemberAction(action)) {
            RegionRole role = region.getRole(player.getUUID());
            if (role.isAtLeast(RegionRole.MEMBER)) {
                return new ProtectionResult(ProtectionDecision.ALLOW, "Player is member of the region", region, flagId);
            }
        }

        // 5. Evaluate the flag policy
        Config config = configManager.getConfig();
        EffectiveRegionPolicy effectivePolicy = flagResolver.resolve(region, flagId, config);

        if (effectivePolicy.isAllowed()) {
            return new ProtectionResult(ProtectionDecision.ALLOW, "Flag policy is ALLOW (" + effectivePolicy.source() + ")", region, flagId);
        } else {
            return new ProtectionResult(ProtectionDecision.DENY, "Flag policy is DENY (" + effectivePolicy.source() + ")", region, flagId);
        }
    }

    public boolean canPlayer(ServerPlayer player, BlockPos pos, RegionAction action) {
        if (player == null) return false;
        ProtectionContext context = new ProtectionContext.Builder(action, player.level(), pos)
                .player(player)
                .build();
        return check(context).isAllowed();
    }

    private boolean isMemberAction(RegionAction action) {
        // PVP applies to everyone (even region members)
        return action != RegionAction.PVP;
    }
}
