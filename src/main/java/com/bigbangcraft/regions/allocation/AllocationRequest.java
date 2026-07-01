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
    private String plotSlotId;
    private String failureReason;
    private int attempts;
    private final long createdAt;
    private long updatedAt;
    private Long completedAt;
    private Long cancelledAt;

    private int retryCount;
    private Long nextRetryAt;
    private int preparationAttempt;

    public AllocationRequest(String id, UUID ownerUuid, String requestedBiomeOption, String targetDimension,
                             AllocationRequestState state, String source, UUID requestedByUuid, String regionId,
                             String plotSlotId, String failureReason, int attempts, long createdAt, long updatedAt,
                             Long completedAt, Long cancelledAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.requestedBiomeOption = requestedBiomeOption;
        this.targetDimension = targetDimension;
        this.state = state;
        this.source = source;
        this.requestedByUuid = requestedByUuid;
        this.regionId = regionId;
        this.plotSlotId = plotSlotId;
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
    public String getPlotSlotId() { return plotSlotId; }
    public String getFailureReason() { return failureReason; }
    public int getAttempts() { return attempts; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public Long getCompletedAt() { return completedAt; }
    public Long getCancelledAt() { return cancelledAt; }
    public int getRetryCount() { return retryCount; }
    public Long getNextRetryAt() { return nextRetryAt; }
    public int getPreparationAttempt() { return preparationAttempt; }

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
        } else if (nextState == AllocationRequestState.PAUSED_RECOVERY) {
            this.nextRetryAt = null;
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
        } else if (nextState == AllocationRequestState.PAUSED_RECOVERY) {
            this.nextRetryAt = null;
        }
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setPlotSlotId(String plotSlotId) {
        this.plotSlotId = plotSlotId;
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

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
        this.updatedAt = System.currentTimeMillis();
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = System.currentTimeMillis();
    }

    public void incrementPreparationAttempt() {
        this.preparationAttempt++;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setPreparationAttempt(int preparationAttempt) {
        this.preparationAttempt = preparationAttempt;
        this.updatedAt = System.currentTimeMillis();
    }

    public void setNextRetryAt(Long nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean shouldRetryNow() {
        return nextRetryAt != null && System.currentTimeMillis() >= nextRetryAt;
    }
}
