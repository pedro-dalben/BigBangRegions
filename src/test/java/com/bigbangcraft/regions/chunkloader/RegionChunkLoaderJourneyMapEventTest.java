package com.bigbangcraft.regions.chunkloader;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.event.RegionChangeEvent;
import com.bigbangcraft.regions.event.RegionChangeListener;
import com.bigbangcraft.regions.event.RegionEventBus;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.repository.RegionRepository;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionChunkLoaderJourneyMapEventTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void publishesAfterPersistingAChunkLoaderSelection() {
        UUID owner = UUID.randomUUID();
        Region region = new Region("region", "Region", RegionType.PLAYER_REGION,
            new RegionBounds("minecraft:overworld", 0, 0, 0, 31, 255, 31),
            0, owner, owner, 0L, 0L, "ACTIVE", Map.of());
        RegionRepository repository = mock(RegionRepository.class);
        PermissionManager permissions = mock(PermissionManager.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(owner);
        when(repository.loadAll()).thenReturn(List.of(region));
        when(repository.loadChunkLoaderChunks(region.getId())).thenReturn(new HashSet<>());
        when(permissions.chunkLoaderPermissionCredits(player)).thenReturn(1);

        AtomicInteger updates = new AtomicInteger();
        RegionChangeListener listener = event -> {
            if (event.getType() == RegionChangeEvent.ChangeType.CHUNK_LOADERS_CHANGED
                && event.getRegion().getId().equals(region.getId())) updates.incrementAndGet();
        };
        RegionEventBus.register(listener);
        try {
            RegionChunkLoaderService service = new RegionChunkLoaderService(repository, permissions);
            assertEquals(RegionChunkLoaderService.ActivationResult.ACTIVATED,
                service.activate(player, region, new ChunkPos(1, 1)));
            assertEquals(1, updates.get());
        } finally {
            RegionEventBus.unregister(listener);
        }
    }
}
