package com.bigbangcraft.regions.allocation;

public class AllocationRequestPreparation {
    private final String allocationRequestId;
    private int preparationAttempt;
    private long startedAt;
    private long timeoutAt;
    private String candidateId;
    private String chunkPlanJson;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String ticketState;
    private boolean cleanupRequired;
    private long updatedAt;

    public AllocationRequestPreparation(String allocationRequestId, int preparationAttempt, long startedAt, long timeoutAt,
                                        String candidateId, String chunkPlanJson, String lastErrorCode,
                                        String lastErrorMessage, String ticketState, boolean cleanupRequired,
                                        long updatedAt) {
        this.allocationRequestId = allocationRequestId;
        this.preparationAttempt = preparationAttempt;
        this.startedAt = startedAt;
        this.timeoutAt = timeoutAt;
        this.candidateId = candidateId;
        this.chunkPlanJson = chunkPlanJson;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = lastErrorMessage;
        this.ticketState = ticketState;
        this.cleanupRequired = cleanupRequired;
        this.updatedAt = updatedAt;
    }

    public String getAllocationRequestId() { return allocationRequestId; }
    public int getPreparationAttempt() { return preparationAttempt; }
    public long getStartedAt() { return startedAt; }
    public long getTimeoutAt() { return timeoutAt; }
    public String getCandidateId() { return candidateId; }
    public String getChunkPlanJson() { return chunkPlanJson; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public String getTicketState() { return ticketState; }
    public boolean isCleanupRequired() { return cleanupRequired; }
    public long getUpdatedAt() { return updatedAt; }

    public void markStarted(int preparationAttempt, long startedAt, long timeoutAt, String candidateId, String chunkPlanJson) {
        this.preparationAttempt = preparationAttempt;
        this.startedAt = startedAt;
        this.timeoutAt = timeoutAt;
        this.candidateId = candidateId;
        this.chunkPlanJson = chunkPlanJson;
        this.ticketState = "REQUESTED";
        this.cleanupRequired = true;
        this.updatedAt = System.currentTimeMillis();
    }

    public void updateTicketState(String ticketState) {
        this.ticketState = ticketState;
        this.updatedAt = System.currentTimeMillis();
    }

    public void markFailure(String code, String message) {
        this.lastErrorCode = code;
        this.lastErrorMessage = message;
        this.updatedAt = System.currentTimeMillis();
    }

    public void markCleanupRequired(boolean cleanupRequired) {
        this.cleanupRequired = cleanupRequired;
        this.updatedAt = System.currentTimeMillis();
    }
}
