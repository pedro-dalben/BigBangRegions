package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.repository.AllocationRequestRepository;
import com.bigbangcraft.regions.repository.PlayerRegionHomeRepository;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.storage.DatabaseManager;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TerrainAllocationCoordinatorDimensionTest {
    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;
    private ConfigManager configManager;
    private AllocationRequestRepository requestRepository;
    private TerrainAllocationCoordinator coordinator;

    @BeforeAll
    public static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    public void setUp() throws Exception {
        dbManager = new DatabaseManager(tempDir.resolve("allocation-dimension.db"));
        dbManager.initialize();

        configManager = new ConfigManager(tempDir);
        requestRepository = new AllocationRequestRepository(dbManager);
        PlotSlotRepository plotSlotRepository = new PlotSlotRepository(dbManager);
        PlayerRegionHomeRepository homeRepository = new PlayerRegionHomeRepository(dbManager);
        RegionRepository regionRepository = new RegionRepository(dbManager);
        RegionCache regionCache = new RegionCache();
        RegionMembershipCache membershipCache = new RegionMembershipCache();
        BiomeOptionRegistry biomeOptionRegistry = new BiomeOptionRegistry(configManager);
        biomeOptionRegistry.load();

        coordinator = new TerrainAllocationCoordinator(
            configManager,
            dbManager,
            requestRepository,
            plotSlotRepository,
            new PlotSlotService(configManager, plotSlotRepository, regionCache),
            homeRepository,
            regionRepository,
            new BiomeSearchService(configManager),
            biomeOptionRegistry,
            regionCache,
            membershipCache
        );
    }

    @AfterEach
    public void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    public void failsRequestWhenStoredTargetDimensionIsUnavailable() {
        AllocationRequest request = new AllocationRequest(
            UUID.randomUUID().toString(),
            UUID.randomUUID(),
            "oceano",
            "missing:test_dimension",
            AllocationRequestState.PENDING,
            "test",
            null,
            null,
            null,
            null,
            0,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            null,
            null
        );
        requestRepository.save(request);

        MinecraftServer server = mock(MinecraftServer.class);

        assertEquals(1, coordinator.processNextRequest(server));

        AllocationRequest reloaded = requestRepository.get(request.getId());
        assertEquals(AllocationRequestState.FAILED_VALIDATION, reloaded.getState());
        assertTrue(reloaded.getFailureReason().contains("Dimensao alvo indisponivel"));
        assertTrue(reloaded.getFailureReason().contains("missing:test_dimension"));
    }

    @Test
    public void usesDimensionStoredOnRequestInsteadOfCurrentConfig() {
        configManager.getConfig().getPlayerLandAllocation().setTargetDimension("missing:config_dimension");

        AllocationRequest request = new AllocationRequest(
            UUID.randomUUID().toString(),
            UUID.randomUUID(),
            "oceano",
            "minecraft:overworld",
            AllocationRequestState.PENDING,
            "test",
            null,
            null,
            null,
            null,
            0,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            null,
            null
        );
        requestRepository.save(request);

        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel overworld = mock(ServerLevel.class);
        ResourceKey<Level> overworldKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        when(server.getLevel(eq(overworldKey))).thenReturn(overworld);

        assertEquals(1, coordinator.processNextRequest(server));

        AllocationRequest reloaded = requestRepository.get(request.getId());
        assertEquals(AllocationRequestState.VIRTUAL_SEARCHING, reloaded.getState());
    }

    @Test
    public void orphanedPausedRecoveryRequestIsRetiredAndDoesNotBlockNewRequest() {
        UUID ownerUuid = UUID.randomUUID();
        AllocationRequest orphaned = new AllocationRequest(
            UUID.randomUUID().toString(),
            ownerUuid,
            "planicies",
            "minecraft:overworld",
            AllocationRequestState.PAUSED_RECOVERY,
            "test",
            ownerUuid,
            null,
            null,
            "Recuperacao: falha na criacao da regiao.",
            0,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            null,
            null
        );
        requestRepository.save(orphaned);

        assertNull(coordinator.getActiveRequest(ownerUuid));

        AllocationRequest cleaned = requestRepository.get(orphaned.getId());
        assertEquals(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION, cleaned.getState());

        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(ownerUuid);

        assertDoesNotThrow(() -> coordinator.createRequest(player, "planicies", "test"));
        assertTrue(coordinator.getActiveRequest(ownerUuid) != null);
    }

    @Test
    public void playerDeleteCooldownBlocksDeletionDuringFirstHour() {
        UUID ownerUuid = UUID.randomUUID();
        Region region = new Region(
            "player_region_test",
            "Player Region",
            RegionType.PLAYER_REGION,
            new RegionBounds("minecraft:overworld", 0, -64, 0, 49, 320, 49),
            100,
            ownerUuid,
            ownerUuid,
            System.currentTimeMillis() - (30L * 60L * 1000L),
            System.currentTimeMillis() - (30L * 60L * 1000L),
            "ACTIVE"
        );

        assertTrue(coordinator.getPlayerRegionDeleteCooldownRemainingMillis(region) > 0);
        assertFalse(coordinator.canDeleteOwnPlayerRegion(ownerUuid, region));
    }

    @Test
    public void virtualSearchTimesOutAfterFiveMinutes() {
        UUID ownerUuid = UUID.randomUUID();
        AllocationRequest request = new AllocationRequest(
            UUID.randomUUID().toString(),
            ownerUuid,
            "oceano",
            "minecraft:overworld",
            AllocationRequestState.VIRTUAL_SEARCHING,
            "test",
            ownerUuid,
            null,
            null,
            null,
            0,
            System.currentTimeMillis() - (6L * 60L * 1000L),
            System.currentTimeMillis() - (6L * 60L * 1000L),
            null,
            null
        );
        requestRepository.save(request);

        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel overworld = mock(ServerLevel.class);
        ResourceKey<Level> overworldKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        when(server.getLevel(eq(overworldKey))).thenReturn(overworld);

        assertEquals(1, coordinator.processNextRequest(server));

        AllocationRequest reloaded = requestRepository.get(request.getId());
        assertEquals(AllocationRequestState.FAILED_NO_TERRAIN, reloaded.getState());
        assertTrue(reloaded.getFailureReason().contains("Tempo limite excedido durante busca"));
    }
}
