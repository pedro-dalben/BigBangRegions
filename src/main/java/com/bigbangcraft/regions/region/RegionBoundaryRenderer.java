package com.bigbangcraft.regions.region;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.chunkloader.RegionChunkLoaderService;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionRole;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegionBoundaryRenderer {

    private final RegionCache regionCache;
    private final RegionRoleResolver roleResolver;
    private final RegionChunkLoaderService chunkLoaderService;
    private final Set<UUID> boundaryVisibilityEnabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> chunkVisibilityEnabled = ConcurrentHashMap.newKeySet();
    private static final int PARTICLE_INTERVAL = 10;
    private static final int CHUNK_VIEW_RADIUS = 2;
    private static final int CHUNK_GRID_SPACING = 8;

    private int tickCounter = 0;

    public RegionBoundaryRenderer(RegionCache regionCache, RegionRoleResolver roleResolver,
                                  RegionChunkLoaderService chunkLoaderService) {
        this.regionCache = regionCache;
        this.roleResolver = roleResolver;
        this.chunkLoaderService = chunkLoaderService;
    }

    public void setVisibility(UUID playerUuid, boolean enabled) {
        if (enabled) {
            boundaryVisibilityEnabled.add(playerUuid);
        } else {
            boundaryVisibilityEnabled.remove(playerUuid);
        }
    }

    public boolean isVisibilityEnabled(UUID playerUuid) {
        return boundaryVisibilityEnabled.contains(playerUuid);
    }

    public void setChunkVisibility(UUID playerUuid, boolean enabled) {
        if (enabled) {
            chunkVisibilityEnabled.add(playerUuid);
        } else {
            chunkVisibilityEnabled.remove(playerUuid);
        }
    }

    public boolean isChunkVisibilityEnabled(UUID playerUuid) {
        return chunkVisibilityEnabled.contains(playerUuid);
    }

    public void clearVisibility(UUID playerUuid) {
        boundaryVisibilityEnabled.remove(playerUuid);
        chunkVisibilityEnabled.remove(playerUuid);
    }

    public void tick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!boundaryVisibilityEnabled.contains(uuid) && !chunkVisibilityEnabled.contains(uuid)) return;
        tickCounter++;
        if (player.getServer() == null || player.getServer().getTickCount() % PARTICLE_INTERVAL != 0) return;

        String dimension = player.level().dimension().location().toString();
        if (!(player.level() instanceof ServerLevel)) return;

        if (boundaryVisibilityEnabled.contains(uuid)) {
            Region targetRegion = ownedOrLedRegion(uuid, dimension);
            if (targetRegion != null) renderBoundary(player, targetRegion);
        }
        if (chunkVisibilityEnabled.contains(uuid)) {
            renderChunkGrid(player, dimension);
        }
    }

    private Region ownedOrLedRegion(UUID uuid, String dimension) {
        for (Region r : regionCache.getAll()) {
            if (r.getType() == com.bigbangcraft.regions.domain.RegionType.PLAYER_REGION) {
                RegionRole role = roleResolver.resolveRole(r, uuid);
                if (role == RegionRole.OWNER || role == RegionRole.LEADER) {
                    if (r.getBounds().getDimension().equals(dimension)) {
                        return r;
                    }
                }
            }
        }
        return null;
    }

    private void renderBoundary(ServerPlayer player, Region targetRegion) {
        RegionBounds b = targetRegion.getBounds();

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

        spawnLine(player, particle, minX, y, minZ, maxX, y, minZ, countPerSegment);
        spawnLine(player, particle, maxX, y, minZ, maxX, y, maxZ, countPerSegment);
        spawnLine(player, particle, maxX, y, maxZ, minX, y, maxZ, countPerSegment);
        spawnLine(player, particle, minX, y, maxZ, minX, y, minZ, countPerSegment);
    }

    private void renderChunkGrid(ServerPlayer player, String dimension) {
        ChunkPos currentChunk = new ChunkPos(player.getBlockX() >> 4, player.getBlockZ() >> 4);
        Region region = ownedRegionAt(player.getUUID(), dimension, currentChunk);
        if (region == null) return;

        RegionBounds bounds = region.getBounds();
        int minChunkX = Math.max(bounds.getMinX() >> 4, currentChunk.x - CHUNK_VIEW_RADIUS);
        int maxChunkX = Math.min(bounds.getMaxX() >> 4, currentChunk.x + CHUNK_VIEW_RADIUS);
        int minChunkZ = Math.max(bounds.getMinZ() >> 4, currentChunk.z - CHUNK_VIEW_RADIUS);
        int maxChunkZ = Math.min(bounds.getMaxZ() >> 4, currentChunk.z + CHUNK_VIEW_RADIUS);
        int y = Math.max(bounds.getMinY(), Math.min(bounds.getMaxY(), player.getBlockY()));

        for (int chunkX = minChunkX; chunkX <= maxChunkX + 1; chunkX++) {
            spawnLine(player, ParticleTypes.END_ROD, chunkX * 16, y, minChunkZ * 16,
                chunkX * 16, y, (maxChunkZ + 1) * 16, Math.max(1, (maxChunkZ - minChunkZ + 1) * 16 / CHUNK_GRID_SPACING));
        }
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ + 1; chunkZ++) {
            spawnLine(player, ParticleTypes.END_ROD, minChunkX * 16, y, chunkZ * 16,
                (maxChunkX + 1) * 16, y, chunkZ * 16, Math.max(1, (maxChunkX - minChunkX + 1) * 16 / CHUNK_GRID_SPACING));
        }

        Set<ChunkPos> selected = chunkLoaderService.selected(region);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                if (chunk.equals(currentChunk)) {
                    spawnMarker(player, ParticleTypes.SOUL_FIRE_FLAME, chunk, y);
                } else if (chunkLoaderService.isActive(region, chunk)) {
                    spawnMarker(player, ParticleTypes.HAPPY_VILLAGER, chunk, y);
                } else if (selected.contains(chunk)) {
                    spawnMarker(player, ParticleTypes.FLAME, chunk, y);
                }
            }
        }
    }

    private Region ownedRegionAt(UUID ownerUuid, String dimension, ChunkPos chunk) {
        for (Region region : regionCache.getAll()) {
            if (region.getType() != com.bigbangcraft.regions.domain.RegionType.PLAYER_REGION
                || !"ACTIVE".equals(region.getStatus()) || !ownerUuid.equals(region.getOwnerUuid())) continue;
            RegionBounds bounds = region.getBounds();
            if (bounds.getDimension().equals(dimension)
                && chunk.x >= (bounds.getMinX() >> 4) && chunk.x <= (bounds.getMaxX() >> 4)
                && chunk.z >= (bounds.getMinZ() >> 4) && chunk.z <= (bounds.getMaxZ() >> 4)) return region;
        }
        return null;
    }

    private void spawnMarker(ServerPlayer player, SimpleParticleType particle, ChunkPos chunk, int y) {
        double x = chunk.x * 16 + 8.5;
        double z = chunk.z * 16 + 8.5;
        for (int height = 1; height <= 3; height++) {
            player.connection.send(new ClientboundLevelParticlesPacket(
                particle, false, x, y + height, z, 0f, 0f, 0f, 0f, 1
            ));
        }
    }

    private void spawnLine(ServerPlayer player, SimpleParticleType particle,
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
