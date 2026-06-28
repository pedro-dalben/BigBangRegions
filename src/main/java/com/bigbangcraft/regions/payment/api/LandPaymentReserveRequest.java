package com.bigbangcraft.regions.payment.api;

import java.util.UUID;

public class LandPaymentReserveRequest {
    private final UUID operationId;
    private final UUID ownerUuid;
    private final long priceGems;
    private final String idempotencyKey;
    private final long leaseDurationSeconds;
    
    public LandPaymentReserveRequest(UUID operationId, UUID ownerUuid, long priceGems, 
                                     String idempotencyKey, long leaseDurationSeconds) {
        this.operationId = operationId;
        this.ownerUuid = ownerUuid;
        this.priceGems = priceGems;
        this.idempotencyKey = idempotencyKey;
        this.leaseDurationSeconds = leaseDurationSeconds;
    }
    
    public UUID getOperationId() { return operationId; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public long getPriceGems() { return priceGems; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getLeaseDurationSeconds() { return leaseDurationSeconds; }
}
