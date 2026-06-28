package com.bigbangcraft.regions.recovery;

import com.bigbangcraft.regions.allocation.AllocationRequest;
import com.bigbangcraft.regions.allocation.AllocationRequestState;
import com.bigbangcraft.regions.allocation.PlotSlot;
import com.bigbangcraft.regions.allocation.PlotSlotState;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.payment.FakeLandPaymentGateway;
import com.bigbangcraft.regions.payment.api.*;
import com.bigbangcraft.regions.repository.AllocationRequestRepository;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LandOperationRecoveryServiceTest {

    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;
    private AllocationRequestRepository requestRepository;
    private PlotSlotRepository slotRepository;
    private RegionRepository regionRepository;
    private RegionCache regionCache;
    private RegionMembershipCache membershipCache;
    private LandOperationRecoveryService recoveryService;
    private UUID ownerUuid;
    private String requestId;

    @BeforeEach
    public void setUp() throws Exception {
        dbManager = new DatabaseManager(tempDir.resolve("test_recovery.db"));
        dbManager.initialize();
        requestRepository = new AllocationRequestRepository(dbManager);
        slotRepository = new PlotSlotRepository(dbManager);
        regionRepository = new RegionRepository(dbManager);
        regionCache = new RegionCache();
        membershipCache = new RegionMembershipCache();

        recoveryService = new LandOperationRecoveryService(
            requestRepository, slotRepository, regionRepository,
            regionCache, membershipCache
        );

        ownerUuid = UUID.randomUUID();
        requestId = UUID.randomUUID().toString();
    }

    @Test
    public void testRecoverRegionCreatingWithExistingRegion() {
        String regionId = "test_region_1";
        String plotSlotId = "dim:0:0";

        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, -64, 0, 49, 320, 49);
        Region region = new Region(regionId, "Test Region", RegionType.PLAYER_REGION,
            bounds, 100, ownerUuid, ownerUuid, 1000, 1000, "ACTIVE");
        regionRepository.save(region);
        regionCache.add(region);

        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.REGION_CREATING, "player", ownerUuid,
            regionId, plotSlotId, null, 0, 1000, 1000, null, null
        );
        requestRepository.save(request);

        recoveryService.recover();

        AllocationRequest recovered = requestRepository.get(requestId);
        assertNotNull(recovered);
        assertEquals(AllocationRequestState.COMPLETED, recovered.getState());
    }

    @Test
    public void testRecoverRegionCreatingWithoutRegion() {
        String plotSlotId = "dim:5:5";

        PlotSlot slot = new PlotSlot(plotSlotId, "minecraft:overworld", 5, 5,
            1280, 1280, 256, PlotSlotState.RESERVED, ownerUuid,
            null, "planicies", 1000L, 10000L, null, 1000L, 1000L);
        slotRepository.save(slot);

        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.REGION_CREATING, "player", ownerUuid,
            "nonexistent_region", plotSlotId, "Regiao nao criada", 0, 1000, 1000, null, null
        );
        requestRepository.save(request);

        recoveryService.recover();

        AllocationRequest recovered = requestRepository.get(requestId);
        assertNotNull(recovered);
        assertTrue(recovered.getState().isTerminal() || recovered.getState() == AllocationRequestState.REGION_CREATING);
    }

    @Test
    public void testRecoverCapturePendingWithExistingRegion() {
        String regionId = "test_region_2";
        String plotSlotId = "dim:10:10";

        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, -64, 0, 49, 320, 49);
        Region region = new Region(regionId, "Test Region", RegionType.PLAYER_REGION,
            bounds, 100, ownerUuid, ownerUuid, 1000, 1000, "ACTIVE");
        regionRepository.save(region);
        regionCache.add(region);

        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.REGION_CREATING, "player", ownerUuid,
            regionId, plotSlotId, null, 0, 1000, 1000, null, null
        );
        requestRepository.save(request);

        recoveryService.recover();

        AllocationRequest recovered = requestRepository.get(requestId);
        assertNotNull(recovered);
        assertEquals(AllocationRequestState.COMPLETED, recovered.getState());
    }

    @Test
    public void testRecoverReleasePendingWithReservation() {
        String plotSlotId = "dim:15:15";

        PlotSlot slot = new PlotSlot(plotSlotId, "minecraft:overworld", 15, 15,
            3840, 3840, 256, PlotSlotState.RESERVED, ownerUuid,
            null, "planicies", 1000L, 10000L, null, 1000L, 1000L);
        slotRepository.save(slot);

        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.RELEASE_PENDING, "player", ownerUuid,
            null, plotSlotId, null, 0, 1000, 1000, null, null
        );
        requestRepository.save(request);

        recoveryService.recover();

        AllocationRequest recovered = requestRepository.get(requestId);
        assertNotNull(recovered);
        assertEquals(AllocationRequestState.LEGACY_REQUIRES_ADMIN_REVIEW, recovered.getState());
    }

    @Test
    public void testRecoverReleasePendingWithoutReservation() {
        String plotSlotId = "dim:20:20";

        AllocationRequest request = new AllocationRequest(
            requestId, ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.RELEASE_PENDING, "player", ownerUuid,
            null, plotSlotId, null, 0, 1000, 1000, null, null
        );
        requestRepository.save(request);

        recoveryService.recover();

        AllocationRequest recovered = requestRepository.get(requestId);
        assertNotNull(recovered);
        assertEquals(AllocationRequestState.LEGACY_REQUIRES_ADMIN_REVIEW, recovered.getState());
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

        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, -64, 0, 49, 320, 49);
        Region r1 = new Region(regionId1, "R1", RegionType.PLAYER_REGION, bounds, 100, ownerUuid, ownerUuid, 1000, 1000, "ACTIVE");
        Region r2 = new Region(regionId2, "R2", RegionType.PLAYER_REGION, bounds, 100, ownerUuid, ownerUuid, 1000, 1000, "ACTIVE");
        regionRepository.save(r1);
        regionRepository.save(r2);
        regionCache.add(r1);
        regionCache.add(r2);

        AllocationRequest req1 = new AllocationRequest(
            UUID.randomUUID().toString(), ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.REGION_CREATING, "player", ownerUuid,
            regionId1, "slot:0:1", null, 0, 1000, 1000, null, null
        );
        requestRepository.save(req1);

        AllocationRequest req2 = new AllocationRequest(
            UUID.randomUUID().toString(), ownerUuid, "planicies", "minecraft:overworld",
            AllocationRequestState.REGION_CREATING, "player", ownerUuid,
            regionId2, "slot:0:2", null, 0, 1000, 1000, null, null
        );
        requestRepository.save(req2);

        recoveryService.recover();

        AllocationRequest recovered1 = requestRepository.get(req1.getId());
        assertEquals(AllocationRequestState.COMPLETED, recovered1.getState());

        AllocationRequest recovered2 = requestRepository.get(req2.getId());
        assertEquals(AllocationRequestState.COMPLETED, recovered2.getState());
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
            AllocationRequestState.REGION_CREATING, "player", ownerUuid,
            null, null, null, 0, 1000, 1000, null, null
        );
        requestRepository.save(request);

        recoveryService.recover();

        AllocationRequest recovered = requestRepository.get(requestId);
        assertEquals(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION, recovered.getState());
    }
}
