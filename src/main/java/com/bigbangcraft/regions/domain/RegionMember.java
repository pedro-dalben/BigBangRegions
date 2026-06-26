package com.bigbangcraft.regions.domain;

import java.util.UUID;

public class RegionMember {
    private final UUID uuid;
    private RegionRole role;
    private final UUID addedByUuid;
    private final long createdAt;
    private long updatedAt;

    public RegionMember(UUID uuid, RegionRole role, UUID addedByUuid, long createdAt, long updatedAt) {
        this.uuid = uuid;
        this.role = role;
        this.addedByUuid = addedByUuid;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getUuid() {
        return uuid;
    }

    public RegionRole getRole() {
        return role;
    }

    public void setRole(RegionRole role) {
        this.role = role;
        this.updatedAt = System.currentTimeMillis();
    }

    public UUID getAddedByUuid() {
        return addedByUuid;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
