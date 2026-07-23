package com.bigbangcraft.regions.region;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.allocation.PlayerRegionHome;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegionContainmentService {
    private final ConfigManager configManager;
    private final RegionCache regionCache;
    private final RegionRoleResolver roleResolver;
    private final Map<UUID, Vec3> lastSafePositions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastWarningTimes = new ConcurrentHashMap<>();
    private final Map<UUID, PwarpStay> pwarpStays = new ConcurrentHashMap<>();

    public RegionContainmentService(ConfigManager configManager) {
        this(configManager, BigBangRegions.getRegionCache(), BigBangRegions.getRoleResolver());
    }

    public RegionContainmentService(ConfigManager configManager, RegionCache regionCache,
                                    RegionRoleResolver roleResolver) {
        this.configManager = configManager;
        this.regionCache = regionCache;
        this.roleResolver = roleResolver;
    }

    public void tick(ServerPlayer player) {
        if (player == null || player.isRemoved() || player.isDeadOrDying()) {
            return;
        }

        UUID uuid = player.getUUID();
        String currentDim = player.level().dimension().location().toString();
        Vec3 pos = player.position();
        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y);
        int bz = (int) Math.floor(pos.z);
        isAuthorizedPwarpStay(uuid, currentDim, bx, by, bz);
        
        // Check permission bypass
        boolean hasBypass = false;
        try {
            hasBypass = me.lucko.fabric.api.permissions.v0.Permissions.check(player, "bigbangregions.bypass.boundary", false);
            if (!hasBypass) {
                hasBypass = me.lucko.fabric.api.permissions.v0.Permissions.check(player, "bigbangregions.bypass", false);
            }
        } catch (Throwable t) {
            hasBypass = player.hasPermissions(2);
        }

        if (hasBypass) {
            return;
        }

        String targetDim = configManager.getConfig().getPlayerLandAllocation().getTargetDimension();

        if (!currentDim.equals(targetDim)) {
            return;
        }

        // Check if player is inside any region they belong to (own region, member of another's, admin region)
        if (regionCache != null) {
            var regionsAt = regionCache.getRegionsAt(currentDim, bx, by, bz);
            for (Region r : regionsAt) {
                if (r.getType() == RegionType.ADMIN_REGION || r.getType() == RegionType.SYSTEM_REGION) {
                    return; // public area, no containment
                }
                if (r.getType() == RegionType.PLAYER_REGION) {
                    if (isAuthorizedPwarpStay(uuid, currentDim, bx, by, bz, r)) {
                        return;
                    }
                    var role = roleResolver.resolveRole(r, uuid);
                    if (role != RegionRole.VISITOR) {
                        return; // inside own region or region where they're a member
                    }
                    // Allow visitor stay if region has enter=ALLOW
                    if (BigBangRegions.isRegionFlagAllowed(r, "enter")) {
                        return;
                    }
                }
            }
        }

        // Check if inside exploration zone
        if (BigBangRegions.getExplorationZoneService() != null) {
            if (BigBangRegions.getExplorationZoneService().isInsideExplorationZone(currentDim, bx, bz)) {
                lastSafePositions.put(uuid, pos);
                return;
            }
        }

        // Find player's own region for containment
        Region playerRegion = resolvePlayerRegion(uuid);
        if (playerRegion == null) {
            return;
        }

        RegionBounds bounds = playerRegion.getBounds();
        boolean inRegion = bounds.contains(currentDim, bx, by, bz);

        if (inRegion) {
            lastSafePositions.put(uuid, pos);
        } else {
            Vec3 safePos = lastSafePositions.get(uuid);
            if (safePos == null) {
                safePos = getRegionHomePosition(playerRegion.getId(), player);
            }

            player.teleportTo(player.serverLevel(), safePos.x, safePos.y, safePos.z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);

            long now = System.currentTimeMillis();
            Long lastWarning = lastWarningTimes.get(uuid);
            if (lastWarning == null || (now - lastWarning) > 2000) {
                player.sendSystemMessage(Component.literal("§cVocê chegou ao limite do seu terreno."));
                lastWarningTimes.put(uuid, now);
            }
        }
    }

    public void onPlayerJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String targetDim = configManager.getConfig().getPlayerLandAllocation().getTargetDimension();
        String currentDim = player.level().dimension().location().toString();
        if (!currentDim.equals(targetDim)) return;

        Vec3 pos = player.position();
        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y);
        int bz = (int) Math.floor(pos.z);

        // Allow if inside any admin/system region or another player's region
        if (regionCache != null) {
            var regionsAt = regionCache.getRegionsAt(currentDim, bx, by, bz);
            for (Region r : regionsAt) {
                if (r.getType() == RegionType.ADMIN_REGION || r.getType() == RegionType.SYSTEM_REGION) {
                    return;
                }
                if (r.getType() == RegionType.PLAYER_REGION) {
                    var role = roleResolver.resolveRole(r, uuid);
                    if (role != RegionRole.VISITOR) {
                        return;
                    }
                    if (BigBangRegions.isRegionFlagAllowed(r, "enter")) {
                        return;
                    }
                }
            }
        }

        if (BigBangRegions.getExplorationZoneService() != null) {
            if (BigBangRegions.getExplorationZoneService().isInsideExplorationZone(currentDim, bx, bz)) {
                return;
            }
        }

        Region playerRegion = resolvePlayerRegion(uuid);
        if (playerRegion == null) return;

        RegionBounds bounds = playerRegion.getBounds();
        if (!bounds.contains(currentDim, bx, by, bz)) {
            Vec3 safePos = getRegionHomePosition(playerRegion.getId(), player);
            player.teleportTo(player.serverLevel(), safePos.x, safePos.y, safePos.z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.sendSystemMessage(Component.literal("§aVocê foi reposicionado para o spawn do seu terreno."));
        }
    }

    public void removePlayer(UUID uuid) {
        lastSafePositions.remove(uuid);
        lastWarningTimes.remove(uuid);
        pwarpStays.remove(uuid);
    }

    public boolean canCreatePlayerWarp(UUID creatorUuid, String dimension, int x, int y, int z) {
        return isWarpOwnerForEveryPlayerRegion(creatorUuid, dimension, x, y, z);
    }

    public boolean canUsePlayerWarp(UUID warpOwnerUuid, String dimension, int x, int y, int z) {
        return isWarpOwnerForEveryPlayerRegion(warpOwnerUuid, dimension, x, y, z);
    }

    public void recordPlayerWarpArrival(UUID playerUuid, UUID warpOwnerUuid,
                                        String dimension, int x, int y, int z) {
        if (playerUuid == null || !canUsePlayerWarp(warpOwnerUuid, dimension, x, y, z) || regionCache == null) {
            return;
        }

        for (Region region : regionCache.getRegionsAt(dimension, x, y, z)) {
            if (region.getType() == RegionType.PLAYER_REGION) {
                if (roleResolver.resolveRole(region, playerUuid) == RegionRole.VISITOR) {
                    pwarpStays.put(playerUuid, new PwarpStay(region.getId(), dimension));
                }
                return;
            }
        }
    }

    private Region resolvePlayerRegion(UUID uuid) {
        if (regionCache == null) return null;
        
        for (Region r : regionCache.getAll()) {
            if (r.getType() == RegionType.PLAYER_REGION && "ACTIVE".equals(r.getStatus())) {
                var role = roleResolver.resolveRole(r, uuid);
                if (role != RegionRole.VISITOR) {
                    return r;
                }
            }
        }
        return null;
    }

    private Vec3 getRegionHomePosition(String regionId, ServerPlayer player) {
        try {
            PlayerRegionHome home = BigBangRegions.getAllocationCoordinator().getHome(regionId);
            if (home != null) {
                return new Vec3(home.getX(), home.getY(), home.getZ());
            }
        } catch (Exception e) {
            // fallback
        }
        
        Region r = regionCache != null ? regionCache.get(regionId) : null;
        if (r != null) {
            int cx = (r.getBounds().getMinX() + r.getBounds().getMaxX()) / 2;
            int cz = (r.getBounds().getMinZ() + r.getBounds().getMaxZ()) / 2;
            int cy = player.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, cx, cz);
            if (cy <= -64) cy = 64;
            return new Vec3(cx + 0.5, cy, cz + 0.5);
        }
        return new Vec3(0.5, 64, 0.5);
    }

    private boolean isWarpOwnerForEveryPlayerRegion(UUID warpOwnerUuid, String dimension,
                                                     int x, int y, int z) {
        if (regionCache == null) {
            return true;
        }

        for (Region region : regionCache.getRegionsAt(dimension, x, y, z)) {
            if (region.getType() == RegionType.PLAYER_REGION
                    && !java.util.Objects.equals(warpOwnerUuid, region.getOwnerUuid())) {
                return false;
            }
        }
        return true;
    }

    boolean isAuthorizedPwarpStay(UUID playerUuid, String dimension, int x, int y, int z) {
        PwarpStay stay = pwarpStays.get(playerUuid);
        if (stay == null) {
            return false;
        }

        Region region = regionCache != null ? regionCache.get(stay.regionId()) : null;
        if (region == null || !stay.dimension().equals(dimension) || !region.contains(dimension, x, y, z)) {
            pwarpStays.remove(playerUuid, stay);
            return false;
        }
        return true;
    }

    private boolean isAuthorizedPwarpStay(UUID playerUuid, String dimension, int x, int y, int z, Region region) {
        if (!isAuthorizedPwarpStay(playerUuid, dimension, x, y, z)) {
            return false;
        }
        PwarpStay stay = pwarpStays.get(playerUuid);
        return stay != null && stay.regionId().equals(region.getId());
    }

    private record PwarpStay(String regionId, String dimension) {}
}
