package com.bigbangcraft.regions.chunkloader;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.repository.RegionRepository;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RegionChunkLoaderService {
    private static final TicketType<UUID> TICKET = TicketType.create(
        "bigbangregions_chunkloader", Comparator.comparing(UUID::toString));
    private static final int TICKET_LEVEL = 2;

    private final RegionRepository repository;
    private final PermissionManager permissions;
    private final Map<String, Set<ChunkPos>> active = new ConcurrentHashMap<>();

    public RegionChunkLoaderService(RegionRepository repository, PermissionManager permissions) {
        this.repository = repository;
        this.permissions = permissions;
    }

    public int quota(ServerPlayer player) {
        Region region = ownedRegion(player.getUUID());
        return region == null ? 0 : permissions.chunkLoaderPermissionCredits(player)
            + repository.getChunkLoaderExtraCredits(player.getUUID());
    }

    public int permissionCredits(ServerPlayer player) { return permissions.chunkLoaderPermissionCredits(player); }

    public int extraCredits(UUID owner) { return repository.getChunkLoaderExtraCredits(owner); }

    public Region ownedRegionFor(UUID owner) { return ownedRegion(owner); }

    public int regionChunkCount(Region region) {
        var b = region.getBounds();
        return ((b.getMaxX() >> 4) - (b.getMinX() >> 4) + 1)
            * ((b.getMaxZ() >> 4) - (b.getMinZ() >> 4) + 1);
    }

    public int loadedCount(Region region) { return active.getOrDefault(region.getId(), Set.of()).size(); }

    public int selectedCount(Region region) { return repository.loadChunkLoaderChunks(region.getId()).size(); }

    public Set<ChunkPos> selected(Region region) { return repository.loadChunkLoaderChunks(region.getId()); }

    public boolean toggle(ServerPlayer player, Region region, ChunkPos chunk) {
        if (!isOwner(player, region) || !inside(region, chunk)) return false;
        Set<ChunkPos> selected = repository.loadChunkLoaderChunks(region.getId());
        if (selected.remove(chunk)) {
            repository.saveChunkLoaderChunks(region.getId(), selected);
            if (active.containsKey(region.getId())) release(player.getServer(), region, Set.of(chunk));
            return true;
        }
        if (selected.size() >= quota(player)) return false;
        selected.add(chunk);
        repository.saveChunkLoaderChunks(region.getId(), selected);
        if (player.getServer() != null) acquire(player.getServer(), region, Set.of(chunk));
        return true;
    }

    public void addCredits(UUID owner, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        repository.addChunkLoaderExtraCredits(owner, amount);
    }

    public void onJoin(ServerPlayer player) {
        for (Region region : ownedRegions(player.getUUID())) {
            Set<ChunkPos> selected = selected(region).stream()
                .sorted(Comparator.comparingInt((ChunkPos c) -> c.x).thenComparingInt(c -> c.z))
                .limit(Math.max(0, quota(player)))
                .collect(java.util.stream.Collectors.toSet());
            if (!selected.isEmpty()) acquire(player.getServer(), region, selected);
        }
    }

    public void onDisconnect(ServerPlayer player) {
        for (Region region : ownedRegions(player.getUUID())) release(player.getServer(), region, active.getOrDefault(region.getId(), Set.of()));
    }

    public void shutdown(MinecraftServer server) {
        for (Region region : new ArrayList<>(allActiveRegions())) release(server, region, active.getOrDefault(region.getId(), Set.of()));
        active.clear();
    }

    public void onRegionDeleted(MinecraftServer server, Region region) {
        release(server, region, active.getOrDefault(region.getId(), Set.of()));
    }

    private void acquire(MinecraftServer server, Region region, Set<ChunkPos> chunks) {
        ServerLevel level = level(server, region);
        if (level == null || chunks.isEmpty()) return;
        Set<ChunkPos> current = active.computeIfAbsent(region.getId(), ignored -> ConcurrentHashMap.newKeySet());
        for (ChunkPos chunk : chunks) if (current.add(chunk)) level.getChunkSource().addRegionTicket(TICKET, chunk, TICKET_LEVEL, region.getOwnerUuid());
    }

    private void release(MinecraftServer server, Region region, Set<ChunkPos> chunks) {
        ServerLevel level = level(server, region);
        if (level == null) return;
        Set<ChunkPos> current = active.get(region.getId());
        if (current == null) return;
        for (ChunkPos chunk : chunks) if (current.remove(chunk)) level.getChunkSource().removeRegionTicket(TICKET, chunk, TICKET_LEVEL, region.getOwnerUuid());
        if (current.isEmpty()) active.remove(region.getId());
    }

    private ServerLevel level(MinecraftServer server, Region region) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(region.getBounds().getDimension()));
        return server.getLevel(key);
    }

    private boolean isOwner(ServerPlayer player, Region region) {
        return region != null && region.getType() == RegionType.PLAYER_REGION && player.getUUID().equals(region.getOwnerUuid());
    }

    private boolean inside(Region region, ChunkPos chunk) {
        var b = region.getBounds();
        return chunk.x >= (b.getMinX() >> 4) && chunk.x <= (b.getMaxX() >> 4)
            && chunk.z >= (b.getMinZ() >> 4) && chunk.z <= (b.getMaxZ() >> 4);
    }

    private Region ownedRegion(UUID owner) {
        return ownedRegions(owner).stream().findFirst().orElse(null);
    }

    private List<Region> ownedRegions(UUID owner) {
        return repository.loadAll().stream()
            .filter(r -> r.getType() == RegionType.PLAYER_REGION && "ACTIVE".equals(r.getStatus()) && owner.equals(r.getOwnerUuid()))
            .toList();
    }

    private Set<Region> allActiveRegions() {
        Set<Region> regions = new HashSet<>();
        for (String id : active.keySet()) {
            Region region = repository.loadAll().stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
            if (region != null) regions.add(region);
        }
        return regions;
    }
}
