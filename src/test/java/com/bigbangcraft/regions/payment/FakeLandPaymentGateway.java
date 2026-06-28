package com.bigbangcraft.regions.payment;

import com.bigbangcraft.regions.payment.api.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FakeLandPaymentGateway implements LandPaymentGateway {
    private final Map<String, FakeReservation> reservations = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyResults = new ConcurrentHashMap<>();
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private boolean available = true;
    private LandPaymentProviderStatus status = LandPaymentProviderStatus.AVAILABLE;
    private boolean failNextReserve = false;
    private LandPaymentFailure nextReserveFailure = LandPaymentFailure.TRANSIENT_ERROR;
    private boolean failNextCapture = false;
    private LandPaymentFailure nextCaptureFailure = LandPaymentFailure.TRANSIENT_ERROR;
    private boolean failNextRenew = false;
    private LandPaymentFailure nextRenewFailure = LandPaymentFailure.TRANSIENT_ERROR;
    private boolean failNextRelease = false;
    private LandPaymentFailure nextReleaseFailure = LandPaymentFailure.TRANSIENT_ERROR;
    private boolean simulateTransientOnAll = false;
    private int reserveCallCount = 0;
    private int captureCallCount = 0;
    private int renewCallCount = 0;
    private int releaseCallCount = 0;

    public static class FakeReservation {
        final String id;
        final UUID ownerUuid;
        final long priceGems;
        long expiresAt;
        final String idempotencyKey;
        boolean captured = false;
        boolean released = false;

        FakeReservation(String id, UUID ownerUuid, long priceGems, long expiresAt, String idempotencyKey) {
            this.id = id;
            this.ownerUuid = ownerUuid;
            this.priceGems = priceGems;
            this.expiresAt = expiresAt;
            this.idempotencyKey = idempotencyKey;
        }
    }

    public void setBalance(UUID playerUuid, long balance) {
        balances.put(playerUuid, balance);
    }

    public void setAvailable(boolean available) {
        this.available = available;
        this.status = available ? LandPaymentProviderStatus.AVAILABLE : LandPaymentProviderStatus.UNAVAILABLE;
    }

    public void setStatus(LandPaymentProviderStatus status) {
        this.status = status;
        this.available = status == LandPaymentProviderStatus.AVAILABLE;
    }

    public void failNextReserve(LandPaymentFailure failure) {
        this.failNextReserve = true;
        this.nextReserveFailure = failure;
    }

    public void failNextCapture(LandPaymentFailure failure) {
        this.failNextCapture = true;
        this.nextCaptureFailure = failure;
    }

    public void failNextRenew(LandPaymentFailure failure) {
        this.failNextRenew = true;
        this.nextRenewFailure = failure;
    }

    public void failNextRelease(LandPaymentFailure failure) {
        this.failNextRelease = true;
        this.nextReleaseFailure = failure;
    }

    public void simulateTransientOnAll() {
        this.simulateTransientOnAll = true;
    }

    public void clearTransientSimulation() {
        this.simulateTransientOnAll = false;
    }

    public FakeReservation getReservation(String id) {
        return reservations.get(id);
    }

    public boolean isReservationReleased(String id) {
        FakeReservation res = reservations.get(id);
        return res != null && res.released;
    }

    public boolean isReservationCaptured(String id) {
        FakeReservation res = reservations.get(id);
        return res != null && res.captured;
    }

    public int getReserveCallCount() { return reserveCallCount; }
    public int getCaptureCallCount() { return captureCallCount; }
    public int getRenewCallCount() { return renewCallCount; }
    public int getReleaseCallCount() { return releaseCallCount; }

    public void reset() {
        reservations.clear();
        idempotencyResults.clear();
        balances.clear();
        available = true;
        status = LandPaymentProviderStatus.AVAILABLE;
        failNextReserve = false;
        failNextCapture = false;
        failNextRenew = false;
        failNextRelease = false;
        simulateTransientOnAll = false;
        reserveCallCount = 0;
        captureCallCount = 0;
        renewCallCount = 0;
        releaseCallCount = 0;
    }

    @Override
    public synchronized LandPaymentOperationResult reserve(LandPaymentReserveRequest request) {
        reserveCallCount++;
        if (!available) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
        }

        // Idempotency check
        String existingResult = idempotencyResults.get(request.getIdempotencyKey());
        if (existingResult != null) {
            FakeReservation existing = reservations.get(existingResult);
            if (existing != null) {
                return LandPaymentOperationResult.success(existing.id, "tx-" + existing.id);
            }
            return LandPaymentOperationResult.failure(LandPaymentFailure.RESERVATION_NOT_FOUND);
        }

        if (simulateTransientOnAll) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.TRANSIENT_ERROR);
        }

        if (failNextReserve) {
            failNextReserve = false;
            return LandPaymentOperationResult.failure(nextReserveFailure);
        }

        // Check balance
        Long balance = balances.get(request.getOwnerUuid());
        if (balance != null && balance < request.getPriceGems()) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.INSUFFICIENT_BALANCE);
        }

        String reservationId = "fake-res-" + UUID.randomUUID().toString().substring(0, 8);
        long expiresAt = System.currentTimeMillis() + request.getLeaseDurationSeconds() * 1000L;
        FakeReservation reservation = new FakeReservation(reservationId, request.getOwnerUuid(),
                request.getPriceGems(), expiresAt, request.getIdempotencyKey());
        reservations.put(reservationId, reservation);
        idempotencyResults.put(request.getIdempotencyKey(), reservationId);

        return LandPaymentOperationResult.success(reservationId, "tx-" + reservationId);
    }

    @Override
    public synchronized LandPaymentOperationResult renew(LandPaymentRenewRequest request) {
        renewCallCount++;
        if (!available) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
        }

        if (simulateTransientOnAll) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.TRANSIENT_ERROR);
        }

        if (failNextRenew) {
            failNextRenew = false;
            return LandPaymentOperationResult.failure(nextRenewFailure);
        }

        FakeReservation reservation = reservations.get(request.getReservationId());
        if (reservation == null) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.RESERVATION_NOT_FOUND);
        }
        if (reservation.captured) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.ALREADY_CAPTURED);
        }
        if (reservation.released) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.ALREADY_RELEASED);
        }

        // Extend lease
        reservation.expiresAt = System.currentTimeMillis() + request.getLeaseDurationSeconds() * 1000L;
        return LandPaymentOperationResult.success(request.getReservationId(), "tx-renew-" + request.getReservationId());
    }

    @Override
    public synchronized LandPaymentOperationResult capture(LandPaymentCaptureRequest request) {
        captureCallCount++;
        if (!available) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
        }

        if (simulateTransientOnAll) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.TRANSIENT_ERROR);
        }

        if (failNextCapture) {
            failNextCapture = false;
            return LandPaymentOperationResult.failure(nextCaptureFailure);
        }

        FakeReservation reservation = reservations.get(request.getReservationId());
        if (reservation == null) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.RESERVATION_NOT_FOUND);
        }
        if (reservation.captured) {
            // Idempotent: already captured is success
            return LandPaymentOperationResult.success(request.getReservationId(), "tx-" + request.getReservationId());
        }
        if (reservation.released) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.ALREADY_RELEASED);
        }

        reservation.captured = true;
        return LandPaymentOperationResult.success(request.getReservationId(), "tx-" + request.getReservationId());
    }

    @Override
    public synchronized LandPaymentOperationResult release(LandPaymentReleaseRequest request) {
        releaseCallCount++;
        if (!available) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.PROVIDER_UNAVAILABLE);
        }

        if (simulateTransientOnAll) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.TRANSIENT_ERROR);
        }

        if (failNextRelease) {
            failNextRelease = false;
            return LandPaymentOperationResult.failure(nextReleaseFailure);
        }

        FakeReservation reservation = reservations.get(request.getReservationId());
        if (reservation == null) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.RESERVATION_NOT_FOUND);
        }
        if (reservation.captured) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.ALREADY_CAPTURED);
        }
        if (reservation.released) {
            // Idempotent: already released is success
            return LandPaymentOperationResult.success(request.getReservationId(), "tx-release-" + request.getReservationId());
        }

        reservation.released = true;
        return LandPaymentOperationResult.success(request.getReservationId(), "tx-release-" + request.getReservationId());
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
        String reservationId = idempotencyResults.get(idempotencyKey);
        if (reservationId == null) return Optional.empty();
        FakeReservation res = reservations.get(reservationId);
        if (res == null) return Optional.empty();
        return Optional.of(new LandPaymentReservation(res.id, res.idempotencyKey, res.expiresAt, res.priceGems));
    }
}
