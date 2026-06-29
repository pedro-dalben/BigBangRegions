package com.bigbangcraft.regions.journeymap;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.event.RegionChangeEvent;
import com.bigbangcraft.regions.event.RegionChangeListener;
import com.bigbangcraft.regions.event.RegionEventBus;
import com.bigbangcraft.regions.region.RegionRoleResolver;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.IServerPlugin;
import journeymap.api.v2.server.overlay.IServerOverlayAPI;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@JourneyMapPlugin(apiVersion = "2.0.0")
public class JourneyMapRegionsPlugin implements IServerPlugin, RegionChangeListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-JM");

    private IServerAPI jmServerApi;
    private IServerOverlayAPI overlayApi;
    private ConfigManager configManager;
    private RegionVisibilityResolver visibilityResolver;
    private RegionOverlayManager overlayManager;
    private RegionMarkerManager markerManager;
    private MinecraftServer server;

    @Override
    public String getModId() {
        return "bigbangregions";
    }

    @Override
    public void initialize(IServerAPI api) {
        if (!BigBangRegions.getConfigManager().getConfig().getJourneyMap().isEnabled()) {
            LOGGER.info("JourneyMap integration disabled via config.");
            return;
        }

        this.jmServerApi = api;
        this.overlayApi = api.getOverlayApi();
        this.configManager = BigBangRegions.getConfigManager();

        RegionRoleResolver roleResolver = BigBangRegions.getRoleResolver();
        var membershipCache = BigBangRegions.getMembershipCache();

        this.visibilityResolver = new RegionVisibilityResolver(configManager, roleResolver, membershipCache);
        this.overlayManager = new RegionOverlayManager(overlayApi, configManager);
        this.markerManager = new RegionMarkerManager(jmServerApi, configManager);

        RegionEventBus.register(this);
        BigBangRegions.setRegionMapIntegration(this::onPlayerJoin);

        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            this.server = srv;
            LOGGER.info("JourneyMap region integration active. {} regions cached.", BigBangRegions.getRegionCache().getAll().size());
            for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
                syncPlayerRegions(player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            if (handler.getPlayer() != null && this.server != null) {
                syncPlayerRegions(handler.getPlayer());
            }
        });

        LOGGER.info("JourneyMap region overlay integration initialized.");
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (jmServerApi != null && configManager.getConfig().getJourneyMap().isEnabled()) {
            syncPlayerRegions(player);
        }
    }

    @Override
    public void onRegionChange(RegionChangeEvent event) {
        if (jmServerApi == null || server == null) return;

        Region region = event.getRegion();
        switch (event.getType()) {
            case CREATED, UPDATED, RESIZED, RENAMED, STATUS_CHANGED, OWNER_TRANSFERRED -> {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (visibilityResolver.canSeeRegion(player, region)) {
                        addRegionForPlayer(player, region);
                    } else {
                        removeRegionFromPlayer(player, region);
                    }
                }
            }
            case DELETED -> {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    removeRegionFromPlayer(player, region);
                }
            }
            case MEMBER_JOINED -> {
                UUID playerUuid = event.getAffectedPlayer();
                if (playerUuid != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
                    if (player != null && visibilityResolver.canSeeRegion(player, region)) {
                        addRegionForPlayer(player, region);
                    }
                }
            }
            case MEMBER_REMOVED -> {
                UUID playerUuid = event.getAffectedPlayer();
                if (playerUuid != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
                    if (player != null) {
                        removeRegionFromPlayer(player, region);
                    }
                }
            }
            case ROLE_CHANGED -> {
                UUID playerUuid = event.getAffectedPlayer();
                if (playerUuid != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
                    if (player != null) {
                        if (visibilityResolver.canSeeRegion(player, region)) {
                            addRegionForPlayer(player, region);
                        } else {
                            removeRegionFromPlayer(player, region);
                        }
                    }
                }
            }
        }
    }

    private void syncPlayerRegions(ServerPlayer player) {
        if (overlayApi == null) return;

        overlayApi.clearAll(player, getModId());

        List<Region> visible = visibilityResolver.getVisibleRegions(player, BigBangRegions.getRegionCache().getAll());
        for (Region region : visible) {
            addRegionForPlayer(player, region);
        }
    }

    private void addRegionForPlayer(ServerPlayer player, Region region) {
        if (overlayManager != null) {
            overlayManager.showRegionOverlay(player, region);
        }
        if (markerManager != null) {
            markerManager.showRegionMarker(player, region);
        }
    }

    private void removeRegionFromPlayer(ServerPlayer player, Region region) {
        if (overlayManager != null) {
            overlayManager.removeRegionOverlay(player, region);
        }
        if (markerManager != null) {
            markerManager.removeRegionMarker(player, region);
        }
    }
}
