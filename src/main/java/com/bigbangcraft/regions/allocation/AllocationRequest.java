package com.bigbangcraft.regions.allocation;

import java.util.UUID;

public class AllocationRequest {
    private final String id;
    private final UUID ownerUuid;
    private final String requestedBiomeOption;
    private final String targetDimension;
    private AllocationRequestState state;
    private final String source;
    private final UUID requestedByUuid;
    private String regionId;
    private String failureReason;
    private int attempts;
    private final long createdAt;
    private long updatedAt;
    private Long completedAt;
    private Long cancelledAt;
    
    // Payment fields
    private long priceGems;
    private boolean paymentRequired;
    private String gemsReservationId;
    private String reserveIdempotencyKey;
    private String renewIdempotencyKey;
    private long renewSequence;
    private String captureIdempotencyKey;
    private String releaseIdempotencyKey;
    private Long reservationLeaseExpiresAt;
    private Long paymentCapturedAt;
    private int retryCount;
    private Long nextRetryAt;

    public AllocationRequest(String id, UUID ownerUuid, String requestedBiomeOption, String targetDimension,
                             AllocationRequestState state, String source, UUID requestedByUuid, String regionId,
                             String failureReason, int attempts, long createdAt, long updatedAt,
                             Long completedAt, Long cancelledAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.requestedBiomeOption = requestedBiomeOption;
        this.targetDimension = targetDimension;
        this.state = state;
        this.source = source;
        this.requestedByUuid = requestedByUuid;
        this.regionId = regionId;
        this.failureReason = failureReason;
        this.attempts = attempts;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
        this.cancelledAt = cancelledAt;
    }

    public String getId() { return id; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getRequestedBiomeOption() { return requestedBiomeOption; }
    public String getTargetDimension() { return targetDimension; }
    public AllocationRequestState getState() { return state; }
    public String getSource() { return source; }
    public UUID getRequestedByUuid() { return requestedByUuid; }
    public String getRegionId() { return regionId; }
    public String getFailureReason() { return failureReason; }
    public int getAttempts() { return attempts; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public Long getCompletedAt() { return completedAt; }
    public Long getCancelledAt() { return cancelledAt; }
    
    // Payment getters
    public long getPriceGems() { return priceGems; }
    public boolean isPaymentRequired() { return paymentRequired; }
    public String getGemsReservationId() { return gemsReservationId; }
    public String getReserveIdempotencyKey() { return reserveIdempotencyKey; }
    public String getRenewIdempotencyKey() { return renewIdempotencyKey; }
    public long getRenewSequence() { return renewSequence; }
    public String getCaptureIdempotencyKey() { return captureIdempotencyKey; }
    public String getReleaseIdempotencyKey() { return releaseIdempotencyKey; }
    public Long getReservationLeaseExpiresAt() { return reservationLeaseExpiresAt; }
    public Long getPaymentCapturedAt() { return paymentCapturedAt; }
    public int getRetryCount() { return retryCount; }
    public Long getNextRetryAt() { return nextRetryAt; }

    public void transitionTo(AllocationRequestState nextState) {
        if (!state.canTransitionTo(nextState)) {
            throw new IllegalStateException("Cannot transition request state from " + state + " to " + nextState);
        }
        this.state = nextState;
        this.updatedAt = System.currentTimeMillis();
        if (nextState == AllocationRequestState.COMPLETED) {
            this.completedAt = this.updatedAt;
        } else if (nextState == AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION) {
            this.cancelledAt = this.updatedAt;
        } else if (nextState == AllocationRequestState.CANCELLED) {
            this.cancelledAt = this.updatedAt;
        }
    }

    public void forceTransitionTo(AllocationRequestState nextState) {
        this.state = nextState;
        this.updatedAt = System.currentTimeMillis();
        if (nextState == AllocationRequestState.COMPLETED) {
            this.completedAt = this.updatedAt;
        } else if (nextState == AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION) {
            this.cancelledAt = this.updatedAt;
        } else if (nextState == AllocationRequestState.CANCELLED) {
            this.cancelledAt = this.updatedAt;
        }
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
        this.updatedAt = System.currentTimeMillis();
    }

    public void incrementAttempts() {
        this.attempts++;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
        this.updatedAt = System.currentTimeMillis();
    }
    
    // Payment setters
    public void setPriceGems(long priceGems) {
        this.priceGems = priceGems;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void setPaymentRequired(boolean paymentRequired) {
        this.paymentRequired = paymentRequired;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void setGemsReservationId(String gemsReservationId) {
        this.gemsReservationId = gemsReservationId;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void setReserveIdempotencyKey(String reserveIdempotencyKey) {
        this.reserveIdempotencyKey = reserveIdempotencyKey;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void setRenewIdempotencyKey(String renewIdempotencyKey) {
        this.renewIdempotencyKey = renewIdempotencyKey;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void setRenewSequence(long renewSequence) {
        this.renewSequence = renewSequence;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void incrementRenewSequence() {
        this.renewSequence++;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void setCaptureIdempotencyKey(String captureIdempotencyKey) {
        this.captureIdempotencyKey = captureIdempotencyKey;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void setReleaseIdempotencyKey(String releaseIdempotencyKey) {
        this.releaseIdempotencyKey = releaseIdempotencyKey;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void setReservationLeaseExpiresAt(Long reservationLeaseExpiresAt) {
        this.reservationLeaseExpiresAt = reservationLeaseExpiresAt;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void setPaymentCapturedAt(Long paymentCapturedAt) {
        this.paymentCapturedAt = paymentCapturedAt;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
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
    
    public boolean isReservationExpired() {
        return reservationLeaseExpiresAt != null && System.currentTimeMillis() > reservationLeaseExpiresAt;
    }
    
    public boolean shouldRetryNow() {
        return nextRetryAt != null && System.currentTimeMillis() >= nextRetryAt;
    }
}
