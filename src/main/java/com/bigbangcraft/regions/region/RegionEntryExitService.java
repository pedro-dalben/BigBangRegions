package com.bigbangcraft.regions.region;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionRole;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RegionEntryExitService {

    private final RegionCache regionCache;
    private final RegionRoleResolver roleResolver;
    private final ConfigManager configManager;
    private final Map<UUID, Set<String>> playerRegions = new ConcurrentHashMap<>();

    private static final long CHECK_INTERVAL_MS = 1000;
    private final Map<UUID, Long> lastCheckTimes = new ConcurrentHashMap<>();

    public RegionEntryExitService(RegionCache regionCache, RegionRoleResolver roleResolver, ConfigManager configManager) {
        this.regionCache = regionCache;
        this.roleResolver = roleResolver;
        this.configManager = configManager;
    }

    public void tick(ServerPlayer player) {
        if (!configManager.getConfig().getPlayerLandAllocation().getNotifications().isEntryExitEnabled()) {
            return;
        }
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long lastCheck = lastCheckTimes.get(uuid);
        if (lastCheck != null && (now - lastCheck) < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheckTimes.put(uuid, now);

        String dimension = player.level().dimension().location().toString();
        BlockPos pos = player.blockPosition();
        List<Region> currentRegions = regionCache.getRegionsAt(dimension, pos.getX(), pos.getY(), pos.getZ());

        Set<String> currentIds = new HashSet<>();
        Set<String> playerRegionIds = new HashSet<>();
        for (Region r : currentRegions) {
            currentIds.add(r.getId());
            if (r.getType() == com.bigbangcraft.regions.domain.RegionType.PLAYER_REGION) {
                RegionRole role = roleResolver.resolveRole(r, uuid);
                if (role != RegionRole.VISITOR) {
                    playerRegionIds.add(r.getId());
                }
            }
        }

        Set<String> previousIds = playerRegions.getOrDefault(uuid, Collections.emptySet());

        Set<String> entered = new HashSet<>(currentIds);
        entered.removeAll(previousIds);

        Set<String> exited = new HashSet<>(previousIds);
        exited.removeAll(currentIds);

        for (String regionId : entered) {
            Region region = regionCache.get(regionId);
            if (region != null) {
                String msg;
                if (region.getType() == com.bigbangcraft.regions.domain.RegionType.PLAYER_REGION) {
                    RegionRole role = roleResolver.resolveRole(region, uuid);
                    if (role != RegionRole.VISITOR) {
                        msg = "§aEntrou na sua regiao: §f" + getDisplayName(region);
                    } else if (configManager.getConfig().getPlayerLandAllocation().getNotifications().isOtherPlayerEntryEnabled()) {
                        msg = "§eEntrando na regiao de §f" + getOwnerName(region);
                    } else {
                        continue;
                    }
                } else {
                    msg = "§eEntrando em: §f" + getDisplayName(region);
                }
                player.displayClientMessage(Component.literal(msg), true);
            }
        }

        for (String regionId : exited) {
            Region region = regionCache.get(regionId);
            if (region != null) {
                String msg;
                if (region.getType() == com.bigbangcraft.regions.domain.RegionType.PLAYER_REGION) {
                    RegionRole role = roleResolver.resolveRole(region, uuid);
                    if (role != RegionRole.VISITOR) {
                        msg = "§aSaiu da sua regiao: §f" + getDisplayName(region);
                    } else if (configManager.getConfig().getPlayerLandAllocation().getNotifications().isOtherPlayerEntryEnabled()) {
                        msg = "§eSaindo da regiao de §f" + getOwnerName(region);
                    } else {
                        continue;
                    }
                } else {
                    msg = "§eSaindo de: §f" + getDisplayName(region);
                }
                player.displayClientMessage(Component.literal(msg), true);
            }
        }

        playerRegions.put(uuid, currentIds);
    }

    public void removePlayer(UUID uuid) {
        playerRegions.remove(uuid);
        lastCheckTimes.remove(uuid);
    }

    private String getDisplayName(Region region) {
        if (region.getType() == com.bigbangcraft.regions.domain.RegionType.PLAYER_REGION) {
            return "Regiao de " + getOwnerName(region);
        }
        return region.getId();
    }

    private String getOwnerName(Region region) {
        return region.getOwnerUuid() != null ? region.getOwnerUuid().toString().substring(0, 8) : "Desconhecido";
    }
}
