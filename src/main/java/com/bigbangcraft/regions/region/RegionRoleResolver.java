package com.bigbangcraft.regions.region;

import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionRole;

import java.util.UUID;

public class RegionRoleResolver {
    private final RegionMembershipCache membershipCache;

    public RegionRoleResolver(RegionMembershipCache membershipCache) {
        this.membershipCache = membershipCache;
    }

    public RegionRole resolveRole(Region region, UUID playerUuid) {
        if (region == null || playerUuid == null) {
            return RegionRole.VISITOR;
        }
        return membershipCache.getRole(region.getId(), playerUuid, region.getOwnerUuid());
    }
}
