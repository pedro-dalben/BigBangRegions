package com.bigbangcraft.regions.domain;

import java.util.*;

public class Region {
    private final String id;
    private final String name;
    private final RegionType type;
    private RegionBounds bounds;
    private int priority;
    private UUID ownerUuid;
    private final UUID createdByUuid;
    private final long createdAt;
    private long updatedAt;
    private String status;

    private final Map<UUID, RegionMember> members;
    private final Map<String, String> flags = new HashMap<>();

    public Region(String id, String name, RegionType type, RegionBounds bounds, int priority,
                  UUID ownerUuid, UUID createdByUuid, long createdAt, long updatedAt, String status) {
        this(id, name, type, bounds, priority, ownerUuid, createdByUuid, createdAt, updatedAt, status, Collections.emptyMap());
    }

    public Region(String id, String name, RegionType type, RegionBounds bounds, int priority,
                  UUID ownerUuid, UUID createdByUuid, long createdAt, long updatedAt, String status,
                  Map<UUID, RegionMember> members) {
        if (type == RegionType.PLAYER_REGION && ownerUuid == null) {
            throw new IllegalArgumentException("PLAYER_REGION must have a valid owner UUID");
        }
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.bounds = Objects.requireNonNull(bounds, "Bounds cannot be null");
        this.priority = priority;
        this.ownerUuid = ownerUuid;
        this.createdByUuid = Objects.requireNonNull(createdByUuid, "CreatedByUuid cannot be null");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.members = members != null ? Collections.unmodifiableMap(new HashMap<>(members)) : Collections.emptyMap();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RegionType getType() {
        return type;
    }

    public RegionBounds getBounds() {
        return bounds;
    }

    public void setBounds(RegionBounds newBounds) {
        Objects.requireNonNull(newBounds, "Bounds cannot be null");
        this.bounds = newBounds;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
        this.updatedAt = System.currentTimeMillis();
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        this.updatedAt = System.currentTimeMillis();
    }

    public UUID getCreatedByUuid() {
        return createdByUuid;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = System.currentTimeMillis();
    }

    public Map<UUID, RegionMember> getMembers() {
        return members;
    }

    public RegionRole getRole(UUID uuid) {
        if (uuid == null) {
            return RegionRole.VISITOR;
        }
        if (uuid.equals(ownerUuid)) {
            return RegionRole.OWNER;
        }
        RegionMember m = members.get(uuid);
        return m != null ? m.getRole() : RegionRole.VISITOR;
    }

    public Map<String, String> getFlags() {
        return Collections.unmodifiableMap(flags);
    }

    public void setFlag(String flagId, String value) {
        if (value == null || value.equalsIgnoreCase("INHERIT")) {
            flags.remove(flagId);
        } else {
            flags.put(flagId, value.toUpperCase());
        }
        this.updatedAt = System.currentTimeMillis();
    }

    public String getFlagValue(String flagId) {
        return flags.getOrDefault(flagId, "INHERIT");
    }

    public boolean contains(String dimension, int x, int y, int z) {
        return bounds.contains(dimension, x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Region region = (Region) o;
        return id.equals(region.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Region{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", bounds=" + bounds +
                ", priority=" + priority +
                ", ownerUuid=" + ownerUuid +
                '}';
    }
}
