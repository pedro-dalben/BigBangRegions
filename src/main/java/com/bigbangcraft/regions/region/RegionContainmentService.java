package com.bigbangcraft.regions.region;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.allocation.PlayerRegionHome;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegionContainmentService {
    private final ConfigManager configManager;
    private final Map<UUID, Vec3> lastSafePositions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastWarningTimes = new ConcurrentHashMap<>();

    public RegionContainmentService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void tick(ServerPlayer player) {
        if (player == null || player.isRemoved() || player.isDeadOrDying()) {
            return;
        }

        UUID uuid = player.getUUID();
        
        // Check permission bypass
        boolean hasBypass = false;
        try {
            hasBypass = me.lucko.fabric.api.permissions.v0.Permissions.check(player, "bigbangregions.bypass.boundary", false);
            if (!hasBypass) {
                hasBypass = me.lucko.fabric.api.permissions.v0.Permissions.check(player, "bigbangregions.bypass", false);
            }
        } catch (Throwable t) {
            // fallback to OP
            hasBypass = player.hasPermissions(2);
        }

        if (hasBypass) {
            return;
        }

        // Find associated region
        Region playerRegion = resolvePlayerRegion(uuid);
        if (playerRegion == null) {
            return;
        }

        String targetDim = configManager.getConfig().getPlayerLandAllocation().getTargetDimension();
        String currentDim = player.level().dimension().location().toString();

        if (!currentDim.equals(targetDim)) {
            return;
        }

        Vec3 pos = player.position();
        RegionBounds bounds = playerRegion.getBounds();

        boolean inRegion = bounds.contains(currentDim, (int) pos.x, (int) pos.y, (int) pos.z);
        boolean inExploration = false;
        if (BigBangRegions.getExplorationZoneService() != null) {
            inExploration = BigBangRegions.getExplorationZoneService().isInsideExplorationZone(currentDim, (int) pos.x, (int) pos.z);
        }

        if (inRegion || inExploration) {
            lastSafePositions.put(uuid, pos);
        } else {
            Vec3 safePos = lastSafePositions.get(uuid);
            if (safePos == null) {
                safePos = getRegionHomePosition(playerRegion.getId(), player);
            }

            // Correct position, reset velocity, and stop momentum
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
        Region playerRegion = resolvePlayerRegion(uuid);
        if (playerRegion == null) return;

        String targetDim = configManager.getConfig().getPlayerLandAllocation().getTargetDimension();
        String currentDim = player.level().dimension().location().toString();

        if (currentDim.equals(targetDim)) {
            Vec3 pos = player.position();
            RegionBounds bounds = playerRegion.getBounds();
            boolean inRegion = bounds.contains(currentDim, (int) pos.x, (int) pos.y, (int) pos.z);
            boolean inExploration = false;
            if (BigBangRegions.getExplorationZoneService() != null) {
                inExploration = BigBangRegions.getExplorationZoneService().isInsideExplorationZone(currentDim, (int) pos.x, (int) pos.z);
            }

            if (!inRegion && !inExploration) {
                Vec3 safePos = getRegionHomePosition(playerRegion.getId(), player);
                player.teleportTo(player.serverLevel(), safePos.x, safePos.y, safePos.z, player.getYRot(), player.getXRot());
                player.setDeltaMovement(Vec3.ZERO);
                player.sendSystemMessage(Component.literal("§aVocê foi reposicionado para o spawn do seu terreno."));
            }
        }
    }

    public void removePlayer(UUID uuid) {
        lastSafePositions.remove(uuid);
        lastWarningTimes.remove(uuid);
    }

    private Region resolvePlayerRegion(UUID uuid) {
        var cache = BigBangRegions.getRegionCache();
        if (cache == null) return null;
        
        for (Region r : cache.getAll()) {
            if (r.getType() == RegionType.PLAYER_REGION && "ACTIVE".equals(r.getStatus())) {
                var role = BigBangRegions.getRoleResolver().resolveRole(r, uuid);
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
        
        Region r = BigBangRegions.getRegionCache().get(regionId);
        if (r != null) {
            int cx = (r.getBounds().getMinX() + r.getBounds().getMaxX()) / 2;
            int cz = (r.getBounds().getMinZ() + r.getBounds().getMaxZ()) / 2;
            int cy = player.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, cx, cz);
            if (cy <= -64) cy = 64;
            return new Vec3(cx + 0.5, cy, cz + 0.5);
        }
        return new Vec3(0.5, 64, 0.5);
    }
}
