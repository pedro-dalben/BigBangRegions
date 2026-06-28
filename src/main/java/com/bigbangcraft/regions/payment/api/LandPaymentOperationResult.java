package com.bigbangcraft.regions.payment.api;

public class LandPaymentOperationResult {
    private final boolean success;
    private final LandPaymentFailure failure;
    private final String reservationId;
    private final String transactionId;
    
    private LandPaymentOperationResult(boolean success, LandPaymentFailure failure, 
                                       String reservationId, String transactionId) {
        this.success = success;
        this.failure = failure;
        this.reservationId = reservationId;
        this.transactionId = transactionId;
    }
    
    public static LandPaymentOperationResult success(String reservationId, String transactionId) {
        return new LandPaymentOperationResult(true, null, reservationId, transactionId);
    }
    
    public static LandPaymentOperationResult failure(LandPaymentFailure failure) {
        return new LandPaymentOperationResult(false, failure, null, null);
    }
    
    public boolean isSuccess() { return success; }
    public LandPaymentFailure getFailure() { return failure; }
    public String getReservationId() { return reservationId; }
    public String getTransactionId() { return transactionId; }
    
    public boolean isInsufficientBalance() {
        return failure == LandPaymentFailure.INSUFFICIENT_BALANCE;
    }
    
    public boolean isTransient() {
        return failure == LandPaymentFailure.TRANSIENT_ERROR || 
               failure == LandPaymentFailure.TIMEOUT;
    }
    
    public boolean isPermanent() {
        return failure == LandPaymentFailure.INSUFFICIENT_BALANCE ||
               failure == LandPaymentFailure.INVALID_REQUEST ||
               failure == LandPaymentFailure.PLAYER_NOT_FOUND;
    }
}
