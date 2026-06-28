package com.bigbangcraft.regions.payment.api;

import java.util.Optional;
import java.util.UUID;

public interface LandPaymentGateway {
    
    LandPaymentOperationResult reserve(LandPaymentReserveRequest request);
    
    LandPaymentOperationResult renew(LandPaymentRenewRequest request);
    
    LandPaymentOperationResult capture(LandPaymentCaptureRequest request);
    
    LandPaymentOperationResult release(LandPaymentReleaseRequest request);
    
    boolean isAvailable();
    
    LandPaymentProviderStatus getProviderStatus();
    
    Optional<LandPaymentReservation> getReservationByIdempotencyKey(String idempotencyKey);
}
