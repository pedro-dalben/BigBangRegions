package com.bigbangcraft.regions.virtualpasture;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.allocation.AllocationMetrics;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.region.RegionResolver;
import com.bigbangcraft.regions.repository.VirtualPastureRepository;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces placement from a durable position index. It deliberately only inspects chunks that
 * Minecraft already loaded; a count must never cause a chunk load.
 */
public final class VirtualPastureService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-VirtualPasture");
    private static final long RESERVATION_MILLIS = 15_000L;

    public record PlacementDecision(boolean allowed, boolean tracked, int current, int maximum, String reason) {
        static PlacementDecision pass() { return new PlacementDecision(true, false, 0, 0, null); }
        static PlacementDecision accepted(int current, int maximum) { return new PlacementDecision(true, true, current, maximum, null); }
        static PlacementDecision denied(int current, int maximum, String reason) { return new PlacementDecision(false, false, current, maximum, reason); }
    }

    private record Change(ServerLevel level, BlockPos pos, boolean pasture) { }

    private final ConfigManager configManager;
    private final VirtualPastureRepository repository;
    private final RegionCache regionCache;
    private final PermissionManager permissions;
    private final Map<String, VirtualPastureRecord> records = new HashMap<>();
    private final Map<String, Set<String>> byRegion = new HashMap<>();
    private final Map<UUID, Set<String>> byOwner = new HashMap<>();
    private final Map<String, Set<String>> byChunk = new HashMap<>();
    private final Map<String, Change> changes = new HashMap<>();
    private final Map<String, LevelChunk> loadedChunks = new HashMap<>();
    private ResourceLocation virtualPastureId;
    private boolean available;
    private boolean warnedUnavailable;

    public VirtualPastureService(ConfigManager configManager, VirtualPastureRepository repository,
                                 RegionCache regionCache, PermissionManager permissions) {
        this.configManager = configManager;
        this.repository = repository;
        this.regionCache = regionCache;
        this.permissions = permissions;
        reload();
    }

    public void reload() {
        Config.VirtualPastureConfig config = configManager.getConfig().getVirtualPasture();
        available = config.isEnabled() && FabricLoader.getInstance().isModLoaded("virtualloot");
        virtualPastureId = null;
        if (available) {
            try {
                ResourceLocation id = ResourceLocation.parse(config.getBlockId());
                if (BuiltInRegistries.BLOCK.containsKey(id)) virtualPastureId = id;
                else LOGGER.warn("Virtual Pasture block '{}' is not registered; protection is disabled.", id);
            } catch (RuntimeException invalid) {
                LOGGER.warn("Virtual Pasture block id '{}' is invalid; protection is disabled.", config.getBlockId());
            }
        }
        available = available && virtualPastureId != null;
        warnedUnavailable = false;
        repository.deleteExpiredPending(System.currentTimeMillis());
        rebuild(repository.loadAll());
        if (available) LOGGER.info("Virtual Pasture limit active for {}.", virtualPastureId);
    }

    public boolean isVirtualPasture(BlockState state) {
        return state != null && isVirtualPasture(state.getBlock());
    }

    public boolean isVirtualPasture(Block block) {
        return available && block != null && virtualPastureId.equals(BuiltInRegistries.BLOCK.getKey(block));
    }

    public boolean tracksPosition(ServerLevel level, BlockPos pos) {
        return available && level != null && pos != null
            && records.containsKey(positionKey(level.dimension().location().toString(), pos.asLong()));
    }

    public PlacementDecision reserve(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!available) {
            if (configManager.getConfig().getVirtualPasture().isEnabled() && !warnedUnavailable) {
                warnedUnavailable = true;
                LOGGER.info("Virtual Pasture limiting is inactive because VirtualLoot or its registered block is unavailable.");
            }
            return PlacementDecision.pass();
        }
        Region region = regionAt(level, pos);
        if (region == null) return PlacementDecision.pass();
        Config.VirtualPastureConfig config = configManager.getConfig().getVirtualPasture();
        UUID owner = region.getOwnerUuid();
        boolean bypass = permissions.hasPermission(player, config.getAdminBypassPermission());
        int regionCount = count(byRegion.get(region.getId()));
        int ownerCount = owner == null ? 0 : count(byOwner.get(owner));
        int chunkCount = count(byChunk.get(chunkKey(level, pos)));
        // A member's VIP node must not raise the owner's cap. Offline owner tiers cannot be queried
        // safely, so members use the default cap until the owner places the next pasture.
        int ownerLimit = owner != null && owner.equals(player.getUUID()) ? ownerLimit(player, config)
            : config.getLimits().getOrDefault("default", config.getMaxPerPlayer());
        VirtualPastureLimitPolicy.Decision limits = VirtualPastureLimitPolicy.check(
            new VirtualPastureLimitPolicy.Counts(regionCount, ownerCount, chunkCount), config.getMaxPerRegion(),
            ownerLimit, config.getMaxPerChunk(), owner != null, bypass);
        if (!limits.allowed()) {
            AllocationMetrics.increment("bigbangregions_virtual_pasture_denied_total");
            return PlacementDecision.denied(limits.current(), limits.maximum(), limits.scope());
        }

        VirtualPastureRecord record = record(level, pos, region, VirtualPastureRecord.State.PENDING,
            System.currentTimeMillis() + RESERVATION_MILLIS);
        try {
            repository.upsert(record); // Fail closed: vanilla placement is not invoked when this fails.
            put(record);
            AllocationMetrics.increment("bigbangregions_virtual_pasture_reservations_total");
            return PlacementDecision.accepted(ownerCount, ownerLimit);
        } catch (IllegalStateException error) {
            LOGGER.warn("Virtual Pasture placement was denied because its reservation could not be persisted.", error);
            return PlacementDecision.denied(0, 0, "banco de dados indisponível");
        }
    }

    public void recordWorldChange(ServerLevel level, BlockPos pos, boolean pasture) {
        if (level == null || pos == null || !available) return;
        changes.put(positionKey(level.dimension().location().toString(), pos.asLong()), new Change(level, pos.immutable(), pasture));
    }

    public void tick() {
        expireReservations();
        if (changes.isEmpty()) return;
        List<Change> pending = new ArrayList<>(changes.values());
        changes.clear();
        for (Change change : pending) {
            String dimension = change.level().dimension().location().toString();
            String key = positionKey(dimension, change.pos().asLong());
            try {
                if (!change.pasture()) {
                    remove(key);
                    continue;
                }
                Region region = regionAt(change.level(), change.pos());
                if (region == null) {
                    remove(key);
                    continue;
                }
                VirtualPastureRecord existing = records.get(key);
                VirtualPastureRecord active = existing != null && region.getId().equals(existing.regionId())
                    ? new VirtualPastureRecord(existing.dimensionKey(), existing.blockPos(), existing.regionId(), existing.ownerUuid(),
                        existing.chunkX(), existing.chunkZ(), VirtualPastureRecord.State.ACTIVE, null)
                    : record(change.level(), change.pos(), region, VirtualPastureRecord.State.ACTIVE, null);
                repository.upsert(active);
                put(active);
                AllocationMetrics.increment("bigbangregions_virtual_pasture_reconciled_total");
            } catch (IllegalStateException error) {
                LOGGER.error("Virtual Pasture index update failed for {}", change.pos(), error);
            }
        }
    }

    public void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        if (!available) return;
        String key = chunkKey(level.dimension().location().toString(), chunk.getPos().x, chunk.getPos().z);
        loadedChunks.put(key, chunk);
        reconcileLoadedChunk(level, chunk);
    }

    public void onChunkUnload(ServerLevel level, LevelChunk chunk) {
        loadedChunks.remove(chunkKey(level.dimension().location().toString(), chunk.getPos().x, chunk.getPos().z));
    }

    public int reconcileLoadedChunks() {
        int reconciled = 0;
        for (LevelChunk chunk : List.copyOf(loadedChunks.values())) {
            ServerLevel level = (ServerLevel) chunk.getLevel();
            reconcileLoadedChunk(level, chunk);
            reconciled++;
        }
        return reconciled;
    }

    public void deleteRegion(String regionId) {
        if (regionId == null) return;
        repository.deleteRegion(regionId);
        forgetRegion(regionId);
    }

    public void forgetRegion(String regionId) {
        if (regionId == null) return;
        for (String key : List.copyOf(byRegion.getOrDefault(regionId, Set.of()))) removeMemory(key);
    }

    public void transferOwner(String regionId, UUID ownerUuid) {
        if (regionId == null) return;
        repository.transferOwner(regionId, ownerUuid);
        for (String key : List.copyOf(byRegion.getOrDefault(regionId, Set.of()))) {
            VirtualPastureRecord old = records.get(key);
            if (old == null) continue;
            put(new VirtualPastureRecord(old.dimensionKey(), old.blockPos(), old.regionId(), ownerUuid, old.chunkX(), old.chunkZ(), old.state(), old.pendingExpiresAt()));
        }
    }

    public int countRegion(String regionId) { return count(byRegion.get(regionId)); }
    public int countOwner(UUID ownerUuid) { return count(byOwner.get(ownerUuid)); }
    public boolean isAvailable() { return available; }

    private void reconcileLoadedChunk(ServerLevel level, LevelChunk chunk) {
        String chunkKey = chunkKey(level.dimension().location().toString(), chunk.getPos().x, chunk.getPos().z);
        Set<String> seen = new HashSet<>();
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            if (!isVirtualPasture(entity.getBlockState())) continue;
            BlockPos pos = entity.getBlockPos();
            seen.add(positionKey(level.dimension().location().toString(), pos.asLong()));
            recordWorldChange(level, pos, true);
        }
        for (String key : List.copyOf(byChunk.getOrDefault(chunkKey, Set.of()))) {
            VirtualPastureRecord record = records.get(key);
            if (record != null && record.state() == VirtualPastureRecord.State.ACTIVE && !seen.contains(key)) remove(key);
        }
        tick();
    }

    private Region regionAt(ServerLevel level, BlockPos pos) {
        return regionCache.getRegionsAt(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ()).stream()
            .sorted(RegionResolver.REGION_PRIORITY_COMPARATOR).findFirst().orElse(null);
    }

    private int ownerLimit(ServerPlayer player, Config.VirtualPastureConfig config) {
        int result = config.getLimits().getOrDefault("default", config.getMaxPerPlayer());
        for (Map.Entry<String, Integer> entry : config.getLimits().entrySet()) {
            if (!"default".equals(entry.getKey()) && permissions.hasPermission(player, "bigbangregions.virtualpasture.limit." + entry.getKey())) {
                result = Math.max(result, Math.max(0, entry.getValue()));
            }
        }
        return result;
    }

    private VirtualPastureRecord record(ServerLevel level, BlockPos pos, Region region, VirtualPastureRecord.State state, Long expires) {
        return new VirtualPastureRecord(level.dimension().location().toString(), pos.asLong(), region.getId(), region.getOwnerUuid(),
            pos.getX() >> 4, pos.getZ() >> 4, state, expires);
    }

    private void rebuild(List<VirtualPastureRecord> source) {
        records.clear(); byRegion.clear(); byOwner.clear(); byChunk.clear();
        for (VirtualPastureRecord record : source) putMemory(record);
    }

    private void expireReservations() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, VirtualPastureRecord> entry : List.copyOf(records.entrySet())) {
            VirtualPastureRecord record = entry.getValue();
            if (record.state() == VirtualPastureRecord.State.PENDING && record.pendingExpiresAt() != null
                && record.pendingExpiresAt() < now) {
                remove(entry.getKey());
            }
        }
    }

    private void put(VirtualPastureRecord record) { removeMemory(positionKey(record.dimensionKey(), record.blockPos())); putMemory(record); }
    private void putMemory(VirtualPastureRecord record) {
        String key = positionKey(record.dimensionKey(), record.blockPos());
        records.put(key, record);
        if (record.regionId() != null) byRegion.computeIfAbsent(record.regionId(), ignored -> new HashSet<>()).add(key);
        if (record.ownerUuid() != null) byOwner.computeIfAbsent(record.ownerUuid(), ignored -> new HashSet<>()).add(key);
        byChunk.computeIfAbsent(chunkKey(record.dimensionKey(), record.chunkX(), record.chunkZ()), ignored -> new HashSet<>()).add(key);
        AllocationMetrics.setGauge("bigbangregions_virtual_pastures_total", records.size());
    }
    private void remove(String key) { VirtualPastureRecord record = records.get(key); if (record != null) repository.delete(record.dimensionKey(), record.blockPos()); removeMemory(key); }
    private void removeMemory(String key) {
        VirtualPastureRecord record = records.remove(key);
        if (record == null) return;
        removeFrom(byRegion, record.regionId(), key); removeFrom(byOwner, record.ownerUuid(), key);
        removeFrom(byChunk, chunkKey(record.dimensionKey(), record.chunkX(), record.chunkZ()), key);
        AllocationMetrics.setGauge("bigbangregions_virtual_pastures_total", records.size());
    }
    private static <K> void removeFrom(Map<K, Set<String>> index, K owner, String key) {
        if (owner == null) return;
        Set<String> values = index.get(owner); if (values == null) return;
        values.remove(key); if (values.isEmpty()) index.remove(owner);
    }
    private static int count(Set<String> values) { return values == null ? 0 : values.size(); }
    private static String positionKey(String dimension, long pos) { return dimension + '\u0000' + pos; }
    private static String chunkKey(ServerLevel level, BlockPos pos) { return chunkKey(level.dimension().location().toString(), pos.getX() >> 4, pos.getZ() >> 4); }
    private static String chunkKey(String dimension, int x, int z) { return dimension + '\u0000' + x + ':' + z; }
}
