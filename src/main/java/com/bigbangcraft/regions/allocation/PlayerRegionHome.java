package com.bigbangcraft.regions.allocation;

public class PlayerRegionHome {
    private final String regionId;
    private final String dimensionKey;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final long createdAt;
    private long updatedAt;

    public PlayerRegionHome(String regionId, String dimensionKey, double x, double y, double z,
                            float yaw, float pitch, long createdAt, long updatedAt) {
        this.regionId = regionId;
        this.dimensionKey = dimensionKey;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getRegionId() { return regionId; }
    public String getDimensionKey() { return dimensionKey; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
