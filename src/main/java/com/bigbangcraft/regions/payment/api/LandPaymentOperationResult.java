package com.bigbangcraft.regions.payment.api;

public class LandPaymentOperationResult {
    private final boolean success;
    private final LandPaymentFailure failure;
    private final String reservationId;
    private final String transactionId;
    private final long leaseExpiresAt;
    
    private LandPaymentOperationResult(boolean success, LandPaymentFailure failure, 
                                       String reservationId, String transactionId) {
        this(success, failure, reservationId, transactionId, 0L);
    }

    private LandPaymentOperationResult(boolean success, LandPaymentFailure failure,
                                       String reservationId, String transactionId, long leaseExpiresAt) {
        this.success = success;
        this.failure = failure;
        this.reservationId = reservationId;
        this.transactionId = transactionId;
        this.leaseExpiresAt = leaseExpiresAt;
    }
    
    public static LandPaymentOperationResult success(String reservationId, String transactionId) {
        return new LandPaymentOperationResult(true, null, reservationId, transactionId);
    }

    public static LandPaymentOperationResult success(String reservationId, String transactionId, long leaseExpiresAt) {
        return new LandPaymentOperationResult(true, null, reservationId, transactionId, leaseExpiresAt);
    }
    
    public static LandPaymentOperationResult failure(LandPaymentFailure failure) {
        return new LandPaymentOperationResult(false, failure, null, null);
    }
    
    public boolean isSuccess() { return success; }
    public LandPaymentFailure getFailure() { return failure; }
    public String getReservationId() { return reservationId; }
    public String getTransactionId() { return transactionId; }
    public long getLeaseExpiresAt() { return leaseExpiresAt; }
    
    public boolean isInsufficientBalance() {
        return failure == LandPaymentFailure.INSUFFICIENT_BALANCE;
    }
    
    public boolean isTransient() {
        return failure == LandPaymentFailure.TRANSIENT_ERROR || 
               failure == LandPaymentFailure.TIMEOUT ||
               failure == LandPaymentFailure.PROVIDER_UNAVAILABLE ||
               failure == LandPaymentFailure.DATABASE_UNAVAILABLE ||
               failure == LandPaymentFailure.EXECUTOR_SATURATED;
    }
    
    public boolean isPermanent() {
        return failure == LandPaymentFailure.INSUFFICIENT_BALANCE ||
               failure == LandPaymentFailure.INVALID_REQUEST ||
               failure == LandPaymentFailure.PLAYER_NOT_FOUND ||
               failure == LandPaymentFailure.INVALID_AMOUNT ||
               failure == LandPaymentFailure.INVALID_LEASE ||
               failure == LandPaymentFailure.MAX_BALANCE_EXCEEDED;
    }
}
