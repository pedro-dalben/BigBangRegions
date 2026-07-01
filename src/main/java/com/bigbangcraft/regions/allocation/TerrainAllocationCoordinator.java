package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.repository.AllocationRequestPreparationRepository;
import com.bigbangcraft.regions.repository.AllocationRequestRepository;
import com.bigbangcraft.regions.repository.PlayerRegionHomeRepository;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.storage.DatabaseManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TerrainAllocationCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-TerrainAllocationCoordinator");

    private final Map<String, PlotSlotService.PlotSlotIterator> searchIterators = new ConcurrentHashMap<>();
    private final Map<String, PreparationHandle> preparationHandles = new ConcurrentHashMap<>();
    private final Map<String, PreparationResult> completedPreparations = new ConcurrentHashMap<>();
    private final Map<String, LoadedWorldValidationResult> validatedWorlds = new ConcurrentHashMap<>();

    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final AllocationRequestRepository requestRepository;
    private final AllocationRequestPreparationRepository preparationRepository;
    private final PlotSlotRepository slotRepository;
    private final PlotSlotService slotService;
    private final PlayerRegionHomeRepository homeRepository;
    private final RegionRepository regionRepository;
    private final BiomeSearchService biomeSearchService;
    private final BiomeOptionRegistry biomeOptionRegistry;
    private final RegionCache regionCache;
    private final RegionMembershipCache membershipCache;
    private final PlotChunkPlanResolver chunkPlanResolver;
    private final RegionPreparationQueue preparationQueue;
    private final ChunkPreparationService chunkPreparationService;
    private final LoadedWorldValidator loadedWorldValidator;

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
        this.preparationRepository = new AllocationRequestPreparationRepository(databaseManager);
        this.slotRepository = slotRepository;
        this.slotService = slotService;
        this.homeRepository = homeRepository;
        this.regionRepository = regionRepository;
        this.biomeSearchService = biomeSearchService;
        this.biomeOptionRegistry = biomeOptionRegistry;
        this.regionCache = regionCache;
        this.membershipCache = membershipCache;
        this.chunkPlanResolver = new DefaultPlotChunkPlanResolver(configManager);
        this.preparationQueue = new RegionPreparationQueue();
        this.chunkPreparationService = new TicketBackedChunkPreparationService(new SimpleRegionChunkTicketManager());
        this.loadedWorldValidator = new PreparedChunkLoadedWorldValidator(configManager, biomeOptionRegistry);
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
        LOGGER.info("Allocation request created (search): id={}, owner={}, biome={}", id, ownerUuid, opt.get().getKey());
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

        cleanupRequestResources(request, PreparationCancelReason.CANCELLED, false);
        LOGGER.info("Allocation request cancelled: id={}, owner={}", request.getId(), ownerUuid);
    }

    public int processNextRequest(ServerLevel level) {
        chunkPreparationService.tick();

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
            request.transitionTo(AllocationRequestState.VIRTUAL_SEARCHING);
            requestRepository.save(request);
            return 1;
        }

        if (request.getState() == AllocationRequestState.VIRTUAL_SEARCHING) {
            return processVirtualSearch(request, level, lac, sc);
        }

        if (request.getState() == AllocationRequestState.VIRTUAL_VALIDATED) {
            request.transitionTo(AllocationRequestState.SLOT_RESERVED);
            requestRepository.save(request);
            return 1;
        }

        if (request.getState() == AllocationRequestState.SLOT_RESERVED) {
            if (isTimedOut(request, sc)) {
                tryReleaseSlot(request);
                failRequest(request, AllocationRequestState.FAILED_NO_TERRAIN, "Tempo limite excedido durante reserva", level);
                return 1;
            }
            preparationQueue.enqueue(request.getId(), RegionPreparationPriority.INTERACTIVE);
            request.transitionTo(AllocationRequestState.PREPARING_CHUNKS);
            requestRepository.save(request);
            return 1;
        }

        if (request.getState() == AllocationRequestState.PREPARING_CHUNKS) {
            if (isTimedOut(request, sc)) {
                handlePreparationFailure(request, level, AllocationRequestState.FAILED, "Tempo limite excedido antes do carregamento de chunks");
                return 1;
            }
            return startPreparationIfPossible(request, level);
        }

        if (request.getState() == AllocationRequestState.WAITING_FOR_CHUNKS) {
            PreparationResult result = completedPreparations.remove(request.getId());
            if (result == null) {
                return 0;
            }
            if (result.type() != PreparationResultType.READY) {
                handlePreparationFailure(request, level, AllocationRequestState.FAILED, String.join("; ", result.diagnostics()));
                return 1;
            }

            AllocationRequestPreparation preparation = preparationRepository.get(request.getId());
            if (preparation != null) {
                preparation.updateTicketState("READY");
                preparationRepository.save(preparation);
            }

            request.transitionTo(AllocationRequestState.VALIDATING_LOADED_WORLD);
            requestRepository.save(request);
            return 1;
        }

        if (request.getState() == AllocationRequestState.VALIDATING_LOADED_WORLD) {
            AllocationRequestPreparation preparation = preparationRepository.get(request.getId());
            if (preparation == null) {
                pauseForRecovery(request, "Metadados de preparacao nao encontrados", null);
                return 1;
            }

            ReservedPlotCandidate candidate = buildReservedCandidate(request, lac);
            if (candidate == null) {
                handlePreparationFailure(request, level, AllocationRequestState.FAILED_VALIDATION, "Nao foi possivel reconstruir o candidato reservado");
                return 1;
            }

            ChunkPreparationPlan plan = ChunkPreparationPlanCodec.decode(preparation.getChunkPlanJson());
            LoadedWorldValidationResult validation = loadedWorldValidator.validate(level, candidate, plan);
            if (!validation.accepted()) {
                AllocationRequestPreparation prep = preparationRepository.get(request.getId());
                if (prep != null) {
                    prep.markFailure(validation.failureReason().name(), String.join("; ", validation.diagnostics()));
                    prep.updateTicketState("FAILED_VALIDATION");
                    preparationRepository.save(prep);
                }
                invalidateReservedSlot(candidate.slotId(), validation.failureReason().name());
                handlePreparationFailure(request, level, AllocationRequestState.FAILED_VALIDATION, String.join("; ", validation.diagnostics()));
                return 1;
            }

            validatedWorlds.put(request.getId(), validation);
            request.setRegionId("player_" + request.getOwnerUuid().toString().substring(0, 8) + "_" + System.currentTimeMillis());
            request.transitionTo(AllocationRequestState.REGION_CREATING);
            requestRepository.save(request);
            return 1;
        }

        if (request.getState() == AllocationRequestState.REGION_CREATING) {
            LoadedWorldValidationResult validation = validatedWorlds.get(request.getId());
            if (validation == null) {
                return recoverCreatingFromSqlite(request);
            }

            try {
                createRegionInSingleTransaction(request, lac, level, validation);
            } catch (Exception e) {
                LOGGER.error("Failed to create region in transaction for request={}: {}", request.getId(), e.getMessage(), e);
                pauseForRecovery(request, "Falha durante criacao da regiao: " + e.getMessage(), validation.failureReason().name());
            }
            return 1;
        }

        if (request.getState() == AllocationRequestState.PAUSED_RECOVERY) {
            return 0;
        }

        return 0;
    }

    private int processVirtualSearch(AllocationRequest request, ServerLevel level, Config.PlayerLandAllocationConfig lac, Config.SchedulerConfig sc) {
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
            Optional<PlotSlotService.PlotSlotCandidate> optCandidate = iterator.peek();
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

            if (!slotService.isSlotEligible(candidate.minX, candidate.minZ, lac.getSlotSize())) {
                iterator.advance();
                continue;
            }

            evaluated++;
            PlotFootprint claimFootprint = buildClaimFootprint(candidate.minX, candidate.minZ, lac);
            BiomeSearchService.MatchResult biomeMatch = biomeSearchService.evaluateBiomeOptionMatching(
                level, claimFootprint.minX(), claimFootprint.maxX(),
                claimFootprint.minZ(), claimFootprint.maxZ(),
                biomeOpt.get()
            );
            iterator.advance();
            if (biomeMatch != BiomeSearchService.MatchResult.MATCH) {
                continue;
            }

            String slotId = lac.getTargetDimension() + ":" + candidate.gridX + ":" + candidate.gridZ;
            PlotSlot existing = slotRepository.getByGrid(lac.getTargetDimension(), candidate.gridX, candidate.gridZ);
            if (existing != null && existing.getState().isOccupied()) {
                continue;
            }

            PlotSlot slot = existing != null && existing.getState() == PlotSlotState.RELEASED
                ? existing
                : new PlotSlot(
                    slotId, lac.getTargetDimension(), candidate.gridX, candidate.gridZ,
                    candidate.minX, candidate.minZ, lac.getSlotSize(),
                    PlotSlotState.RELEASED, null, null, null,
                    null, null, null, System.currentTimeMillis(), System.currentTimeMillis()
                );
            slot.reserve(request.getOwnerUuid(), request.getRequestedBiomeOption(), sc.getReservationLeaseSeconds() * 1000L);
            slotRepository.save(slot);

            request.transitionTo(AllocationRequestState.VIRTUAL_VALIDATED);
            requestRepository.save(request);

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

    private int startPreparationIfPossible(AllocationRequest request, ServerLevel level) {
        Optional<String> activeRequestId = preparationQueue.activeRequestId();
        if (activeRequestId.isPresent() && !activeRequestId.get().equals(request.getId())) {
            return 0;
        }

        if (preparationHandles.containsKey(request.getId())) {
            return 0;
        }

        Optional<String> started = preparationQueue.tryStartNext();
        if (started.isEmpty() || !started.get().equals(request.getId())) {
            return 0;
        }

        Config.PlayerLandAllocationConfig lac = configManager.getConfig().getPlayerLandAllocation();
        ReservedPlotCandidate candidate = buildReservedCandidate(request, lac);
        if (candidate == null) {
            handlePreparationFailure(request, level, AllocationRequestState.FAILED_VALIDATION, "Candidato reservado nao encontrado");
            return 1;
        }

        request.incrementPreparationAttempt();
        requestRepository.save(request);

        ChunkPreparationPlan plan;
        try {
            plan = chunkPlanResolver.resolve(candidate.footprint(), buildRegionGeometry(candidate.footprint()), PreparationPurpose.PHYSICAL_VALIDATION);
        } catch (IllegalStateException e) {
            handlePreparationFailure(request, level, AllocationRequestState.FAILED_VALIDATION, e.getMessage());
            return 1;
        }

        long now = System.currentTimeMillis();
        AllocationRequestPreparation preparation = new AllocationRequestPreparation(
            request.getId(),
            request.getPreparationAttempt(),
            now,
            now + plan.timeout().toMillis(),
            candidate.slotId(),
            ChunkPreparationPlanCodec.encode(plan),
            null,
            null,
            "REQUESTED",
            true,
            now
        );
        preparationRepository.save(preparation);

        PreparationHandle handle = chunkPreparationService.beginPreparation(level, candidate, plan, this::handlePreparationCallback);
        preparationHandles.put(request.getId(), handle);

        request.transitionTo(AllocationRequestState.WAITING_FOR_CHUNKS);
        requestRepository.save(request);
        LOGGER.info("[BigBangRegions] Preparation started request={} chunks={} candidate={}.", request.getId(), plan.chunkCount(), candidate.footprint().centerX() + "," + candidate.footprint().centerZ());
        return 1;
    }

    private void handlePreparationCallback(PreparationHandle handle, PreparationResult result) {
        completedPreparations.put(handle.requestId(), result);
    }

    private void createRegionInSingleTransaction(AllocationRequest request,
                                                 Config.PlayerLandAllocationConfig lac,
                                                 ServerLevel level,
                                                 LoadedWorldValidationResult validation) throws SQLException {
        PlotSlot slot = slotRepository.get(request.getPlotSlotId());
        if (slot == null) {
            throw new SQLException("Slot nao encontrado durante criacao: " + request.getPlotSlotId());
        }

        synchronized (databaseManager) {
            Connection conn = databaseManager.getConnection();
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String regionId = request.getRegionId();
                PlotFootprint claimFootprint = buildClaimFootprint(slot.getMinX(), slot.getMinZ(), lac);
                RegionBounds bounds = new RegionBounds(
                    lac.getTargetDimension(),
                    claimFootprint.minX(), -64, claimFootprint.minZ(),
                    claimFootprint.maxX(), 320, claimFootprint.maxZ()
                );

                long now = System.currentTimeMillis();
                Region region = new Region(regionId, "Player Region", RegionType.PLAYER_REGION,
                    bounds, 100, request.getOwnerUuid(), request.getOwnerUuid(), now, now, "ACTIVE");
                regionRepository.saveOnConnection(conn, region);
                regionRepository.saveMembersOnConnection(conn, regionId, Collections.emptyMap());

                slot.allocate(regionId);
                slot.occupy();
                slotRepository.saveOnConnection(conn, slot);

                BlockPos homePos = validation.safeSpawn().blockPos();
                PlayerRegionHome home = new PlayerRegionHome(
                    regionId,
                    lac.getTargetDimension(),
                    homePos.getX() + 0.5,
                    homePos.getY(),
                    homePos.getZ() + 0.5,
                    0.0f,
                    0.0f,
                    now,
                    now
                );
                homeRepository.saveOnConnection(conn, home);

                request.transitionTo(AllocationRequestState.COMPLETED);
                requestRepository.saveOnConnection(conn, request);

                conn.commit();

                regionCache.add(region);
                membershipCache.loadFromRegion(region);

                snapshotCreatedTerrain(regionId, bounds, level, homePos, configManager.getConfig().getPlayerLandAllocation().getBorder().isRestoreOnDelete());
                postRegionCreationSetup(request, bounds, level, homePos);

                cleanupRequestResources(request, PreparationCancelReason.COMPLETED, true);
                LOGGER.info("[BigBangRegions] Preparation ready request={} duration={}ms.", request.getId(), System.currentTimeMillis() - now);
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
            boolean restored = RegionTerrainSnapshot.restore(level, region, getRestoreDirectory());
            if (restored) {
                LOGGER.info("Restored terrain snapshot for region {}", region.getId());
            }
            return restored;
        } catch (Exception e) {
            LOGGER.warn("Failed to restore terrain snapshot for region {}: {}", region.getId(), e.getMessage());
            return false;
        }
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

        generateGlassBorder(level, bounds, borderConfig.getMaterial(), borderConfig.isCreateCeiling());
        applyRegionBiome(level, bounds, request.getRequestedBiomeOption());
        generateSpawnPlatform(level, homePos);

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.getOwnerUuid());
        if (player != null) {
            player.teleportTo(level, homePos.getX() + 0.5, homePos.getY(), homePos.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("§aSeu terreno foi criado com sucesso!"));
            player.sendSystemMessage(Component.literal("§7Você foi teleportado para sua nova região."));
        }
    }

    private void generateSpawnPlatform(ServerLevel level, BlockPos homePos) {
        try {
            var cobblestone = net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            var glowstone = net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState();

            int minX = homePos.getX() - 1;
            int maxX = homePos.getX() + 2;
            int minZ = homePos.getZ() - 1;
            int maxZ = homePos.getZ() + 2;
            int yFloor = homePos.getY() - 1;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, yFloor, z), cobblestone, 2);
                    level.setBlock(new BlockPos(x, yFloor + 1, z), air, 2);
                    level.setBlock(new BlockPos(x, yFloor + 2, z), air, 2);
                }
            }

            level.setBlock(new BlockPos(homePos.getX(), yFloor, homePos.getZ()), glowstone, 2);
        } catch (Exception e) {
            LOGGER.error("Failed to generate spawn platform", e);
        }
    }

    private void generateGlassBorder(ServerLevel level, RegionBounds bounds, String materialId, boolean createCeiling) {
        try {
            ResourceLocation loc = ResourceLocation.parse(materialId);
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
            if (opt.isEmpty() || opt.get().getAcceptedBiomeIds().isEmpty()) {
                return;
            }
            LOGGER.warn("Skipping direct biome palette mutation for region {} (biome option {})", bounds, biomeOptionKey);
        } catch (Exception e) {
            LOGGER.error("Failed to apply biome to region", e);
        }
    }

    private int recoverCreatingFromSqlite(AllocationRequest request) {
        String regionId = request.getRegionId();
        if (regionId == null) {
            pauseForRecovery(request, "REGION_CREATING sem region_id. Contate um administrador.", null);
            return 1;
        }

        boolean existsInDb = regionExistsInDb(regionId);
        if (existsInDb) {
            reloadCachesFromDb();
            Region cached = regionCache.get(regionId);
            if (cached != null) {
                request.transitionTo(AllocationRequestState.COMPLETED);
                requestRepository.save(request);
                cleanupRequestResources(request, PreparationCancelReason.COMPLETED, true);
                LOGGER.info("Recovery: region {} found in DB, marking request {} complete", regionId, request.getId());
                return 1;
            }
        }

        pauseForRecovery(request, "Criacao interrompida antes da confirmacao final da regiao", null);
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

    private PlotFootprint buildClaimFootprint(int slotMinX, int slotMinZ, Config.PlayerLandAllocationConfig lac) {
        int claimOffset = (lac.getSlotSize() - lac.getInitialClaimSize()) / 2;
        int claimMinX = slotMinX + claimOffset;
        int claimMinZ = slotMinZ + claimOffset;
        return new PlotFootprint(
            claimMinX,
            claimMinX + lac.getInitialClaimSize() - 1,
            claimMinZ,
            claimMinZ + lac.getInitialClaimSize() - 1
        );
    }

    private RegionBuildGeometry buildRegionGeometry(PlotFootprint footprint) {
        return new RegionBuildGeometry(List.of(footprint));
    }

    private ReservedPlotCandidate buildReservedCandidate(AllocationRequest request, Config.PlayerLandAllocationConfig lac) {
        String slotId = request.getPlotSlotId();
        if (slotId == null) {
            return null;
        }
        PlotSlot slot = slotRepository.get(slotId);
        if (slot == null) {
            return null;
        }
        return new ReservedPlotCandidate(
            request.getId(),
            slotId,
            request.getRequestedBiomeOption(),
            lac.getTargetDimension(),
            buildClaimFootprint(slot.getMinX(), slot.getMinZ(), lac)
        );
    }

    private boolean isTimedOut(AllocationRequest request, Config.SchedulerConfig sc) {
        return System.currentTimeMillis() - request.getUpdatedAt() > sc.getRequestTimeoutSeconds() * 1000L;
    }

    private void failRequest(AllocationRequest request, AllocationRequestState target, String reason, ServerLevel level) {
        request.forceTransitionTo(target);
        request.setFailureReason(reason);
        requestRepository.save(request);
        searchIterators.remove(request.getId());
        cleanupRequestResources(request, PreparationCancelReason.FAILED, false);
        if (level != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.getOwnerUuid());
            if (player != null) {
                player.sendSystemMessage(Component.literal("§cNão foi possível criar seu terreno: " + reason));
            }
        }
    }

    private void handlePreparationFailure(AllocationRequest request, ServerLevel level, AllocationRequestState target, String reason) {
        AllocationRequestPreparation preparation = preparationRepository.get(request.getId());
        if (preparation != null) {
            preparation.markFailure(target.name(), reason);
            preparation.updateTicketState("FAILED");
            preparation.markCleanupRequired(false);
            preparationRepository.save(preparation);
        }
        tryReleaseSlot(request);
        failRequest(request, target, reason, level);
    }

    private void invalidateReservedSlot(String slotId, String reason) {
        PlotSlot slot = slotRepository.get(slotId);
        if (slot != null) {
            slot.markInvalidated(reason);
            slotRepository.save(slot);
        }
    }

    private void tryReleaseSlot(AllocationRequest request) {
        String slotId = request.getPlotSlotId();
        if (slotId != null) {
            PlotSlot slot = slotRepository.get(slotId);
            if (slot != null && (slot.getState() == PlotSlotState.RESERVED || slot.getState() == PlotSlotState.PLAYER_RESERVED || slot.getState() == PlotSlotState.PREPARING)) {
                slot.forceRelease();
                slotRepository.save(slot);
            }
        }
    }

    private void cleanupRequestResources(AllocationRequest request, PreparationCancelReason reason, boolean deletePreparationRecord) {
        searchIterators.remove(request.getId());
        validatedWorlds.remove(request.getId());
        completedPreparations.remove(request.getId());

        PreparationHandle handle = preparationHandles.remove(request.getId());
        if (handle != null) {
            chunkPreparationService.cancelPreparation(handle, reason);
        }
        preparationQueue.remove(request.getId());

        AllocationRequestPreparation preparation = preparationRepository.get(request.getId());
        if (preparation != null) {
            preparation.markCleanupRequired(false);
            preparation.updateTicketState(reason.name());
            preparationRepository.save(preparation);
            if (deletePreparationRecord) {
                preparationRepository.delete(request.getId());
            }
        }
    }

    private void pauseForRecovery(AllocationRequest request, String message, String errorCode) {
        request.forceTransitionTo(AllocationRequestState.PAUSED_RECOVERY);
        request.setFailureReason(message);
        requestRepository.save(request);
        AllocationRequestPreparation preparation = preparationRepository.get(request.getId());
        if (preparation != null) {
            if (errorCode != null) {
                preparation.markFailure(errorCode, message);
            }
            preparation.updateTicketState("PAUSED_RECOVERY");
            preparationRepository.save(preparation);
        }
        cleanupRequestResources(request, PreparationCancelReason.RECOVERY, false);
    }
}
