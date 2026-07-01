package com.bigbangcraft.regions.allocation;

import java.util.UUID;

public class PlotSlot {
    private final String id;
    private final String dimensionKey;
    private final int gridX;
    private final int gridZ;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final int slotSize;
    private PlotSlotState state;
    private UUID reservedForUuid;
    private String regionId;
    private String biomeOptionKey;
    private String allocationRequestId;
    private Long reservedAt;
    private Long leaseExpiresAt;
    private Long allocatedAt;
    private Long consumedAt;
    private Long invalidatedAt;
    private String invalidationReason;
    private int validationSchemaVersion;
    private Long validatedAt;
    private final long createdAt;
    private long updatedAt;

    public PlotSlot(String id, String dimensionKey, int gridX, int gridZ, int minX, int minZ, int slotSize,
                    PlotSlotState state, UUID reservedForUuid, String regionId, String biomeOptionKey,
                    Long reservedAt, Long leaseExpiresAt, Long allocatedAt, long createdAt, long updatedAt) {
        this(id, dimensionKey, gridX, gridZ, minX, minZ, minX + slotSize - 1, minZ + slotSize - 1, slotSize,
            state, reservedForUuid, regionId, biomeOptionKey, null, reservedAt, leaseExpiresAt,
            allocatedAt, null, null, null, 0, null, createdAt, updatedAt);
    }

