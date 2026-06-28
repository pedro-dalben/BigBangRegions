package com.bigbangcraft.regions.payment.api;

public class LandPaymentReservation {
    private final String reservationId;
    private final String idempotencyKey;
    private final long expiresAt;
    private final long priceGems;
    
    public LandPaymentReservation(String reservationId, String idempotencyKey, 
                                  long expiresAt, long priceGems) {
        this.reservationId = reservationId;
        this.idempotencyKey = idempotencyKey;
        this.expiresAt = expiresAt;
        this.priceGems = priceGems;
    }
    
    public String getReservationId() { return reservationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getExpiresAt() { return expiresAt; }
    public long getPriceGems() { return priceGems; }
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
    
    public long getRemainingSeconds() {
        long remaining = (expiresAt - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }
}
