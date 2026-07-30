package com.bigbangcraft.regions.expansion;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.payment.FakeLandPaymentGateway;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.storage.DatabaseManager;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RegionExpansionCoordinatorTest {
    @Test
    void workerCompletesExpansionAfterItIsStarted(@TempDir Path directory) throws Exception {
        UUID owner = UUID.randomUUID();
        Config config = new Config();
        config.getRegionExpansion().setEnabled(true);
        config.getRegionExpansion().setPricePerAddedBlock(1);
        config.getRegionExpansion().setAllowedSizes(List.of(100));

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getConfig()).thenReturn(config);
        DatabaseManager database = new DatabaseManager(directory.resolve("regions.db"));
        database.initialize();

        Region region = new Region("region", "region", RegionType.PLAYER_REGION,
            new RegionBounds("minecraft:overworld", 0, 60, 0, 79, 80, 79), 100,
            owner, owner, 1, 1, "ACTIVE");
        RegionCache regionCache = new RegionCache();
        regionCache.add(region);

        PlotSlotRepository slotRepository = mock(PlotSlotRepository.class);
        var slot = mock(com.bigbangcraft.regions.allocation.PlotSlot.class);
        when(slot.getId()).thenReturn("slot");
        when(slot.getMinX()).thenReturn(0);
        when(slot.getMinZ()).thenReturn(0);
        when(slotRepository.getByRegionId("region")).thenReturn(slot);

        FakeLandPaymentGateway gateway = new FakeLandPaymentGateway();
        RegionExpansionOperationRepository repository = new RegionExpansionOperationRepository(database);
        RegionExpansionCoordinator coordinator = new RegionExpansionCoordinator(
            configManager, database, repository,
            mock(RegionRepository.class), slotRepository, regionCache,
            new RegionMembershipCache(), gateway);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(owner);

        try {
            RegionExpansionOperation started = coordinator.beginExpansion(player, 100);
            for (int i = 0; i < 12; i++) {
                coordinator.processNextExpansion();
                Thread.sleep(300);
                RegionExpansionOperation pending = repository.get(started.getOperationId());
                if (pending.getState() == RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING) {
                    assertEquals(0, gateway.getCaptureCallCount());
                    pending.setBorderAppliedAt(System.currentTimeMillis());
                    repository.save(pending);
                    break;
                }
            }

            assertTrue(repository.get(started.getOperationId()).getBorderAppliedAt() != null);
            for (int i = 0; i < 8; i++) {
                coordinator.processNextExpansion();
                Thread.sleep(300);
            }

            RegionExpansionOperation finished = coordinator.getExpansion(started.getOperationId());
            assertEquals(RegionExpansionState.COMPLETED, finished.getState());
            assertEquals(305, region.getBounds().getMaxX());
            assertEquals(305, region.getBounds().getMaxZ());
            assertEquals(1, gateway.getCaptureCallCount());
        } finally {
            coordinator.shutdown();
            database.close();
        }
    }

    @Test
    void asyncExpansionRejectsInsufficientGemsBeforeResize(@TempDir Path directory) throws Exception {
        UUID owner = UUID.randomUUID();
        Config config = new Config();
        config.getRegionExpansion().setEnabled(true);
        config.getRegionExpansion().setPricePerAddedBlock(1);
        config.getRegionExpansion().setAllowedSizes(List.of(100));

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getConfig()).thenReturn(config);
        DatabaseManager database = new DatabaseManager(directory.resolve("regions.db"));
        database.initialize();

        Region region = new Region("region", "region", RegionType.PLAYER_REGION,
            new RegionBounds("minecraft:overworld", 0, 60, 0, 79, 80, 79), 100,
            owner, owner, 1, 1, "ACTIVE");
        RegionCache regionCache = new RegionCache();
        regionCache.add(region);

        PlotSlotRepository slotRepository = mock(PlotSlotRepository.class);
        var slot = mock(com.bigbangcraft.regions.allocation.PlotSlot.class);
        when(slot.getId()).thenReturn("slot");
        when(slot.getMinX()).thenReturn(0);
        when(slot.getMinZ()).thenReturn(0);
        when(slotRepository.getByRegionId("region")).thenReturn(slot);

        FakeLandPaymentGateway gateway = new FakeLandPaymentGateway();
        gateway.setBalance(owner, 0);
        RegionExpansionOperationRepository repository = new RegionExpansionOperationRepository(database);
        RegionExpansionCoordinator coordinator = new RegionExpansionCoordinator(
            configManager, database, repository,
            mock(RegionRepository.class), slotRepository, regionCache,
            new RegionMembershipCache(), gateway);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(owner);

        try {
            CompletableFuture<RegionExpansionOperation> future = coordinator
                .beginExpansionAsync(player, 100).toCompletableFuture();
            for (int i = 0; i < 12 && !future.isDone(); i++) {
                coordinator.processNextExpansion();
                Thread.sleep(100);
            }

            ExecutionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                ExecutionException.class, () -> future.get());
            assertEquals("Você não tem Gems suficientes para realizar essa expansão.",
                failure.getCause().getMessage());
            String state;
            try (PreparedStatement statement = database.getConnection().prepareStatement(
                "SELECT state FROM region_expansion_operations WHERE region_id = ?")) {
                statement.setString(1, "region");
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    state = rows.getString(1);
                }
            }
            assertEquals(RegionExpansionState.CANCELLED_BEFORE_RESIZE.name(), state);
            assertEquals(79, region.getBounds().getMaxX());
            assertEquals(0, gateway.getCaptureCallCount());
        } finally {
            coordinator.shutdown();
            database.close();
        }
    }
}
