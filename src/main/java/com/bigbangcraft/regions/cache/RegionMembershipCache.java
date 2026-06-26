package com.bigbangcraft.regions.cache;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegionMembershipCache {
    private final Map<String, Map<UUID, RegionRole>> cache = new ConcurrentHashMap<>();

    public void loadFromRegion(Region region) {
        if (region == null) return;
        Map<UUID, RegionRole> memberRoles = new ConcurrentHashMap<>();
        for (RegionMember member : region.getMembers().values()) {
            memberRoles.put(member.getUuid(), member.getRole());
        }
        cache.put(region.getId().toLowerCase(), memberRoles);
    }

    public void updateMember(String regionId, UUID memberUuid, RegionRole role) {
        if (regionId == null || memberUuid == null) return;
        String idKey = regionId.toLowerCase();
        Map<UUID, RegionRole> memberRoles = cache.computeIfAbsent(idKey, k -> new ConcurrentHashMap<>());
        if (role == null || role == RegionRole.VISITOR) {
            memberRoles.remove(memberUuid);
        } else {
            memberRoles.put(memberUuid, role);
        }
    }

    public RegionRole getRole(String regionId, UUID memberUuid, UUID ownerUuid) {
        if (regionId == null || memberUuid == null) {
            return RegionRole.VISITOR;
        }
        if (memberUuid.equals(ownerUuid)) {
            return RegionRole.OWNER;
        }
        Map<UUID, RegionRole> memberRoles = cache.get(regionId.toLowerCase());
        if (memberRoles != null) {
            RegionRole role = memberRoles.get(memberUuid);
            if (role != null) {
                return role;
            }
        }
        return RegionRole.VISITOR;
    }

    public void removeRegion(String regionId) {
        if (regionId == null) return;
        cache.remove(regionId.toLowerCase());
    }

    public void clear() {
        cache.clear();
    }
}
