package com.bigbangcraft.regions.journeymap;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import journeymap.api.v2.server.overlay.IServerOverlayAPI;
import journeymap.api.v2.server.overlay.ServerPolygon;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RegionOverlayManagerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void drawsActiveAndSelectedChunksAsSeparateClippedOverlays() {
        IServerOverlayAPI api = mock(IServerOverlayAPI.class);
        ServerPlayer player = mock(ServerPlayer.class);
        RegionOverlayManager manager = new RegionOverlayManager(api, new ConfigManager(Path.of("build", "test-config")));
        Region region = region();

        manager.showChunkLoaderOverlays(player, region,
            Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0)), Set.of(new ChunkPos(1, 0)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ServerPolygon[]> shown = ArgumentCaptor.forClass(ServerPolygon[].class);
        verify(api, times(2)).show(eq(player), eq("bigbangregions"), shown.capture());
        Map<String, ServerPolygon> overlays = shown.getAllValues().stream()
            .flatMap(polygons -> java.util.Arrays.stream(polygons))
            .collect(java.util.stream.Collectors.toMap(ServerPolygon::overlayId, polygon -> polygon));

        ServerPolygon active = overlays.get("bigbangregions:region/region/chunk-loaders-active");
        ServerPolygon selected = overlays.get("bigbangregions:region/region/chunk-loaders-selected");
        assertEquals(0x43A047, active.props().fillColor());
        assertEquals(0xFFC107, selected.props().fillColor());
        assertEquals(1, active.polygons().size());
        assertEquals(1, selected.polygons().size());
        assertEquals(16, blockX(active.polygons().getFirst().outer().points().getFirst()));
        assertEquals(20, blockX(active.polygons().getFirst().outer().points().get(2)));
        assertTrue(selected.polygons().getFirst().outer().points().stream().allMatch(point -> blockX(point) <= 15));
        assertEquals(8, active.props().minZoom());
        assertEquals(Integer.MAX_VALUE, selected.props().maxZoom());
    }

    @Test
    void splitsRegionOverlayIntoOverviewAndDetailZoomLayers() {
        IServerOverlayAPI api = mock(IServerOverlayAPI.class);
        ServerPlayer player = mock(ServerPlayer.class);
        ConfigManager configManager = new ConfigManager(Path.of("build", "test-config"));
        RegionOverlayManager manager = new RegionOverlayManager(api, configManager);

        manager.showRegionOverlay(player, region(), configManager.getConfig().getJourneyMap().getMemberRegion());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ServerPolygon[]> shown = ArgumentCaptor.forClass(ServerPolygon[].class);
        verify(api, times(1)).show(eq(player), eq("bigbangregions"), shown.capture());
        Map<String, ServerPolygon> overlays = shown.getAllValues().stream()
            .flatMap(polygons -> java.util.Arrays.stream(polygons))
            .collect(java.util.stream.Collectors.toMap(ServerPolygon::overlayId, polygon -> polygon));
        assertEquals(0, overlays.get("bigbangregions:region/region/outline-overview").props().minZoom());
        assertEquals(7, overlays.get("bigbangregions:region/region/outline-overview").props().maxZoom());
        assertEquals(8, overlays.get("bigbangregions:region/region/outline-detail").props().minZoom());
    }

    private static int blockX(long encoded) {
        return (int) (encoded >> 38);
    }

    private static Region region() {
        UUID owner = UUID.randomUUID();
        return new Region("region", "Region", RegionType.PLAYER_REGION,
            new RegionBounds("minecraft:overworld", 1, 0, 0, 20, 255, 15),
            0, owner, owner, 0L, 0L, "ACTIVE", Map.of());
    }
}
