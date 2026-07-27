package com.bigbangcraft.regions.payment;

import com.bigbangcraft.regions.payment.api.*;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class LandPaymentAsyncContractTest {
    @Test
    void requestMetadataKeepsActorAndOperationDistinct() {
        UUID operation = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        LandPaymentReserveRequest reserve = new LandPaymentReserveRequest(operation, player, 125, "reserve-key", 300);
        LandPaymentRenewRequest renew = new LandPaymentRenewRequest(operation, actor, "reservation", "renew-key", 2, 300);
        LandPaymentCaptureRequest capture = new LandPaymentCaptureRequest(operation, actor, "reservation", "capture-key");
        LandPaymentReleaseRequest release = new LandPaymentReleaseRequest(operation, actor, "reservation", "release-key");

        assertEquals(player, reserve.getOwnerUuid());
        assertEquals(operation, reserve.getOperationId());
        assertEquals(actor, renew.getActorUuid());
        assertEquals(actor, capture.getActorUuid());
        assertEquals(actor, release.getActorUuid());
        assertEquals("bigbangregions", reserve.getSource());
        assertEquals("player_region_expansion", reserve.getPurpose());
        assertEquals(operation.toString(), reserve.getExternalReference());
    }

    @Test
    void asyncDefaultNeverRequiresCallerToBlock() throws Exception {
        FakeLandPaymentGateway gateway = new FakeLandPaymentGateway();
        UUID operation = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        gateway.setBalance(player, 500);

        CompletionStage<LandPaymentOperationResult> future = gateway.reserveAsync(
            new LandPaymentReserveRequest(operation, player, 100, "async-key", 300));

        assertTrue(future.toCompletableFuture().get().isSuccess());
    }

    @Test
    void idempotencyConflictIsNotTransient() {
        LandPaymentOperationResult conflict = LandPaymentOperationResult.failure(LandPaymentFailure.IDEMPOTENCY_CONFLICT);

        assertFalse(conflict.isTransient());
        assertFalse(conflict.isPermanent());
    }
}
