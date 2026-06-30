package com.bigbangcraft.regions.journeymap;

import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.region.RegionRoleResolver;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class RegionVisibilityResolver {
    private final ConfigManager configManager;
    private final RegionRoleResolver roleResolver;
    private final RegionMembershipCache membershipCache;
    private final PermissionManager permissionManager;

    public RegionVisibilityResolver(ConfigManager configManager, RegionRoleResolver roleResolver,
                                    RegionMembershipCache membershipCache, PermissionManager permissionManager) {
        this.configManager = configManager;
        this.roleResolver = roleResolver;
        this.membershipCache = membershipCache;
        this.permissionManager = permissionManager;
    }

    public boolean canSeeRegion(ServerPlayer player, Region region) {
        Config.JourneyMapConfig jmConfig = configManager.getConfig().getJourneyMap();
        if (!jmConfig.isEnabled()) return false;

        UUID playerUuid = player.getUUID();

        if (region.getType() == RegionType.PLAYER_REGION) {
            return canSeePlayerRegion(player, region, playerUuid, jmConfig);
        }

        if (region.getType() == RegionType.ADMIN_REGION) {
            return canSeeAdminRegion(player, jmConfig);
        }

        return false;
    }

    public List<Region> getVisibleRegions(ServerPlayer player, Collection<Region> allRegions) {
        List<Region> result = new ArrayList<>();
        for (Region region : allRegions) {
            if (canSeeRegion(player, region)) {
                result.add(region);
            }
        }
        return result;
    }

    private boolean canSeePlayerRegion(ServerPlayer player, Region region, UUID playerUuid,
                                       Config.JourneyMapConfig jmConfig) {
        RegionRole role = roleResolver.resolveRole(region, playerUuid);

        if (role == RegionRole.OWNER || role == RegionRole.LEADER
            || role == RegionRole.MANAGER || role == RegionRole.MEMBER) {
            return PlayerMapPreference.isShowOwnRegion(playerUuid);
        }

        if (permissionManager.hasPermission(player, "bigbangregions.journeymap.view-all")) {
            return true;
        }

        String visibility = region.getFlagValue("map-visibility");
        if ("public".equalsIgnoreCase(visibility) && jmConfig.getPublicRegions().isShowOnMap()) {
            return permissionManager.hasPermission(player, "bigbangregions.journeymap.view-public");
        }

        return false;
    }

    private boolean canSeeAdminRegion(ServerPlayer player, Config.JourneyMapConfig jmConfig) {
        Config.JourneyMapConfig.AdminRegionVisibility visibility = jmConfig.getAdminRegionVisibility();

        return switch (visibility) {
            case PUBLIC -> true;
            case STAFF_ONLY -> permissionManager.hasPermission(player, "bigbangregions.journeymap.view-admin");
            case HIDDEN -> false;
            case PERMISSION -> permissionManager.hasPermission(player, "bigbangregions.journeymap.view-admin");
        };
    }
}
