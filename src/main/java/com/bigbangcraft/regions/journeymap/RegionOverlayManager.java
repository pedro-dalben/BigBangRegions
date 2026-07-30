package com.bigbangcraft.regions.journeymap;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import journeymap.api.v2.server.overlay.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class RegionOverlayManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-JM");

    private final IServerOverlayAPI overlayApi;
    private final ConfigManager configManager;

    public RegionOverlayManager(IServerOverlayAPI overlayApi, ConfigManager configManager) {
        this.overlayApi = overlayApi;
        this.configManager = configManager;
    }

    public void showRegionOverlay(ServerPlayer player, Region region) {
        showRegionOverlay(player, region, null);
    }

    public void showRegionOverlay(ServerPlayer player, Region region,
                                  Config.JourneyMapConfig.RegionStyle visualStyle) {
        try {
            Config.JourneyMapConfig jmConfig = configManager.getConfig().getJourneyMap();
            Config.JourneyMapConfig.RegionStyle style = visualStyle != null
                ? visualStyle : defaultStyle(jmConfig, region);

            RegionBounds bounds = region.getBounds();
            int minX = bounds.getMinX();
            int minZ = bounds.getMinZ();
            int maxX = bounds.getMaxX();
            int maxZ = bounds.getMaxZ();

            ResourceKey<net.minecraft.world.level.Level> dimensionKey = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(bounds.getDimension())
            );

            OverlayPolygon rectangle = new OverlayPolygon(
                new OverlayPoints(List.of(
                    encodeBlockPos(minX, 64, minZ),
                    encodeBlockPos(maxX, 64, minZ),
                    encodeBlockPos(maxX, 64, maxZ),
                    encodeBlockPos(minX, 64, maxZ)
                )),
                List.of()
            );

            // Remove the pre-zoom overlay id used by older builds before replacing it.
            removeOverlay(player, overlayId(region, "outline"));
            overlayApi.show(player, getModId(),
                new ServerPolygon(overlayId(region, "outline-overview"), dimensionKey,
                    List.of(rectangle), new OverlayShapeProps(
                        rgb(style.getFillColor()), Math.min(style.getFillOpacity(), 0.05f),
                        rgb(style.getStrokeColor()), 2.0f, style.getStrokeOpacity(),
                        10, 0, 7, Set.of(), Set.of(), region.getName(), region.getName()
                    )),
                new ServerPolygon(overlayId(region, "outline-detail"), dimensionKey,
                    List.of(rectangle), new OverlayShapeProps(
                        rgb(style.getFillColor()), style.getFillOpacity(),
                        rgb(style.getStrokeColor()), 2.0f, style.getStrokeOpacity(),
                        10, 8, Integer.MAX_VALUE, Set.of(), Set.of(), "", region.getName()
                    ))
            );
        } catch (Exception e) {
            LOGGER.error("Failed to show overlay for region {} to player {}: {}",
                region.getId(), player.getName().getString(), e.getMessage());
        }
    }

    private static Config.JourneyMapConfig.RegionStyle defaultStyle(
        Config.JourneyMapConfig config, Region region) {
        if (region.getType() == RegionType.ADMIN_REGION) return config.getAdminRegion();
        if (region.getType() == RegionType.SYSTEM_REGION) return config.getMaintenanceRegion();
        return "ACTIVE".equals(region.getStatus()) ? config.getPlayerRegion() : config.getBlockedRegion();
    }

    public void removeRegionOverlay(ServerPlayer player, Region region) {
        removeOverlay(player, overlayId(region, "outline"));
        removeOverlay(player, overlayId(region, "outline-overview"));
        removeOverlay(player, overlayId(region, "outline-detail"));
        removeChunkLoaderOverlays(player, region);
    }

    public void showChunkLoaderOverlays(ServerPlayer player, Region region,
                                        Set<ChunkPos> selected, Set<ChunkPos> active) {
        List<ChunkPos> activeChunks = selected.stream()
            .filter(active::contains)
            .sorted(Comparator.comparingInt((ChunkPos chunk) -> chunk.x).thenComparingInt(chunk -> chunk.z))
            .toList();
        List<ChunkPos> selectedOnly = selected.stream()
            .filter(chunk -> !active.contains(chunk))
            .sorted(Comparator.comparingInt((ChunkPos chunk) -> chunk.x).thenComparingInt(chunk -> chunk.z))
            .toList();
        Config.JourneyMapConfig config = configManager.getConfig().getJourneyMap();
        showChunkLoaderOverlay(player, region, "chunk-loaders-active", activeChunks,
            config.getChunkLoaderActive(), "Chunk loaders ativos");
        showChunkLoaderOverlay(player, region, "chunk-loaders-selected", selectedOnly,
            config.getChunkLoaderSelected(), "Chunk loaders selecionados");
    }

    public void removeChunkLoaderOverlays(ServerPlayer player, Region region) {
        removeOverlay(player, overlayId(region, "chunk-loaders-active"));
        removeOverlay(player, overlayId(region, "chunk-loaders-selected"));
    }

    private void showChunkLoaderOverlay(ServerPlayer player, Region region, String suffix,
                                        List<ChunkPos> chunks, Config.JourneyMapConfig.RegionStyle style,
                                        String title) {
        String id = overlayId(region, suffix);
        if (chunks.isEmpty()) {
            removeOverlay(player, id);
            return;
        }
        try {
            RegionBounds bounds = region.getBounds();
            List<OverlayPolygon> tiles = new ArrayList<>();
            for (ChunkPos chunk : chunks) {
                int minX = Math.max(bounds.getMinX(), chunk.x * 16);
                int maxX = Math.min(bounds.getMaxX(), chunk.x * 16 + 15);
                int minZ = Math.max(bounds.getMinZ(), chunk.z * 16);
                int maxZ = Math.min(bounds.getMaxZ(), chunk.z * 16 + 15);
                if (minX <= maxX && minZ <= maxZ) {
                    tiles.add(rectangle(minX, minZ, maxX, maxZ));
                }
            }
            if (tiles.isEmpty()) {
                removeOverlay(player, id);
                return;
            }
            ResourceKey<net.minecraft.world.level.Level> dimension = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(bounds.getDimension())
            );
            OverlayShapeProps props = new OverlayShapeProps(
                rgb(style.getFillColor()), style.getFillOpacity(),
                rgb(style.getStrokeColor()), 1.0f, style.getStrokeOpacity(),
                20, 8, Integer.MAX_VALUE,
                Set.of(), Set.of(), "", region.getName() + " - " + title
            );
            overlayApi.show(player, getModId(), new ServerPolygon(id, dimension, tiles, props));
        } catch (Exception e) {
            LOGGER.error("Failed to show {} for region {} to player {}: {}",
                suffix, region.getId(), player.getName().getString(), e.getMessage());
        }
    }

    private void removeOverlay(ServerPlayer player, String id) {
        try {
            overlayApi.remove(player, getModId(), id);
        } catch (Exception e) {
            LOGGER.error("Failed to remove JourneyMap overlay {}: {}", id, e.getMessage());
        }
    }

    private static String overlayId(Region region, String suffix) {
        return "bigbangregions:region/" + region.getId() + "/" + suffix;
    }

    private static OverlayPolygon rectangle(int minX, int minZ, int maxX, int maxZ) {
        return new OverlayPolygon(new OverlayPoints(List.of(
            encodeBlockPos(minX, 64, minZ),
            encodeBlockPos(maxX, 64, minZ),
            encodeBlockPos(maxX, 64, maxZ),
            encodeBlockPos(minX, 64, maxZ)
        )), List.of());
    }

    private static long encodeBlockPos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38
             | ((long) z & 0x3FFFFFF) << 12
             | ((long) y & 0xFFF);
    }

    private static int rgb(int color) {
        return color & 0xFFFFFF;
    }

    private static String getModId() {
        return "bigbangregions";
    }
}
