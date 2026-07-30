package com.bigbangcraft.regions.chunkloader;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.repository.RegionRepository;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RegionChunkLoaderServiceTest {
    private final UUID owner = UUID.randomUUID();
    private final Set<ChunkPos> selected = new HashSet<>();
    private RegionChunkLoaderService service;
    private RegionRepository repository;
    private ServerPlayer player;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() {
        repository = mock(RegionRepository.class);
        PermissionManager permissions = mock(PermissionManager.class);
        player = mock(ServerPlayer.class);
        Level level = mock(Level.class);
        @SuppressWarnings("unchecked")
        ResourceKey<Level> dimension = mock(ResourceKey.class);
        ResourceLocation location = ResourceLocation.parse("minecraft:overworld");

        when(player.getUUID()).thenReturn(owner);
        when(player.getBlockX()).thenReturn(20);
        when(player.getBlockZ()).thenReturn(20);
        when(player.level()).thenReturn(level);
        when(level.dimension()).thenReturn(dimension);
        when(dimension.location()).thenReturn(location);
        when(repository.loadAll()).thenReturn(List.of(region()));
        when(repository.loadChunkLoaderChunks("region")).thenReturn(selected);
        when(permissions.chunkLoaderPermissionCredits(player)).thenReturn(1);

        service = new RegionChunkLoaderService(repository, permissions);
    }

    @Test
    void activatesCurrentChunkOnceAndRejectsQuotaOrOutsideRegion() {
        assertEquals(RegionChunkLoaderService.ActivationResult.ACTIVATED, service.activateCurrentChunk(player));
        assertEquals(RegionChunkLoaderService.ActivationResult.ALREADY_SELECTED, service.activateCurrentChunk(player));
        verify(repository, times(1)).saveChunkLoaderChunks("region", selected);

        when(player.getBlockX()).thenReturn(40);
        assertEquals(RegionChunkLoaderService.ActivationResult.OUTSIDE_REGION, service.activateCurrentChunk(player));

        selected.clear();
        selected.add(new ChunkPos(0, 0));
        when(player.getBlockX()).thenReturn(20);
        assertEquals(RegionChunkLoaderService.ActivationResult.QUOTA_EXHAUSTED, service.activateCurrentChunk(player));
    }

    @Test
    void activatesTheLaterOwnedRegionContainingThePlayer() {
        Region laterRegion = new Region(
            "later-region", "Later Region", RegionType.PLAYER_REGION,
            new RegionBounds("minecraft:overworld", 32, 0, 0, 63, 255, 31),
            0, owner, owner, 0L, 0L, "ACTIVE", Map.of()
        );
        Set<ChunkPos> laterSelection = new HashSet<>();
        when(player.getBlockX()).thenReturn(40);
        when(repository.loadAll()).thenReturn(List.of(region(), laterRegion));
        when(repository.loadChunkLoaderChunks("later-region")).thenReturn(laterSelection);

        assertEquals(RegionChunkLoaderService.ActivationResult.ACTIVATED, service.activateCurrentChunk(player));
        assertEquals("later-region", service.ownedRegionAt(player).getId());
        verify(repository).saveChunkLoaderChunks("later-region", laterSelection);
    }

    private Region region() {
        return new Region(
            "region", "Region", RegionType.PLAYER_REGION,
            new RegionBounds("minecraft:overworld", 0, 0, 0, 31, 255, 31),
            0, owner, owner, 0L, 0L, "ACTIVE", Map.of()
        );
    }
}
