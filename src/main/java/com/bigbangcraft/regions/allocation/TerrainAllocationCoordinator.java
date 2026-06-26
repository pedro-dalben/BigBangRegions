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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TerrainAllocationCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-TerrainAllocationCoordinator");

    private final ConfigManager configManager;
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
            AllocationRequestState.PENDING, source, ownerUuid, null, null, 0, now, now, null, null
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
        request.transitionTo(AllocationRequestState.CANCELLED);
        requestRepository.save(request);
        if (request.getRegionId() != null) {
            PlotSlot slot = slotRepository.getByRegionId(request.getRegionId());
            if (slot != null && slot.getState() == PlotSlotState.RESERVED) {
                slot.release();
                slotRepository.save(slot);
            }
        }
        LOGGER.info("Allocation request cancelled: id={}, owner={}", request.getId(), ownerUuid);
    }

    public int processNextRequest(ServerLevel level) {
        Config config = configManager.getConfig();
        Config.SchedulerConfig sc = config.getPlayerLandAllocation().getScheduler();
        Config.PlayerLandAllocationConfig lac = config.getPlayerLandAllocation();

        List<AllocationRequest> active = requestRepository.getActiveRequests();
        if (active.isEmpty()) return 0;
        AllocationRequest request = active.get(0);
        long now = System.currentTimeMillis();

        if (request.getState() == AllocationRequestState.PENDING) {
            if (isTimedOut(request, sc)) {
                failRequest(request, "Tempo limite excedido");
                return 1;
            }
            request.transitionTo(AllocationRequestState.SEARCHING);
            requestRepository.save(request);
            return 1;
        }

        if (request.getState() == AllocationRequestState.SEARCHING) {
            if (isTimedOut(request, sc)) {
                failRequest(request, "Tempo limite excedido durante busca");
                return 1;
            }
            Optional<BiomeOption> biomeOpt = biomeOptionRegistry.lookup(request.getRequestedBiomeOption());
            if (biomeOpt.isEmpty()) {
                failRequest(request, "Opcao de bioma nao encontrada: " + request.getRequestedBiomeOption());
                return 1;
            }
            int maxCandidates = sc.getMaxCandidateEvaluationsPerTick();
            List<PlotSlotService.PlotSlotCandidate> candidates = slotService.getCandidates(request.getOwnerUuid(), maxCandidates);
            for (PlotSlotService.PlotSlotCandidate candidate : candidates) {
                boolean biomeMatch = biomeSearchService.isBiomeOptionMatching(
                    level, candidate.minX, candidate.minX + lac.getSlotSize() - 1,
                    candidate.minZ, candidate.minZ + lac.getSlotSize() - 1,
                    biomeOpt.get()
                );
                if (!biomeMatch) continue;
                String slotId = lac.getTargetDimension() + ":" + candidate.gridX + ":" + candidate.gridZ;
                PlotSlot existing = slotRepository.getByGrid(lac.getTargetDimension(), candidate.gridX, candidate.gridZ);
                if (existing != null && (existing.getState() == PlotSlotState.RESERVED || existing.getState() == PlotSlotState.ALLOCATED)) {
                    continue;
                }
                PlotSlot slot;
                if (existing != null && existing.getState() == PlotSlotState.RELEASED) {
                    slot = existing;
                } else {
                    long slotNow = System.currentTimeMillis();
                    slot = new PlotSlot(slotId, lac.getTargetDimension(), candidate.gridX, candidate.gridZ,
                        candidate.minX, candidate.minZ, lac.getSlotSize(),
                        PlotSlotState.RESERVED, request.getOwnerUuid(), null, request.getRequestedBiomeOption(),
                        slotNow, slotNow + sc.getReservationLeaseSeconds() * 1000L, null, slotNow, slotNow);
                }
                slot.reserve(request.getOwnerUuid(), request.getRequestedBiomeOption(), sc.getReservationLeaseSeconds() * 1000L);
                slotRepository.save(slot);
                request.transitionTo(AllocationRequestState.SLOT_RESERVED);
                request.setRegionId(slotId);
                requestRepository.save(request);
                LOGGER.info("Slot reserved: slotId={}, request={}, grid=({},{})", slotId, request.getId(), candidate.gridX, candidate.gridZ);
                return 1;
            }
            return 0;
        }

        if (request.getState() == AllocationRequestState.SLOT_RESERVED) {
            if (isTimedOut(request, sc)) {
                releaseSlot(request);
                failRequest(request, "Tempo limite excedido durante reserva");
                return 1;
            }
            request.transitionTo(AllocationRequestState.PREPARING);
            requestRepository.save(request);
            return 1;
        }

        if (request.getState() == AllocationRequestState.PREPARING) {
            if (isTimedOut(request, sc)) {
                releaseSlot(request);
                failRequest(request, "Tempo limite excedido durante preparacao");
                return 1;
            }
            PlotSlot slot = slotRepository.get(request.getRegionId());
            if (slot == null) {
                releaseSlot(request);
                failRequest(request, "Slot nao encontrado");
                return 1;
            }
            String regionId = "player_" + request.getOwnerUuid().toString().substring(0, 8) + "_" + System.currentTimeMillis();
            RegionBounds bounds = new RegionBounds(lac.getTargetDimension(),
                slot.getMinX(), -64, slot.getMinZ(),
                slot.getMinX() + lac.getInitialClaimSize() - 1, 320, slot.getMinZ() + lac.getInitialClaimSize() - 1);
            long regionNow = System.currentTimeMillis();
            Region region = new Region(regionId, "Player Region", RegionType.PLAYER_REGION,
                bounds, 100, request.getOwnerUuid(), request.getOwnerUuid(), regionNow, regionNow, "ACTIVE");
            regionRepository.save(region);
            regionRepository.saveMembers(regionId, Collections.emptyMap());
            regionCache.add(region);
            membershipCache.loadFromRegion(region);
            slot.allocate(regionId);
            slotRepository.save(slot);
            reportSlotDependencies(slot);
            Optional<BlockPos> spawnPos = SafeSpawnFinder.findSafeSpawn(level, slot.getMinX(),
                slot.getMinX() + lac.getInitialClaimSize() - 1,
                slot.getMinZ(), slot.getMinZ() + lac.getInitialClaimSize() - 1);
            BlockPos homePos = spawnPos.orElse(new BlockPos(slot.getMinX() + lac.getInitialClaimSize() / 2, 64, slot.getMinZ() + lac.getInitialClaimSize() / 2));
            long homeNow = System.currentTimeMillis();
            PlayerRegionHome home = new PlayerRegionHome(regionId, lac.getTargetDimension(),
                homePos.getX() + 0.5, homePos.getY(), homePos.getZ() + 0.5, 0.0f, 0.0f, homeNow, homeNow);
            homeRepository.save(home);
            request.setRegionId(regionId);
            request.transitionTo(AllocationRequestState.COMPLETED);
            requestRepository.save(request);
            LOGGER.info("Allocation completed: request={}, regionId={}, home=({},{},{})",
                request.getId(), regionId, homePos.getX(), homePos.getY(), homePos.getZ());
            return 1;
        }

        return 0;
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
            .filter(r -> r.getType() == RegionType.PLAYER_REGION && ownerUuid.equals(r.getOwnerUuid()))
            .findFirst();
        if (playerRegion.isEmpty()) {
            throw new IllegalStateException("Voce nao possui uma regiao de jogador");
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
            .filter(r -> r.getType() == RegionType.PLAYER_REGION && ownerUuid.equals(r.getOwnerUuid()))
            .findFirst();
        if (playerRegion.isEmpty()) {
            throw new IllegalStateException("Voce nao possui uma regiao de jogador");
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

    public int resizeClaim(ServerPlayer player, int newSize) {
        UUID ownerUuid = player.getUUID();
        Optional<Region> playerRegion = regionCache.getAll().stream()
            .filter(r -> r.getType() == RegionType.PLAYER_REGION && ownerUuid.equals(r.getOwnerUuid()))
            .findFirst();
        if (playerRegion.isEmpty()) {
            throw new IllegalStateException("Voce nao possui uma regiao de jogador");
        }
        Region region = playerRegion.get();
        Config.PlayerLandAllocationConfig lac = configManager.getConfig().getPlayerLandAllocation();
        int maxSize = lac.getFutureMaximumClaimSize();
        int minSize = lac.getSlotInternalMargin();
        if (newSize < minSize) {
            throw new IllegalStateException("Tamanho minimo: " + minSize);
        }
        if (newSize > maxSize) {
            throw new IllegalStateException("Tamanho maximo: " + maxSize);
        }
        RegionBounds oldBounds = region.getBounds();
        int oldSize = oldBounds.getMaxX() - oldBounds.getMinX() + 1;
        if (newSize == oldSize) {
            throw new IllegalStateException("Sua regiao ja possui " + newSize + " blocos");
        }

        int newMaxX = oldBounds.getMinX() + newSize - 1;
        int newMaxZ = oldBounds.getMinZ() + newSize - 1;

        PlotSlot slot = slotRepository.getByRegionId(region.getId());
        if (slot != null) {
            int slotMaxX = slot.getMinX() + lac.getSlotSize() - 1;
            int slotMaxZ = slot.getMinZ() + lac.getSlotSize() - 1;
            if (newMaxX > slotMaxX || newMaxZ > slotMaxZ) {
                throw new IllegalStateException("Nao e possivel expandir alem do slot (max " + lac.getSlotSize() + ")");
            }
        }

        RegionBounds newBounds = new RegionBounds(
            oldBounds.getDimension(),
            oldBounds.getMinX(), oldBounds.getMinY(), oldBounds.getMinZ(),
            newMaxX, oldBounds.getMaxY(), newMaxZ
        );

        for (Region other : regionCache.getAll()) {
            if (other.getId().equals(region.getId())) continue;
            if (other.getBounds().intersects(newBounds)) {
                throw new IllegalStateException("Nova area se sobrepoe a regiao '" + other.getId() + "'");
            }
        }

        region.setBounds(newBounds);
        regionRepository.save(region);
        regionCache.add(region);
        LOGGER.info("Region {} resized from {} to {}", region.getId(), oldSize, newSize);
        return newSize;
    }

    public void retireSlot(String regionId) {
        PlotSlot slot = slotRepository.getByRegionId(regionId);
        if (slot != null && slot.getState() == PlotSlotState.ALLOCATED) {
            slot.retire();
            slotRepository.save(slot);
            LOGGER.info("Slot {} retired for region {}", slot.getId(), regionId);
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

    public Collection<BiomeOption> getBiomeOptions() {
        return biomeOptionRegistry.getAll();
    }

    private boolean isTimedOut(AllocationRequest request, Config.SchedulerConfig sc) {
        return System.currentTimeMillis() - request.getUpdatedAt() > sc.getRequestTimeoutSeconds() * 1000L;
    }

    private void failRequest(AllocationRequest request, String reason) {
        request.forceTransitionTo(AllocationRequestState.FAILED);
        request.setFailureReason(reason);
        requestRepository.save(request);
    }

    private void releaseSlot(AllocationRequest request) {
        if (request.getRegionId() != null) {
            PlotSlot slot = slotRepository.get(request.getRegionId());
            if (slot != null && slot.getState() == PlotSlotState.RESERVED) {
                slot.release();
                slotRepository.save(slot);
            }
        }
    }

    private void reportSlotDependencies(PlotSlot slot) {
        LOGGER.debug("Slot {} allocated for region {}. Forwarding to dependency resolver.", slot.getId(), slot.getRegionId());
    }
}
