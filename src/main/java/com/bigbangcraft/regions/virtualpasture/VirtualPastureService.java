package com.bigbangcraft.regions.virtualpasture;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.allocation.AllocationMetrics;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.region.RegionResolver;
import com.bigbangcraft.regions.repository.VirtualPastureRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import me.lucko.fabric.api.permissions.v0.Permissions;

/**
 * Enforces placement from a durable position index. It deliberately only inspects chunks that
 * Minecraft already loaded; a count must never cause a chunk load.
 */
public final class VirtualPastureService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-Pastures");
    private static final long RESERVATION_MILLIS = 15_000L;
    private static final int RECONCILIATION_STEPS_PER_TICK = 64;
    private static final long RECONCILIATION_BUDGET_NANOS = 2_000_000L;

    public record PlacementDecision(boolean allowed, boolean tracked, int current, int maximum, String reason) {
        static PlacementDecision pass() { return new PlacementDecision(true, false, 0, 0, null); }
        static PlacementDecision accepted(int current, int maximum) { return new PlacementDecision(true, true, current, maximum, null); }
        static PlacementDecision denied(int current, int maximum, String reason) { return new PlacementDecision(false, false, current, maximum, reason); }
    }

    private record Change(ServerLevel level, BlockPos pos, boolean pasture) { }

    private static final class ChunkReconciliation {
        private final String key;
        private final ServerLevel level;
        private final LevelChunk chunk;
        private final List<BlockPos> entityPositions;
        private int entityPositionIndex;
        private final Set<String> seen = new HashSet<>();
        private Iterator<String> indexedRecords;

        private ChunkReconciliation(String key, ServerLevel level, LevelChunk chunk) {
            this.key = key;
            this.level = level;
            this.chunk = chunk;
            this.entityPositions = List.copyOf(chunk.getBlockEntities().keySet());
        }
    }

    private final ConfigManager configManager;
    private final VirtualPastureRepository repository;
    private final RegionCache regionCache;
    private final PermissionManager permissions;
    private final Map<String, VirtualPastureRecord> records = new HashMap<>();
    private final Map<String, Set<String>> byRegion = new HashMap<>();
    private final Map<UUID, Set<String>> byOwner = new HashMap<>();
    private final Map<String, Set<String>> byChunk = new HashMap<>();
    private final Map<String, Change> changes = new LinkedHashMap<>();
    private final Map<String, LevelChunk> loadedChunks = new HashMap<>();
    private final Deque<ChunkReconciliation> reconciliationQueue = new ArrayDeque<>();
    private final Map<String, LevelChunk> queuedChunks = new HashMap<>();
    private final Map<UUID, Config.PastureLimit> cachedOwnerLimits = new ConcurrentHashMap<>();
    private final Set<UUID> refreshingOwnerLimits = ConcurrentHashMap.newKeySet();
    private Set<ResourceLocation> pastureIds = Set.of();
    private boolean configured;
    private boolean registryReady;
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
        configured = config.isEnabled();
        pastureIds = configured ? configuredBlockIds(config.getBlockIds()) : Set.of();
        available = registryReady && !pastureIds.isEmpty();
        if (registryReady && configured && pastureIds.isEmpty()) {
            LOGGER.warn("No configured Pasture block is registered; Pasture limits are disabled.");
        }
        warnedUnavailable = false;
        repository.deleteExpiredPending(System.currentTimeMillis());
        rebuild(repository.loadAll());
        cachedOwnerLimits.clear();
        refreshingOwnerLimits.clear();
        if (available) {
            for (VirtualPastureRecord record : records.values()) refreshOwnerLimit(record.ownerUuid(), config);
        }
        if (available) LOGGER.info("Pasture limits active for {}.", pastureIds);
    }

    public void onServerStarted() {
        registryReady = true;
        reload();
        reconcileLoadedChunks();
    }

    /** A two-block Pasture consumes quota only at its lower, block-entity half. */
    public boolean isVirtualPasture(BlockState state) {
        return available && isCountedVirtualPasture(state, pastureIds);
    }

    static boolean isCountedVirtualPasture(BlockState state, ResourceLocation blockId) {
        return isCountedVirtualPasture(state, blockId == null ? Set.of() : Set.of(blockId));
    }

    static boolean isCountedVirtualPasture(BlockState state, Collection<ResourceLocation> blockIds) {
        if (state == null || blockIds == null || !blockIds.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
            return false;
        }
        for (Property<?> property : state.getProperties()) {
            if ("part".equals(property.getName())) return "bottom".equalsIgnoreCase(propertyValueName(state, property));
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(BlockState state, Property property) {
        return property.getName((Comparable) state.getValue(property));
    }

    public boolean tracksPosition(ServerLevel level, BlockPos pos) {
        return available && level != null && pos != null
            && records.containsKey(positionKey(level.dimension().location().toString(), pos.asLong()));
    }

    public PlacementDecision reserve(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!available) {
            if (configManager.getConfig().getVirtualPasture().isEnabled() && !warnedUnavailable) {
                warnedUnavailable = true;
                LOGGER.info("Pasture limiting is inactive because no configured Pasture block is registered.");
            }
            return PlacementDecision.pass();
        }
        Region region = regionAt(level, pos);
        if (region == null) return PlacementDecision.pass();
        Config.VirtualPastureConfig config = configManager.getConfig().getVirtualPasture();
        UUID owner = region.getOwnerUuid();
        String key = positionKey(level.dimension().location().toString(), pos.asLong());
        VirtualPastureRecord existing = records.get(key);
        if (existing != null && existing.state() == VirtualPastureRecord.State.PENDING
            && existing.pendingExpiresAt() != null && existing.pendingExpiresAt() >= System.currentTimeMillis()) {
            return PlacementDecision.accepted(count(byOwner.get(existing.ownerUuid())),
                ownerLimits(level, existing.ownerUuid(), config).getPerPlayer());
        }
        boolean bypass = player != null && permissions.hasPermission(player, config.getAdminBypassPermission());
        int regionCount = count(byRegion.get(region.getId()));
        int ownerCount = owner == null ? 0 : count(byOwner.get(owner));
        int chunkCount = count(byChunk.get(chunkKey(level, pos)));
        Config.PastureLimit ownerLimits = ownerLimits(level, owner, config);
        VirtualPastureLimitPolicy.Decision limits = VirtualPastureLimitPolicy.check(
            new VirtualPastureLimitPolicy.Counts(regionCount, ownerCount, chunkCount), ownerLimits.getPerRegion(),
            ownerLimits.getPerPlayer(), config.getMaxPerChunk(), owner != null, bypass);
        if (!limits.allowed()) {
            AllocationMetrics.increment("bigbangregions_pastures_denied_total");
            return PlacementDecision.denied(limits.current(), limits.maximum(), limits.scope());
        }

        VirtualPastureRecord record = record(level, pos, region, VirtualPastureRecord.State.PENDING,
            System.currentTimeMillis() + RESERVATION_MILLIS);
        try {
            repository.upsert(record); // Fail closed: vanilla placement is not invoked when this fails.
            put(record);
            AllocationMetrics.increment("bigbangregions_pastures_reservations_total");
            return PlacementDecision.accepted(ownerCount, ownerLimits.getPerPlayer());
        } catch (IllegalStateException error) {
            LOGGER.warn("Pasture placement was denied because its reservation could not be persisted.", error);
            return PlacementDecision.denied(0, 0, "banco de dados indisponível");
        }
    }

    public void recordWorldChange(ServerLevel level, BlockPos pos, boolean pasture) {
        if (level == null || pos == null || !available) return;
        changes.put(positionKey(level.dimension().location().toString(), pos.asLong()), new Change(level, pos.immutable(), pasture));
    }

    public void tick() {
        expireReservations();
        long deadline = System.nanoTime() + RECONCILIATION_BUDGET_NANOS;
        int steps = reconcileChunks(deadline, RECONCILIATION_STEPS_PER_TICK);
        reconcileChanges(deadline, RECONCILIATION_STEPS_PER_TICK - steps);
    }

    private int reconcileChanges(long deadline, int remaining) {
        int processed = 0;
        Iterator<Map.Entry<String, Change>> iterator = changes.entrySet().iterator();
        while (remaining-- > 0 && System.nanoTime() < deadline && iterator.hasNext()) {
            Change change = iterator.next().getValue();
            iterator.remove();
            processed++;
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
                AllocationMetrics.increment("bigbangregions_pastures_reconciled_total");
            } catch (IllegalStateException error) {
                LOGGER.error("Pasture index update failed for {}", change.pos(), error);
            }
        }
        return processed;
    }

    public void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        if (!configured) return;
        String key = chunkKey(level.dimension().location().toString(), chunk.getPos().x, chunk.getPos().z);
        loadedChunks.put(key, chunk);
        if (available) enqueueReconciliation(key, level, chunk);
    }

    public void onChunkUnload(ServerLevel level, LevelChunk chunk) {
        String key = chunkKey(level.dimension().location().toString(), chunk.getPos().x, chunk.getPos().z);
        loadedChunks.remove(key, chunk);
    }

    public int reconcileLoadedChunks() {
        int queued = 0;
        for (Map.Entry<String, LevelChunk> entry : List.copyOf(loadedChunks.entrySet())) {
            if (queuedChunks.get(entry.getKey()) == entry.getValue()) continue;
            enqueueReconciliation(entry.getKey(), (ServerLevel) entry.getValue().getLevel(), entry.getValue());
            queued++;
        }
        return queued;
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
        refreshTransferredOwner(regionId, ownerUuid);
    }

    /** The durable update already committed with the region row; only hot indexes change here. */
    public void refreshTransferredOwner(String regionId, UUID ownerUuid) {
        if (regionId == null) return;
        for (String key : List.copyOf(byRegion.getOrDefault(regionId, Set.of()))) {
            VirtualPastureRecord old = records.get(key);
            if (old == null) continue;
            put(new VirtualPastureRecord(old.dimensionKey(), old.blockPos(), old.regionId(), ownerUuid, old.chunkX(), old.chunkZ(), old.state(), old.pendingExpiresAt()));
        }
        refreshOwnerLimit(ownerUuid, configManager.getConfig().getVirtualPasture());
    }

    public int countRegion(String regionId) { return count(byRegion.get(regionId)); }
    public int countOwner(UUID ownerUuid) { return count(byOwner.get(ownerUuid)); }
    public boolean isAvailable() { return available; }

    private void enqueueReconciliation(String key, ServerLevel level, LevelChunk chunk) {
        if (queuedChunks.put(key, chunk) != chunk) reconciliationQueue.addLast(new ChunkReconciliation(key, level, chunk));
    }

    private int reconcileChunks(long deadline, int remaining) {
        int processed = 0;
        while (remaining > 0 && System.nanoTime() < deadline && !reconciliationQueue.isEmpty()) {
            ChunkReconciliation reconciliation = reconciliationQueue.peekFirst();
            if (loadedChunks.get(reconciliation.key) != reconciliation.chunk) {
                reconciliationQueue.removeFirst();
                queuedChunks.remove(reconciliation.key, reconciliation.chunk);
                continue;
            }
            if (reconciliation.entityPositionIndex < reconciliation.entityPositions.size()) {
                BlockEntity entity = reconciliation.chunk.getBlockEntities().get(
                    reconciliation.entityPositions.get(reconciliation.entityPositionIndex++)
                );
                processed++;
                remaining--;
                if (entity != null && isVirtualPasture(entity.getBlockState())) {
                    BlockPos pos = entity.getBlockPos();
                    reconciliation.seen.add(positionKey(reconciliation.level.dimension().location().toString(), pos.asLong()));
                    recordWorldChange(reconciliation.level, pos, true);
                }
                continue;
            }
            if (reconciliation.indexedRecords == null) {
                reconciliation.indexedRecords = List.copyOf(byChunk.getOrDefault(reconciliation.key, Set.of())).iterator();
            }
            if (reconciliation.indexedRecords.hasNext()) {
                String key = reconciliation.indexedRecords.next();
                processed++;
                remaining--;
                VirtualPastureRecord record = records.get(key);
                if (record != null && record.state() == VirtualPastureRecord.State.ACTIVE && !reconciliation.seen.contains(key)) remove(key);
                continue;
            }
            reconciliationQueue.removeFirst();
            queuedChunks.remove(reconciliation.key, reconciliation.chunk);
        }
        return processed;
    }

    private Region regionAt(ServerLevel level, BlockPos pos) {
        return regionCache.getRegionsAt(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ()).stream()
            .sorted(RegionResolver.REGION_PRIORITY_COMPARATOR).findFirst().orElse(null);
    }

    private Config.PastureLimit ownerLimits(ServerPlayer player, Config.VirtualPastureConfig config) {
        Config.PastureLimit result = defaultLimit(config);
        for (Map.Entry<String, Config.PastureLimit> entry : config.getLimits().entrySet()) {
            Config.PastureLimit limit = entry.getValue();
            if (!"default".equals(entry.getKey()) && limit != null
                && permissions.hasPermission(player, "bigbangregions.virtualpasture.limit." + entry.getKey())) {
                result = larger(result, limit);
            }
        }
        return result;
    }

    private Config.PastureLimit ownerLimits(ServerLevel level, UUID owner, Config.VirtualPastureConfig config) {
        Config.PastureLimit defaultLimit = defaultLimit(config);
        if (owner == null) return defaultLimit;
        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
        if (ownerPlayer != null) {
            Config.PastureLimit limit = ownerLimits(ownerPlayer, config);
            cachedOwnerLimits.put(owner, limit);
            return limit;
        }
        refreshOwnerLimit(owner, config);
        return cachedOwnerLimits.getOrDefault(owner, defaultLimit);
    }

    private void refreshOwnerLimit(UUID owner, Config.VirtualPastureConfig config) {
        if (owner == null || !refreshingOwnerLimits.add(owner)) return;
        Config.PastureLimit defaultLimit = defaultLimit(config);
        List<Map.Entry<String, Config.PastureLimit>> tiers = config.getLimits().entrySet().stream()
            .filter(entry -> !"default".equals(entry.getKey())).toList();
        List<CompletableFuture<Boolean>> checks = tiers.stream()
            .map(entry -> Permissions.check(owner, "bigbangregions.virtualpasture.limit." + entry.getKey(), false))
            .toList();
        CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
            Config.PastureLimit limit = defaultLimit;
            if (error == null) {
                for (int index = 0; index < checks.size(); index++) {
                    if (Boolean.TRUE.equals(checks.get(index).getNow(false))) {
                        limit = larger(limit, tiers.get(index).getValue());
                    }
                }
            }
            cachedOwnerLimits.put(owner, limit);
            refreshingOwnerLimits.remove(owner);
        });
    }

    private VirtualPastureRecord record(ServerLevel level, BlockPos pos, Region region, VirtualPastureRecord.State state, Long expires) {
        return new VirtualPastureRecord(level.dimension().location().toString(), pos.asLong(), region.getId(), region.getOwnerUuid(),
            pos.getX() >> 4, pos.getZ() >> 4, state, expires);
    }

    private void rebuild(List<VirtualPastureRecord> source) {
        records.clear(); byRegion.clear(); byOwner.clear(); byChunk.clear();
        changes.clear(); reconciliationQueue.clear(); queuedChunks.clear();
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
        AllocationMetrics.setGauge("bigbangregions_pastures_total", records.size());
    }
    private void remove(String key) { VirtualPastureRecord record = records.get(key); if (record != null) repository.delete(record.dimensionKey(), record.blockPos()); removeMemory(key); }
    private void removeMemory(String key) {
        VirtualPastureRecord record = records.remove(key);
        if (record == null) return;
        removeFrom(byRegion, record.regionId(), key); removeFrom(byOwner, record.ownerUuid(), key);
        removeFrom(byChunk, chunkKey(record.dimensionKey(), record.chunkX(), record.chunkZ()), key);
        AllocationMetrics.setGauge("bigbangregions_pastures_total", records.size());
    }
    private static <K> void removeFrom(Map<K, Set<String>> index, K owner, String key) {
        if (owner == null) return;
        Set<String> values = index.get(owner); if (values == null) return;
        values.remove(key); if (values.isEmpty()) index.remove(owner);
    }
    private static int count(Set<String> values) { return values == null ? 0 : values.size(); }
    private static Config.PastureLimit defaultLimit(Config.VirtualPastureConfig config) {
        return config.getLimits().getOrDefault("default", new Config.PastureLimit(0, 0));
    }
    private static Config.PastureLimit larger(Config.PastureLimit first, Config.PastureLimit second) {
        return new Config.PastureLimit(Math.max(first.getPerPlayer(), second.getPerPlayer()),
            Math.max(first.getPerRegion(), second.getPerRegion()));
    }
    private static Set<ResourceLocation> configuredBlockIds(Collection<String> values) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        if (values == null) return Set.of();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            try {
                ResourceLocation id = ResourceLocation.parse(value);
                if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                    LOGGER.warn("Configured Pasture block '{}' is not registered; that target is disabled.", id);
                    continue;
                }
                ids.add(id);
            } catch (RuntimeException invalid) {
                LOGGER.warn("Configured Pasture block id '{}' is invalid; that target is disabled.", value);
            }
        }
        return Set.copyOf(ids);
    }
    private static String positionKey(String dimension, long pos) { return dimension + '\u0000' + pos; }
    private static String chunkKey(ServerLevel level, BlockPos pos) { return chunkKey(level.dimension().location().toString(), pos.getX() >> 4, pos.getZ() >> 4); }
    private static String chunkKey(String dimension, int x, int z) { return dimension + '\u0000' + x + ':' + z; }
}
