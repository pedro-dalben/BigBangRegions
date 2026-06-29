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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        try {
            Config.JourneyMapConfig jmConfig = configManager.getConfig().getJourneyMap();
            int fillColor;
            int strokeColor;
            float fillOpacity;
            float strokeOpacity;

            if (region.getType() == RegionType.ADMIN_REGION) {
                fillColor = jmConfig.getAdminRegion().getFillColor();
                strokeColor = jmConfig.getAdminRegion().getStrokeColor();
                fillOpacity = jmConfig.getAdminRegion().getFillOpacity();
                strokeOpacity = jmConfig.getAdminRegion().getStrokeOpacity();
            } else if (region.getType() == RegionType.SYSTEM_REGION) {
                fillColor = jmConfig.getMaintenanceRegion().getFillColor();
                strokeColor = jmConfig.getMaintenanceRegion().getStrokeColor();
                fillOpacity = jmConfig.getMaintenanceRegion().getFillOpacity();
                strokeOpacity = jmConfig.getMaintenanceRegion().getStrokeOpacity();
            } else {
                fillColor = jmConfig.getPlayerRegion().getFillColor();
                strokeColor = jmConfig.getPlayerRegion().getStrokeColor();
                fillOpacity = jmConfig.getPlayerRegion().getFillOpacity();
                strokeOpacity = jmConfig.getPlayerRegion().getStrokeOpacity();

                if (!"ACTIVE".equals(region.getStatus())) {
                    fillColor = jmConfig.getBlockedRegion().getFillColor();
                    strokeColor = jmConfig.getBlockedRegion().getStrokeColor();
                    fillOpacity = jmConfig.getBlockedRegion().getFillOpacity();
                    strokeOpacity = jmConfig.getBlockedRegion().getStrokeOpacity();
                }
            }

            RegionBounds bounds = region.getBounds();
            int minX = bounds.getMinX();
            int minZ = bounds.getMinZ();
            int maxX = bounds.getMaxX();
            int maxZ = bounds.getMaxZ();

            ResourceKey<net.minecraft.world.level.Level> dimensionKey = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(bounds.getDimension())
            );

            String overlayId = "bigbangregions:region/" + region.getId() + "/outline";

            OverlayShapeProps props = new OverlayShapeProps(
                fillColor, fillOpacity,
                strokeColor, 2.0f, strokeOpacity,
                10, 0, Integer.MAX_VALUE,
                Set.of(), Set.of(),
                "", region.getName()
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

            ServerPolygon serverPolygon = new ServerPolygon(
                overlayId, dimensionKey, List.of(rectangle), props
            );

            overlayApi.show(player, getModId(), serverPolygon);
        } catch (Exception e) {
            LOGGER.error("Failed to show overlay for region {} to player {}: {}",
                region.getId(), player.getName().getString(), e.getMessage());
        }
    }

    public void removeRegionOverlay(ServerPlayer player, Region region) {
        try {
            overlayApi.remove(player, getModId(), "bigbangregions:region/" + region.getId() + "/outline");
        } catch (Exception e) {
            LOGGER.error("Failed to remove overlay for region {}: {}", region.getId(), e.getMessage());
        }
    }

    private static long encodeBlockPos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38
             | ((long) z & 0x3FFFFFF) << 12
             | ((long) y & 0xFFF);
    }

    private static String getModId() {
        return "bigbangregions";
    }
}
