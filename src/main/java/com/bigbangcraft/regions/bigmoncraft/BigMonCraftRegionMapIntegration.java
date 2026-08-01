package com.bigbangcraft.regions.bigmoncraft;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.chunkloader.RegionChunkLoaderService;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.event.RegionChangeEvent;
import com.bigbangcraft.regions.event.RegionChangeListener;
import com.bigbangcraft.regions.event.RegionEventBus;
import com.bigbangcraft.regions.journeymap.RegionMapIntegration;
import com.bigbangcraft.regions.journeymap.RegionVisibilityResolver;
import com.pedrodalben.bigmoncraft.api.BigMonCraftApi;
import com.pedrodalben.bigmoncraft.api.ServerMapApi;
import com.pedrodalben.bigmoncraft.api.ServerMapSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BigMonCraftRegionMapIntegration implements RegionMapIntegration, RegionChangeListener {
    public static final String SOURCE_ID = "bigbangregions";
    public static final String WAYPOINT_SOURCE_ID = SOURCE_ID + "-waypoints";
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-BigMonCraft");

    private final MinecraftServer server;
    private final RegionCache regionCache;
    private final RegionVisibilityResolver visibilityResolver;
    private final RegionChunkLoaderService chunkLoaderService;

    public BigMonCraftRegionMapIntegration(MinecraftServer server, RegionCache regionCache,
                                           RegionVisibilityResolver visibilityResolver,
                                           RegionChunkLoaderService chunkLoaderService) {
        this.server = server;
        this.regionCache = regionCache;
        this.visibilityResolver = visibilityResolver;
        this.chunkLoaderService = chunkLoaderService;
        RegionEventBus.register(this);
        LOGGER.info("BigBangRegions map source registered");
    }

    @Override
    public void onPlayerJoin(ServerPlayer player) {
        sync(player, null, true);
    }

    @Override
    public void onPlayerDisconnect(ServerPlayer player) {
        ServerMapApi mapApi = BigMonCraftApi.serverMap();
        if (mapApi.isAvailable()) {
            mapApi.clear(player, SOURCE_ID);
            mapApi.clear(player, WAYPOINT_SOURCE_ID);
        }
    }

    @Override
    public void clearAllPlayers(net.minecraft.server.MinecraftServer server) {
        ServerMapApi mapApi = BigMonCraftApi.serverMap();
        if (!mapApi.isAvailable()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                mapApi.clear(player, SOURCE_ID);
                mapApi.clear(player, WAYPOINT_SOURCE_ID);
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to clear map for {}: {}", player.getGameProfile().getName(), exception.getMessage());
            }
        }
    }

    @Override
    public void onRegionChange(RegionChangeEvent event) {
        String excludedId = event.getType() == RegionChangeEvent.ChangeType.DELETED
            ? event.getRegion().getId() : null;
        boolean refreshWaypoints = event.getType() != RegionChangeEvent.ChangeType.RESIZED;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player, excludedId, refreshWaypoints);
        }
    }

    @Override
    public void close() {
        RegionEventBus.unregister(this);
    }

    private void sync(ServerPlayer player, String excludedRegionId, boolean refreshWaypoints) {
        ServerMapApi mapApi = BigMonCraftApi.serverMap();
        if (!mapApi.isAvailable()) return;
        try {
            List<ServerMapSnapshot.Rectangle> rectangles = new ArrayList<>();
            List<ServerMapSnapshot.Waypoint> waypoints = new ArrayList<>();
            for (Region region : visibilityResolver.getVisibleRegions(player, regionCache.getAll())) {
                if (region.getId().equals(excludedRegionId)) continue;
                addRegion(player, region, rectangles, waypoints);
            }
            mapApi.replace(player, SOURCE_ID, new ServerMapSnapshot(rectangles, List.of()));
            if (refreshWaypoints) {
                mapApi.replace(player, WAYPOINT_SOURCE_ID, new ServerMapSnapshot(List.of(), waypoints));
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to sync map for {}: {}", player.getGameProfile().getName(), exception.getMessage());
        }
    }

    private void addRegion(ServerPlayer player, Region region,
                            List<ServerMapSnapshot.Rectangle> rectangles,
                            List<ServerMapSnapshot.Waypoint> waypoints) {
        RegionBounds bounds = region.getBounds();
        Config.JourneyMapConfig.RegionStyle style = visibilityResolver.styleFor(player, region);
        String regionId = safeId(region.getId());
        rectangles.add(new ServerMapSnapshot.Rectangle("region-" + regionId, bounds.getDimension(),
            bounds.getMinX(), bounds.getMinY(), bounds.getMinZ(),
            bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ(), toStyle(style),
            region.getName(), region.getName()));
        waypoints.add(new ServerMapSnapshot.Waypoint("waypoint-" + regionId, bounds.getDimension(),
            (bounds.getMinX() + bounds.getMaxX()) / 2,
            Math.max(bounds.getMinY(), Math.min(64, bounds.getMaxY())),
            (bounds.getMinZ() + bounds.getMaxZ()) / 2,
            region.getName(), style.getFillColor()));

        if (!visibilityResolver.canSeeChunkLoaders(player, region)) return;
        Collection<ChunkPos> active = chunkLoaderService.activeChunks(region);
        List<ChunkPos> selected = new ArrayList<>(chunkLoaderService.selected(region));
        selected.sort(Comparator.comparingInt((ChunkPos chunk) -> chunk.x)
            .thenComparingInt(chunk -> chunk.z));
        for (ChunkPos chunk : selected) {
            int minX = Math.max(bounds.getMinX(), chunk.getMinBlockX());
            int maxX = Math.min(bounds.getMaxX(), chunk.getMaxBlockX());
            int minZ = Math.max(bounds.getMinZ(), chunk.getMinBlockZ());
            int maxZ = Math.min(bounds.getMaxZ(), chunk.getMaxBlockZ());
            if (minX > maxX || minZ > maxZ) continue;
            Config.JourneyMapConfig.RegionStyle chunkStyle = active.contains(chunk)
                ? config().getChunkLoaderActive() : config().getChunkLoaderSelected();
            rectangles.add(new ServerMapSnapshot.Rectangle(
                "chunk-" + regionId + "-" + chunk.x + "-" + chunk.z, bounds.getDimension(),
                minX, bounds.getMinY(), minZ, maxX, bounds.getMaxY(), maxZ, toStyle(chunkStyle),
                "", region.getName()));
        }
    }

    private Config.JourneyMapConfig config() {
        return visibilityResolver.config();
    }

    static ServerMapSnapshot.Style toStyle(Config.JourneyMapConfig.RegionStyle style) {
        return new ServerMapSnapshot.Style(style.getFillColor(), 0.0f,
            style.getStrokeColor(), 2.0f, style.getStrokeOpacity(), 10, 0, Integer.MAX_VALUE);
    }

    private static String safeId(String value) {
        String normalized = value == null ? "region" : value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_.:/-]", "-");
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 72) + "-" + Integer.toHexString(normalized.hashCode());
        }
        return normalized.isBlank() ? "region" : normalized;
    }
}
