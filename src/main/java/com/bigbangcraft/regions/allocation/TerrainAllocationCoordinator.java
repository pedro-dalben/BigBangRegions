package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.*;
import com.bigbangcraft.regions.repository.AllocationRequestRepository;
import com.bigbangcraft.regions.repository.PlayerRegionHomeRepository;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.storage.DatabaseManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TerrainAllocationCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-TerrainAllocationCoordinator");

    // Per-request incremental candidate iterator; avoids regenerating the whole spiral each tick.
    private final Map<String, PlotSlotService.PlotSlotIterator> searchIterators = new ConcurrentHashMap<>();

    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final AllocationRequestRepository requestRepository;
    private final PlotSlotRepository slotRepository;
    private final PlotSlotService slotService;
    private final PlayerRegionHomeRepository homeRepository;
    private final RegionRepository regionRepository;
    private final BiomeSearchService biomeSearchService;
    private final BiomeOptionRegistry biomeOptionRegistry;
    private final RegionCache regionCache;
    private final RegionMembershipCache membershipCache;

    private final Map<UUID, Long> creationCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> homeTeleportCooldowns = new ConcurrentHashMap<>();

    public TerrainAllocationCoordinator(ConfigManager configManager,
                                         DatabaseManager databaseManager,
                                         AllocationRequestRepository requestRepository,
                                         PlotSlotRepository slotRepository,
                                         PlotSlotService slotService,
                                         PlayerRegionHomeRepository homeRepository,
                                         RegionRepository regionRepository,
                                         BiomeSearchService biomeSearchService,
                                         BiomeOptionRegistry biomeOptionRegistry,
                                         RegionCache regionCache,
                                         RegionMembershipCache membershipCache) {
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.requestRepository = requestRepository;
        this.slotRepository = slotRepository;
        this.slotService = slotService;
        this.homeRepository = homeRepository;
        this.regionRepository = regionRepository;
        this.biomeSearchService = biomeSearchService;
        this.biomeOptionRegistry = biomeOptionRegistry;
        this.regionCache = regionCache;
        this.membershipCache = membershipCache;
    }

    public String createRequest(ServerPlayer player, String biomeQuery, String source) {
        Optional<BiomeOption> opt = biomeOptionRegistry.lookup(biomeQuery);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Opcao de bioma nao encontrada: " + biomeQuery);
        }
        UUID ownerUuid = player.getUUID();
        Config.SchedulerConfig sc = configManager.getConfig().getPlayerLandAllocation().getScheduler();
        long cooldownMs = sc.getCreationCooldownSeconds() * 1000L;
        if (cooldownMs > 0) {
            Long lastCreation = creationCooldowns.get(ownerUuid);
            if (lastCreation != null) {
                long elapsed = System.currentTimeMillis() - lastCreation;
                if (elapsed < cooldownMs) {
                    long remaining = (cooldownMs - elapsed + 999) / 1000;
                    throw new IllegalStateException("Aguarde " + remaining + " segundos antes de criar um novo pedido.");
                }
            }
        }
        AllocationRequest existing = requestRepository.getActiveRequestByOwner(ownerUuid);
        if (existing != null) {
            throw new IllegalStateException("Voce ja possui um pedido de alocacao ativo (ID: " + existing.getId() + ")");
        }
        Config.PlayerLandAllocationConfig lac = configManager.getConfig().getPlayerLandAllocation();
        if (lac.getMaxRegionsPerOwner() > 0) {
            long playerRegionCount = regionCache.getAll().stream()
                .filter(r -> r.getType() == RegionType.PLAYER_REGION && ownerUuid.equals(r.getOwnerUuid()))
                .count();
            if (playerRegionCount >= lac.getMaxRegionsPerOwner()) {
                throw new IllegalStateException("Voce ja atingiu o limite maximo de " + lac.getMaxRegionsPerOwner() + " regioes");
            }
        }

        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        AllocationRequest request = new AllocationRequest(
            id, ownerUuid, opt.get().getKey(), lac.getTargetDimension(),
            AllocationRequestState.PENDING, source, ownerUuid, null, null, null, 0, now, now, null, null
        );

        requestRepository.save(request);
        creationCooldowns.put(ownerUuid, now);
        LOGGER.info("Allocation request created: id={}, owner={}, biome={}", id, ownerUuid, opt.get().getKey());
        return id;
    }

    public long getCreationCooldownRemaining(UUID ownerUuid) {
        Config.SchedulerConfig sc = configManager.getConfig().getPlayerLandAllocation().getScheduler();
        long cooldownMs = sc.getCreationCooldownSeconds() * 1000L;
        if (cooldownMs <= 0) return 0;
        Long lastCreation = creationCooldowns.get(ownerUuid);
        if (lastCreation == null) return 0;
        long elapsed = System.currentTimeMillis() - lastCreation;
        if (elapsed >= cooldownMs) {
            creationCooldowns.remove(ownerUuid);
            return 0;
        }
        return (cooldownMs - elapsed + 999) / 1000;
    }

    public AllocationRequest getActiveRequest(UUID ownerUuid) {
        return requestRepository.getActiveRequestByOwner(ownerUuid);
    }

    public void cancelRequest(UUID ownerUuid) {
        AllocationRequest request = requestRepository.getActiveRequestByOwner(ownerUuid);
        if (request == null) {
            throw new IllegalStateException("Voce nao possui um pedido de alocacao ativo");
        }

        if (!request.getState().isPreRegionCreation()) {
            throw new IllegalStateException("Esta operacao ja criou a regiao e nao pode ser cancelada. Um administrador precisa verificar a operacao.");
        }

        request.forceTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION);
        requestRepository.save(request);

        searchIterators.remove(request.getId());
        tryReleaseSlot(request);
        LOGGER.info("Allocation request cancelled: id={}, owner={}", request.getId(), ownerUuid);
    }

    public int processNextRequest(ServerLevel level) {
        List<AllocationRequest> active = requestRepository.getActiveRequests();
        if (active.isEmpty()) return 0;

        long now = System.currentTimeMillis();

        for (AllocationRequest request : active) {
            if (request.getNextRetryAt() != null && request.getNextRetryAt() > now) {
                continue;
            }
            return processRequest(request, level);
        }
        return 0;
    }

    private int processRequest(AllocationRequest request, ServerLevel level) {
        Config config = configManager.getConfig();
        Config.SchedulerConfig sc = config.getPlayerLandAllocation().getScheduler();
        Config.PlayerLandAllocationConfig lac = config.getPlayerLandAllocation();

        if (request.getState().isLegacyPaymentState()) {
            request.forceTransitionTo(AllocationRequestState.LEGACY_REQUIRES_ADMIN_REVIEW);
            request.setFailureReason("Operacao legada com pagamento detectada. Revisao administrativa necessaria.");
            requestRepository.save(request);
            LOGGER.warn("Legacy payment request {} moved to LEGACY_REQUIRES_ADMIN_REVIEW", request.getId());
            return 1;
        }

        if (request.getState() == AllocationRequestState.PENDING) {
            if (isTimedOut(request, sc)) {
                failRequest(request, AllocationRequestState.FAILED_NO_TERRAIN, "Tempo limite excedido", level);
                return 1;
            }
            request.transitionTo(AllocationRequestState.SEARCHING);
            requestRepository.save(request);
            return 1;
        }

        if (request.getState() == AllocationRequestState.SEARCHING) {
            if (isTimedOut(request, sc)) {
                failRequest(request, AllocationRequestState.FAILED_NO_TERRAIN, "Tempo limite excedido durante busca", level);
                return 1;
            }
            Optional<BiomeOption> biomeOpt = biomeOptionRegistry.lookup(request.getRequestedBiomeOption());
            if (biomeOpt.isEmpty()) {
                failRequest(request, AllocationRequestState.FAILED_VALIDATION, "Opcao de bioma nao encontrada: " + request.getRequestedBiomeOption(), level);
                return 1;
            }

            int maxCandidates = Math.max(1, sc.getMaxCandidateEvaluationsPerTick());
            long timeBudgetNanos = Math.max(1L, sc.getMaxBiomeSearchMillisPerTick()) * 1_000_000L;
            long deadline = System.nanoTime() + timeBudgetNanos;
            int evaluated = 0;

            PlotSlotService.PlotSlotIterator iterator = searchIterators.computeIfAbsent(
                request.getId(), rid -> slotService.iteratorFor(request.getOwnerUuid()));

            int maxRadiusBlocks = lac.getBiomeSearch().getMaximumSearchRadiusBlocks();
            int maxRing = maxRadiusBlocks / lac.getSlotSize();

            while (evaluated < maxCandidates && System.nanoTime() < deadline) {
                Optional<PlotSlotService.PlotSlotCandidate> optCandidate = iterator.next();
                if (optCandidate.isEmpty()) {
                    searchIterators.remove(request.getId());
                    failRequest(request, AllocationRequestState.FAILED_NO_TERRAIN, "Nenhum terreno com bioma adequado encontrado no raio limite", level);
                    return 1;
                }
                PlotSlotService.PlotSlotCandidate candidate = optCandidate.get();
                if (Math.abs(candidate.gridX) > maxRing || Math.abs(candidate.gridZ) > maxRing) {
                    searchIterators.remove(request.getId());
                    failRequest(request, AllocationRequestState.FAILED_NO_TERRAIN, "Raio de busca excedido", level);
                    return 1;
                }

                evaluated++;
                int claimOffset = (lac.getSlotSize() - lac.getInitialClaimSize()) / 2;
                int claimMinX = candidate.minX + claimOffset;
                int claimMaxX = claimMinX + lac.getInitialClaimSize() - 1;
                int claimMinZ = candidate.minZ + claimOffset;
                int claimMaxZ = claimMinZ + lac.getInitialClaimSize() - 1;
                boolean biomeMatch = biomeSearchService.isBiomeOptionMatching(
                    level, claimMinX, claimMaxX,
                    claimMinZ, claimMaxZ,
                    biomeOpt.get()
                );
                if (!biomeMatch) continue;
                String slotId = lac.getTargetDimension() + ":" + candidate.gridX + ":" + candidate.gridZ;
                PlotSlot existing = slotRepository.getByGrid(lac.getTargetDimension(), candidate.gridX, candidate.gridZ);
                if (existing != null && (existing.getState() == PlotSlotState.RESERVED || existing.getState() == PlotSlotState.ALLOCATED || existing.getState() == PlotSlotState.OCCUPIED)) {
                    continue;
                }
                PlotSlot slot;
                if (existing != null && existing.getState() == PlotSlotState.RELEASED) {
                    slot = existing;
                } else {
                    long slotNow = System.currentTimeMillis();
                    slot = new PlotSlot(slotId, lac.getTargetDimension(), candidate.gridX, candidate.gridZ,
                        candidate.minX, candidate.minZ, lac.getSlotSize(),
                        PlotSlotState.RELEASED, null, null, null,
                        null, null, null, slotNow, slotNow);
                }
                slot.reserve(request.getOwnerUuid(), request.getRequestedBiomeOption(), sc.getReservationLeaseSeconds() * 1000L);
                slotRepository.save(slot);
                request.transitionTo(AllocationRequestState.SLOT_RESERVED);
                request.setPlotSlotId(slotId);
                searchIterators.remove(request.getId());
                requestRepository.save(request);
                LOGGER.info("Slot reserved: slotId={}, request={}, grid=({},{})", slotId, request.getId(), candidate.gridX, candidate.gridZ);
                return 1;
            }

            request.incrementAttempts();
            requestRepository.save(request);
            return 1;
        }

        if (request.getState() == AllocationRequestState.SLOT_RESERVED) {
            if (isTimedOut(request, sc)) {
                tryReleaseSlot(request);
                failRequest(request, AllocationRequestState.FAILED_NO_TERRAIN, "Tempo limite excedido durante reserva", level);
                return 1;
            }
            request.transitionTo(AllocationRequestState.PREPARING);
            requestRepository.save(request);
            return 1;
        }

        if (request.getState() == AllocationRequestState.PREPARING) {
            if (isTimedOut(request, sc)) {
                tryReleaseSlot(request);
                failRequest(request, AllocationRequestState.FAILED_NO_TERRAIN, "Tempo limite excedido durante preparacao", level);
                return 1;
            }
            String slotId = request.getPlotSlotId();
            if (slotId == null) {
                failRequest(request, AllocationRequestState.FAILED_VALIDATION, "Slot ID nao definido", level);
                return 1;
            }
            PlotSlot slot = slotRepository.get(slotId);
            if (slot == null) {
                tryReleaseSlot(request);
                failRequest(request, AllocationRequestState.FAILED_NO_TERRAIN, "Slot nao encontrado", level);
                return 1;
            }

            String regionId = "player_" + request.getOwnerUuid().toString().substring(0, 8) + "_" + System.currentTimeMillis();

            request.setRegionId(regionId);
            request.transitionTo(AllocationRequestState.REGION_CREATING);
            requestRepository.save(request);

            try {
                createRegionInSingleTransaction(request, slot, lac, level);
            } catch (Exception e) {
                LOGGER.error("Failed to create region in transaction for request={}: {}", request.getId(), e.getMessage());
                return 1;
            }
            return 1;
        }

        if (request.getState() == AllocationRequestState.REGION_CREATING) {
            return recoverCreatingFromSqlite(request, level, lac);
        }

        return 0;
    }

    private void createRegionInSingleTransaction(AllocationRequest request, PlotSlot slot,
                                                  Config.PlayerLandAllocationConfig lac,
                                                  ServerLevel level) throws SQLException {
        synchronized (databaseManager) {
            Connection conn = databaseManager.getConnection();
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String regionId = request.getRegionId();
                String dimension = lac.getTargetDimension();
                int claimOffset = (lac.getSlotSize() - lac.getInitialClaimSize()) / 2;
                int claimMinX = slot.getMinX() + claimOffset;
                int claimMinZ = slot.getMinZ() + claimOffset;
                int claimMaxX = claimMinX + lac.getInitialClaimSize() - 1;
                int claimMaxZ = claimMinZ + lac.getInitialClaimSize() - 1;

                // Pre-load and fully generate chunks in the claim area to prevent asynchronous corruption
                forceLoadChunks(level, claimMinX, claimMaxX, claimMinZ, claimMaxZ);

                long now = System.currentTimeMillis();
                RegionBounds bounds = new RegionBounds(dimension, claimMinX, -64, claimMinZ, claimMaxX, 320, claimMaxZ);
                Region region = new Region(regionId, "Player Region", RegionType.PLAYER_REGION,
                    bounds, 100, request.getOwnerUuid(), request.getOwnerUuid(), now, now, "ACTIVE");
                regionRepository.saveOnConnection(conn, region);
                regionRepository.saveMembersOnConnection(conn, regionId, Collections.emptyMap());

                slot.allocate(regionId);
                slot.occupy();
                slotRepository.saveOnConnection(conn, slot);

                Optional<BlockPos> spawnPos = SafeSpawnFinder.findSafeSpawn(level, claimMinX, claimMaxX,
                    claimMinZ, claimMaxZ);
                if (spawnPos.isEmpty()) {
                    conn.rollback();
                    tryReleaseSlotOnly(conn, slot);
                    request.forceTransitionTo(AllocationRequestState.FAILED_NO_TERRAIN);
                    request.setFailureReason("Nenhum safe spawn encontrado dentro do claim");
                    requestRepository.saveOnConnection(conn, request);
                    conn.commit();
                    LOGGER.warn("No safe spawn found for request={}, region={}", request.getId(), regionId);

                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.getOwnerUuid());
                    if (player != null) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNão foi possível criar seu terreno: Nenhum safe spawn encontrado dentro do claim"));
                    }
                    return;
                }
                BlockPos homePos = spawnPos.get();
                long homeNow = System.currentTimeMillis();
                PlayerRegionHome home = new PlayerRegionHome(regionId, dimension,
                    homePos.getX() + 0.5, homePos.getY(), homePos.getZ() + 0.5, 0.0f, 0.0f, homeNow, homeNow);
                homeRepository.saveOnConnection(conn, home);

                request.transitionTo(AllocationRequestState.COMPLETED);
                requestRepository.saveOnConnection(conn, request);

                conn.commit();

                regionCache.add(region);
                membershipCache.loadFromRegion(region);

                LOGGER.info("Region created (free): request={}, regionId={}, claimOffset={}, home=({},{},{})",
                    request.getId(), regionId, claimOffset, homePos.getX(), homePos.getY(), homePos.getZ());

                // Capture the original terrain before we place borders/platforms so deletion
                // can restore the area instead of restoring our own generated blocks.
                snapshotCreatedTerrain(regionId, bounds, level, homePos, configManager.getConfig().getPlayerLandAllocation().getBorder().isRestoreOnDelete());
                postRegionCreationSetup(request, bounds, level, homePos);
            } catch (Exception e) {
                conn.rollback();
                throw (e instanceof SQLException) ? (SQLException) e : new SQLException(e);
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        }
    }

    public boolean restorePlayerRegionTerrain(Region region, ServerLevel level) {
        if (region == null || level == null) {
            return false;
        }

        if (region.getType() != RegionType.PLAYER_REGION) {
            return false;
        }

        if (!configManager.getConfig().getPlayerLandAllocation().getBorder().isRestoreOnDelete()) {
            return false;
        }

        try {
            Path restoreDir = getRestoreDirectory();
            boolean restored = RegionTerrainSnapshot.restore(level, region, restoreDir);
            if (restored) {
                LOGGER.info("Restored terrain snapshot for region {}", region.getId());
            }
            return restored;
        } catch (Exception e) {
            LOGGER.warn("Failed to restore terrain snapshot for region {}: {}", region.getId(), e.getMessage());
            return false;
        }
    }

    private void tryReleaseSlotOnly(Connection conn, PlotSlot slot) throws SQLException {
        slot.release();
        slotRepository.saveOnConnection(conn, slot);
    }

    private void snapshotCreatedTerrain(String regionId, RegionBounds bounds, ServerLevel level, BlockPos homePos, boolean enabled) {
        if (!enabled) {
            return;
        }

        try {
            RegionTerrainSnapshot.capture(level, bounds, homePos, regionId, getRestoreDirectory());
        } catch (Exception e) {
            LOGGER.warn("Failed to snapshot terrain for region {}: {}", regionId, e.getMessage());
        }
    }

    private Path getRestoreDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("bigbangregions").resolve("terrain-restores");
    }

    private void postRegionCreationSetup(AllocationRequest request, RegionBounds bounds, ServerLevel level, BlockPos homePos) {
        Config config = configManager.getConfig();
        Config.BorderConfig borderConfig = config.getPlayerLandAllocation().getBorder();

        // 1. Generate glass border
        generateGlassBorder(level, bounds, borderConfig.getMaterial(), borderConfig.isCreateCeiling());

        // 2. Set biome
        applyRegionBiome(level, bounds, request.getRequestedBiomeOption());

        // 2.5 Generate cobblestone spawn platform
        generateSpawnPlatform(level, homePos);

        // 3. Teleport and notify
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.getOwnerUuid());
        if (player != null) {
            player.teleportTo(level, homePos.getX() + 0.5, homePos.getY(), homePos.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aSeu terreno foi criado com sucesso!"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Você foi teleportado para sua nova região."));
        }
    }

    private void generateSpawnPlatform(ServerLevel level, BlockPos homePos) {
        try {
            var cobblestone = net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            var glowstone = net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState();

            // 4x4 platform centered around homePos at yFloor = homePos.getY() - 1
            int minX = homePos.getX() - 1;
            int maxX = homePos.getX() + 2;
            int minZ = homePos.getZ() - 1;
            int maxZ = homePos.getZ() + 2;
            int yFloor = homePos.getY() - 1;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // Set floor to Cobblestone
                    level.setBlock(new BlockPos(x, yFloor, z), cobblestone, 2);
                    // Clear 2 blocks of air above
                    level.setBlock(new BlockPos(x, yFloor + 1, z), air, 2);
                    level.setBlock(new BlockPos(x, yFloor + 2, z), air, 2);
                }
            }

            // Highlight the exact home center on the floor.
            level.setBlock(new BlockPos(homePos.getX(), yFloor, homePos.getZ()), glowstone, 2);
        } catch (Exception e) {
            LOGGER.error("Failed to generate spawn platform", e);
        }
    }

    private void forceLoadChunks(ServerLevel level, int minX, int maxX, int minZ, int maxZ) {
        try {
            int minChunkX = minX >> 4;
            int maxChunkX = maxX >> 4;
            int minChunkZ = minZ >> 4;
            int maxChunkZ = maxZ >> 4;

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    level.getChunkSource().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to pre-load/generate chunks for region", e);
        }
    }

    private void generateGlassBorder(ServerLevel level, RegionBounds bounds, String materialId, boolean createCeiling) {
        try {
            net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.parse(materialId);
            net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(loc);
            if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
                block = net.minecraft.world.level.block.Blocks.GLASS;
            }
            net.minecraft.world.level.block.state.BlockState glassState = block.defaultBlockState();

            int minX = bounds.getMinX();
            int maxX = bounds.getMaxX();
            int minZ = bounds.getMinZ();
            int maxZ = bounds.getMaxZ();
            int minY = bounds.getMinY();
            int maxY = bounds.getMaxY();

            // Set blocks along borders with flag 2 (prevent block physics/neighbor updates)
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(minX, y, z), glassState, 2);
                    level.setBlock(new BlockPos(maxX, y, z), glassState, 2);
                }
                for (int x = minX; x <= maxX; x++) {
                    level.setBlock(new BlockPos(x, y, minZ), glassState, 2);
                    level.setBlock(new BlockPos(x, y, maxZ), glassState, 2);
                }
            }

            if (createCeiling) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        level.setBlock(new BlockPos(x, maxY, z), glassState, 2);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to generate glass border", e);
        }
    }

    private void applyRegionBiome(ServerLevel level, RegionBounds bounds, String biomeOptionKey) {
        try {
            Optional<BiomeOption> opt = biomeOptionRegistry.lookup(biomeOptionKey);
            if (opt.isEmpty()) return;
            List<String> acceptedBiomes = opt.get().getAcceptedBiomeIds();
            if (acceptedBiomes.isEmpty()) return;

            // 1.21+ does not expose a safe public API to mutate chunk biome palettes
            // after generation. Direct PalettedContainer writes can recurse during
            // palette resize and crash the server. Keep the biome option as a
            // search/validation input, but do not rewrite chunk biomes here.
            LOGGER.warn("Skipping direct biome palette mutation for region {} (biome option {})", bounds, biomeOptionKey);
        } catch (Exception e) {
            LOGGER.error("Failed to apply biome to region", e);
        }
    }

    private int recoverCreatingFromSqlite(AllocationRequest request, ServerLevel level,
                                           Config.PlayerLandAllocationConfig lac) {
        String regionId = request.getRegionId();
        if (regionId == null) {
            request.forceTransitionTo(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION);
            request.setFailureReason("REGION_CREATING sem region_id. Contate um administrador.");
            requestRepository.save(request);
            return 1;
        }

        boolean existsInDb = regionExistsInDb(regionId);

        if (existsInDb) {
            reloadCachesFromDb();
            Region cached = regionCache.get(regionId);
            if (cached != null) {
                request.transitionTo(AllocationRequestState.COMPLETED);
                requestRepository.save(request);
                LOGGER.info("Recovery: region {} found in DB, marking request {} complete", regionId, request.getId());
                return 1;
            }
        }

        String slotId = request.getPlotSlotId();
        if (slotId != null) {
            PlotSlot slot = slotRepository.get(slotId);
            if (slot != null && slot.getState() == PlotSlotState.RESERVED) {
                try {
                    createRegionInSingleTransaction(request, slot, lac, level);
                    return 1;
                } catch (Exception e) {
                    LOGGER.error("Recovery: Failed to rebuild region for request={}: {}", request.getId(), e.getMessage());
                }
            }
        }

        request.forceTransitionTo(AllocationRequestState.BLOCKED_FOR_MANUAL_RECONCILIATION);
        request.setFailureReason("Falha na criacao da regiao. Contate um administrador.");
        requestRepository.save(request);
        return 1;
    }

    private boolean regionExistsInDb(String regionId) {
        List<Region> allRegions = regionRepository.loadAll();
        return allRegions.stream().anyMatch(r -> r.getId().equals(regionId));
    }

    private void reloadCachesFromDb() {
        try {
            List<Region> regions = regionRepository.loadAll();
            for (Region r : regions) {
                regionCache.add(r);
                membershipCache.loadFromRegion(r);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to reload caches from DB", e);
        }
    }

    public boolean teleportToHome(ServerPlayer player) {
        UUID ownerUuid = player.getUUID();
        Config.SchedulerConfig sc = configManager.getConfig().getPlayerLandAllocation().getScheduler();
        long cooldownMs = sc.getHomeTeleportCooldownSeconds() * 1000L;
        if (cooldownMs > 0) {
            Long lastTeleport = homeTeleportCooldowns.get(ownerUuid);
            if (lastTeleport != null) {
                long elapsed = System.currentTimeMillis() - lastTeleport;
                if (elapsed < cooldownMs) {
                    long remaining = (cooldownMs - elapsed + 999) / 1000;
                    throw new IllegalStateException("Aguarde " + remaining + " segundos antes de usar /casa novamente.");
                }
            }
        }
        Optional<Region> playerRegion = regionCache.getAll().stream()
            .filter(r -> r.getType() == RegionType.PLAYER_REGION && ownerUuid.equals(r.getOwnerUuid()) && "ACTIVE".equals(r.getStatus()))
            .findFirst();
        if (playerRegion.isEmpty()) {
            throw new IllegalStateException("Voce nao possui uma regiao de jogador ativa");
        }
        PlayerRegionHome home = homeRepository.get(playerRegion.get().getId());
        if (home == null) {
            throw new IllegalStateException("Sua regiao nao possui uma casa definida");
        }
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(home.getDimensionKey()));
        ServerLevel targetLevel = player.getServer().getLevel(dimensionKey);
        if (targetLevel == null) {
            throw new IllegalStateException("Dimensao invalida: " + home.getDimensionKey());
        }
        player.teleportTo(targetLevel, home.getX(), home.getY(), home.getZ(), home.getYaw(), home.getPitch());
        homeTeleportCooldowns.put(ownerUuid, System.currentTimeMillis());
        return true;
    }

    public boolean setHome(ServerPlayer player) {
        UUID ownerUuid = player.getUUID();
        Optional<Region> playerRegion = regionCache.getAll().stream()
            .filter(r -> r.getType() == RegionType.PLAYER_REGION && ownerUuid.equals(r.getOwnerUuid()) && "ACTIVE".equals(r.getStatus()))
            .findFirst();
        if (playerRegion.isEmpty()) {
            throw new IllegalStateException("Voce nao possui uma regiao de jogador ativa");
        }
        Region region = playerRegion.get();
        RegionBounds b = region.getBounds();
            BlockPos p = player.blockPosition();
            if (!b.contains(player.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ())) {
            throw new IllegalStateException("Voce precisa estar dentro da sua regiao para definir a casa");
        }
        long now = System.currentTimeMillis();
        String dimensionKey = player.level().dimension().location().toString();
        PlayerRegionHome home = new PlayerRegionHome(
            region.getId(), dimensionKey,
            player.getX(), player.getY(), player.getZ(),
            player.getYRot(), player.getXRot(),
            now, now
        );
        homeRepository.save(home);
        LOGGER.info("Home set: region={}, pos=({},{},{})", region.getId(), player.getX(), player.getY(), player.getZ());
        return true;
    }

    public long getHomeTeleportCooldownRemaining(UUID ownerUuid) {
        Config.SchedulerConfig sc = configManager.getConfig().getPlayerLandAllocation().getScheduler();
        long cooldownMs = sc.getHomeTeleportCooldownSeconds() * 1000L;
        if (cooldownMs <= 0) return 0;
        Long lastTeleport = homeTeleportCooldowns.get(ownerUuid);
        if (lastTeleport == null) return 0;
        long elapsed = System.currentTimeMillis() - lastTeleport;
        if (elapsed >= cooldownMs) {
            homeTeleportCooldowns.remove(ownerUuid);
            return 0;
        }
        return (cooldownMs - elapsed + 999) / 1000;
    }

    public void releaseExpiredReservations() {
        List<PlotSlot> expired = slotRepository.getExpiredReservations();
        for (PlotSlot slot : expired) {
            slot.release();
            slotRepository.save(slot);
            LOGGER.info("Expired reservation released: slotId={}", slot.getId());
        }
    }

    public void cleanCooldowns() {
        long now = System.currentTimeMillis();
        Config.SchedulerConfig sc = configManager.getConfig().getPlayerLandAllocation().getScheduler();
        long creationCooldownMs = sc.getCreationCooldownSeconds() * 1000L;
        long homeCooldownMs = sc.getHomeTeleportCooldownSeconds() * 1000L;
        if (creationCooldownMs > 0) {
            creationCooldowns.entrySet().removeIf(e -> now - e.getValue() >= creationCooldownMs);
        }
        if (homeCooldownMs > 0) {
            homeTeleportCooldowns.entrySet().removeIf(e -> now - e.getValue() >= homeCooldownMs);
        }
    }

    public void retireSlot(String regionId) {
        PlotSlot slot = slotRepository.getByRegionId(regionId);
        if (slot != null && (slot.getState() == PlotSlotState.ALLOCATED || slot.getState() == PlotSlotState.OCCUPIED)) {
            slot.retire();
            slotRepository.save(slot);
            LOGGER.info("Slot {} retired for region {}", slot.getId(), regionId);
        }
    }

    public void releaseSlotForRegion(String regionId) {
        PlotSlot slot = slotRepository.getByRegionId(regionId);
        if (slot != null) {
            slot.forceRelease();
            slotRepository.save(slot);
            LOGGER.info("Slot {} released for deleted region {}", slot.getId(), regionId);
        }
    }

    public void recycleSlot(String slotId) {
        PlotSlot slot = slotRepository.get(slotId);
        if (slot == null) {
            throw new IllegalArgumentException("Slot nao encontrado: " + slotId);
        }
        if (slot.getState() != PlotSlotState.RETIRED) {
            throw new IllegalStateException("Slot " + slotId + " nao esta RETIRED (estado: " + slot.getState() + ")");
        }
        slot.recycle();
        slotRepository.save(slot);
        LOGGER.info("Slot {} recycled", slotId);
    }

    public PlayerRegionHome getHome(String regionId) {
        return homeRepository.get(regionId);
    }

    public Collection<BiomeOption> getBiomeOptions() {
        return biomeOptionRegistry.getAll();
    }

    private boolean isTimedOut(AllocationRequest request, Config.SchedulerConfig sc) {
        return System.currentTimeMillis() - request.getUpdatedAt() > sc.getRequestTimeoutSeconds() * 1000L;
    }

    private void failRequest(AllocationRequest request, AllocationRequestState target, String reason, ServerLevel level) {
        request.forceTransitionTo(target);
        request.setFailureReason(reason);
        requestRepository.save(request);
        searchIterators.remove(request.getId());
        if (level != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.getOwnerUuid());
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNão foi possível criar seu terreno: " + reason));
            }
        }
    }

    private void tryReleaseSlot(AllocationRequest request) {
        String slotId = request.getPlotSlotId();
        if (slotId != null) {
            PlotSlot slot = slotRepository.get(slotId);
            if (slot != null && slot.getState() == PlotSlotState.RESERVED) {
                slot.release();
                slotRepository.save(slot);
            }
        }
    }
}
