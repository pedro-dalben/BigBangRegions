package com.bigbangcraft.regions.recovery;

import com.bigbangcraft.regions.allocation.AllocationRequest;
import com.bigbangcraft.regions.allocation.AllocationRequestState;
import com.bigbangcraft.regions.allocation.PlotSlot;
import com.bigbangcraft.regions.allocation.PlotSlotState;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.payment.FakeLandPaymentGateway;
import com.bigbangcraft.regions.payment.api.*;
import com.bigbangcraft.regions.repository.AllocationRequestRepository;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LandOperationRecoveryServiceTest {

    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;
    private AllocationRequestRepository requestRepository;
    private PlotSlotRepository slotRepository;
    private RegionCache regionCache;
    private FakeLandPaymentGateway paymentGateway;
    private LandOperationRecoveryService recoveryService;
    private UUID ownerUuid;
    private String requestId;

    @BeforeEach
    public void setUp() throws Exception {
        dbManager = new DatabaseManager(tempDir.resolve("test_recovery.db"));
        dbManager.initialize();
        requestRepository = new AllocationRequestRepository(dbManager);
        slotRepository = new PlotSlotRepository(dbManager);
        regionCache = new RegionCache();
        paymentGateway = new FakeLandPaymentGateway();

        ConfigManager configManager = new ConfigManager(tempDir);
        recoveryService = new LandOperationRecoveryService(
            requestRepository, slotRepository, regionCache, paymentGateway, configManager
        );

        ownerUuid = UUID.randomUUID();
        requestId = UUID.randomUUID().toString();
    }

    @Test
    public void testRecoverRegionCreatingWithExistingRegion() {
        String regionId = "test_region_1";
        String plotSlotId = "dim:0:0";

        // Create a region in cache (simulating it was created before crash)
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, -64, 0, 49, 320, 49);
        Region region = new Region(regionId, "Test Region", RegionType.PLAYER_REGION,
            bounds, 100, ownerUuid, ownerUuid, 1000, 1000, "PENDING_PAYMENT");
        regionCache.add(region);

        // Create request stuck in REGION_CREATING
        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.REGION_CREATING, "player", ownerUuid,
            regionId, plotSlotId, null, 0, 1000, 1000, null, null
        );
        requestRepository.save(request);

        recoveryService.recover();

        // Should advance to REGION_CREATED_PAYMENT_CAPTURE_PENDING
        AllocationRequest recovered = requestRepository.get(requestId);
        assertNotNull(recovered);
        assertEquals(AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING, recovered.getState());
    }

    @Test
    public void testRecoverRegionCreatingWithoutRegion() {
        String plotSlotId = "dim:5:5";

        // Create a reserved slot
        PlotSlot slot = new PlotSlot(plotSlotId, "minecraft:overworld", 5, 5,
            1280, 1280, 256, PlotSlotState.RESERVED, ownerUuid,
            null, "planicies", 1000L, 10000L, null, 1000L, 1000L);
        slotRepository.save(slot);

        // Create request stuck in REGION_CREATING without a region (crash before world ops)
        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.REGION_CREATING, "player", ownerUuid,
            null, plotSlotId, "Regiao nao criada", 0, 1000, 1000, null, null
        );
        requestRepository.save(request);

        recoveryService.recover();

        AllocationRequest recovered = requestRepository.get(requestId);
        assertNotNull(recovered);
        assertTrue(recovered.getState().isTerminal());
    }

    @Test
    public void testRecoverCapturePendingWithExistingRegion() {
        String regionId = "test_region_2";
        String plotSlotId = "dim:10:10";

        // Create region in cache
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, -64, 0, 49, 320, 49);
        Region region = new Region(regionId, "Test Region", RegionType.PLAYER_REGION,
            bounds, 100, ownerUuid, ownerUuid, 1000, 1000, "PENDING_PAYMENT");
        regionCache.add(region);

        // Reserve payment
        LandPaymentReserveRequest reserveReq = new LandPaymentReserveRequest(
            UUID.fromString(requestId), ownerUuid, 100, "test-recover-cap-key", 300);
        paymentGateway.setBalance(ownerUuid, 1000);
        LandPaymentOperationResult reserveResult = paymentGateway.reserve(reserveReq);

        // Create request stuck in REGION_CREATED_PAYMENT_CAPTURE_PENDING
        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING, "player", ownerUuid,
            regionId, plotSlotId, null, 0, 1000, 1000, null, null
        );
        request.setGemsReservationId(reserveResult.getReservationId());
        request.setCaptureIdempotencyKey("test-recover-cap-key-1");
        requestRepository.save(request);

        recoveryService.recover();

        // Should schedule retry (nextRetryAt should be set)
        AllocationRequest recovered = requestRepository.get(requestId);
        assertNotNull(recovered);
        assertEquals(AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING, recovered.getState());
        assertNotNull(recovered.getNextRetryAt());
    }

    @Test
    public void testRecoverReleasePendingWithReservation() {
        String plotSlotId = "dim:15:15";

        // Create a reserved slot
        PlotSlot slot = new PlotSlot(plotSlotId, "minecraft:overworld", 15, 15,
            3840, 3840, 256, PlotSlotState.RESERVED, ownerUuid,
            null, "planicies", 1000L, 10000L, null, 1000L, 1000L);
        slotRepository.save(slot);

        // Reserve payment
        LandPaymentReserveRequest reserveReq = new LandPaymentReserveRequest(
            UUID.fromString(requestId), ownerUuid, 100, "test-recover-rel-key", 300);
        paymentGateway.setBalance(ownerUuid, 1000);
        LandPaymentOperationResult reserveResult = paymentGateway.reserve(reserveReq);

        // Create request stuck in RELEASE_PENDING
        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.RELEASE_PENDING, "player", ownerUuid,
            null, plotSlotId, null, 0, 1000, 1000, null, null
        );
        request.setGemsReservationId(reserveResult.getReservationId());
        requestRepository.save(request);

        recoveryService.recover();

        // Should release payment and cancel
        assertTrue(paymentGateway.isReservationReleased(reserveResult.getReservationId()));
        AllocationRequest recovered = requestRepository.get(requestId);
        assertNotNull(recovered);
        assertEquals(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION, recovered.getState());
        assertNull(recovered.getGemsReservationId());
    }

    @Test
    public void testRecoverReleasePendingWithoutReservation() {
        String plotSlotId = "dim:20:20";

        // Create request stuck in RELEASE_PENDING without reservation
        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.RELEASE_PENDING, "player", ownerUuid,
            null, plotSlotId, null, 0, 1000, 1000, null, null
        );
        requestRepository.save(request);

        recoveryService.recover();

        // Should move to CANCELLED_BEFORE_REGION_CREATION
        AllocationRequest recovered = requestRepository.get(requestId);
        assertNotNull(recovered);
        assertEquals(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION, recovered.getState());
    }

    @Test
    public void testRecoverWithBlockedStateDoesNothing() {
        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION, "player", ownerUuid,
            null, null, "Manual block", 0, 1000, 1000, null, null
        );
        requestRepository.save(request);

        recoveryService.recover();

        AllocationRequest recovered = requestRepository.get(requestId);
        assertNotNull(recovered);
        assertEquals(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION, recovered.getState());
    }

    @Test
    public void testRecoverWithCompleteStateDoesNothing() {
        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.COMPLETED, "player", ownerUuid,
            null, null, null, 0, 1000, 1000, 2000L, null
        );
        request.setPaymentCapturedAt(1500L);
        requestRepository.save(request);

        recoveryService.recover();

        AllocationRequest recovered = requestRepository.get(requestId);
        assertNotNull(recovered);
        assertEquals(AllocationRequestState.COMPLETED, recovered.getState());
    }

    @Test
    public void testRecoverMultipleRequests() {
        String regionId1 = "multi_region_1";
        String regionId2 = "multi_region_2";

        // Create cache regions
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, -64, 0, 49, 320, 49);
        regionCache.add(new Region(regionId1, "R1", RegionType.PLAYER_REGION, bounds, 100, ownerUuid, ownerUuid, 1000, 1000, "PENDING_PAYMENT"));
        regionCache.add(new Region(regionId2, "R2", RegionType.PLAYER_REGION, bounds, 100, ownerUuid, ownerUuid, 1000, 1000, "ACTIVE"));

        // Request 1: REGION_CREATING with region (should advance to capture pending)
        AllocationRequest r1 = new AllocationRequest(
            UUID.randomUUID().toString(), ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.REGION_CREATING, "player", ownerUuid,
            regionId1, "slot:0:1", null, 0, 1000, 1000, null, null
        );
        requestRepository.save(r1);

        // Request 2: REGION_CREATED_PAYMENT_CAPTURE_PENDING (should schedule retry)
        AllocationRequest r2 = new AllocationRequest(
            UUID.randomUUID().toString(), ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING, "player", ownerUuid,
            regionId2, "slot:0:2", null, 0, 1000, 1000, null, null
        );
        r2.setCaptureIdempotencyKey("multi-cap-key");
        requestRepository.save(r2);

        recoveryService.recover();

        AllocationRequest recovered1 = requestRepository.get(r1.getId());
        assertEquals(AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING, recovered1.getState());

        AllocationRequest recovered2 = requestRepository.get(r2.getId());
        assertEquals(AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING, recovered2.getState());
        assertNotNull(recovered2.getNextRetryAt());
    }

    @Test
    public void testRecoverRegionCreatingNoRegionId() {
        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.REGION_CREATING, "player", ownerUuid,
            null, null, null, 0, 1000, 1000, null, null
        );
        requestRepository.save(request);

        recoveryService.recover();

        AllocationRequest recovered = requestRepository.get(requestId);
        assertEquals(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION, recovered.getState());
    }

    @Test
    public void testRecoverCapturePendingNoRegionId() {
        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.REGION_CREATED_PAYMENT_CAPTURE_PENDING, "player", ownerUuid,
            null, null, null, 0, 1000, 1000, null, null
        );
        requestRepository.save(request);

        recoveryService.recover();

        AllocationRequest recovered = requestRepository.get(requestId);
        assertEquals(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION, recovered.getState());
    }
}
