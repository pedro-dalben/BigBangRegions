package com.bigbangcraft.regions.api;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record RegionView(
    String id,
    String name,
    RegionType type,
    String dimensionKey,
    int minX, int minY, int minZ,
    int maxX, int maxY, int maxZ,
    int priority,
    UUID ownerUuid,
    Map<UUID, String> members,
    Map<String, String> flags,
    long createdAt,
    long updatedAt,
    String status
) {
    public static RegionView from(Region region) {
        if (region == null) return null;
        Map<UUID, String> memberRoles = new HashMap<>();
        region.getMembers().forEach((uuid, role) -> memberRoles.put(uuid, role.name()));
        return new RegionView(
            region.getId(),
            region.getName(),
            region.getType(),
            region.getBounds().getDimension(),
            region.getBounds().getMinX(),
            region.getBounds().getMinY(),
            region.getBounds().getMinZ(),
            region.getBounds().getMaxX(),
            region.getBounds().getMaxY(),
            region.getBounds().getMaxZ(),
            region.getPriority(),
            region.getOwnerUuid(),
            Map.copyOf(memberRoles),
            Map.copyOf(region.getFlags()),
            region.getCreatedAt(),
            region.getUpdatedAt(),
            region.getStatus()
        );
    }
}
