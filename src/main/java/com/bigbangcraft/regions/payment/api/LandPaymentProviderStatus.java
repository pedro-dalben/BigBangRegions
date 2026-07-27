package com.bigbangcraft.regions.payment.api;

public enum LandPaymentProviderStatus {
    NOT_INSTALLED,
    WAITING_FOR_PROVIDER,
    WAITING_FOR_DATABASE,
    TEMPORARILY_UNAVAILABLE,
    SHUTTING_DOWN,
    FAILED,
    API_INCOMPATIBLE,
    AVAILABLE,
    READY,
    UNAVAILABLE,
    INCOMPATIBLE_VERSION,
    BOOTSTRAP_FAILED
}
