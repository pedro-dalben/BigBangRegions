package com.bigbangcraft.regions.journeymap;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.server.IServerAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegionMarkerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-JM");

    private final IServerAPI serverApi;
    private final ConfigManager configManager;

    // Track waypoint IDs per player per region for clean removal
    private final Map<UUID, Map<String, String>> playerWaypointIds = new HashMap<>();

    public RegionMarkerManager(IServerAPI serverApi, ConfigManager configManager) {
        this.serverApi = serverApi;
        this.configManager = configManager;
    }

    public void showRegionMarker(ServerPlayer player, Region region) {
        try {
            Config.JourneyMapConfig jmConfig = configManager.getConfig().getJourneyMap();
            RegionBounds bounds = region.getBounds();
            int centerX = (bounds.getMinX() + bounds.getMaxX()) / 2;
            int centerZ = (bounds.getMinZ() + bounds.getMaxZ()) / 2;
            int centerY = Math.min(Math.max(bounds.getMinY() + 1, 64), bounds.getMaxY() - 1);

            int color;
            String label;

            if (region.getType() == RegionType.PLAYER_REGION) {
                color = jmConfig.getPlayerRegion().getFillColor();
                label = region.getName();
            } else if (region.getType() == RegionType.ADMIN_REGION) {
                color = jmConfig.getAdminRegion().getFillColor();
                label = region.getName();
            } else {
                color = jmConfig.getMaintenanceRegion().getFillColor();
                label = region.getName();
            }

            removeRegionMarker(player, region);

            Waypoint waypoint = WaypointFactory.createClientWaypoint(
                "bigbangregions",
                new BlockPos(centerX, centerY, centerZ),
                label,
                "§7" + region.getType().name().toLowerCase().replace("_region", ""),
                false
            );
            waypoint.setColor(color);

            serverApi.addPlayerWaypoint(player.getUUID(), waypoint);

            String waypointId = waypoint.getId();
            if (waypointId != null) {
                playerWaypointIds
                    .computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                    .put(region.getId(), waypointId);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to show marker for region {}: {}", region.getId(), e.getMessage());
        }
    }

    public void removeRegionMarker(ServerPlayer player, Region region) {
        try {
            Map<String, String> playerMap = playerWaypointIds.get(player.getUUID());
            if (playerMap != null) {
                String waypointId = playerMap.remove(region.getId());
                if (waypointId != null) {
                    serverApi.deletePlayerWaypoint(player.getUUID(), waypointId);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to remove marker for region {}: {}", region.getId(), e.getMessage());
        }
    }

    public void clearAll(ServerPlayer player) {
        try {
            Map<String, String> playerMap = playerWaypointIds.remove(player.getUUID());
            if (playerMap != null) {
                for (String waypointId : playerMap.values()) {
                    try {
                        serverApi.deletePlayerWaypoint(player.getUUID(), waypointId);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to clear markers for player {}: {}", player.getUUID(), e.getMessage());
        }
    }
}
