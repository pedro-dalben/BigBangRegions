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
import com.bigbangcraft.regions.repository.AllocationSearchCursorRepository;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TerrainAllocationCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-TerrainAllocationCoordinator");

    private final Map<String, PreparationHandle> preparationHandles = new ConcurrentHashMap<>();
    private final Map<String, PreparationResult> completedPreparations = new ConcurrentHashMap<>();
    private final Map<String, LoadedWorldValidationResult> validatedWorlds = new ConcurrentHashMap<>();
    private final Map<String, Long> lastProgressNotifications = new ConcurrentHashMap<>();
    private final Map<String, Long> lastProgressLogs = new ConcurrentHashMap<>();
    private final Map<String, List<int[]>> sectorSequenceCache = new ConcurrentHashMap<>();

    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final AllocationRequestRepository requestRepository;
    private final AllocationRequestPreparationRepository preparationRepository;
    private final AllocationSearchCursorRepository cursorRepository;
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
    private final BiomeAnchorLocator biomeAnchorLocator;

    private final Map<UUID, Long> creationCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> homeTeleportCooldowns = new ConcurrentHashMap<>();

    private enum LocalAnchorSearchResult {
        RESERVED,
        CONTINUE,
        EXHAUSTED
    }

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
        this.cursorRepository = new AllocationSearchCursorRepository(databaseManager);
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
        this.biomeAnchorLocator = new WorldgenBiomeAnchorLocator();
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
                resumeSearchAfterCandidateFailure(
                    request,
                    lac,
                    level,
                    "rejected_physical_validation",
                    "Preparacao do terreno falhou: " + String.join("; ", result.diagnostics()),
                    false
                );
                return 1;
            }

            AllocationRequestPreparation preparation = preparationRepository.get(request.getId());
            if (preparation != null) {
                preparation.updateTicketState("READY");
                preparationRepository.save(preparation);
            } else {
                LOGGER.warn("[BigBangRegions] Preparation metadata missing while chunks became ready for request={}. Rebuilding validation plan.", request.getId());
            }

            request.transitionTo(AllocationRequestState.VALIDATING_LOADED_WORLD);
            requestRepository.save(request);
            return 1;
        }

        if (request.getState() == AllocationRequestState.VALIDATING_LOADED_WORLD) {
            AllocationRequestPreparation preparation = preparationRepository.get(request.getId());
            ReservedPlotCandidate candidate = buildReservedCandidate(request, lac);
            if (candidate == null) {
                handlePreparationFailure(request, level, AllocationRequestState.FAILED_VALIDATION, "Nao foi possivel reconstruir o candidato reservado");
                return 1;
            }

            ChunkPreparationPlan plan;
            if (preparation != null && preparation.getChunkPlanJson() != null) {
                plan = ChunkPreparationPlanCodec.decode(preparation.getChunkPlanJson());
            } else {
                LOGGER.warn("[BigBangRegions] Preparation metadata not found for request={}. Recomputing physical validation plan from reserved candidate.", request.getId());
                plan = resolvePhysicalValidationPlan(candidate);
            }
            LoadedWorldValidationResult validation = loadedWorldValidator.validate(level, candidate, plan);
            if (!validation.accepted()) {
                AllocationRequestPreparation prep = preparationRepository.get(request.getId());
                if (prep != null) {
                    prep.markFailure(validation.failureReason().name(), String.join("; ", validation.diagnostics()));
                    prep.updateTicketState("FAILED_VALIDATION");
                    preparationRepository.save(prep);
                }
                invalidateReservedSlot(candidate.slotId(), validation.failureReason().name());
                resumeSearchAfterCandidateFailure(
                    request,
                    lac,
                    level,
                    "rejected_physical_validation",
                    String.join("; ", validation.diagnostics()),
                    true
                );
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
            return recoverPausedRequest(request, level, lac);
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
        AllocationSearchCursor cursor = loadOrCreateCursor(request, lac);
        Config.WorldgenSearchConfig worldgen = lac.getWorldgenSearch();
        WorldgenSearchContext context = biomeSearchService.getContextFactory().getOrCreate(level, configManager.getConfig());
        long deadline = System.nanoTime() + Math.max(1L, worldgen.getMaxSearchWorkNanosPerTick());
        int maxSteps = Math.max(1, worldgen.getMaxSearchStepsPerTick());
        boolean progressed = false;

        for (int step = 0; step < maxSteps && System.nanoTime() < deadline; step++) {
            AllocationSearchSector sector = resolveCurrentSector(request, cursor, worldgen);
            if (sector == null) {
                if (tryFallbackSpiral(request, level, lac, sc, biomeOpt.get(), cursor)) {
                    return 1;
                }
                cursor.setLastRejectionReason("rejected_anchor_not_found");
                cursorRepository.save(cursor);
                failRequest(request, AllocationRequestState.FAILED_NO_TERRAIN, "Nenhuma area valida encontrada dentro das bandas ativas", level);
                return 1;
            }

            if (hasPendingAnchor(cursor, worldgen)) {
                LocalAnchorSearchResult pendingResult = continueAnchorSearch(request, level, lac, sc, biomeOpt.get(), cursor, sector, deadline);
                if (pendingResult == LocalAnchorSearchResult.RESERVED) {
                    return 1;
                }
                if (pendingResult == LocalAnchorSearchResult.CONTINUE) {
                    progressed = true;
                    break;
                }
                markSectorRejected(cursor, sector, cursor.getLastRejectionReason() == null ? "rejected_anchor_candidates_exhausted" : cursor.getLastRejectionReason());
                progressed = true;
                continue;
            }

            if (!sectorContainsAcceptedBiome(context, biomeOpt.get(), sector)) {
                markSectorRejected(cursor, sector, "rejected_no_biome_in_sector");
                progressed = true;
                continue;
            }

            BiomeAnchorSearchStepResult anchorResult = biomeAnchorLocator.searchStep(
                context,
                biomeOpt.get(),
                anchorCursorForSector(cursor, sector, worldgen),
                new SearchBudget(worldgen.getBlockCheckInterval(), worldgen.getMaxLocateCallsPerSearchStep())
            );
            if (anchorResult instanceof BiomeAnchorSearchStepResult.Found found) {
                cursor = found.nextCursor();
                cursor.setAnchorsFound(cursor.getAnchorsFound() + 1);
                LocalAnchorSearchResult anchorSearchResult = continueAnchorSearch(request, level, lac, sc, biomeOpt.get(), cursor, sector, deadline);
                if (anchorSearchResult == LocalAnchorSearchResult.RESERVED) {
                    return 1;
                }
                if (anchorSearchResult == LocalAnchorSearchResult.CONTINUE) {
                    progressed = true;
                    break;
                }
                markSectorRejected(cursor, sector, cursor.getLastRejectionReason() == null ? "rejected_anchor_not_found" : cursor.getLastRejectionReason());
                progressed = true;
                continue;
            }

            cursor.setLastRejectionReason("rejected_anchor_not_found");
            markSectorRejected(cursor, sector, "rejected_anchor_not_found");
            progressed = true;
        }

        if (progressed) {
            request.incrementAttempts();
            requestRepository.save(request);
            maybeEmitProgress(request, level, cursor);
            cursorRepository.save(cursor);
            return 1;
        }

        return 0;
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
        if (preparationRepository.get(request.getId()) == null) {
            LOGGER.error("[BigBangRegions] Failed to persist preparation metadata for request={}. Resuming search instead of entering chunk wait states.", request.getId());
            resumeSearchAfterCandidateFailure(
                request,
                lac,
                level,
                "rejected_physical_validation",
                "Metadados de preparacao nao persistidos",
                false
            );
            return 1;
        }

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

    private ChunkPreparationPlan resolvePhysicalValidationPlan(ReservedPlotCandidate candidate) {
        return chunkPlanResolver.resolve(
            candidate.footprint(),
            buildRegionGeometry(candidate.footprint()),
            PreparationPurpose.PHYSICAL_VALIDATION
        );
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

    private AllocationSearchCursor loadOrCreateCursor(AllocationRequest request, Config.PlayerLandAllocationConfig lac) {
        AllocationSearchCursor cursor = cursorRepository.get(request.getId());
        if (cursor != null) {
            return cursor;
        }

        cursor = new AllocationSearchCursor(request.getId());
        List<Config.AllocationBandConfig> bands = enabledBands(lac.getWorldgenSearch());
        if (!bands.isEmpty()) {
            cursor.setCurrentBandId(bands.getFirst().getId());
        }
        cursor.setLastProgressAt(System.currentTimeMillis());
        cursorRepository.save(cursor);
        return cursor;
    }

    private List<Config.AllocationBandConfig> enabledBands(Config.WorldgenSearchConfig worldgen) {
        return worldgen.getAllocationBands().stream()
            .filter(Config.AllocationBandConfig::isEnabled)
            .sorted(Comparator.comparingInt(Config.AllocationBandConfig::getMinRadiusBlocks))
            .toList();
    }

    private AllocationSearchSector resolveCurrentSector(AllocationRequest request, AllocationSearchCursor cursor, Config.WorldgenSearchConfig worldgen) {
        List<Config.AllocationBandConfig> bands = enabledBands(worldgen);
        if (bands.isEmpty()) {
            return null;
        }

        int sectorsPerBand = Math.max(1, worldgen.getMaxSectorsPerRequest());
        int bandIndex = Math.max(0, cursor.getCurrentBandId() == null ? 0 : indexOfBand(bands, cursor.getCurrentBandId()));
        while (bandIndex < bands.size()) {
            Config.AllocationBandConfig band = bands.get(bandIndex);
            if (cursor.getCurrentSectorIndex() >= sectorsPerBand) {
                bandIndex++;
                cursor.setCurrentSectorIndex(0);
                cursor.setCurrentBandId(bandIndex < bands.size() ? bands.get(bandIndex).getId() : null);
                continue;
            }
            int[] coords = sectorCoordinates(request, band, cursor.getCurrentSectorIndex(), worldgen.getSectorSizeBlocks());
            cursor.setCurrentBandId(band.getId());
            cursor.setSectorX(coords[0]);
            cursor.setSectorZ(coords[1]);
            return new AllocationSearchSector(
                band.getId(),
                cursor.getCurrentSectorIndex(),
                coords[0],
                coords[1],
                worldgen.getSectorSizeBlocks(),
                band.getMinRadiusBlocks(),
                band.getMaxRadiusBlocks()
            );
        }
        return null;
    }

    private int indexOfBand(List<Config.AllocationBandConfig> bands, String bandId) {
        for (int i = 0; i < bands.size(); i++) {
            if (bands.get(i).getId().equals(bandId)) {
                return i;
            }
        }
        return 0;
    }

    private int[] sectorCoordinates(AllocationRequest request, Config.AllocationBandConfig band, int sectorIndex, int sectorSizeBlocks) {
        List<int[]> cells = sectorSequenceCache.computeIfAbsent(
            request.getId() + ":" + band.getId() + ":" + sectorSizeBlocks,
            ignored -> buildSectorSequence(request, band, sectorSizeBlocks)
        );
        if (cells.isEmpty()) {
            int minRing = Math.max(1, Math.floorDiv(band.getMinRadiusBlocks(), sectorSizeBlocks));
            return new int[]{minRing, 0};
        }
        return cells.get(Math.min(sectorIndex, cells.size() - 1));
    }

    private List<int[]> buildSectorSequence(AllocationRequest request, Config.AllocationBandConfig band, int sectorSizeBlocks) {
        int minRing = Math.max(1, Math.floorDiv(band.getMinRadiusBlocks(), sectorSizeBlocks));
        int maxRing = Math.max(minRing, Math.floorDiv(band.getMaxRadiusBlocks(), sectorSizeBlocks));
        List<int[]> cells = new ArrayList<>();
        for (int ring = minRing; ring <= maxRing; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    int radius = Math.max(Math.abs(dx), Math.abs(dz));
                    if (radius >= minRing && radius <= maxRing) {
                        cells.add(new int[]{dx, dz});
                    }
                }
            }
        }
        if (cells.isEmpty()) {
            return List.of(new int[]{minRing, 0});
        }
        java.util.Collections.shuffle(cells, new java.util.Random(deterministicSectorSeed(request, band)));
        return cells;
    }

    private long deterministicSectorSeed(AllocationRequest request, Config.AllocationBandConfig band) {
        long seed = request.getOwnerUuid().getMostSignificantBits() ^ request.getOwnerUuid().getLeastSignificantBits();
        seed = 31L * seed + request.getId().hashCode();
        seed = 31L * seed + request.getRequestedBiomeOption().hashCode();
        seed = 31L * seed + request.getTargetDimension().hashCode();
        seed = 31L * seed + band.getId().hashCode();
        return seed;
    }

    private boolean sectorContainsAcceptedBiome(WorldgenSearchContext context, BiomeOption option, AllocationSearchSector sector) {
        Set<ResourceKey<net.minecraft.world.level.biome.Biome>> accepted = new HashSet<>();
        for (String biomeId : option.getAcceptedBiomeIds()) {
            try {
                accepted.add(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId)));
            } catch (Exception ignored) {
            }
        }
        if (accepted.isEmpty()) {
            return false;
        }

        int sectorMinX = sector.sectorX() * sector.sectorSizeBlocks();
        int sectorMinZ = sector.sectorZ() * sector.sectorSizeBlocks();
        int sectorMaxX = sectorMinX + sector.sectorSizeBlocks() - 1;
        int sectorMaxZ = sectorMinZ + sector.sectorSizeBlocks() - 1;
        int gridSize = 5;
        int stepX = Math.max(1, (sectorMaxX - sectorMinX) / (gridSize - 1));
        int stepZ = Math.max(1, (sectorMaxZ - sectorMinZ) / (gridSize - 1));
        long seed = deterministicSectorSeedFromValues(option.getKey(), sector.bandId(), sector.sectorIndex(), sector.sectorX(), sector.sectorZ());

        for (int gz = 0; gz < gridSize; gz++) {
            for (int gx = 0; gx < gridSize; gx++) {
                int jitterX = deterministicJitter(seed, gx, gz, Math.max(1, stepX / 3));
                int jitterZ = deterministicJitter(seed ^ 0x9E3779B97F4A7C15L, gz, gx, Math.max(1, stepZ / 3));
                int x = clamp(sectorMinX + (gx * stepX) + jitterX, sectorMinX, sectorMaxX);
                int z = clamp(sectorMinZ + (gz * stepZ) + jitterZ, sectorMinZ, sectorMaxZ);
                ResourceKey<net.minecraft.world.level.biome.Biome> key = biomeSearchService.getVirtualSampler()
                    .sampleAtBlock(context, x, context.sampleBlockY(), z);
                if (key != null && accepted.contains(key)) {
                    return true;
                }
            }
        }

        int[][] fallbackPoints = new int[][]{
            {sector.centerBlockX(), sector.centerBlockZ()},
            {sectorMinX, sectorMinZ},
            {sectorMaxX, sectorMinZ},
            {sectorMinX, sectorMaxZ},
            {sectorMaxX, sectorMaxZ}
        };
        for (int[] point : fallbackPoints) {
            ResourceKey<net.minecraft.world.level.biome.Biome> key = biomeSearchService.getVirtualSampler()
                .sampleAtBlock(context, point[0], context.sampleBlockY(), point[1]);
            if (key != null && accepted.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private AllocationSearchCursor anchorCursorForSector(AllocationSearchCursor cursor, AllocationSearchSector sector, Config.WorldgenSearchConfig worldgen) {
        cursor.setSectorX(sector.centerBlockX());
        cursor.setSectorZ(sector.centerBlockZ());
        cursor.setAnchorAttempt(Math.min(worldgen.getLocateRadiusBlocks(), Math.max(1, sector.halfSizeBlocks())));
        return cursor;
    }

    private boolean hasPendingAnchor(AllocationSearchCursor cursor, Config.WorldgenSearchConfig worldgen) {
        return cursor.getCurrentAnchorX() != null
            && cursor.getCurrentAnchorZ() != null
            && cursor.getLocalCandidateIndex() < worldgen.getMaxCandidateSlotsPerAnchor();
    }

    private LocalAnchorSearchResult continueAnchorSearch(AllocationRequest request,
                                                         ServerLevel level,
                                                         Config.PlayerLandAllocationConfig lac,
                                                         Config.SchedulerConfig sc,
                                                         BiomeOption biomeOption,
                                                         AllocationSearchCursor cursor,
                                                         AllocationSearchSector sector,
                                                         long deadlineNanos) {
        if (cursor.getCurrentAnchorX() == null || cursor.getCurrentAnchorZ() == null) {
            cursor.setLastRejectionReason("rejected_anchor_not_found");
            return LocalAnchorSearchResult.EXHAUSTED;
        }

        int maxPerAnchor = lac.getWorldgenSearch().getMaxCandidateSlotsPerAnchor();
        int remaining = Math.max(0, maxPerAnchor - cursor.getLocalCandidateIndex());
        if (remaining <= 0) {
            cursor.setLastRejectionReason(cursor.getLastRejectionReason() == null ? "rejected_anchor_candidates_exhausted" : cursor.getLastRejectionReason());
            return LocalAnchorSearchResult.EXHAUSTED;
        }

        int maxPerTick = Math.max(1, sc.getMaxCandidateEvaluationsPerTick());
        int limit = Math.max(1, Math.min(remaining, maxPerTick));
        BiomeAnchor anchor = new BiomeAnchor(cursor.getCurrentAnchorX(), cursor.getCurrentAnchorZ(), cursor.getCurrentAnchorBiomeId());
        List<PlotSlotService.PlotSlotCandidate> candidates = localCandidatesNearAnchor(anchor, lac, cursor.getLocalCandidateIndex(), limit);
        WorldgenSearchContext context = biomeSearchService.getContextFactory().getOrCreate(level, configManager.getConfig());
        int processed = 0;
        for (int i = 0; i < candidates.size() && (processed == 0 || System.nanoTime() < deadlineNanos); i++) {
            PlotSlotService.PlotSlotCandidate candidate = candidates.get(i);
            processed++;
            cursor.setLocalCandidateIndex(cursor.getLocalCandidateIndex() + 1);
            cursor.setTotalVirtualCandidatesChecked(cursor.getTotalVirtualCandidatesChecked() + 1);

            if (!slotWithinBand(candidate, lac.getSlotSize(), sector)) {
                cursor.setLastRejectionReason("rejected_outside_active_band");
                AllocationMetrics.increment("rejected_outside_active_band");
                continue;
            }
            if (!slotService.isSlotEligible(candidate.minX, candidate.minZ, lac.getSlotSize())) {
                cursor.setLastRejectionReason("rejected_exclusion_zone");
                AllocationMetrics.increment("rejected_exclusion_zone");
                continue;
            }

            PlotFootprint claimFootprint = buildClaimFootprint(candidate.minX, candidate.minZ, lac);
            BiomeSearchService.MatchResult biomeMatch = biomeSearchService.evaluateBiomeOptionMatching(context, claimFootprint.minX(), claimFootprint.maxX(), claimFootprint.minZ(), claimFootprint.maxZ(), biomeOption);
            cursor.setTotalBiomeSamples(cursor.getTotalBiomeSamples() + 1);
            if (biomeMatch != BiomeSearchService.MatchResult.MATCH) {
                cursor.setLastRejectionReason("rejected_border_mismatch");
                AllocationMetrics.increment("rejected_border_mismatch");
                continue;
            }

            if (reserveSlotForCandidate(request, candidate, lac, sc)) {
                cursorRepository.save(cursor);
                maybeEmitProgress(request, level, cursor);
                LOGGER.info("Slot reserved: slotId={}, request={}, grid=({},{})", request.getPlotSlotId(), request.getId(), candidate.gridX, candidate.gridZ);
                return LocalAnchorSearchResult.RESERVED;
            }

            cursor.setLastRejectionReason("rejected_slot_overlap");
            AllocationMetrics.increment("rejected_slot_overlap");
        }

        if (cursor.getLocalCandidateIndex() < maxPerAnchor) {
            return LocalAnchorSearchResult.CONTINUE;
        }

        cursor.setLastRejectionReason(cursor.getLastRejectionReason() == null ? "rejected_anchor_candidates_exhausted" : cursor.getLastRejectionReason());
        return LocalAnchorSearchResult.EXHAUSTED;
    }

    private boolean reserveSlotForCandidate(AllocationRequest request,
                                            PlotSlotService.PlotSlotCandidate candidate,
                                            Config.PlayerLandAllocationConfig lac,
                                            Config.SchedulerConfig sc) {
        String slotId = lac.getTargetDimension() + ":" + candidate.gridX + ":" + candidate.gridZ;
        PlotSlot existing = slotRepository.getByGrid(lac.getTargetDimension(), candidate.gridX, candidate.gridZ);
        if (existing != null && existing.getState().isOccupied()) {
            return false;
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
        request.setPlotSlotId(slotId);
        request.transitionTo(AllocationRequestState.VIRTUAL_VALIDATED);
        requestRepository.save(request);
        return true;
    }

    private List<PlotSlotService.PlotSlotCandidate> localCandidatesNearAnchor(BiomeAnchor anchor, Config.PlayerLandAllocationConfig lac, int offset, int limit) {
        int slotSize = lac.getSlotSize();
        int anchorGridX = Math.floorDiv(anchor.blockX(), slotSize);
        int anchorGridZ = Math.floorDiv(anchor.blockZ(), slotSize);
        int[][] deltas = new int[][]{
            {0, 0}, {0, -1}, {0, 1}, {1, 0}, {-1, 0},
            {1, -1}, {1, 1}, {-1, -1}, {-1, 1},
            {0, -2}, {0, 2}, {2, 0}, {-2, 0},
            {2, -1}, {2, 1}, {-2, -1}, {-2, 1},
            {1, -2}, {1, 2}, {-1, -2}, {-1, 2},
            {2, -2}, {2, 2}, {-2, -2}, {-2, 2}
        };
        List<PlotSlotService.PlotSlotCandidate> list = new ArrayList<>();
        for (int i = offset; i < deltas.length && list.size() < limit; i++) {
            int[] delta = deltas[i];
            int gridX = anchorGridX + delta[0];
            int gridZ = anchorGridZ + delta[1];
            list.add(new PlotSlotService.PlotSlotCandidate(gridX, gridZ, gridX * slotSize, gridZ * slotSize));
        }
        return list;
    }

    private boolean slotWithinBand(PlotSlotService.PlotSlotCandidate candidate, int slotSize, AllocationSearchSector sector) {
        if (sector == null) {
            return false;
        }
        PlotFootprint footprint = new PlotFootprint(candidate.minX, candidate.minX + slotSize - 1, candidate.minZ, candidate.minZ + slotSize - 1);
        int radius = Math.max(Math.abs(footprint.centerX()), Math.abs(footprint.centerZ()));
        return radius >= sector.minRadiusBlocks() && radius <= sector.maxRadiusBlocks();
    }

    private long deterministicSectorSeedFromValues(String biomeKey, String bandId, int sectorIndex, int sectorX, int sectorZ) {
        long seed = 17L;
        seed = 31L * seed + (biomeKey == null ? 0 : biomeKey.hashCode());
        seed = 31L * seed + (bandId == null ? 0 : bandId.hashCode());
        seed = 31L * seed + sectorIndex;
        seed = 31L * seed + sectorX;
        seed = 31L * seed + sectorZ;
        return seed;
    }

    private int deterministicJitter(long seed, int a, int b, int bound) {
        if (bound <= 0) {
            return 0;
        }
        java.util.Random random = new java.util.Random(seed ^ (31L * a) ^ (17L * b));
        return random.nextInt(bound * 2 + 1) - bound;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void markSectorRejected(AllocationSearchCursor cursor, AllocationSearchSector sector, String reason) {
        cursor.setCurrentSectorIndex(cursor.getCurrentSectorIndex() + 1);
        cursor.setTotalSectorsChecked(cursor.getTotalSectorsChecked() + 1);
        cursor.setSectorsDiscarded(cursor.getSectorsDiscarded() + 1);
        cursor.setAnchorAttempt(0);
        cursor.setLocalCandidateIndex(0);
        cursor.setCurrentAnchorX(null);
        cursor.setCurrentAnchorZ(null);
        cursor.setCurrentAnchorBiomeId(null);
        cursor.setLastRejectionReason(reason);
        cursor.setLastProgressAt(System.currentTimeMillis());
        AllocationMetrics.increment(reason);
        cursorRepository.save(cursor);
    }

    private boolean tryFallbackSpiral(AllocationRequest request,
                                      ServerLevel level,
                                      Config.PlayerLandAllocationConfig lac,
                                      Config.SchedulerConfig sc,
                                      BiomeOption option,
                                      AllocationSearchCursor cursor) {
        Config.WorldgenSearchConfig worldgen = lac.getWorldgenSearch();
        if (!worldgen.isFallbackSpiralEnabled()) {
            return false;
        }
        cursor.setFallbackMode("FALLBACK_SPIRAL");
        PlotSlotService.PlotSlotIterator iterator = slotService.iteratorFor(request.getOwnerUuid());
        int budget = Math.max(1, worldgen.getFallbackSpiralMaxCandidates());
        while (budget-- > 0) {
            Optional<PlotSlotService.PlotSlotCandidate> opt = iterator.next();
            if (opt.isEmpty()) {
                return false;
            }
            PlotSlotService.PlotSlotCandidate candidate = opt.get();
            PlotFootprint footprint = buildClaimFootprint(candidate.minX, candidate.minZ, lac);
            if (biomeSearchService.evaluateBiomeOptionMatching(level, footprint.minX(), footprint.maxX(), footprint.minZ(), footprint.maxZ(), option) == BiomeSearchService.MatchResult.MATCH
                && reserveSlotForCandidate(request, candidate, lac, sc)) {
                return true;
            }
        }
        return false;
    }

    public AllocationStatusSnapshot getAllocationStatus(UUID ownerUuid) {
        AllocationRequest request = getActiveRequest(ownerUuid);
        return request == null ? null : inspectAllocation(request.getId());
    }

    public AllocationStatusSnapshot inspectAllocation(String requestId) {
        AllocationRequest request = requestRepository.get(requestId);
        if (request == null) {
            return null;
        }
        AllocationSearchCursor cursor = cursorRepository.get(requestId);
        Optional<BiomeOption> biome = biomeOptionRegistry.lookup(request.getRequestedBiomeOption());
        long now = System.currentTimeMillis();
        Config.SchedulerConfig scheduler = configManager.getConfig().getPlayerLandAllocation().getScheduler();
        long elapsed = now - request.getCreatedAt();
        long timeoutRemaining = Math.max(0L, scheduler.getRequestTimeoutSeconds() * 1000L - elapsed);
        int totalKnownSectors = Math.max(1, enabledBands(configManager.getConfig().getPlayerLandAllocation().getWorldgenSearch()).size()
            * Math.max(1, configManager.getConfig().getPlayerLandAllocation().getWorldgenSearch().getMaxSectorsPerRequest()));
        return new AllocationStatusSnapshot(
            request,
            cursor,
            biome.map(BiomeOption::getDisplayName).orElse(request.getRequestedBiomeOption()),
            elapsed,
            timeoutRemaining,
            totalKnownSectors,
            describeState(request)
        );
    }

    private String describeState(AllocationRequest request) {
        return switch (request.getState()) {
            case VIRTUAL_SEARCHING -> "Procurando bioma virtualmente";
            case VIRTUAL_VALIDATED -> "Area validada virtualmente";
            case SLOT_RESERVED -> "Slot reservado";
            case PREPARING_CHUNKS, WAITING_FOR_CHUNKS -> "Preparando sua regiao";
            case VALIDATING_LOADED_WORLD -> "Validando terreno carregado";
            case REGION_CREATING -> "Criando regiao";
            default -> request.getState().name();
        };
    }

    private void maybeEmitProgress(AllocationRequest request, ServerLevel level, AllocationSearchCursor cursor) {
        long now = System.currentTimeMillis();
        int intervalSeconds = Math.max(1, configManager.getConfig().getPlayerLandAllocation().getNotifications().getAllocationProgressIntervalSeconds());
        long intervalMillis = intervalSeconds * 1000L;
        if (configManager.getConfig().getPlayerLandAllocation().getNotifications().isAllocationProgressEnabled()) {
            Long lastNotify = lastProgressNotifications.get(request.getId());
            if (lastNotify == null || now - lastNotify >= intervalMillis) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.getOwnerUuid());
                if (player != null) {
                    player.sendSystemMessage(Component.literal("§6[Terrenos] " + describeProgressLine(cursor)));
                }
                lastProgressNotifications.put(request.getId(), now);
            }
        }

        Long lastLog = lastProgressLogs.get(request.getId());
        if (lastLog == null || now - lastLog >= 10_000L) {
            LOGGER.info("[BigBangRegions] Allocation progress: request={} biome={} state={} elapsed={}s sectors={} anchors={} candidates={} lastRejection={}",
                request.getId().substring(0, 8),
                request.getRequestedBiomeOption(),
                request.getState(),
                (now - request.getCreatedAt()) / 1000L,
                cursor.getTotalSectorsChecked(),
                cursor.getAnchorsFound(),
                cursor.getTotalVirtualCandidatesChecked(),
                cursor.getLastRejectionReason());
            lastProgressLogs.put(request.getId(), now);
        }
    }

    private String describeProgressLine(AllocationSearchCursor cursor) {
        if (cursor.getCurrentAnchorBiomeId() != null) {
            return "Encontramos uma area promissora. Validando terreno...";
        }
        if (cursor.getFallbackMode() != null) {
            return "Busca em fallback limitado. Candidatos testados: " + cursor.getTotalVirtualCandidatesChecked();
        }
        return "Procurando uma area do bioma solicitado... setores descartados: " + cursor.getSectorsDiscarded();
    }

    private boolean isTimedOut(AllocationRequest request, Config.SchedulerConfig sc) {
        return System.currentTimeMillis() - request.getCreatedAt() > sc.getRequestTimeoutSeconds() * 1000L;
    }

    private void failRequest(AllocationRequest request, AllocationRequestState target, String reason, ServerLevel level) {
        request.forceTransitionTo(target);
        request.setFailureReason(reason);
        requestRepository.save(request);
        cleanupRequestResources(request, PreparationCancelReason.FAILED, false);
        if (level != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.getOwnerUuid());
            if (player != null) {
                if (target == AllocationRequestState.FAILED_NO_TERRAIN) {
                    player.sendSystemMessage(Component.literal("§cNão encontramos uma área grande o suficiente de " + request.getRequestedBiomeOption() + " dentro do limite atual."));
                    player.sendSystemMessage(Component.literal("§7Nenhuma região foi criada e nenhum recurso foi consumido. Tente novamente ou escolha outro bioma."));
                } else {
                    player.sendSystemMessage(Component.literal("§cA criação do terreno falhou: " + reason));
                    player.sendSystemMessage(Component.literal("§7Nenhuma região foi criada. Você pode tentar novamente."));
                }
            }
        }
    }

    private void resumeSearchAfterCandidateFailure(AllocationRequest request,
                                                   Config.PlayerLandAllocationConfig lac,
                                                   ServerLevel level,
                                                   String rejectionReason,
                                                   String diagnostic,
                                                   boolean slotAlreadyInvalidated) {
        if (!slotAlreadyInvalidated) {
            tryReleaseSlot(request);
        }

        AllocationMetrics.increment(rejectionReason);
        AllocationSearchCursor cursor = loadOrCreateCursor(request, lac);
        cursor.setLastRejectionReason(rejectionReason);
        cursorRepository.save(cursor);

        cleanupRequestResources(request, PreparationCancelReason.FAILED, false);
        preparationRepository.delete(request.getId());

        request.setPlotSlotId(null);
        request.setFailureReason(null);
        request.transitionTo(AllocationRequestState.VIRTUAL_SEARCHING);
        requestRepository.save(request);

        LOGGER.info("[BigBangRegions] Candidate rejected after reservation: request={} reason={} detail={}. Resuming virtual search.",
            request.getId(), rejectionReason, diagnostic);

        if (level != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.getOwnerUuid());
            if (player != null) {
                player.sendSystemMessage(Component.literal("§6[Terrenos] Encontramos uma área, mas ela não passou na validação final. Continuando a busca..."));
            }
        }
    }

    private int recoverPausedRequest(AllocationRequest request, ServerLevel level, Config.PlayerLandAllocationConfig lac) {
        String reason = request.getFailureReason();
        if (reason != null && (
            reason.contains("Metadados de preparacao nao encontrados")
                || reason.contains("Recuperacao: preparacao fisica interrompida")
                || reason.contains("Recuperacao: criacao fisica interrompida")
        )) {
            LOGGER.info("[BigBangRegions] Auto-recovering paused allocation request={} by resuming virtual search.", request.getId());
            resumeSearchAfterCandidateFailure(
                request,
                lac,
                level,
                "rejected_physical_validation",
                reason,
                false
            );
            return 1;
        }
        return 0;
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
        validatedWorlds.remove(request.getId());
        completedPreparations.remove(request.getId());
        lastProgressNotifications.remove(request.getId());
        lastProgressLogs.remove(request.getId());
        sectorSequenceCache.entrySet().removeIf(entry -> entry.getKey().startsWith(request.getId() + ":"));

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
        if (deletePreparationRecord) {
            cursorRepository.delete(request.getId());
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
