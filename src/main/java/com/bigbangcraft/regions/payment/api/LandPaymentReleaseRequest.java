package com.bigbangcraft.regions.payment.api;

import java.util.UUID;

public class LandPaymentReleaseRequest {
    private final UUID operationId;
    private final String reservationId;
    private final String idempotencyKey;
    
    public LandPaymentReleaseRequest(UUID operationId, String reservationId, String idempotencyKey) {
        this.operationId = operationId;
        this.reservationId = reservationId;
        this.idempotencyKey = idempotencyKey;
    }
    
    public UUID getOperationId() { return operationId; }
    public String getReservationId() { return reservationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
