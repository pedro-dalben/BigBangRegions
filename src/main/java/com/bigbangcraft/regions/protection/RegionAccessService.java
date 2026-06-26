package com.bigbangcraft.regions.protection;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.flag.EffectiveRegionPolicy;
import com.bigbangcraft.regions.flag.FlagResolver;
import com.bigbangcraft.regions.region.RegionRoleResolver;

import java.util.UUID;

public class RegionAccessService {
    private final RegionRoleResolver roleResolver;
    private final FlagResolver flagResolver;
    private final ConfigManager configManager;

    public RegionAccessService(RegionRoleResolver roleResolver, FlagResolver flagResolver, ConfigManager configManager) {
        this.roleResolver = roleResolver;
        this.flagResolver = flagResolver;
        this.configManager = configManager;
    }

    public ProtectionResult checkAccess(Region region, UUID playerUuid, RegionAction action) {
        String flagId = action.getFlagId();
        Config config = configManager.getConfig();

        if (region.getType() == RegionType.SYSTEM_REGION || region.getType() == RegionType.ADMIN_REGION) {
            EffectiveRegionPolicy effectivePolicy = flagResolver.resolve(region, flagId, config);
            if (effectivePolicy.isAllowed()) {
                return new ProtectionResult(ProtectionDecision.ALLOW, "ALLOW_REASON_REGION_FLAG", region, flagId);
            } else {
                return new ProtectionResult(ProtectionDecision.DENY, "DENY_REASON_ADMIN_REGION", region, flagId);
            }
        }

        // PLAYER_REGION
        RegionRole role = roleResolver.resolveRole(region, playerUuid);
        boolean roleAllowed = RegionRolePolicy.isAllowed(role, action);
        if (!roleAllowed) {
            return new ProtectionResult(ProtectionDecision.DENY, "DENY_REASON_VISITOR_ROLE", region, flagId);
        }

        EffectiveRegionPolicy effectivePolicy = flagResolver.resolve(region, flagId, config);
        if (!effectivePolicy.isAllowed()) {
            return new ProtectionResult(ProtectionDecision.DENY, "DENY_REASON_REGION_FLAG", region, flagId);
        }

        return new ProtectionResult(ProtectionDecision.ALLOW, "ALLOW_REASON_" + role.name(), region, flagId);
    }
}
