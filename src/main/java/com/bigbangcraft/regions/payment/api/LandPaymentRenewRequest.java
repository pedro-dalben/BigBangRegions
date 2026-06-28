package com.bigbangcraft.regions.payment.api;

import java.util.UUID;

public class LandPaymentRenewRequest {
    private final UUID operationId;
    private final String reservationId;
    private final String idempotencyKey;
    private final long renewSequence;
    private final long leaseDurationSeconds;
    
    public LandPaymentRenewRequest(UUID operationId, String reservationId, 
                                   String idempotencyKey, long renewSequence, long leaseDurationSeconds) {
        this.operationId = operationId;
        this.reservationId = reservationId;
        this.idempotencyKey = idempotencyKey;
        this.renewSequence = renewSequence;
        this.leaseDurationSeconds = leaseDurationSeconds;
    }
    
    public UUID getOperationId() { return operationId; }
    public String getReservationId() { return reservationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getRenewSequence() { return renewSequence; }
    public long getLeaseDurationSeconds() { return leaseDurationSeconds; }
}
