package com.bigbangcraft.regions.payment.essentials;

import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.payment.api.*;
import com.pedrodalben.bigbangessentials.api.BigBangEssentialsApi;
import com.pedrodalben.bigbangessentials.api.gems.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Optional adapter. Loading this class is isolated behind BigBangRegions' reflection boundary. */
public final class BigBangEssentialsGemsGateway implements LandPaymentGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-BigBangEssentialsGemsGateway");
    private static final String SOURCE = "bigbangregions";
    private static final String PURPOSE = "player_region_expansion";
    private static final long INITIAL_RETRY_MS = 1_000L;
    private static final long MAX_RETRY_MS = 30_000L;

    private volatile GemsIntegrationApi integration;
    private volatile GemsProviderSnapshot lastSnapshot;
    private volatile LandPaymentProviderStatus status = LandPaymentProviderStatus.WAITING_FOR_PROVIDER;
    private volatile String lastFailure;
    private volatile long nextReadinessCheckAt;
    private volatile long readinessBackoffMs = INITIAL_RETRY_MS;

    public BigBangEssentialsGemsGateway(ConfigManager ignored) {
        refreshReadiness();
    }

    /** Non-blocking readiness probe; it only reads provider lifecycle state. */
    @Override
    public boolean refreshReadiness() {
        long now = System.currentTimeMillis();
        if (now < nextReadinessCheckAt) return isAvailable();
        try {
            GemsIntegrationApi candidate = BigBangEssentialsApi.gemsIntegration();
            GemsProviderSnapshot snapshot = candidate.status();
            lastSnapshot = snapshot;
            if (snapshot.apiVersion() != 1) {
                status = LandPaymentProviderStatus.API_INCOMPATIBLE;
                lastFailure = "unsupported_api_version=" + snapshot.apiVersion();
                nextReadinessCheckAt = Long.MAX_VALUE;
                LOGGER.error("Unsupported BigBangEssentials Gems API version: {}", snapshot.apiVersion());
                return false;
            }
            integration = candidate;
            LandPaymentProviderStatus previousStatus = status;
            status = mapStatus(snapshot);
            lastFailure = snapshot.failure();
            if (status == LandPaymentProviderStatus.READY) {
                readinessBackoffMs = INITIAL_RETRY_MS;
                nextReadinessCheckAt = now + 5_000L;
                if (previousStatus != status) {
                    LOGGER.info("Gems provider readiness: READY (API v{}, database={})",
                        snapshot.apiVersion(), snapshot.databaseType());
                }
            } else {
                nextReadinessCheckAt = now + readinessBackoffMs;
                readinessBackoffMs = Math.min(MAX_RETRY_MS, readinessBackoffMs * 2L);
                if (previousStatus != status) {
                    LOGGER.info("Gems provider readiness: {} (databaseReady={}, failure={})",
                        status, snapshot.databaseReady(), snapshot.failure());
                }
            }
            return isAvailable();
        } catch (LinkageError e) {
            status = LandPaymentProviderStatus.API_INCOMPATIBLE;
            lastFailure = e.getClass().getSimpleName();
            nextReadinessCheckAt = Long.MAX_VALUE;
            LOGGER.error("BigBangEssentials Gems API is binary-incompatible", e);
            return false;
        } catch (RuntimeException e) {
            status = LandPaymentProviderStatus.TEMPORARILY_UNAVAILABLE;
            lastFailure = e.getClass().getSimpleName();
            nextReadinessCheckAt = now + readinessBackoffMs;
            readinessBackoffMs = Math.min(MAX_RETRY_MS, readinessBackoffMs * 2L);
            LOGGER.warn("Gems provider readiness probe failed: {}", e.getMessage());
            return false;
        }
    }

    public String getLastFailure() {
        return lastFailure;
    }

    @Override
    public String getDiagnosticDetails() {
        GemsProviderSnapshot snapshot = lastSnapshot;
        return "status=" + status + ", available=" + isAvailable()
            + ", apiVersion=" + (snapshot == null ? "unknown" : snapshot.apiVersion())
            + ", configured=" + (snapshot != null && snapshot.configured())
            + ", enabled=" + (snapshot != null && snapshot.enabled())
            + ", databaseReady=" + (snapshot != null && snapshot.databaseReady())
            + ", database=" + (snapshot == null ? "unknown" : snapshot.databaseType())
            + ", capabilities=" + (snapshot == null ? "unknown" : snapshot.capabilities())
            + ", lastFailure=" + lastFailure + ", nextReadinessCheckAt=" + nextReadinessCheckAt;
    }

    @Override
    public LandPaymentOperationResult reserve(LandPaymentReserveRequest request) {
        return reserveAsync(request).toCompletableFuture().join();
    }

    @Override
    public CompletionStage<LandPaymentOperationResult> reserveAsync(LandPaymentReserveRequest request) {
        GemsIntegrationApi api = readyApi();
        if (api == null) return completedFailure(failureForStatus());
        try {
            return api.reserveAsync(new GemsReserveRequest(
                request.getOwnerUuid(), request.getPriceGems(), request.getSource(), request.getPurpose(),
                request.getIdempotencyKey(), request.getExternalReference(),
                Duration.ofSeconds(request.getLeaseDurationSeconds()), Map.of("operation_id", request.getOperationId().toString())))
                .handle((result, error) -> error == null ? mapReservation(result) : failureFrom(error));
        } catch (LinkageError e) {
            markIncompatible(e);
            return completedFailure(LandPaymentFailure.API_INCOMPATIBLE);
        }
    }

    @Override
    public LandPaymentOperationResult renew(LandPaymentRenewRequest request) {
        return renewAsync(request).toCompletableFuture().join();
    }

    @Override
    public CompletionStage<LandPaymentOperationResult> renewAsync(LandPaymentRenewRequest request) {
        GemsIntegrationApi api = readyApi();
        if (api == null) return completedFailure(failureForStatus());
        try {
            return api.renewAsync(new GemsRenewRequest(
                UUID.fromString(request.getReservationId()), Duration.ofSeconds(request.getLeaseDurationSeconds()),
                SOURCE, PURPOSE, request.getActorUuid(), request.getIdempotencyKey(), request.getOperationId().toString(), Map.of(
                    "operation_id", request.getOperationId().toString(), "renew_sequence", Long.toString(request.getRenewSequence()))))
                .handle((result, error) -> error == null ? mapOperation(result) : failureFrom(error));
        } catch (LinkageError e) {
            markIncompatible(e);
            return completedFailure(LandPaymentFailure.API_INCOMPATIBLE);
        } catch (IllegalArgumentException e) {
            return completedFailure(LandPaymentFailure.INVALID_REQUEST);
        }
    }

    @Override
    public LandPaymentOperationResult capture(LandPaymentCaptureRequest request) {
        return captureAsync(request).toCompletableFuture().join();
    }

    @Override
    public CompletionStage<LandPaymentOperationResult> captureAsync(LandPaymentCaptureRequest request) {
        GemsIntegrationApi api = readyApi();
        if (api == null) return completedFailure(failureForStatus());
        try {
            return api.captureAsync(new GemsCaptureRequest(
                UUID.fromString(request.getReservationId()), SOURCE, PURPOSE, request.getActorUuid(),
                request.getIdempotencyKey(), request.getOperationId().toString(), Map.of("operation_id", request.getOperationId().toString())))
                .handle((result, error) -> error == null ? mapOperation(result) : failureFrom(error));
        } catch (LinkageError e) {
            markIncompatible(e);
            return completedFailure(LandPaymentFailure.API_INCOMPATIBLE);
        } catch (IllegalArgumentException e) {
            return completedFailure(LandPaymentFailure.INVALID_REQUEST);
        }
    }

    @Override
    public LandPaymentOperationResult release(LandPaymentReleaseRequest request) {
        return releaseAsync(request).toCompletableFuture().join();
    }

    @Override
    public CompletionStage<LandPaymentOperationResult> releaseAsync(LandPaymentReleaseRequest request) {
        GemsIntegrationApi api = readyApi();
        if (api == null) return completedFailure(failureForStatus());
        try {
            return api.releaseAsync(new GemsReleaseRequest(
                UUID.fromString(request.getReservationId()), SOURCE, PURPOSE, request.getActorUuid(), "allocation_cancelled",
                request.getIdempotencyKey(), request.getOperationId().toString(), Map.of(
                    "operation_id", request.getOperationId().toString(), "reason", "allocation_cancelled")))
                .handle((result, error) -> error == null ? mapOperation(result) : failureFrom(error));
        } catch (LinkageError e) {
            markIncompatible(e);
            return completedFailure(LandPaymentFailure.API_INCOMPATIBLE);
        } catch (IllegalArgumentException e) {
            return completedFailure(LandPaymentFailure.INVALID_REQUEST);
        }
    }

    @Override
    public boolean isAvailable() {
        return status == LandPaymentProviderStatus.READY || status == LandPaymentProviderStatus.AVAILABLE;
    }

    @Override
    public LandPaymentProviderStatus getProviderStatus() {
        return status;
    }

    @Override
    public Optional<LandPaymentReservation> getReservationByIdempotencyKey(String ignored) {
        return Optional.empty();
    }

    @Override
    public CompletionStage<Optional<LandPaymentReservation>> getReservationByIdempotencyKeyAsync(String idempotencyKey) {
        GemsIntegrationApi api = readyApi();
        if (api == null) return CompletableFuture.completedFuture(Optional.empty());
        try {
            return api.findReservationByIdempotencyKeyAsync(idempotencyKey).handle((result, error) -> {
                if (error != null || result == null || !result.found()) return Optional.empty();
                return Optional.of(new LandPaymentReservation(result.reservationUuid().toString(), idempotencyKey,
                    result.leaseExpiresAt(), result.amount()));
            });
        } catch (LinkageError e) {
            markIncompatible(e);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private GemsIntegrationApi readyApi() {
        refreshReadiness();
        return isAvailable() ? integration : null;
    }

    private LandPaymentProviderStatus mapStatus(GemsProviderSnapshot snapshot) {
        return switch (snapshot.state()) {
            case READY -> LandPaymentProviderStatus.READY;
            case WAITING_FOR_DATABASE -> LandPaymentProviderStatus.WAITING_FOR_DATABASE;
            case DISABLED -> LandPaymentProviderStatus.UNAVAILABLE;
            case TEMPORARILY_UNAVAILABLE -> LandPaymentProviderStatus.TEMPORARILY_UNAVAILABLE;
            case SHUTTING_DOWN -> LandPaymentProviderStatus.SHUTTING_DOWN;
            case FAILED -> LandPaymentProviderStatus.FAILED;
        };
    }

    private LandPaymentFailure failureForStatus() {
        return switch (status) {
            case WAITING_FOR_PROVIDER -> LandPaymentFailure.PROVIDER_STARTING;
            case WAITING_FOR_DATABASE -> LandPaymentFailure.DATABASE_UNAVAILABLE;
            case API_INCOMPATIBLE, INCOMPATIBLE_VERSION -> LandPaymentFailure.API_INCOMPATIBLE;
            case SHUTTING_DOWN -> LandPaymentFailure.SHUTTING_DOWN;
            case TEMPORARILY_UNAVAILABLE, FAILED, UNAVAILABLE, NOT_INSTALLED, BOOTSTRAP_FAILED -> LandPaymentFailure.PROVIDER_UNAVAILABLE;
            default -> LandPaymentFailure.PROVIDER_UNAVAILABLE;
        };
    }

    private LandPaymentOperationResult mapReservation(GemsReservationResult result) {
        if (result.success()) {
            return LandPaymentOperationResult.success(result.reservationUuid().toString(), null, result.leaseExpiresAt());
        }
        return LandPaymentOperationResult.failure(mapFailure(result.failure()));
    }

    private LandPaymentOperationResult mapOperation(GemsOperationResult result) {
        if (result.success()) {
            return LandPaymentOperationResult.success(
                result.reservationUuid() == null ? null : result.reservationUuid().toString(),
                result.transactionUuid() == null ? null : result.transactionUuid().toString(), result.leaseExpiresAt());
        }
        return LandPaymentOperationResult.failure(mapFailure(result.failure()));
    }

    private LandPaymentOperationResult failureFrom(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof LinkageError) {
            markIncompatible(cause);
            return LandPaymentOperationResult.failure(LandPaymentFailure.API_INCOMPATIBLE);
        }
        if (cause instanceof java.util.concurrent.RejectedExecutionException) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.EXECUTOR_SATURATED);
        }
        return LandPaymentOperationResult.failure(status == LandPaymentProviderStatus.WAITING_FOR_DATABASE
            ? LandPaymentFailure.DATABASE_UNAVAILABLE : LandPaymentFailure.TRANSIENT_ERROR);
    }

    private LandPaymentFailure mapFailure(GemsFailure failure) {
        if (failure == null) return LandPaymentFailure.UNKNOWN_ERROR;
        return switch (failure) {
            case INSUFFICIENT_BALANCE -> LandPaymentFailure.INSUFFICIENT_BALANCE;
            case RESERVATION_EXPIRED -> LandPaymentFailure.RESERVATION_EXPIRED;
            case RESERVATION_NOT_FOUND -> LandPaymentFailure.RESERVATION_NOT_FOUND;
            case RESERVATION_NOT_ACTIVE -> LandPaymentFailure.RESERVATION_NOT_ACTIVE;
            case ALREADY_CAPTURED -> LandPaymentFailure.ALREADY_CAPTURED;
            case ALREADY_RELEASED -> LandPaymentFailure.ALREADY_RELEASED;
            case IDEMPOTENCY_CONFLICT -> LandPaymentFailure.IDEMPOTENCY_CONFLICT;
            case INVALID_AMOUNT -> LandPaymentFailure.INVALID_AMOUNT;
            case INVALID_LEASE -> LandPaymentFailure.INVALID_LEASE;
            case MAX_BALANCE_EXCEEDED -> LandPaymentFailure.MAX_BALANCE_EXCEEDED;
            case PERSISTENCE_FAILURE -> LandPaymentFailure.PERSISTENCE_FAILURE;
            case DATA_INTEGRITY_FAILURE -> LandPaymentFailure.DATA_INTEGRITY_FAILURE;
            case EXECUTOR_SATURATED -> LandPaymentFailure.EXECUTOR_SATURATED;
            case SHUTTING_DOWN -> LandPaymentFailure.SHUTTING_DOWN;
            case DISABLED -> LandPaymentFailure.PROVIDER_UNAVAILABLE;
            case UNKNOWN -> LandPaymentFailure.UNKNOWN_ERROR;
        };
    }

    private void markIncompatible(Throwable error) {
        status = LandPaymentProviderStatus.API_INCOMPATIBLE;
        lastFailure = error.getClass().getSimpleName();
        nextReadinessCheckAt = Long.MAX_VALUE;
        LOGGER.error("BigBangEssentials Gems API linkage failure", error);
    }

    private static CompletionStage<LandPaymentOperationResult> completedFailure(LandPaymentFailure failure) {
        return CompletableFuture.completedFuture(LandPaymentOperationResult.failure(failure));
    }
}
