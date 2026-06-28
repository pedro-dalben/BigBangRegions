package com.bigbangcraft.regions.payment;

import com.bigbangcraft.regions.payment.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class FakeLandPaymentGatewayTest {

    private FakeLandPaymentGateway gateway;
    private UUID playerUuid;
    private UUID operationId;

    @BeforeEach
    public void setUp() {
        gateway = new FakeLandPaymentGateway();
        playerUuid = UUID.randomUUID();
        operationId = UUID.randomUUID();
        gateway.setBalance(playerUuid, 1000);
    }

    @Test
    public void testReserveSuccess() {
        LandPaymentReserveRequest req = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-reserve-key-1", 300);
        LandPaymentOperationResult result = gateway.reserve(req);
        assertTrue(result.isSuccess());
        assertNotNull(result.getReservationId());
        assertEquals(1, gateway.getReserveCallCount());
    }

    @Test
    public void testReserveInsufficientBalance() {
        gateway.setBalance(playerUuid, 50);
        LandPaymentReserveRequest req = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-reserve-key-2", 300);
        LandPaymentOperationResult result = gateway.reserve(req);
        assertFalse(result.isSuccess());
        assertTrue(result.isInsufficientBalance());
    }

    @Test
    public void testReserveIdempotency() {
        LandPaymentReserveRequest req = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-reserve-idem", 300);
        LandPaymentOperationResult r1 = gateway.reserve(req);
        assertTrue(r1.isSuccess());
        LandPaymentOperationResult r2 = gateway.reserve(req);
        assertTrue(r2.isSuccess());
        assertEquals(r1.getReservationId(), r2.getReservationId());
        assertEquals(2, gateway.getReserveCallCount());
    }

    @Test
    public void testCaptureSuccess() {
        LandPaymentReserveRequest reserveReq = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-capture-key", 300);
        LandPaymentOperationResult reserveResult = gateway.reserve(reserveReq);
        assertTrue(reserveResult.isSuccess());

        LandPaymentCaptureRequest captureReq = new LandPaymentCaptureRequest(
            operationId, reserveResult.getReservationId(), "test-capture-key-1");
        LandPaymentOperationResult result = gateway.capture(captureReq);
        assertTrue(result.isSuccess());
        assertEquals(1, gateway.getCaptureCallCount());
    }

    @Test
    public void testCaptureIdempotency() {
        LandPaymentReserveRequest reserveReq = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-capture-idem", 300);
        LandPaymentOperationResult reserveResult = gateway.reserve(reserveReq);

        LandPaymentCaptureRequest captureReq = new LandPaymentCaptureRequest(
            operationId, reserveResult.getReservationId(), "test-capture-idem-1");
        assertTrue(gateway.capture(captureReq).isSuccess());
        assertTrue(gateway.capture(captureReq).isSuccess());
        assertEquals(2, gateway.getCaptureCallCount());
    }

    @Test
    public void testCaptureNotFound() {
        LandPaymentCaptureRequest captureReq = new LandPaymentCaptureRequest(
            operationId, "nonexistent-reservation", "test-capture-key-3");
        LandPaymentOperationResult result = gateway.capture(captureReq);
        assertFalse(result.isSuccess());
        assertEquals(LandPaymentFailure.RESERVATION_NOT_FOUND, result.getFailure());
    }

    @Test
    public void testRenewSuccess() {
        LandPaymentReserveRequest reserveReq = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-renew-key", 300);
        LandPaymentOperationResult reserveResult = gateway.reserve(reserveReq);

        LandPaymentRenewRequest renewReq = new LandPaymentRenewRequest(
            operationId, reserveResult.getReservationId(), "test-renew-key-1", 1, 600);
        LandPaymentOperationResult result = gateway.renew(renewReq);
        assertTrue(result.isSuccess());
        assertEquals(1, gateway.getRenewCallCount());
    }

    @Test
    public void testRenewAfterCaptureFails() {
        LandPaymentReserveRequest reserveReq = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-renew-after-capture", 300);
        LandPaymentOperationResult reserveResult = gateway.reserve(reserveReq);

        gateway.capture(new LandPaymentCaptureRequest(
            operationId, reserveResult.getReservationId(), "test-renew-cap-key"));
        
        LandPaymentRenewRequest renewReq = new LandPaymentRenewRequest(
            operationId, reserveResult.getReservationId(), "test-renew-key-2", 1, 600);
        LandPaymentOperationResult result = gateway.renew(renewReq);
        assertFalse(result.isSuccess());
        assertEquals(LandPaymentFailure.ALREADY_CAPTURED, result.getFailure());
    }

    @Test
    public void testReleaseSuccess() {
        LandPaymentReserveRequest reserveReq = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-release-key", 300);
        LandPaymentOperationResult reserveResult = gateway.reserve(reserveReq);

        LandPaymentReleaseRequest releaseReq = new LandPaymentReleaseRequest(
            operationId, reserveResult.getReservationId(), "test-release-key-1");
        LandPaymentOperationResult result = gateway.release(releaseReq);
        assertTrue(result.isSuccess());
        assertEquals(1, gateway.getReleaseCallCount());
    }

    @Test
    public void testReleaseIdempotency() {
        LandPaymentReserveRequest reserveReq = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-release-idem", 300);
        LandPaymentOperationResult reserveResult = gateway.reserve(reserveReq);

        LandPaymentReleaseRequest releaseReq = new LandPaymentReleaseRequest(
            operationId, reserveResult.getReservationId(), "test-release-idem-1");
        assertTrue(gateway.release(releaseReq).isSuccess());
        assertTrue(gateway.release(releaseReq).isSuccess());
        assertEquals(2, gateway.getReleaseCallCount());
    }

    @Test
    public void testReleaseAfterCaptureFails() {
        LandPaymentReserveRequest reserveReq = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-release-after-capture", 300);
        LandPaymentOperationResult reserveResult = gateway.reserve(reserveReq);

        gateway.capture(new LandPaymentCaptureRequest(
            operationId, reserveResult.getReservationId(), "test-release-cap-key"));

        LandPaymentReleaseRequest releaseReq = new LandPaymentReleaseRequest(
            operationId, reserveResult.getReservationId(), "test-release-key-2");
        LandPaymentOperationResult result = gateway.release(releaseReq);
        assertFalse(result.isSuccess());
        assertEquals(LandPaymentFailure.ALREADY_CAPTURED, result.getFailure());
    }

    @Test
    public void testProviderUnavailable() {
        gateway.setAvailable(false);
        LandPaymentReserveRequest req = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-unavailable", 300);
        LandPaymentOperationResult result = gateway.reserve(req);
        assertFalse(result.isSuccess());
        assertEquals(LandPaymentFailure.PROVIDER_UNAVAILABLE, result.getFailure());
    }

    @Test
    public void testTransientFailure() {
        gateway.simulateTransientOnAll();
        
        LandPaymentReserveRequest req = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-transient", 300);
        LandPaymentOperationResult result = gateway.reserve(req);
        assertFalse(result.isSuccess());
        assertTrue(result.isTransient());
    }

    @Test
    public void testReserveThenGetByIdempotencyKey() {
        LandPaymentReserveRequest req = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-get-by-idem", 300);
        LandPaymentOperationResult reserveResult = gateway.reserve(req);
        
        var reservation = gateway.getReservationByIdempotencyKey("test-get-by-idem");
        assertTrue(reservation.isPresent());
        assertEquals(reserveResult.getReservationId(), reservation.get().getReservationId());
        assertEquals(100, reservation.get().getPriceGems());
    }

    @Test
    public void testFullSagaFlow() {
        // Reserve
        LandPaymentReserveRequest reserveReq = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-full-saga-reserve", 300);
        LandPaymentOperationResult reserveResult = gateway.reserve(reserveReq);
        assertTrue(reserveResult.isSuccess());

        // Renew
        LandPaymentRenewRequest renewReq = new LandPaymentRenewRequest(
            operationId, reserveResult.getReservationId(), "test-full-saga-renew", 1, 600);
        assertTrue(gateway.renew(renewReq).isSuccess());

        // Capture
        LandPaymentCaptureRequest captureReq = new LandPaymentCaptureRequest(
            operationId, reserveResult.getReservationId(), "test-full-saga-capture");
        assertTrue(gateway.capture(captureReq).isSuccess());

        // Verify captured
        assertTrue(gateway.getReservation(reserveResult.getReservationId()).captured);
        assertEquals(1, gateway.getReserveCallCount());
        assertEquals(1, gateway.getRenewCallCount());
        assertEquals(1, gateway.getCaptureCallCount());
        assertEquals(0, gateway.getReleaseCallCount());
    }

    @Test
    public void testFailNextReserve() {
        gateway.failNextReserve(LandPaymentFailure.TRANSIENT_ERROR);
        LandPaymentReserveRequest req = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-fail-next", 300);
        LandPaymentOperationResult result = gateway.reserve(req);
        assertFalse(result.isSuccess());
        assertTrue(result.isTransient());

        // Second call should succeed
        result = gateway.reserve(req);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testReset() {
        LandPaymentReserveRequest req = new LandPaymentReserveRequest(
            operationId, playerUuid, 100, "test-reset", 300);
        assertTrue(gateway.reserve(req).isSuccess());
        
        gateway.reset();
        assertEquals(0, gateway.getReserveCallCount());
        assertEquals(0, gateway.getCaptureCallCount());
        assertTrue(gateway.isAvailable());
        assertEquals(LandPaymentProviderStatus.AVAILABLE, gateway.getProviderStatus());
    }
}
