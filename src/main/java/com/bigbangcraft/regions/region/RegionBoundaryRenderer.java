package com.bigbangcraft.regions.region;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionRole;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegionBoundaryRenderer {

    private final RegionCache regionCache;
    private final RegionRoleResolver roleResolver;
    private final Set<UUID> visibilityEnabled = ConcurrentHashMap.newKeySet();
    private final int particleInterval = 10;

    private int tickCounter = 0;

    public RegionBoundaryRenderer(RegionCache regionCache, RegionRoleResolver roleResolver) {
        this.regionCache = regionCache;
        this.roleResolver = roleResolver;
    }

    public void setVisibility(UUID playerUuid, boolean enabled) {
        if (enabled) {
            visibilityEnabled.add(playerUuid);
        } else {
            visibilityEnabled.remove(playerUuid);
        }
    }

    public boolean isVisibilityEnabled(UUID playerUuid) {
        return visibilityEnabled.contains(playerUuid);
    }

    public void tick(ServerPlayer player) {
        if (!visibilityEnabled.contains(player.getUUID())) return;
        tickCounter++;
        if (tickCounter % particleInterval != 0) return;

        UUID uuid = player.getUUID();
        String dimension = player.level().dimension().location().toString();

        Region targetRegion = null;
        for (Region r : regionCache.getAll()) {
            if (r.getType() == com.bigbangcraft.regions.domain.RegionType.PLAYER_REGION) {
                RegionRole role = roleResolver.resolveRole(r, uuid);
                if (role == RegionRole.OWNER || role == RegionRole.LEADER) {
                    if (r.getBounds().getDimension().equals(dimension)) {
                        targetRegion = r;
                        break;
                    }
                }
            }
        }
        if (targetRegion == null) return;

        RegionBounds b = targetRegion.getBounds();
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        Vec3 playerPos = player.position();
        double renderDist = 64.0;
        double centerX = (b.getMinX() + b.getMaxX()) / 2.0;
        double centerZ = (b.getMinZ() + b.getMaxZ()) / 2.0;
        double dist = Math.sqrt(Math.pow(playerPos.x - centerX, 2) + Math.pow(playerPos.z - centerZ, 2));
        if (dist > renderDist) return;

        int minX = b.getMinX();
        int maxX = b.getMaxX();
        int minZ = b.getMinZ();
        int maxZ = b.getMaxZ();
        int y = (int) Math.floor(playerPos.y);
        y = Math.max(b.getMinY(), Math.min(b.getMaxY(), y));

        SimpleParticleType particle = ParticleTypes.END_ROD;
        int spacing = 4;
        int countPerSegment = Math.max(1, Math.max(maxX - minX, maxZ - minZ) / spacing);

        spawnLine(serverLevel, player, particle, minX, y, minZ, maxX, y, minZ, countPerSegment);
        spawnLine(serverLevel, player, particle, maxX, y, minZ, maxX, y, maxZ, countPerSegment);
        spawnLine(serverLevel, player, particle, maxX, y, maxZ, minX, y, maxZ, countPerSegment);
        spawnLine(serverLevel, player, particle, minX, y, maxZ, minX, y, minZ, countPerSegment);
    }

    private void spawnLine(ServerLevel level, ServerPlayer player, SimpleParticleType particle,
                           int x1, int y1, int z1, int x2, int y2, int z2, int count) {
        double dx = (double) (x2 - x1) / count;
        double dy = (double) (y2 - y1) / count;
        double dz = (double) (z2 - z1) / count;
        for (int i = 0; i <= count; i++) {
            double px = x1 + dx * i;
            double py = y1 + dy * i + 0.5;
            double pz = z1 + dz * i + 0.5;
            player.connection.send(new ClientboundLevelParticlesPacket(
                particle, false, px, py, pz, 0f, 0f, 0f, 0f, 1
            ));
        }
    }

    public int getTickCounter() {
        return tickCounter;
    }
}
