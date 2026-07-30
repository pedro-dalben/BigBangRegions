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
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.Optional;

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
    private RegionCache regionCache;
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
        regionCache = new RegionCache();
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
    public void createsManualRequestCenteredAtCommandPosition() {
        UUID ownerUuid = UUID.randomUUID();
        BlockPos commandPosition = new BlockPos(3123, 80, -3456);

        String requestId = coordinator.createRequestAt(
            ownerUuid, "planicies", "minecraft:overworld", commandPosition, "test"
        );

        AllocationRequest request = requestRepository.get(requestId);
        PlotSlot slot = new PlotSlotRepository(dbManager).get(request.getPlotSlotId());

        assertEquals(AllocationRequestState.VIRTUAL_VALIDATED, request.getState());
        assertEquals(PlotSlotState.RESERVED, slot.getState());
        int claimSize = configManager.getConfig().getPlayerLandAllocation().getInitialClaimSize();
        int claimOffset = (configManager.getConfig().getPlayerLandAllocation().getSlotSize() - claimSize) / 2;
        int claimMinX = slot.getMinX() + claimOffset;
        int claimMinZ = slot.getMinZ() + claimOffset;
        assertEquals(commandPosition.getX() - claimSize / 2, claimMinX);
        assertEquals(commandPosition.getZ() - claimSize / 2, claimMinZ);
        assertTrue(commandPosition.getX() <= claimMinX + claimSize - 1);
        assertTrue(commandPosition.getZ() <= claimMinZ + claimSize - 1);
    }

    @Test
    public void rejectsManualRequestInsideExplorationExclusion() {
        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> coordinator.createRequestAt(
                UUID.randomUUID(), "planicies", "minecraft:overworld", new BlockPos(123, 80, -456), "test"
            )
        );

        assertTrue(error.getMessage().contains("área de exploração"));
    }

    @Test
    public void createsManualRequestUsingBiomeAtPosition() {
        UUID ownerUuid = UUID.randomUUID();
        BlockPos commandPosition = new BlockPos(4123, 80, -4456);
        ServerLevel level = mock(ServerLevel.class);
        @SuppressWarnings("unchecked")
        Holder<Biome> plains = mock(Holder.class);
        ResourceKey<Biome> plainsKey = ResourceKey.create(Registries.BIOME, ResourceLocation.parse("minecraft:plains"));
        when(plains.unwrapKey()).thenReturn(Optional.of(plainsKey));
        when(level.getBiome(commandPosition)).thenReturn(plains);
        when(level.dimension()).thenReturn(Level.OVERWORLD);

        String requestId = coordinator.createRequestAt(ownerUuid, level, commandPosition, "test");

        AllocationRequest request = requestRepository.get(requestId);
        assertEquals("planicies", request.getRequestedBiomeOption());
        assertEquals(AllocationRequestState.VIRTUAL_VALIDATED, request.getState());
    }

    @Test
    public void rejectsSecondRegionForOwnerWithClearMessage() {
        UUID ownerUuid = UUID.randomUUID();
        regionCache.add(new Region(
            "existing-player-region",
            "Player Region",
            RegionType.PLAYER_REGION,
            new RegionBounds("minecraft:overworld", 3000, -64, 3000, 3079, 320, 3079),
            100,
            ownerUuid,
            ownerUuid,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            "ACTIVE"
        ));

        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> coordinator.createRequestAt(
                ownerUuid, "planicies", "minecraft:overworld", new BlockPos(4123, 80, -4456), "test"
            )
        );

        assertEquals("Você já possui uma região criada e não pode criar outra.", error.getMessage());
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
