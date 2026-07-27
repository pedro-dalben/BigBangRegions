package com.bigbangcraft.regions.payment;

import com.bigbangcraft.regions.payment.api.*;

import java.util.Optional;
import java.util.UUID;

public class NoPaymentGateway implements LandPaymentGateway {
    private final LandPaymentProviderStatus status;

    public NoPaymentGateway() {
        this(LandPaymentProviderStatus.NOT_INSTALLED);
    }

    public NoPaymentGateway(LandPaymentProviderStatus status) {
        this.status = status;
    }
    
    @Override
    public LandPaymentOperationResult reserve(LandPaymentReserveRequest request) {
        return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
    }
    
    @Override
    public LandPaymentOperationResult renew(LandPaymentRenewRequest request) {
        return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
    }
    
    @Override
    public LandPaymentOperationResult capture(LandPaymentCaptureRequest request) {
        return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
    }
    
    @Override
    public LandPaymentOperationResult release(LandPaymentReleaseRequest request) {
        return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
    }
    
    @Override
    public boolean isAvailable() {
        return false;
    }
    
    @Override
    public LandPaymentProviderStatus getProviderStatus() {
        return status;
    }
    
    @Override
    public Optional<LandPaymentReservation> getReservationByIdempotencyKey(String idempotencyKey) {
        return Optional.empty();
    }
}
