package com.bigbangcraft.regions.payment.essentials;

import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.payment.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public class BigBangEssentialsGemsGateway implements LandPaymentGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-BigBangEssentialsGemsGateway");

    private static final String ESSENTIALS_API_CLASS = "com.pedrodalben.bigbangessentials.api.BigBangEssentialsAPI";
    private static final String GEMS_SERVICE_CLASS = "com.pedrodalben.bigbangessentials.gems.GemsService";
    private static final String GEMS_RESERVATION_CLASS = "com.pedrodalben.bigbangessentials.gems.GemsReservation";
    private static final String GEMS_RESULT_CLASS = "com.pedrodalben.bigbangessentials.gems.GemsOperationResult";

    private final ConfigManager configManager;
    private Object gemsService;
    private boolean available;
    private LandPaymentProviderStatus status;

    public BigBangEssentialsGemsGateway(ConfigManager configManager) {
        this.configManager = configManager;
        this.available = false;
        this.status = LandPaymentProviderStatus.BOOTSTRAP_FAILED;
        initialize();
    }

    private void initialize() {
        try {
            Class<?> apiClass = Class.forName(ESSENTIALS_API_CLASS);
            Method getInstance = apiClass.getMethod("getInstance");
            Object apiInstance = getInstance.invoke(null);
            Method getGemsService = apiClass.getMethod("getGemsService");
            this.gemsService = getGemsService.invoke(apiInstance);

            if (this.gemsService == null) {
                LOGGER.warn("BigBang Essentials GemsService is null");
                this.status = LandPaymentProviderStatus.UNAVAILABLE;
                return;
            }

            this.available = true;
            this.status = LandPaymentProviderStatus.AVAILABLE;
            LOGGER.info("BigBang Essentials GemsGateway initialized successfully");
        } catch (ClassNotFoundException e) {
            LOGGER.error("BigBang Essentials API not found: {}", e.getMessage());
            this.status = LandPaymentProviderStatus.UNAVAILABLE;
        } catch (Exception e) {
            LOGGER.error("Failed to initialize BigBang Essentials GemsGateway: {}", e.getMessage());
            this.status = LandPaymentProviderStatus.BOOTSTRAP_FAILED;
        }
    }

    @Override
    public LandPaymentOperationResult reserve(LandPaymentReserveRequest request) {
        if (!available || gemsService == null) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
        }
        try {
            Class<?> gsClass = Class.forName(GEMS_SERVICE_CLASS);
            Method reserveMethod = gsClass.getMethod("reserveGems", UUID.class, long.class, String.class, long.class);
            Object resultObj = reserveMethod.invoke(gemsService,
                    request.getOwnerUuid(),
                    request.getPriceGems(),
                    request.getIdempotencyKey(),
                    request.getLeaseDurationSeconds());

            return mapGemsResult(resultObj);
        } catch (Exception e) {
            LOGGER.error("Failed to reserve gems for request={}: {}", request.getOperationId(), e.getMessage());
            return LandPaymentOperationResult.failure(LandPaymentFailure.TRANSIENT_ERROR);
        }
    }

    @Override
    public LandPaymentOperationResult renew(LandPaymentRenewRequest request) {
        if (!available || gemsService == null) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
        }
        try {
            Class<?> gsClass = Class.forName(GEMS_SERVICE_CLASS);
            Method renewMethod = gsClass.getMethod("renewReservation", String.class, String.class, long.class, long.class);
            Object resultObj = renewMethod.invoke(gemsService,
                    request.getReservationId(),
                    request.getIdempotencyKey(),
                    request.getRenewSequence(),
                    request.getLeaseDurationSeconds());

            return mapGemsResult(resultObj);
        } catch (Exception e) {
            LOGGER.error("Failed to renew gems reservation for request={}: {}", request.getOperationId(), e.getMessage());
            return LandPaymentOperationResult.failure(LandPaymentFailure.TRANSIENT_ERROR);
        }
    }

    @Override
    public LandPaymentOperationResult capture(LandPaymentCaptureRequest request) {
        if (!available || gemsService == null) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
        }
        try {
            Class<?> gsClass = Class.forName(GEMS_SERVICE_CLASS);
            Method captureMethod = gsClass.getMethod("captureReservation", String.class, String.class);
            Object resultObj = captureMethod.invoke(gemsService,
                    request.getReservationId(),
                    request.getIdempotencyKey());

            return mapGemsResult(resultObj);
        } catch (Exception e) {
            LOGGER.error("Failed to capture gems for request={}: {}", request.getOperationId(), e.getMessage());
            return LandPaymentOperationResult.failure(LandPaymentFailure.TRANSIENT_ERROR);
        }
    }

    @Override
    public LandPaymentOperationResult release(LandPaymentReleaseRequest request) {
        if (!available || gemsService == null) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
        }
        try {
            Class<?> gsClass = Class.forName(GEMS_SERVICE_CLASS);
            Method releaseMethod = gsClass.getMethod("releaseReservation", String.class, String.class);
            Object resultObj = releaseMethod.invoke(gemsService,
                    request.getReservationId(),
                    request.getIdempotencyKey());

            return mapGemsResult(resultObj);
        } catch (Exception e) {
            LOGGER.error("Failed to release gems for request={}: {}", request.getOperationId(), e.getMessage());
            return LandPaymentOperationResult.failure(LandPaymentFailure.TRANSIENT_ERROR);
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public LandPaymentProviderStatus getProviderStatus() {
        return status;
    }

    @Override
    public Optional<LandPaymentReservation> getReservationByIdempotencyKey(String idempotencyKey) {
        if (!available || gemsService == null) {
            return Optional.empty();
        }
        try {
            Class<?> gsClass = Class.forName(GEMS_SERVICE_CLASS);
            Method getReservationMethod = gsClass.getMethod("getReservation", String.class);
            Object reservationObj = getReservationMethod.invoke(gemsService, idempotencyKey);

            if (reservationObj == null) {
                return Optional.empty();
            }

            Class<?> grClass = Class.forName(GEMS_RESERVATION_CLASS);
            Method getId = grClass.getMethod("getId");
            Method getPriceGems = grClass.getMethod("getPriceGems");
            Method getExpiresAt = grClass.getMethod("getExpiresAt");

            String resId = (String) getId.invoke(reservationObj);
            long priceGems = (long) getPriceGems.invoke(reservationObj);
            long expiresAt = (long) getExpiresAt.invoke(reservationObj);

            return Optional.of(new LandPaymentReservation(resId, idempotencyKey, expiresAt, priceGems));
        } catch (Exception e) {
            LOGGER.error("Failed to get reservation by idempotency key: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private LandPaymentOperationResult mapGemsResult(Object resultObj) {
        try {
            Class<?> grClass = Class.forName(GEMS_RESULT_CLASS);
            Method isSuccess = grClass.getMethod("isSuccess");
            Method getReservationId = grClass.getMethod("getReservationId");
            Method getTransactionId = grClass.getMethod("getTransactionId");
            Method getFailureCode = grClass.getMethod("getFailureCode");

            boolean success = (boolean) isSuccess.invoke(resultObj);
            if (success) {
                String reservationId = (String) getReservationId.invoke(resultObj);
                String transactionId = (String) getTransactionId.invoke(resultObj);
                return LandPaymentOperationResult.success(reservationId, transactionId);
            }

            String failureCode = (String) getFailureCode.invoke(resultObj);
            return LandPaymentOperationResult.failure(mapFailure(failureCode));
        } catch (Exception e) {
            LOGGER.error("Failed to map Gems operation result: {}", e.getMessage());
            return LandPaymentOperationResult.failure(LandPaymentFailure.UNKNOWN_ERROR);
        }
    }

    private LandPaymentFailure mapFailure(String failureCode) {
        if (failureCode == null) return LandPaymentFailure.UNKNOWN_ERROR;
        return switch (failureCode.toUpperCase()) {
            case "INSUFFICIENT_BALANCE" -> LandPaymentFailure.INSUFFICIENT_BALANCE;
            case "TIMEOUT" -> LandPaymentFailure.TIMEOUT;
            case "INVALID_REQUEST" -> LandPaymentFailure.INVALID_REQUEST;
            case "PLAYER_NOT_FOUND" -> LandPaymentFailure.PLAYER_NOT_FOUND;
            case "RESERVATION_EXPIRED" -> LandPaymentFailure.RESERVATION_EXPIRED;
            case "RESERVATION_NOT_FOUND" -> LandPaymentFailure.RESERVATION_NOT_FOUND;
            case "ALREADY_CAPTURED" -> LandPaymentFailure.ALREADY_CAPTURED;
            case "ALREADY_RELEASED" -> LandPaymentFailure.ALREADY_RELEASED;
            default -> LandPaymentFailure.TRANSIENT_ERROR;
        };
    }
}
