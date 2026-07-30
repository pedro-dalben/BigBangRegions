package com.bigbangcraft.regions.expansion;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.allocation.PlotSlot;
import com.bigbangcraft.regions.payment.api.*;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.storage.DatabaseManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RegionExpansionCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-RegionExpansionCoordinator");

    private static final String SOURCE = "bigbangregions";
    private static final String PURPOSE = "player_region_expansion";

    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final RegionExpansionOperationRepository expansionRepository;
    private final RegionRepository regionRepository;
    private final PlotSlotRepository slotRepository;
    private final RegionCache regionCache;
    private final RegionMembershipCache membershipCache;
    private final LandPaymentGateway paymentGateway;
    private final RegionExpansionPricingPolicy pricingPolicy;
    private final Set<String> visualReconciliationsInFlight = ConcurrentHashMap.newKeySet();
    // ponytail: one serialized expansion worker keeps SQLite/cache ordering safe; split per operation only if measured throughput needs it.
    private final ExecutorService operationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "BigBangRegions-ExpansionExecutor");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean workInFlight = new AtomicBoolean();
    private final AtomicBoolean visualWorkInFlight = new AtomicBoolean();
    private final AtomicLong nextWorkAt = new AtomicLong();
    private final Set<String> paymentInFlight = ConcurrentHashMap.newKeySet();
    private final Map<String, CompletableFuture<RegionExpansionOperation>> startWaiters = new ConcurrentHashMap<>();
    private volatile MinecraftServer server;

    public RegionExpansionCoordinator(ConfigManager configManager,
                                       DatabaseManager databaseManager,
                                       RegionExpansionOperationRepository expansionRepository,
                                       RegionRepository regionRepository,
                                       PlotSlotRepository slotRepository,
                                       RegionCache regionCache,
                                       RegionMembershipCache membershipCache,
                                       LandPaymentGateway paymentGateway) {
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.expansionRepository = expansionRepository;
        this.regionRepository = regionRepository;
        this.slotRepository = slotRepository;
        this.regionCache = regionCache;
        this.membershipCache = membershipCache;
        this.paymentGateway = paymentGateway;
        this.pricingPolicy = new RegionExpansionPricingPolicy(configManager.getConfig().getRegionExpansion());
    }

    public RegionExpansionOperation beginExpansion(ServerPlayer player, int targetSize) {
        UUID ownerUuid = player.getUUID();
        Config.RegionExpansionConfig ec = configManager.getConfig().getRegionExpansion();

        if (!ec.isEnabled()) {
            throw new IllegalStateException("Expansao de regioes nao esta habilitada neste servidor.");
        }

        if (ec.isPaymentRequired() && !paymentGateway.isAvailable()) {
            throw new IllegalStateException("Sistema de pagamento indisponivel. Tente novamente mais tarde.");
        }

        if (ec.getPricePerAddedBlock() == 0) {
            throw new IllegalStateException("A politica de precos para expansao ainda nao foi configurada. Contate um administrador.");
        }

        Optional<Region> playerRegion = regionCache.getAll().stream()
            .filter(r -> r.getType() == RegionType.PLAYER_REGION
                && ownerUuid.equals(r.getOwnerUuid())
                && "ACTIVE".equals(r.getStatus()))
            .findFirst();
        if (playerRegion.isEmpty()) {
            throw new IllegalStateException("Voce nao possui uma regiao de jogador ativa para expandir.");
        }

        Region region = playerRegion.get();
        RegionBounds bounds = region.getBounds();
        int currentSize = bounds.getMaxX() - bounds.getMinX() + 1;

        RegionExpansionOperation existing = expansionRepository.getActiveByRegion(region.getId());
        if (existing != null) {
            throw new IllegalStateException("Ja existe uma operacao de expansao ativa para esta regiao (ID: " + existing.getOperationId() + ")");
        }

        Config.PlayerLandAllocationConfig lac = configManager.getConfig().getPlayerLandAllocation();

        com.bigbangcraft.regions.allocation.PlotSlot slot = slotRepository.getByRegionId(region.getId());
        if (slot == null) {
            throw new IllegalStateException("Plot slot nao encontrado para esta regiao.");
        }

        RegionExpansionQuote quote = pricingPolicy.calculateQuote(currentSize, targetSize);
        if (!quote.isAccepted()) {
            throw new IllegalArgumentException(quote.getRejectionReason());
        }

        int slotMinX = slot.getMinX();
        int slotMinZ = slot.getMinZ();
        int slotSize = lac.getSlotSize();
        int claimOffset = (slotSize - targetSize) / 2;
        int targetMinX = slotMinX + claimOffset;
        int targetMinZ = slotMinZ + claimOffset;
        int targetMaxX = targetMinX + targetSize - 1;
        int targetMaxZ = targetMinZ + targetSize - 1;

        if (targetMinX < slotMinX || targetMinZ < slotMinZ
            || targetMaxX > slotMinX + slotSize - 1 || targetMaxZ > slotMinZ + slotSize - 1) {
            throw new IllegalStateException("Expansao ultrapassa os limites do Plot Slot.");
        }

        String operationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        RegionExpansionOperation operation = new RegionExpansionOperation(
            operationId, region.getId(), ownerUuid,
            slot.getId(),
            bounds.getDimension(),
            currentSize, targetSize,
            bounds.getMinX(), bounds.getMinZ(), bounds.getMaxX(), bounds.getMaxZ(),
            targetMinX, targetMinZ, targetMaxX, targetMaxZ,
            quote.getPriceGems(), quote.getPolicyVersion(),
            RegionExpansionState.REQUESTED,
            now
        );
        expansionRepository.save(operation);

        operation.transitionTo(RegionExpansionState.QUOTED);
        operation.setFailureCode(null);
        operation.setFailureDetail(null);
        expansionRepository.save(operation);

        LOGGER.info("Expansion operation created: op={}, region={}, {}x{}→{}x{} ({} gems)",
            operationId, region.getId(), currentSize, currentSize, targetSize, targetSize, quote.getPriceGems());
        return operation;
    }

    public RegionExpansionOperation beginExpansion(ServerPlayer player, ExpansionDirection direction, int increment) {
        if (increment != 1 && increment != 5 && increment != 10) {
            throw new IllegalArgumentException("Incremento inválido. Escolha 1, 5 ou 10 blocos.");
        }
        UUID ownerUuid = player.getUUID();
        Config.RegionExpansionConfig ec = configManager.getConfig().getRegionExpansion();
        if (!ec.isEnabled()) throw new IllegalStateException("Expansão de regiões desabilitada.");
        if (ec.getPricePerAddedBlock() == 0) {
            throw new IllegalStateException("A política de preços para expansão ainda não foi configurada.");
        }
        if (ec.isPaymentRequired() && !paymentGateway.isAvailable()) {
            throw new IllegalStateException("Sistema de pagamento indisponível.");
        }
        Region region = regionCache.getAll().stream()
            .filter(r -> r.getType() == RegionType.PLAYER_REGION && ownerUuid.equals(r.getOwnerUuid()) && "ACTIVE".equals(r.getStatus()))
            .findFirst().orElseThrow(() -> new IllegalStateException("Você não possui uma região ativa para expandir."));
        if (expansionRepository.getActiveByRegion(region.getId()) != null) {
            throw new IllegalStateException("Já existe uma expansão ativa para esta região.");
        }
        RegionBounds old = region.getBounds();
        RegionBounds target = directionalBounds(old, direction, increment);
        Config.PlayerLandAllocationConfig lac = configManager.getConfig().getPlayerLandAllocation();
        int max = lac.getFutureMaximumClaimSize();
        if (target.getMaxX() - target.getMinX() + 1 > max || target.getMaxZ() - target.getMinZ() + 1 > max) {
            throw new IllegalArgumentException("A expansão ultrapassa o tamanho máximo permitido.");
        }
        PlotSlot slot = slotRepository.getByRegionId(region.getId());
        if (slot == null || target.getMinX() < slot.getMinX() || target.getMinZ() < slot.getMinZ()
            || target.getMaxX() > slot.getMinX() + lac.getSlotSize() - 1
            || target.getMaxZ() > slot.getMinZ() + lac.getSlotSize() - 1) {
            throw new IllegalArgumentException("A expansão ultrapassa os limites do Plot Slot.");
        }
        RegionExpansionQuote quote = pricingPolicy.calculateQuote(old, target);
        if (!quote.isAccepted()) throw new IllegalArgumentException(quote.getRejectionReason());
        long now = System.currentTimeMillis();
        RegionExpansionOperation operation = new RegionExpansionOperation(
            UUID.randomUUID().toString(), region.getId(), ownerUuid, slot.getId(), old.getDimension(),
            old.getMaxX() - old.getMinX() + 1, Math.max(target.getMaxX() - target.getMinX() + 1, target.getMaxZ() - target.getMinZ() + 1),
            old.getMinX(), old.getMinZ(), old.getMaxX(), old.getMaxZ(),
            target.getMinX(), target.getMinZ(), target.getMaxX(), target.getMaxZ(),
            quote.getPriceGems(), quote.getPolicyVersion(), RegionExpansionState.REQUESTED, now);
        expansionRepository.save(operation);
        operation.transitionTo(RegionExpansionState.QUOTED);
        expansionRepository.save(operation);
        return operation;
    }

    public CompletionStage<RegionExpansionOperation> beginExpansionAsync(ServerPlayer player, int targetSize) {
        return startPaymentAndWait(beginExpansion(player, targetSize));
    }

    public CompletionStage<RegionExpansionOperation> beginExpansionAsync(ServerPlayer player,
                                                                          ExpansionDirection direction,
                                                                          int increment) {
        return startPaymentAndWait(beginExpansion(player, direction, increment));
    }

    private CompletionStage<RegionExpansionOperation> startPaymentAndWait(RegionExpansionOperation operation) {
        CompletableFuture<RegionExpansionOperation> result = new CompletableFuture<>();
        startWaiters.put(operation.getOperationId(), result);
        try {
            startPaymentReserve(operation, configManager.getConfig().getRegionExpansion());
        } catch (Throwable error) {
            startWaiters.remove(operation.getOperationId());
            result.completeExceptionally(error);
        }
        return result;
    }

    public static RegionBounds directionalBounds(RegionBounds old, ExpansionDirection direction, int increment) {
        int minX = old.getMinX(), maxX = old.getMaxX(), minZ = old.getMinZ(), maxZ = old.getMaxZ();
        if (direction == ExpansionDirection.WEST || direction == ExpansionDirection.ALL) minX -= increment;
        if (direction == ExpansionDirection.EAST || direction == ExpansionDirection.ALL) maxX += increment;
        if (direction == ExpansionDirection.NORTH || direction == ExpansionDirection.ALL) minZ -= increment;
        if (direction == ExpansionDirection.SOUTH || direction == ExpansionDirection.ALL) maxZ += increment;
        return new RegionBounds(old.getDimension(), minX, old.getMinY(), minZ, maxX, old.getMaxY(), maxZ);
    }

    public RegionExpansionOperation getActiveExpansion(UUID ownerUuid) {
        Optional<Region> playerRegion = regionCache.getAll().stream()
            .filter(r -> r.getType() == RegionType.PLAYER_REGION
                && ownerUuid.equals(r.getOwnerUuid())
                && "ACTIVE".equals(r.getStatus()))
            .findFirst();
        return playerRegion.map(region -> expansionRepository.getActiveByRegion(region.getId())).orElse(null);
    }

    public void cancelExpansion(ServerPlayer player) {
        UUID ownerUuid = player.getUUID();
        RegionExpansionOperation op = getActiveExpansion(ownerUuid);
        if (op == null) {
            throw new IllegalStateException("Voce nao possui uma operacao de expansao ativa.");
        }

        if (!op.getState().isPreResize()) {
            throw new IllegalStateException("A expansao ja ultrapassou o ponto irreversivel e nao pode ser cancelada.");
        }

        beginReleaseBeforeResize(op, "CANCELLED_BY_PLAYER", "Cancelled by player");
    }

    public int processNextExpansion() {
        return processNextExpansion(null);
    }

    public int processNextExpansion(MinecraftServer server) {
        if (server != null) this.server = server;
        long now = System.currentTimeMillis();
        if (now < nextWorkAt.get() || !workInFlight.compareAndSet(false, true)) return 0;
        operationExecutor.execute(() -> {
            try {
                List<RegionExpansionOperation> active = expansionRepository.getActiveOperations();
                Config.RegionExpansionConfig ec = configManager.getConfig().getRegionExpansion();
                for (RegionExpansionOperation op : active) {
                    if (op.getNextRetryAt() != null && op.getNextRetryAt() > System.currentTimeMillis()) continue;
                    processOperation(op, ec);
                    break;
                }
            } catch (Throwable error) {
                LOGGER.error("Expansion worker failed; durable operation state was preserved where possible.", error);
            } finally {
                nextWorkAt.set(System.currentTimeMillis() + 250L);
                workInFlight.set(false);
            }
        });
        return 1;
    }

    public void shutdown() {
        operationExecutor.shutdownNow();
    }

    public void reconcileExpansionVisuals(MinecraftServer server) {
        if (!visualWorkInFlight.compareAndSet(false, true)) return;
        operationExecutor.execute(() -> {
            List<RegionExpansionOperation> pending = new ArrayList<>();
            try {
                long now = System.currentTimeMillis();
                pending.addAll(expansionRepository.getActiveOperations().stream()
                    .filter(op -> op.getState() == RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING)
                    .filter(op -> op.getBorderAppliedAt() == null)
                    .filter(op -> op.getNextRetryAt() == null || op.getNextRetryAt() <= now)
                    .filter(op -> visualReconciliationsInFlight.add(op.getOperationId()))
                    .toList());
                if (!pending.isEmpty()) server.execute(() -> applyExpansionVisuals(server, pending));
            } catch (Throwable error) {
                pending.forEach(op -> visualReconciliationsInFlight.remove(op.getOperationId()));
                LOGGER.warn("Failed to load expansion visuals off the server thread", error);
            } finally {
                visualWorkInFlight.set(false);
            }
        });
    }

    private void applyExpansionVisuals(MinecraftServer server, List<RegionExpansionOperation> pending) {
        for (RegionExpansionOperation op : pending) {
            try {
                ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(op.getDimensionKey())));
                Region region = regionCache.get(op.getRegionId());
                if (level == null || region == null) {
                    scheduleVisualRetry(op, "Mundo ou regiao indisponivel para aplicar a borda.");
                    continue;
                }
                RegionBounds current = region.getBounds();
                if (current.getMinX() != op.getTargetMinX() || current.getMinZ() != op.getTargetMinZ()
                    || current.getMaxX() != op.getTargetMaxX() || current.getMaxZ() != op.getTargetMaxZ()) {
                    failOperation(op, RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION,
                        "VISUAL_BOUNDS_MISMATCH", "Bounds da regiao nao correspondem ao resize pendente.");
                    continue;
                }
                RegionBounds oldBounds = new RegionBounds(op.getDimensionKey(), op.getOldMinX(), current.getMinY(),
                    op.getOldMinZ(), op.getOldMaxX(), current.getMaxY(), op.getOldMaxZ());
                if (BigBangRegions.getAllocationCoordinator().refreshExpansionBorder(level, oldBounds, current, region.getId())) {
                    op.setBorderAppliedAt(System.currentTimeMillis());
                    op.setNextRetryAt(null);
                    op.setFailureCode(null);
                    op.setFailureDetail(null);
                    expansionRepository.save(op);
                } else {
                    scheduleVisualRetry(op, "Falha ao aplicar a borda; nova tentativa sera feita.");
                }
            } catch (Throwable error) {
                LOGGER.warn("Failed to apply expansion border for op={}", op.getOperationId(), error);
                scheduleVisualRetry(op, "Erro ao aplicar a borda; nova tentativa sera feita.");
            } finally {
                visualReconciliationsInFlight.remove(op.getOperationId());
            }
        }
    }

    private void scheduleVisualRetry(RegionExpansionOperation op, String detail) {
        op.incrementRetryCount();
        if (op.getRetryCount() >= configManager.getConfig().getRegionExpansion().getMaxPaymentRetriesBeforeManualBlock()) {
            failOperation(op, RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION,
                "VISUAL_BORDER_MAX_RETRIES_EXCEEDED", detail);
            return;
        }
        op.setFailureCode("VISUAL_BORDER_PENDING");
        op.setFailureDetail(detail);
        op.setNextRetryAt(System.currentTimeMillis()
            + configManager.getConfig().getRegionExpansion().getRetryBackoffSeconds() * 1000L);
        expansionRepository.save(op);
    }

    private int processOperation(RegionExpansionOperation op, Config.RegionExpansionConfig ec) {
        switch (op.getState()) {
            case REQUESTED:
            case QUOTED:
                return startPaymentReserve(op, ec);
            case PAYMENT_RESERVE_PENDING:
                return handleReserveResult(op, ec);
            case PAYMENT_RESERVED:
                if (op.isReservationExpired()) {
                    return releaseBeforeResize(op, "RESERVATION_EXPIRED", "Reserva de Gems expirada.");
                }
                long renewThreshold = ec.getRenewBeforeExpirySeconds() * 1000L;
                if (op.getReservationLeaseExpiresAt() != null
                    && System.currentTimeMillis() + renewThreshold > op.getReservationLeaseExpiresAt()) {
                    return startPaymentRenew(op, ec);
                }
                return applyResize(op, ec);
            case PAYMENT_RENEW_PENDING:
                return handleRenewResult(op, ec);
            case RESIZE_APPLYING:
                return recoverResizeApplying(op);
            case RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING:
                if (op.getBorderAppliedAt() == null) return 0;
                return handleCaptureResult(op, ec);
            case RELEASE_PENDING:
                return releaseBeforeResize(op,
                    op.getFailureCode() == null ? "RELEASE_RETRY" : op.getFailureCode(),
                    op.getFailureDetail() == null ? "Nova tentativa de liberar a reserva de Gems." : op.getFailureDetail());
            default:
                return 0;
        }
    }

    private int startPaymentReserve(RegionExpansionOperation op, Config.RegionExpansionConfig ec) {
        op.transitionTo(RegionExpansionState.PAYMENT_RESERVE_PENDING);
        String reserveKey = generateIdempotencyKey(op.getOperationId(), "expand_reserve");
        op.setReserveIdempotencyKey(reserveKey);
        expansionRepository.save(op);

        LandPaymentReserveRequest req = new LandPaymentReserveRequest(
            op.getPaymentOperationUuid(),
            op.getOwnerUuid(),
            op.getPriceGems(),
            reserveKey,
            ec.getReservationLeaseSeconds()
        );

        submitReserve(op, ec, req);
        return 1;
    }

    private int handleReserveResult(RegionExpansionOperation op, Config.RegionExpansionConfig ec) {
        if (op.getReserveIdempotencyKey() == null) {
            return failOperation(op, RegionExpansionState.FAILED_ECONOMY_UNAVAILABLE,
                "NO_RESERVE_KEY", "Chave de reserva ausente.");
        }

        LandPaymentReserveRequest req = new LandPaymentReserveRequest(
            op.getPaymentOperationUuid(),
            op.getOwnerUuid(),
            op.getPriceGems(),
            op.getReserveIdempotencyKey(),
            ec.getReservationLeaseSeconds()
        );

        submitReserve(op, ec, req);
        return 1;
    }

    private int startPaymentRenew(RegionExpansionOperation op, Config.RegionExpansionConfig ec) {
        op.transitionTo(RegionExpansionState.PAYMENT_RENEW_PENDING);
        String renewKey = generateIdempotencyKey(op.getOperationId(), "expand_renew_" + op.getRenewSequence());
        op.setRenewIdempotencyKey(renewKey);
        expansionRepository.save(op);

        LandPaymentRenewRequest req = new LandPaymentRenewRequest(
            op.getPaymentOperationUuid(),
            op.getOwnerUuid(),
            op.getGemsReservationId(),
            renewKey,
            op.getRenewSequence(),
            ec.getReservationLeaseSeconds()
        );

        submitRenew(op, ec, req);
        return 1;
    }

    private int handleRenewResult(RegionExpansionOperation op, Config.RegionExpansionConfig ec) {
        if (op.getRenewIdempotencyKey() == null) {
            return failOperation(op, RegionExpansionState.FAILED_ECONOMY_UNAVAILABLE,
                "NO_RENEW_KEY", "Chave de renovacao ausente.");
        }

        LandPaymentRenewRequest req = new LandPaymentRenewRequest(
            op.getPaymentOperationUuid(),
            op.getOwnerUuid(),
            op.getGemsReservationId(),
            op.getRenewIdempotencyKey(),
            op.getRenewSequence(),
            ec.getReservationLeaseSeconds()
        );

        submitRenew(op, ec, req);
        return 1;
    }

    private void submitReserve(RegionExpansionOperation op, Config.RegionExpansionConfig ec, LandPaymentReserveRequest request) {
        String flightKey = op.getOperationId() + ":reserve:" + request.getIdempotencyKey();
        if (!paymentInFlight.add(flightKey)) return;
        try {
            paymentGateway.reserveAsync(request).whenComplete((result, error) -> operationExecutor.execute(() -> {
                paymentInFlight.remove(flightKey);
                finishReserve(op, ec, error == null ? result : failureFrom(error));
            }));
        } catch (Throwable error) {
            paymentInFlight.remove(flightKey);
            finishReserve(op, ec, failureFrom(error));
        }
    }

    private void finishReserve(RegionExpansionOperation op, Config.RegionExpansionConfig ec,
                               LandPaymentOperationResult result) {
        if (result.isSuccess()) {
            op.setGemsReservationId(result.getReservationId());
            long expiresAt = result.getLeaseExpiresAt() > 0
                ? result.getLeaseExpiresAt()
                : System.currentTimeMillis() + ec.getReservationLeaseSeconds() * 1000L;
            op.setReservationLeaseExpiresAt(expiresAt);
            op.transitionTo(RegionExpansionState.PAYMENT_RESERVED);
            op.setNextRetryAt(null);
            expansionRepository.save(op);
            LOGGER.info("Expansion Gems reserved: op={}, reservationId={}", op.getOperationId(), result.getReservationId());
            completeStartWaiter(op, null);
        } else {
            handlePaymentFailure(op, result, ec);
            if (op.getState().isTerminal()) {
                completeStartWaiter(op, new IllegalStateException(op.getFailureDetail()));
            }
        }
    }

    private void completeStartWaiter(RegionExpansionOperation op, Throwable failure) {
        CompletableFuture<RegionExpansionOperation> waiter = startWaiters.remove(op.getOperationId());
        if (waiter == null) return;
        if (failure == null) waiter.complete(op);
        else waiter.completeExceptionally(failure);
    }

    private void submitRenew(RegionExpansionOperation op, Config.RegionExpansionConfig ec, LandPaymentRenewRequest request) {
        String flightKey = op.getOperationId() + ":renew:" + request.getIdempotencyKey();
        if (!paymentInFlight.add(flightKey)) return;
        try {
            paymentGateway.renewAsync(request).whenComplete((result, error) -> operationExecutor.execute(() -> {
                paymentInFlight.remove(flightKey);
                finishRenew(op, ec, error == null ? result : failureFrom(error));
            }));
        } catch (Throwable error) {
            paymentInFlight.remove(flightKey);
            finishRenew(op, ec, failureFrom(error));
        }
    }

    private void finishRenew(RegionExpansionOperation op, Config.RegionExpansionConfig ec,
                             LandPaymentOperationResult result) {
        if (result.isSuccess()) {
            op.incrementRenewSequence();
            long expiresAt = result.getLeaseExpiresAt() > 0
                ? result.getLeaseExpiresAt()
                : System.currentTimeMillis() + ec.getReservationLeaseSeconds() * 1000L;
            op.setReservationLeaseExpiresAt(expiresAt);
            op.transitionTo(RegionExpansionState.PAYMENT_RESERVED);
            op.setNextRetryAt(null);
            expansionRepository.save(op);
            LOGGER.info("Expansion Gems renewed: op={}, seq={}", op.getOperationId(), op.getRenewSequence());
        } else {
            handlePaymentFailure(op, result, ec);
        }
    }

    private LandPaymentOperationResult failureFrom(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof LinkageError) return LandPaymentOperationResult.failure(LandPaymentFailure.API_INCOMPATIBLE);
        if (cause instanceof java.util.concurrent.RejectedExecutionException) {
            return LandPaymentOperationResult.failure(LandPaymentFailure.EXECUTOR_SATURATED);
        }
        return LandPaymentOperationResult.failure(LandPaymentFailure.TRANSIENT_ERROR);
    }

    private int applyResize(RegionExpansionOperation op, Config.RegionExpansionConfig ec) {
        RegionExpansionOperation persisted = expansionRepository.get(op.getOperationId());
        if (persisted == null || persisted.getState() != RegionExpansionState.PAYMENT_RESERVED) {
            return 0;
        }
        op = persisted;
        if (op.isReservationExpired()) {
            return releaseBeforeResize(op, "RESERVATION_EXPIRED", "Reserva de Gems expirada antes do resize.");
        }

        String captureKey = generateIdempotencyKey(op.getOperationId(), "expand_capture");
        op.setCaptureIdempotencyKey(captureKey);
        op.transitionTo(RegionExpansionState.RESIZE_APPLYING);
        expansionRepository.save(op);

        try {
            applyExpansionInSingleTransaction(op);
        } catch (Exception e) {
            LOGGER.error("Failed to apply expansion in transaction for op={}: {}", op.getOperationId(), e.getMessage());
            return 1;
        }
        return 1;
    }

    private int recoverResizeApplying(RegionExpansionOperation op) {
        Region region = regionCache.get(op.getRegionId());
        if (region == null) {
            try {
                regionRepository.reloadCaches(regionCache, membershipCache);
                region = regionCache.get(op.getRegionId());
            } catch (Exception e) {
                LOGGER.error("Failed to reload regions for resize recovery", e);
            }
        }

        if (region != null) {
            RegionBounds currentBounds = region.getBounds();
            int currentMaxX = currentBounds.getMaxX();
            int currentMaxZ = currentBounds.getMaxZ();

            if (currentMaxX == op.getTargetMaxX() && currentMaxZ == op.getTargetMaxZ()
                && currentBounds.getMinX() == op.getTargetMinX() && currentBounds.getMinZ() == op.getTargetMinZ()) {
                op.transitionTo(RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING);
                expansionRepository.save(op);
                return 1;
            }

            if (currentBounds.getMaxX() == op.getOldMaxX() && currentBounds.getMaxZ() == op.getOldMaxZ()
                && currentBounds.getMinX() == op.getOldMinX() && currentBounds.getMinZ() == op.getOldMinZ()) {
                try {
                    applyExpansionInSingleTransaction(op);
                    return 1;
                } catch (Exception e) {
                    LOGGER.error("Recovery: Failed to apply expansion for op={}", op.getOperationId(), e);
                }
            }
        }

        op.forceTransitionTo(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION);
        op.setFailureCode("RECOVERY_BOUNDS_MISMATCH");
        op.setFailureDetail("Bounds atuais nao correspondem a old nem a target. Revisao administrativa necessaria.");
        expansionRepository.save(op);
        return 1;
    }

    private void applyExpansionInSingleTransaction(RegionExpansionOperation op) throws SQLException {
        Region region = regionCache.get(op.getRegionId());
        if (region == null) {
            regionRepository.reloadCaches(regionCache, membershipCache);
            region = regionCache.get(op.getRegionId());
        }
        if (region == null) {
            throw new IllegalStateException("Region not found: " + op.getRegionId());
        }

        synchronized (databaseManager) {
            Connection conn = databaseManager.getConnection();
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                RegionBounds newBounds = new RegionBounds(
                    op.getDimensionKey(),
                    op.getTargetMinX(), region.getBounds().getMinY(), op.getTargetMinZ(),
                    op.getTargetMaxX(), region.getBounds().getMaxY(), op.getTargetMaxZ()
                );

                region.setBounds(newBounds);
                regionRepository.saveOnConnection(conn, region);

                op.setResizeAppliedAt(System.currentTimeMillis());
                op.transitionTo(RegionExpansionState.RESIZE_APPLIED_PAYMENT_CAPTURE_PENDING);
                expansionRepository.saveOnConnection(conn, op);

                conn.commit();

                regionCache.add(region);

                LOGGER.info("Expansion applied: op={}, region={}, {}x{}→{}x{}",
                    op.getOperationId(), op.getRegionId(), op.getCurrentSize(), op.getCurrentSize(),
                    op.getTargetSize(), op.getTargetSize());
            } catch (Exception e) {
                conn.rollback();
                throw (e instanceof SQLException) ? (SQLException) e : new SQLException(e);
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        }
    }

    private int handleCaptureResult(RegionExpansionOperation op, Config.RegionExpansionConfig ec) {
        String captureKey = op.getCaptureIdempotencyKey();
        if (captureKey == null) {
            captureKey = generateIdempotencyKey(op.getOperationId(), "expand_capture");
            op.setCaptureIdempotencyKey(captureKey);
            expansionRepository.save(op);
        }

        LandPaymentCaptureRequest req = new LandPaymentCaptureRequest(
            op.getPaymentOperationUuid(),
            op.getOwnerUuid(),
            op.getGemsReservationId(),
            captureKey
        );
        submitCapture(op, ec, req);
        return 1;
    }

    private void submitCapture(RegionExpansionOperation op, Config.RegionExpansionConfig ec,
                               LandPaymentCaptureRequest request) {
        String flightKey = op.getOperationId() + ":capture:" + request.getIdempotencyKey();
        if (!paymentInFlight.add(flightKey)) return;
        try {
            paymentGateway.captureAsync(request).whenComplete((result, error) -> operationExecutor.execute(() -> {
                paymentInFlight.remove(flightKey);
                finishCapture(op, ec, error == null ? result : failureFrom(error));
            }));
        } catch (Throwable error) {
            paymentInFlight.remove(flightKey);
            finishCapture(op, ec, failureFrom(error));
        }
    }

    private void finishCapture(RegionExpansionOperation op, Config.RegionExpansionConfig ec,
                               LandPaymentOperationResult result) {
        if (result.isSuccess() || result.getFailure() == LandPaymentFailure.ALREADY_CAPTURED) {
            op.setPaymentCapturedAt(System.currentTimeMillis());
            op.transitionTo(RegionExpansionState.COMPLETED);
            expansionRepository.save(op);
            notifyExpansionCompleted(op);
            LOGGER.info("Expansion completed: op={}, region={}, size={}x{}",
                op.getOperationId(), op.getRegionId(), op.getTargetSize(), op.getTargetSize());
            return;
        }

        if (result.getFailure() == LandPaymentFailure.RESERVATION_EXPIRED) {
            op.forceTransitionTo(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION);
            op.setFailureCode("PAYMENT_RESERVATION_EXPIRED_AFTER_RESIZE");
            op.setFailureDetail("Reserva de Gems expirou apos o resize. Revisao administrativa necessaria.");
            expansionRepository.save(op);
            LOGGER.error("Expansion payment reservation expired after resize: op={}", op.getOperationId());
            return;
        }

        if (result.getFailure() == LandPaymentFailure.IDEMPOTENCY_CONFLICT
            || result.getFailure() == LandPaymentFailure.API_INCOMPATIBLE
            || result.getFailure() == LandPaymentFailure.DATA_INTEGRITY_FAILURE) {
            failOperation(op, RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION,
                result.getFailure().name(), "Capture exige reconciliacao administrativa; nenhum retry cego sera feito.");
            return;
        }

        op.incrementRetryCount();
        if (op.getRetryCount() >= ec.getMaxPaymentRetriesBeforeManualBlock()) {
            op.forceTransitionTo(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION);
            op.setFailureCode("CAPTURE_MAX_RETRIES_EXCEEDED");
            op.setFailureDetail("Falha ao capturar pagamento apos " + op.getRetryCount() + " tentativas.");
        } else {
            op.setNextRetryAt(System.currentTimeMillis() + ec.getRetryBackoffSeconds() * 1000L);
        }
        expansionRepository.save(op);
    }

    private int handlePaymentFailure(RegionExpansionOperation op, LandPaymentOperationResult result,
                                      Config.RegionExpansionConfig ec) {
        if (result.isInsufficientBalance()) {
            if (op.getGemsReservationId() != null) {
                return releaseBeforeResize(op, "INSUFFICIENT_BALANCE",
                    "Você não tem Gems suficientes para realizar essa expansão.");
            }
            return failOperation(op, RegionExpansionState.CANCELLED_BEFORE_RESIZE,
                "INSUFFICIENT_BALANCE", "Você não tem Gems suficientes para realizar essa expansão.");
        }

        if (result.getFailure() == LandPaymentFailure.RESERVATION_EXPIRED
            || result.getFailure() == LandPaymentFailure.RESERVATION_NOT_FOUND) {
            return releaseBeforeResize(op, String.valueOf(result.getFailure()), "Reserva de Gems expirada.");
        }

        if (result.getFailure() == LandPaymentFailure.IDEMPOTENCY_CONFLICT
            || result.getFailure() == LandPaymentFailure.API_INCOMPATIBLE
            || result.getFailure() == LandPaymentFailure.DATA_INTEGRITY_FAILURE) {
            return failOperation(op, RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION,
                result.getFailure().name(), "A operacao exige reconciliacao administrativa; nao sera repetida automaticamente.");
        }

        return retryPaymentFailure(op, ec, result.getFailure().name(),
            result.isTransient() ? "Falha temporaria no sistema de pagamento." : "Sistema de pagamento indisponivel.");
    }

    private int retryPaymentFailure(RegionExpansionOperation op, Config.RegionExpansionConfig ec,
                                    String failureCode, String failureDetail) {
        op.incrementRetryCount();
        if (op.getRetryCount() >= ec.getMaxPaymentRetriesBeforeManualBlock()) {
            if (op.getGemsReservationId() != null) {
                return releaseBeforeResize(op, failureCode, failureDetail);
            }
            return failOperation(op, RegionExpansionState.FAILED_ECONOMY_UNAVAILABLE, failureCode, failureDetail);
        }
        op.setFailureCode(failureCode);
        op.setFailureDetail(failureDetail);
        op.setNextRetryAt(System.currentTimeMillis() + ec.getRetryBackoffSeconds() * 1000L);
        expansionRepository.save(op);
        return 1;
    }

    private void beginReleaseBeforeResize(RegionExpansionOperation op, String code, String reason) {
        RegionExpansionOperation persisted = expansionRepository.get(op.getOperationId());
        if (persisted == null || !persisted.getState().isPreResize()) {
            throw new IllegalStateException("A expansao ja ultrapassou o ponto irreversivel e nao pode ser cancelada.");
        }
        op = persisted;
        op.setReleaseIdempotencyKey(generateIdempotencyKey(op.getOperationId(), "expand_release"));
        op.forceTransitionTo(RegionExpansionState.RELEASE_PENDING);
        op.setFailureCode(code);
        op.setFailureDetail(reason);
        expansionRepository.save(op);
        RegionExpansionOperation releaseOperation = op;
        operationExecutor.execute(() -> releaseBeforeResize(releaseOperation, code, reason));
    }

    private int releaseBeforeResize(RegionExpansionOperation op, String code, String reason) {
        RegionExpansionOperation persisted = expansionRepository.get(op.getOperationId());
        if (persisted != null) {
            if (!persisted.getState().isPreResize()) {
                LOGGER.warn("Skipping payment release after resize for op={} in state={}", op.getOperationId(), persisted.getState());
                return 0;
            }
            op = persisted;
        }
        String releaseKey = generateIdempotencyKey(op.getOperationId(), "expand_release");
        op.setReleaseIdempotencyKey(releaseKey);
        op.forceTransitionTo(RegionExpansionState.RELEASE_PENDING);
        op.setFailureCode(code);
        expansionRepository.save(op);

        if (op.getGemsReservationId() == null) {
            op.forceTransitionTo(RegionExpansionState.CANCELLED_BEFORE_RESIZE);
            expansionRepository.save(op);
            return 1;
        }

        LandPaymentReleaseRequest req = new LandPaymentReleaseRequest(
            op.getPaymentOperationUuid(),
            op.getOwnerUuid(),
            op.getGemsReservationId(),
            releaseKey
        );

        submitRelease(op, req, reason);
        return 1;
    }

    private void submitRelease(RegionExpansionOperation op, LandPaymentReleaseRequest request, String reason) {
        String flightKey = op.getOperationId() + ":release:" + request.getIdempotencyKey();
        if (!paymentInFlight.add(flightKey)) return;
        try {
            paymentGateway.releaseAsync(request).whenComplete((result, error) -> operationExecutor.execute(() -> {
                paymentInFlight.remove(flightKey);
                finishRelease(op, reason, error == null ? result : failureFrom(error));
            }));
        } catch (Throwable error) {
            paymentInFlight.remove(flightKey);
            finishRelease(op, reason, failureFrom(error));
        }
    }

    private void notifyExpansionCompleted(RegionExpansionOperation op) {
        MinecraftServer currentServer = server;
        if (currentServer == null) return;
        currentServer.execute(() -> {
            ServerPlayer player = currentServer.getPlayerList().getPlayer(op.getOwnerUuid());
            if (player != null) {
                player.sendSystemMessage(Component.literal("§aExpansão concluída!"));
            }
        });
    }

    private void finishRelease(RegionExpansionOperation op, String reason, LandPaymentOperationResult result) {
        if (result.isSuccess() || result.getFailure() == LandPaymentFailure.ALREADY_RELEASED
            || result.getFailure() == LandPaymentFailure.RESERVATION_EXPIRED
            || result.getFailure() == LandPaymentFailure.RESERVATION_NOT_FOUND) {
            op.setGemsReservationId(null);
            op.forceTransitionTo(RegionExpansionState.CANCELLED_BEFORE_RESIZE);
            op.setFailureDetail(reason);
            expansionRepository.save(op);
            LOGGER.info("Expansion cancelled before resize: op={}, reason={}", op.getOperationId(), reason);
        } else {
            op.incrementRetryCount();
            Config.RegionExpansionConfig ec = configManager.getConfig().getRegionExpansion();
            if (op.getRetryCount() >= ec.getMaxPaymentRetriesBeforeManualBlock()) {
                op.forceTransitionTo(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION);
                op.setFailureCode("RELEASE_MAX_RETRIES_EXCEEDED");
                op.setFailureDetail("Nao foi possivel liberar a reserva de Gems automaticamente.");
            } else {
                op.setNextRetryAt(System.currentTimeMillis() + ec.getRetryBackoffSeconds() * 1000L);
            }
            expansionRepository.save(op);
            LOGGER.warn("Failed to release expansion payment: op={}, failure={}, state={}",
                op.getOperationId(), result.getFailure(), op.getState());
        }
    }

    private int failOperation(RegionExpansionOperation op, RegionExpansionState target,
                               String failureCode, String failureDetail) {
        op.forceTransitionTo(target);
        op.setFailureCode(failureCode);
        op.setFailureDetail(failureDetail);
        expansionRepository.save(op);
        return 1;
    }

    public RegionExpansionOperation getExpansion(String operationId) {
        return expansionRepository.get(operationId);
    }

    public List<RegionExpansionOperation> getActiveExpansions() {
        return expansionRepository.getActiveOperations();
    }

    public void adminBlockOperation(String operationId) {
        RegionExpansionOperation op = expansionRepository.get(operationId);
        if (op == null) {
            throw new IllegalArgumentException("Operacao nao encontrada: " + operationId);
        }
        if (op.getState().isTerminal()) {
            throw new IllegalStateException("Operacao ja em estado terminal: " + op.getState());
        }
        op.forceTransitionTo(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION);
        op.setFailureCode("ADMIN_BLOCKED");
        op.setFailureDetail("Bloqueado manualmente por administrador.");
        expansionRepository.save(op);
    }

    public void adminScheduleRetry(String operationId) {
        RegionExpansionOperation op = expansionRepository.get(operationId);
        if (op == null) {
            throw new IllegalArgumentException("Operacao nao encontrada: " + operationId);
        }
        if (op.getState().isTerminal()) {
            throw new IllegalStateException("Operacao ja em estado terminal: " + op.getState());
        }
        op.setNextRetryAt(0L);
        expansionRepository.save(op);
    }


    private String generateIdempotencyKey(String operationId, String operation) {
        String compactId = operationId.replace("-", "");
        return "regions_" + compactId + "_" + operation;
    }
}
