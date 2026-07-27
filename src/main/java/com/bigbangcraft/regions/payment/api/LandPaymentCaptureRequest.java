package com.bigbangcraft.regions.payment.api;

import java.util.UUID;

public class LandPaymentCaptureRequest {
    private final UUID operationId;
    private final UUID actorUuid;
    private final String reservationId;
    private final String idempotencyKey;
    
    public LandPaymentCaptureRequest(UUID operationId, String reservationId, String idempotencyKey) {
        this(operationId, operationId, reservationId, idempotencyKey);
    }

    public LandPaymentCaptureRequest(UUID operationId, UUID actorUuid, String reservationId, String idempotencyKey) {
        this.operationId = operationId;
        this.actorUuid = actorUuid;
        this.reservationId = reservationId;
        this.idempotencyKey = idempotencyKey;
    }
    
    public UUID getOperationId() { return operationId; }
    public UUID getActorUuid() { return actorUuid; }
    public String getReservationId() { return reservationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
