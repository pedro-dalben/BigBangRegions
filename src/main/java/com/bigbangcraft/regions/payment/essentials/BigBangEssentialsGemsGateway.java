package com.bigbangcraft.regions.payment.essentials;

import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.payment.api.*;
import com.pedrodalben.bigbangessentials.api.BigBangEssentialsApi;
import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class BigBangEssentialsGemsGateway implements LandPaymentGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-BigBangEssentialsGemsGateway");

    private static final String SOURCE = "bigbangregions";
    private static final String PURPOSE = "terrain_allocation";

    private GemsService gemsService;
    private boolean available;
    private LandPaymentProviderStatus status;

    public BigBangEssentialsGemsGateway(ConfigManager configManager) {
        initialize();
    }

    private void initialize() {
        try {
            if (!BigBangEssentialsApi.isGemsEnabled()) {
                LOGGER.warn("BigBang Essentials gems system is disabled");
                this.status = LandPaymentProviderStatus.UNAVAILABLE;
                return;
            }
            this.gemsService = BigBangEssentialsApi.requireGems();
            this.available = true;
            this.status = LandPaymentProviderStatus.AVAILABLE;
            LOGGER.info("BigBang Essentials GemsGateway initialized successfully (API v{})",
                BigBangEssentialsApi.gemsApiVersion());
        } catch (Exception e) {
            LOGGER.error("Failed to initialize BigBang Essentials GemsGateway", e);
            this.status = LandPaymentProviderStatus.BOOTSTRAP_FAILED;
        }
    }

    @Override
    public LandPaymentOperationResult reserve(LandPaymentReserveRequest request) {
        if (!available || gemsService == null) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
        }
        try {
            GemReservationRequest gemReq = new GemReservationRequest(
                request.getOwnerUuid(),
                request.getPriceGems(),
                SOURCE,
                PURPOSE,
                request.getIdempotencyKey(),
                request.getOperationId().toString(),
                Duration.ofSeconds(request.getLeaseDurationSeconds()),
                Map.of("operation_id", request.getOperationId().toString())
            );
            GemReservationResult result = gemsService.reserve(gemReq);
            return mapReservationResult(result);
        } catch (Exception e) {
            LOGGER.error("Failed to reserve gems for request={}", request.getOperationId(), e);
            return LandPaymentOperationResult.failure(LandPaymentFailure.TRANSIENT_ERROR);
        }
    }

    @Override
    public LandPaymentOperationResult renew(LandPaymentRenewRequest request) {
        if (!available || gemsService == null) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
        }
        try {
            GemRenewRequest gemReq = new GemRenewRequest(
                UUID.fromString(request.getReservationId()),
                Duration.ofSeconds(request.getLeaseDurationSeconds()),
                SOURCE,
                PURPOSE,
                request.getOperationId(),
                request.getIdempotencyKey(),
                request.getOperationId().toString(),
                Map.of()
            );
            GemOperationResult result = gemsService.renew(gemReq);
            return mapOperationResult(result);
        } catch (Exception e) {
            LOGGER.error("Failed to renew gems reservation for request={}", request.getOperationId(), e);
            return LandPaymentOperationResult.failure(LandPaymentFailure.TRANSIENT_ERROR);
        }
    }

    @Override
    public LandPaymentOperationResult capture(LandPaymentCaptureRequest request) {
        if (!available || gemsService == null) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
        }
        try {
            GemCaptureRequest gemReq = new GemCaptureRequest(
                UUID.fromString(request.getReservationId()),
                SOURCE,
                PURPOSE,
                request.getOperationId(),
                request.getIdempotencyKey(),
                request.getOperationId().toString(),
                Map.of()
            );
            GemOperationResult result = gemsService.capture(gemReq);
            return mapOperationResult(result);
        } catch (Exception e) {
            LOGGER.error("Failed to capture gems for request={}", request.getOperationId(), e);
            return LandPaymentOperationResult.failure(LandPaymentFailure.TRANSIENT_ERROR);
        }
    }

    @Override
    public LandPaymentOperationResult release(LandPaymentReleaseRequest request) {
        if (!available || gemsService == null) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
        }
        try {
            GemReleaseRequest gemReq = new GemReleaseRequest(
                UUID.fromString(request.getReservationId()),
                SOURCE,
                PURPOSE,
                request.getOperationId(),
                "allocation_cancelled",
                request.getIdempotencyKey(),
                request.getOperationId().toString(),
                Map.of()
            );
            GemOperationResult result = gemsService.release(gemReq);
            return mapOperationResult(result);
        } catch (Exception e) {
            LOGGER.error("Failed to release gems for request={}", request.getOperationId(), e);
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
            Optional<GemReservation> gemRes = gemsService.findReservationByIdempotencyKey(idempotencyKey);
            return gemRes.map(r -> new LandPaymentReservation(
                r.getReservationId().toString(),
                r.getIdempotencyKey(),
                r.getExpiresAt(),
                r.getAmount()
            ));
        } catch (Exception e) {
            LOGGER.error("Failed to get reservation by idempotency key {}", idempotencyKey, e);
            return Optional.empty();
        }
    }

    private LandPaymentOperationResult mapReservationResult(GemReservationResult result) {
        if (result.success()) {
            String reservationId = result.reservationId() != null ? result.reservationId().toString() : null;
            return LandPaymentOperationResult.success(reservationId, null);
        }
        return LandPaymentOperationResult.failure(mapFailure(result.failure()));
    }

    private LandPaymentOperationResult mapOperationResult(GemOperationResult result) {
        if (result.success()) {
            String reservationId = result.reservationId() != null ? result.reservationId().toString() : null;
            String transactionId = result.transactionId() != null ? result.transactionId().toString() : null;
            return LandPaymentOperationResult.success(reservationId, transactionId);
        }
        return LandPaymentOperationResult.failure(mapFailure(result.failure()));
    }

    private LandPaymentFailure mapFailure(GemOperationFailure failure) {
        if (failure == null) return LandPaymentFailure.UNKNOWN_ERROR;
        return switch (failure) {
            case INSUFFICIENT_AVAILABLE_BALANCE -> LandPaymentFailure.INSUFFICIENT_BALANCE;
            case RESERVATION_EXPIRED -> LandPaymentFailure.RESERVATION_EXPIRED;
            case RESERVATION_NOT_FOUND -> LandPaymentFailure.RESERVATION_NOT_FOUND;
            case RESERVATION_ALREADY_CAPTURED -> LandPaymentFailure.ALREADY_CAPTURED;
            case RESERVATION_ALREADY_RELEASED -> LandPaymentFailure.ALREADY_RELEASED;
            case RESERVATION_NOT_ACTIVE -> LandPaymentFailure.RESERVATION_NOT_FOUND;
            case IDEMPOTENCY_CONFLICT -> LandPaymentFailure.TRANSIENT_ERROR;
            case PERSISTENCE_FAILURE, DATA_INTEGRITY_FAILURE, SHUTTING_DOWN, UNKNOWN ->
                LandPaymentFailure.TRANSIENT_ERROR;
            case INVALID_AMOUNT, NEGATIVE_AMOUNT, FRACTIONAL_AMOUNT, OVERFLOW, INVALID_LEASE,
                 MAX_BALANCE_EXCEEDED, UNAUTHORIZED_SOURCE ->
                LandPaymentFailure.INVALID_REQUEST;
            case DISABLED -> LandPaymentFailure.PROVIDER_UNAVAILABLE;
        };
    }
}
