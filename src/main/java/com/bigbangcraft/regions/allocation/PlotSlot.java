package com.bigbangcraft.regions.allocation;

import java.util.UUID;

public class PlotSlot {
    private final String id;
    private final String dimensionKey;
    private final int gridX;
    private final int gridZ;
    private final int minX;
    private final int minZ;
    private final int slotSize;
    private PlotSlotState state;
    private UUID reservedForUuid;
    private String regionId;
    private String biomeOptionKey;
    private Long reservedAt;
    private Long leaseExpiresAt;
    private Long allocatedAt;
    private final long createdAt;
    private long updatedAt;

    public PlotSlot(String id, String dimensionKey, int gridX, int gridZ, int minX, int minZ, int slotSize,
                    PlotSlotState state, UUID reservedForUuid, String regionId, String biomeOptionKey,
                    Long reservedAt, Long leaseExpiresAt, Long allocatedAt, long createdAt, long updatedAt) {
        this.id = id;
        this.dimensionKey = dimensionKey;
        this.gridX = gridX;
        this.gridZ = gridZ;
        this.minX = minX;
        this.minZ = minZ;
        this.slotSize = slotSize;
        this.state = state;
        this.reservedForUuid = reservedForUuid;
        this.regionId = regionId;
        this.biomeOptionKey = biomeOptionKey;
        this.reservedAt = reservedAt;
        this.leaseExpiresAt = leaseExpiresAt;
        this.allocatedAt = allocatedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getDimensionKey() { return dimensionKey; }
    public int getGridX() { return gridX; }
    public int getGridZ() { return gridZ; }
    public int getMinX() { return minX; }
    public int getMinZ() { return minZ; }
    public int getSlotSize() { return slotSize; }
    public PlotSlotState getState() { return state; }
    public UUID getReservedForUuid() { return reservedForUuid; }
    public String getRegionId() { return regionId; }
    public String getBiomeOptionKey() { return biomeOptionKey; }
    public Long getReservedAt() { return reservedAt; }
    public Long getLeaseExpiresAt() { return leaseExpiresAt; }
    public Long getAllocatedAt() { return allocatedAt; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }

    public int getMaxX() { return minX + slotSize - 1; }
    public int getMaxZ() { return minZ + slotSize - 1; }

    public void reserve(UUID playerUuid, String biomeOptionKey, long leaseDurationMs) {
        this.state = PlotSlotState.RESERVED;
        this.reservedForUuid = playerUuid;
        this.biomeOptionKey = biomeOptionKey;
        this.reservedAt = System.currentTimeMillis();
        this.leaseExpiresAt = this.reservedAt + leaseDurationMs;
        this.updatedAt = this.reservedAt;
    }

    public void allocate(String regionId) {
        this.state = PlotSlotState.ALLOCATED;
        this.regionId = regionId;
        this.allocatedAt = System.currentTimeMillis();
        this.updatedAt = this.allocatedAt;
    }

    public void release() {
        this.state = PlotSlotState.RELEASED;
        this.reservedForUuid = null;
        this.regionId = null;
        this.biomeOptionKey = null;
        this.reservedAt = null;
        this.leaseExpiresAt = null;
        this.allocatedAt = null;
        this.updatedAt = System.currentTimeMillis();
    }
}
