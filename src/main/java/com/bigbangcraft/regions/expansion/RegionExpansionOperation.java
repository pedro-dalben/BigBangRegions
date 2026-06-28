package com.bigbangcraft.regions.expansion;

import java.util.UUID;

public class RegionExpansionOperation {
    private final String operationId;
    private final String regionId;
    private final UUID ownerUuid;
    private final String plotSlotId;
    private final String dimensionKey;

    private int currentSize;
    private int targetSize;

    private int oldMinX;
    private int oldMinZ;
    private int oldMaxX;
    private int oldMaxZ;

    private int targetMinX;
    private int targetMinZ;
    private int targetMaxX;
    private int targetMaxZ;

    private long priceGems;
    private int pricingPolicyVersion;

    private RegionExpansionState state;

    private String gemsReservationId;

    private String reserveIdempotencyKey;
    private String renewIdempotencyKey;
    private long renewSequence;
    private String captureIdempotencyKey;
    private String releaseIdempotencyKey;

    private Long reservationLeaseExpiresAt;
    private int retryCount;
    private Long nextRetryAt;

    private final long requestedAt;
    private long updatedAt;
    private Long resizeAppliedAt;
    private Long paymentCapturedAt;

    private String failureCode;
    private String failureDetail;

    public RegionExpansionOperation(String operationId, String regionId, UUID ownerUuid,
                                    String plotSlotId, String dimensionKey,
                                    int currentSize, int targetSize,
                                    int oldMinX, int oldMinZ, int oldMaxX, int oldMaxZ,
                                    int targetMinX, int targetMinZ, int targetMaxX, int targetMaxZ,
                                    long priceGems, int pricingPolicyVersion,
                                    RegionExpansionState state,
                                    long requestedAt) {
        this.operationId = operationId;
        this.regionId = regionId;
        this.ownerUuid = ownerUuid;
        this.plotSlotId = plotSlotId;
        this.dimensionKey = dimensionKey;
        this.currentSize = currentSize;
        this.targetSize = targetSize;
        this.oldMinX = oldMinX;
        this.oldMinZ = oldMinZ;
        this.oldMaxX = oldMaxX;
        this.oldMaxZ = oldMaxZ;
        this.targetMinX = targetMinX;
        this.targetMinZ = targetMinZ;
        this.targetMaxX = targetMaxX;
        this.targetMaxZ = targetMaxZ;
        this.priceGems = priceGems;
        this.pricingPolicyVersion = pricingPolicyVersion;
        this.state = state;
        this.requestedAt = requestedAt;
        this.updatedAt = requestedAt;
    }

    public String getOperationId() { return operationId; }
    public String getRegionId() { return regionId; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getPlotSlotId() { return plotSlotId; }
    public String getDimensionKey() { return dimensionKey; }

    public int getCurrentSize() { return currentSize; }
    public int getTargetSize() { return targetSize; }

    public int getOldMinX() { return oldMinX; }
    public int getOldMinZ() { return oldMinZ; }
    public int getOldMaxX() { return oldMaxX; }
    public int getOldMaxZ() { return oldMaxZ; }

    public int getTargetMinX() { return targetMinX; }
    public int getTargetMinZ() { return targetMinZ; }
    public int getTargetMaxX() { return targetMaxX; }
    public int getTargetMaxZ() { return targetMaxZ; }

    public long getPriceGems() { return priceGems; }
    public int getPricingPolicyVersion() { return pricingPolicyVersion; }

    public RegionExpansionState getState() { return state; }

    public String getGemsReservationId() { return gemsReservationId; }

    public String getReserveIdempotencyKey() { return reserveIdempotencyKey; }
    public String getRenewIdempotencyKey() { return renewIdempotencyKey; }
    public long getRenewSequence() { return renewSequence; }
    public String getCaptureIdempotencyKey() { return captureIdempotencyKey; }
    public String getReleaseIdempotencyKey() { return releaseIdempotencyKey; }

    public Long getReservationLeaseExpiresAt() { return reservationLeaseExpiresAt; }
    public int getRetryCount() { return retryCount; }
    public Long getNextRetryAt() { return nextRetryAt; }

    public long getRequestedAt() { return requestedAt; }
    public long getUpdatedAt() { return updatedAt; }
    public Long getResizeAppliedAt() { return resizeAppliedAt; }
    public Long getPaymentCapturedAt() { return paymentCapturedAt; }

    public String getFailureCode() { return failureCode; }
    public String getFailureDetail() { return failureDetail; }

    public void setGemsReservationId(String gemsReservationId) {
        this.gemsReservationId = gemsReservationId;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setReserveIdempotencyKey(String key) {
        this.reserveIdempotencyKey = key;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setRenewIdempotencyKey(String key) {
        this.renewIdempotencyKey = key;
        this.updatedAt = System.currentTimeMillis();
    }

    public void incrementRenewSequence() {
        this.renewSequence++;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setCaptureIdempotencyKey(String key) {
        this.captureIdempotencyKey = key;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setReleaseIdempotencyKey(String key) {
        this.releaseIdempotencyKey = key;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setReservationLeaseExpiresAt(Long expiresAt) {
        this.reservationLeaseExpiresAt = expiresAt;
        this.updatedAt = System.currentTimeMillis();
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setNextRetryAt(Long nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setResizeAppliedAt(Long resizeAppliedAt) {
        this.resizeAppliedAt = resizeAppliedAt;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setPaymentCapturedAt(Long paymentCapturedAt) {
        this.paymentCapturedAt = paymentCapturedAt;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setFailureDetail(String failureDetail) {
        this.failureDetail = failureDetail;
        this.updatedAt = System.currentTimeMillis();
    }

    public void transitionTo(RegionExpansionState nextState) {
        if (!state.canTransitionTo(nextState)) {
            throw new IllegalStateException("Cannot transition expansion state from " + state + " to " + nextState);
        }
        this.state = nextState;
        this.updatedAt = System.currentTimeMillis();
        if (nextState == RegionExpansionState.COMPLETED) {
            this.paymentCapturedAt = this.updatedAt;
        }
    }

    public void forceTransitionTo(RegionExpansionState nextState) {
        this.state = nextState;
        this.updatedAt = System.currentTimeMillis();
        if (nextState == RegionExpansionState.COMPLETED) {
            this.paymentCapturedAt = this.updatedAt;
        }
    }

    public boolean isReservationExpired() {
        return reservationLeaseExpiresAt != null && System.currentTimeMillis() > reservationLeaseExpiresAt;
    }

    public boolean shouldRetryNow() {
        return nextRetryAt != null && System.currentTimeMillis() >= nextRetryAt;
    }
}
