package com.bigbangcraft.regions.expansion;

import com.bigbangcraft.regions.payment.api.LandPaymentOperationResult;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RegionExpansionOperationPersistenceTest {
    @Test
    void checkpointKeysAndCountersSurviveReload(@TempDir Path directory) throws Exception {
        DatabaseManager database = new DatabaseManager(directory.resolve("regions.db"));
        database.initialize();
        try {
            String operationId = UUID.randomUUID().toString();
            RegionExpansionOperation operation = new RegionExpansionOperation(
                operationId, "region", UUID.randomUUID(), "slot", "minecraft:overworld",
                100, 125, 0, 0, 99, 99, -12, -12, 111, 111,
                5000, 1, RegionExpansionState.PAYMENT_RENEW_PENDING, System.currentTimeMillis());
            operation.setReserveIdempotencyKey("reserve-key");
            operation.setGemsReservationId(UUID.randomUUID().toString());
            operation.setRenewIdempotencyKey("renew-key-sequence-2");
            operation.setRenewSequence(2);
            operation.setCaptureIdempotencyKey("capture-key");
            operation.setReleaseIdempotencyKey("release-key");
            operation.setRetryCount(4);
            operation.setNextRetryAt(12345L);

            RegionExpansionOperationRepository repository = new RegionExpansionOperationRepository(database);
            repository.save(operation);
            RegionExpansionOperation reloaded = repository.get(operationId);

            assertNotNull(reloaded);
            assertEquals("reserve-key", reloaded.getReserveIdempotencyKey());
            assertEquals("renew-key-sequence-2", reloaded.getRenewIdempotencyKey());
            assertEquals(2, reloaded.getRenewSequence());
            assertEquals(4, reloaded.getRetryCount());
            assertEquals(12345L, reloaded.getNextRetryAt());
            assertEquals("capture-key", reloaded.getCaptureIdempotencyKey());
            assertEquals("release-key", reloaded.getReleaseIdempotencyKey());
        } finally {
            database.close();
        }
    }
}