    public PlotSlot(String id, String dimensionKey, int gridX, int gridZ, int minX, int minZ, int maxX, int maxZ,
                    int slotSize, PlotSlotState state, UUID reservedForUuid, String regionId, String biomeOptionKey,
                    String allocationRequestId, Long reservedAt, Long leaseExpiresAt, Long allocatedAt,
                    Long consumedAt, Long invalidatedAt, String invalidationReason,
                    int validationSchemaVersion, Long validatedAt, long createdAt, long updatedAt) {
        this.id = id;
        this.dimensionKey = dimensionKey;
        this.gridX = gridX;
        this.gridZ = gridZ;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.slotSize = slotSize;
        this.state = state;
        this.reservedForUuid = reservedForUuid;
        this.regionId = regionId;
        this.biomeOptionKey = biomeOptionKey;
        this.allocationRequestId = allocationRequestId;
        this.reservedAt = reservedAt;
        this.leaseExpiresAt = leaseExpiresAt;
        this.allocatedAt = allocatedAt;
        this.consumedAt = consumedAt;
        this.invalidatedAt = invalidatedAt;
        this.invalidationReason = invalidationReason;
        this.validationSchemaVersion = validationSchemaVersion;
        this.validatedAt = validatedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getDimensionKey() { return dimensionKey; }
    public int getGridX() { return gridX; }
    public int getGridZ() { return gridZ; }
    public int getMinX() { return minX; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxZ() { return maxZ; }
    public int getSlotSize() { return slotSize; }
    public PlotSlotState getState() { return state; }
    public UUID getReservedForUuid() { return reservedForUuid; }
    public String getRegionId() { return regionId; }
    public String getBiomeOptionKey() { return biomeOptionKey; }
    public String getAllocationRequestId() { return allocationRequestId; }
    public Long getReservedAt() { return reservedAt; }
    public Long getLeaseExpiresAt() { return leaseExpiresAt; }
    public Long getAllocatedAt() { return allocatedAt; }
    public Long getConsumedAt() { return consumedAt; }
    public Long getInvalidatedAt() { return invalidatedAt; }
    public String getInvalidationReason() { return invalidationReason; }
    public int getValidationSchemaVersion() { return validationSchemaVersion; }
    public Long getValidatedAt() { return validatedAt; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }

    public void setState(PlotSlotState state) { this.state = state; this.updatedAt = System.currentTimeMillis(); }

    public void reserve(UUID playerUuid, String biomeOptionKey, long leaseDurationMs) {
        if (state == PlotSlotState.RESERVED) {
            throw new IllegalStateException("Slot " + id + " is already reserved");
        }
        if (state == PlotSlotState.ALLOCATED || state == PlotSlotState.OCCUPIED) {
            throw new IllegalStateException("Slot " + id + " is already allocated/occupied");
        }
        if (state == PlotSlotState.RETIRED) {
            throw new IllegalStateException("Slot " + id + " is retired and cannot be reserved");
        }
        this.state = PlotSlotState.RESERVED;
        this.reservedForUuid = playerUuid;
        this.biomeOptionKey = biomeOptionKey;
        this.reservedAt = System.currentTimeMillis();
        this.leaseExpiresAt = this.reservedAt + leaseDurationMs;
        this.updatedAt = this.reservedAt;
    }

    public void allocate(String regionId) {
        if (state != PlotSlotState.RESERVED) {
            throw new IllegalStateException("Slot " + id + " must be RESERVED before allocation, current state: " + state);
        }
        this.state = PlotSlotState.ALLOCATED;
        this.regionId = regionId;
        this.allocatedAt = System.currentTimeMillis();
        this.updatedAt = this.allocatedAt;
    }

    public void occupy() {
        if (state != PlotSlotState.ALLOCATED && state != PlotSlotState.RESERVED) {
            throw new IllegalStateException("Slot " + id + " must be ALLOCATED or RESERVED before occupation, current state: " + state);
        }
        this.state = PlotSlotState.OCCUPIED;
        this.updatedAt = System.currentTimeMillis();
    }

    public void release() {
        if (state != PlotSlotState.RESERVED && state != PlotSlotState.ALLOCATED) {
            throw new IllegalStateException("Slot " + id + " must be RESERVED or ALLOCATED to release, current state: " + state);
        }
        this.state = PlotSlotState.RELEASED;
        this.reservedForUuid = null;
        this.regionId = null;
        this.biomeOptionKey = null;
        this.reservedAt = null;
        this.leaseExpiresAt = null;
        this.allocatedAt = null;
        this.updatedAt = System.currentTimeMillis();
    }

    public void retire() {
        if (state != PlotSlotState.ALLOCATED && state != PlotSlotState.OCCUPIED) {
            throw new IllegalStateException("Slot " + id + " must be ALLOCATED or OCCUPIED to retire, current state: " + state);
        }
        this.state = PlotSlotState.RETIRED;
        this.updatedAt = System.currentTimeMillis();
    }

    public void recycle() {
        if (state != PlotSlotState.RETIRED) {
            throw new IllegalStateException("Slot " + id + " must be RETIRED to recycle, current state: " + state);
        }
        this.state = PlotSlotState.RELEASED;
        this.reservedForUuid = null;
        this.regionId = null;
        this.biomeOptionKey = null;
        this.reservedAt = null;
        this.leaseExpiresAt = null;
        this.allocatedAt = null;
        this.updatedAt = System.currentTimeMillis();
    }

    public void forceRelease() {
        if (state == PlotSlotState.RELEASED) {
            return;
        }
        this.state = PlotSlotState.RELEASED;
        this.reservedForUuid = null;
        this.regionId = null;
        this.biomeOptionKey = null;
        this.reservedAt = null;
        this.leaseExpiresAt = null;
        this.allocatedAt = null;
        this.updatedAt = System.currentTimeMillis();
    }

    public void markAvailable(int validationSchemaVersion) {
        if (state == PlotSlotState.AVAILABLE) return;
        this.state = PlotSlotState.AVAILABLE;
        this.reservedForUuid = null;
        this.regionId = null;
        this.allocationRequestId = null;
        this.reservedAt = null;
        this.leaseExpiresAt = null;
        this.allocatedAt = null;
        this.consumedAt = null;
        this.invalidatedAt = null;
        this.invalidationReason = null;
        this.validationSchemaVersion = validationSchemaVersion;
        this.validatedAt = System.currentTimeMillis();
        this.updatedAt = this.validatedAt;
    }

    public void markStale(String reason) {
        this.state = PlotSlotState.STALE;
        this.invalidatedAt = System.currentTimeMillis();
        this.invalidationReason = reason;
        this.updatedAt = this.invalidatedAt;
    }

    public void markPlayerReserved(UUID playerUuid, String biomeOptionKey, String allocationRequestId) {
        if (state != PlotSlotState.AVAILABLE) {
            throw new IllegalStateException("Slot " + id + " must be AVAILABLE to reserve for player, current state: " + state);
        }
        this.state = PlotSlotState.PLAYER_RESERVED;
        this.reservedForUuid = playerUuid;
        this.biomeOptionKey = biomeOptionKey;
        this.allocationRequestId = allocationRequestId;
        this.reservedAt = System.currentTimeMillis();
        this.updatedAt = this.reservedAt;
    }

    public void markPreparing() {
        if (state != PlotSlotState.PLAYER_RESERVED) {
            throw new IllegalStateException("Slot " + id + " must be PLAYER_RESERVED to prepare, current state: " + state);
        }
        this.state = PlotSlotState.PREPARING;
        this.updatedAt = System.currentTimeMillis();
    }

    public void markConsumed() {
        if (state != PlotSlotState.PREPARING) {
            throw new IllegalStateException("Slot " + id + " must be PREPARING to consume, current state: " + state);
        }
        this.state = PlotSlotState.CONSUMED;
        this.consumedAt = System.currentTimeMillis();
        this.updatedAt = this.consumedAt;
    }

    public void markInvalidated(String reason) {
        this.state = PlotSlotState.INVALIDATED;
        this.invalidatedAt = System.currentTimeMillis();
        this.invalidationReason = reason;
        this.updatedAt = this.invalidatedAt;
    }

    public void markFailed(String reason) {
        this.state = PlotSlotState.FAILED;
        this.invalidationReason = reason;
        this.invalidatedAt = System.currentTimeMillis();
        this.updatedAt = this.invalidatedAt;
    }
}
