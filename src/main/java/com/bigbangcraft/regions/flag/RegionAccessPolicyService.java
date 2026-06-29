package com.bigbangcraft.regions.flag;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.flag.EffectiveRegionPolicy;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.protection.ProtectionContext;
import com.bigbangcraft.regions.protection.RegionAction;
import com.bigbangcraft.regions.region.RegionRoleResolver;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class RegionAccessPolicyService {
    private final PermissionManager permissionManager;
    private final RegionRoleResolver roleResolver;
    private final RegionFlagResolver flagResolver;
    private final ConfigManager configManager;

    public RegionAccessPolicyService(PermissionManager permissionManager, RegionRoleResolver roleResolver,
                                     RegionFlagResolver flagResolver, ConfigManager configManager) {
        this.permissionManager = permissionManager;
        this.roleResolver = roleResolver;
        this.flagResolver = flagResolver;
        this.configManager = configManager;
    }

    public boolean canPerform(ServerPlayer player, Region region, RegionAction action, ProtectionContext context) {
        if (region == null) {
            return false;
        }

        Config config = configManager.getConfig();
        UUID playerUuid = player != null ? player.getUUID() : null;

        if (player != null && permissionManager.hasBypass(player, action.getFlagId())) {
            return true;
        }

        if (region.getType() == RegionType.SYSTEM_REGION || region.getType() == RegionType.ADMIN_REGION) {
            return flagResolver.resolve(region, action.getFlagId(), config).isAllowed();
        }

        RegionRole role = roleResolver.resolveRole(region, playerUuid);
        if (role == RegionRole.VISITOR) {
            return flagResolver.resolve(region, action.getFlagId(), config).isAllowed();
        }

        if (!role.isAtLeast(RegionRole.MEMBER)) {
            return false;
        }

        EffectiveRegionPolicy policy = flagResolver.resolve(region, action.getFlagId(), config);
        return policy.isAllowed();
    }
}
