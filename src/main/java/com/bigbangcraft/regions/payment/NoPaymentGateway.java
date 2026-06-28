package com.bigbangcraft.regions.payment;

import com.bigbangcraft.regions.payment.api.*;

import java.util.Optional;
import java.util.UUID;

public class NoPaymentGateway implements LandPaymentGateway {
    
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
        return LandPaymentProviderStatus.UNAVAILABLE;
    }
    
    @Override
    public Optional<LandPaymentReservation> getReservationByIdempotencyKey(String idempotencyKey) {
        return Optional.empty();
    }
}
