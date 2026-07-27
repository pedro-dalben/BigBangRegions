package com.bigbangcraft.regions.payment.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface LandPaymentGateway {

    default boolean refreshReadiness() {
        return isAvailable();
    }
    
    LandPaymentOperationResult reserve(LandPaymentReserveRequest request);
    
    LandPaymentOperationResult renew(LandPaymentRenewRequest request);
    
    LandPaymentOperationResult capture(LandPaymentCaptureRequest request);
    
    LandPaymentOperationResult release(LandPaymentReleaseRequest request);

    default CompletionStage<LandPaymentOperationResult> reserveAsync(LandPaymentReserveRequest request) {
        return CompletableFuture.supplyAsync(() -> reserve(request));
    }

    default CompletionStage<LandPaymentOperationResult> renewAsync(LandPaymentRenewRequest request) {
        return CompletableFuture.supplyAsync(() -> renew(request));
    }

    default CompletionStage<LandPaymentOperationResult> captureAsync(LandPaymentCaptureRequest request) {
        return CompletableFuture.supplyAsync(() -> capture(request));
    }

    default CompletionStage<LandPaymentOperationResult> releaseAsync(LandPaymentReleaseRequest request) {
        return CompletableFuture.supplyAsync(() -> release(request));
    }
    
    boolean isAvailable();
    
    LandPaymentProviderStatus getProviderStatus();

    default String getDiagnosticDetails() {
        return "status=" + getProviderStatus() + ", available=" + isAvailable();
    }
    
    Optional<LandPaymentReservation> getReservationByIdempotencyKey(String idempotencyKey);

    default CompletionStage<Optional<LandPaymentReservation>> getReservationByIdempotencyKeyAsync(String idempotencyKey) {
        return CompletableFuture.supplyAsync(() -> getReservationByIdempotencyKey(idempotencyKey));
    }
}
