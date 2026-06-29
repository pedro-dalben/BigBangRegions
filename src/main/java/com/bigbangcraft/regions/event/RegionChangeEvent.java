package com.bigbangcraft.regions.event;

import com.bigbangcraft.regions.domain.Region;

import java.util.UUID;

public class RegionChangeEvent {
    public enum ChangeType {
        CREATED,
        UPDATED,
        DELETED,
        RESIZED,
        RENAMED,
        STATUS_CHANGED,
        MEMBER_JOINED,
        MEMBER_REMOVED,
        ROLE_CHANGED,
        OWNER_TRANSFERRED
    }

    private final ChangeType type;
    private final Region region;
    private final UUID affectedPlayer;

    public RegionChangeEvent(ChangeType type, Region region) {
        this(type, region, null);
    }

    public RegionChangeEvent(ChangeType type, Region region, UUID affectedPlayer) {
        this.type = type;
        this.region = region;
        this.affectedPlayer = affectedPlayer;
    }

    public ChangeType getType() { return type; }
    public Region getRegion() { return region; }
    public UUID getAffectedPlayer() { return affectedPlayer; }
}
