package com.bigbangcraft.regions.expansion;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.payment.api.*;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.storage.DatabaseManager;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

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

        String operationId = "expand_" + region.getId() + "_" + System.currentTimeMillis();
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

        beginReleaseBeforeResize(op, "Cancelled by player");
    }

    public int processNextExpansion() {
        List<RegionExpansionOperation> active = expansionRepository.getActiveOperations();
        if (active.isEmpty()) return 0;

        Config.RegionExpansionConfig ec = configManager.getConfig().getRegionExpansion();

        long now = System.currentTimeMillis();
        for (RegionExpansionOperation op : active) {
            if (op.getNextRetryAt() != null && op.getNextRetryAt() > now) continue;
            return processOperation(op, ec);
        }
        return 0;
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
                    return failOperation(op, RegionExpansionState.CANCELLED_BEFORE_RESIZE,
                        "RESERVATION_EXPIRED", "Reserva de Gems expirada.");
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
                return handleCaptureResult(op, ec);
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
            UUID.fromString(op.getOperationId()),
            op.getOwnerUuid(),
            op.getPriceGems(),
            reserveKey,
            ec.getReservationLeaseSeconds()
        );

        LandPaymentOperationResult result = paymentGateway.reserve(req);
        if (result.isSuccess()) {
            op.setGemsReservationId(result.getReservationId());
            op.setReservationLeaseExpiresAt(System.currentTimeMillis() + ec.getReservationLeaseSeconds() * 1000L);
            op.transitionTo(RegionExpansionState.PAYMENT_RESERVED);
            expansionRepository.save(op);
            LOGGER.info("Expansion gems reserved: op={}, reservationId={}", op.getOperationId(), result.getReservationId());
            return 1;
        } else {
            return handlePaymentFailure(op, result, ec);
        }
    }

    private int handleReserveResult(RegionExpansionOperation op, Config.RegionExpansionConfig ec) {
        if (op.getReserveIdempotencyKey() == null) {
            return failOperation(op, RegionExpansionState.FAILED_ECONOMY_UNAVAILABLE,
                "NO_RESERVE_KEY", "Chave de reserva ausente.");
        }

        LandPaymentReserveRequest req = new LandPaymentReserveRequest(
            UUID.fromString(op.getOperationId()),
            op.getOwnerUuid(),
            op.getPriceGems(),
            op.getReserveIdempotencyKey(),
            ec.getReservationLeaseSeconds()
        );

        LandPaymentOperationResult result = paymentGateway.reserve(req);
        if (result.isSuccess()) {
            op.setGemsReservationId(result.getReservationId());
            op.setReservationLeaseExpiresAt(System.currentTimeMillis() + ec.getReservationLeaseSeconds() * 1000L);
            op.transitionTo(RegionExpansionState.PAYMENT_RESERVED);
            expansionRepository.save(op);
            return 1;
        }
        return handlePaymentFailure(op, result, ec);
    }

    private int startPaymentRenew(RegionExpansionOperation op, Config.RegionExpansionConfig ec) {
        op.transitionTo(RegionExpansionState.PAYMENT_RENEW_PENDING);
        String renewKey = generateIdempotencyKey(op.getOperationId(), "expand_renew_" + op.getRenewSequence());
        op.setRenewIdempotencyKey(renewKey);
        expansionRepository.save(op);

        LandPaymentRenewRequest req = new LandPaymentRenewRequest(
            UUID.fromString(op.getOperationId()),
            op.getGemsReservationId(),
            renewKey,
            op.getRenewSequence(),
            ec.getReservationLeaseSeconds()
        );

        LandPaymentOperationResult result = paymentGateway.renew(req);
        if (result.isSuccess()) {
            op.incrementRenewSequence();
            op.setReservationLeaseExpiresAt(System.currentTimeMillis() + ec.getReservationLeaseSeconds() * 1000L);
            op.transitionTo(RegionExpansionState.PAYMENT_RESERVED);
            expansionRepository.save(op);
            LOGGER.info("Expansion gems renewed: op={}, seq={}", op.getOperationId(), op.getRenewSequence());
            return 1;
        }
        return handlePaymentFailure(op, result, ec);
    }

    private int handleRenewResult(RegionExpansionOperation op, Config.RegionExpansionConfig ec) {
        if (op.getRenewIdempotencyKey() == null) {
            return failOperation(op, RegionExpansionState.FAILED_ECONOMY_UNAVAILABLE,
                "NO_RENEW_KEY", "Chave de renovacao ausente.");
        }

        LandPaymentRenewRequest req = new LandPaymentRenewRequest(
            UUID.fromString(op.getOperationId()),
            op.getGemsReservationId(),
            op.getRenewIdempotencyKey(),
            op.getRenewSequence(),
            ec.getReservationLeaseSeconds()
        );

        LandPaymentOperationResult result = paymentGateway.renew(req);
        if (result.isSuccess()) {
            op.incrementRenewSequence();
            op.setReservationLeaseExpiresAt(System.currentTimeMillis() + ec.getReservationLeaseSeconds() * 1000L);
            op.transitionTo(RegionExpansionState.PAYMENT_RESERVED);
            expansionRepository.save(op);
            return 1;
        }
        return handlePaymentFailure(op, result, ec);
    }

    private int applyResize(RegionExpansionOperation op, Config.RegionExpansionConfig ec) {
        if (op.isReservationExpired()) {
            return failOperation(op, RegionExpansionState.CANCELLED_BEFORE_RESIZE,
                "RESERVATION_EXPIRED", "Reserva de Gems expirada antes do resize.");
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
                List<Region> allRegions = regionRepository.loadAll();
                for (Region r : allRegions) {
                    regionCache.add(r);
                    membershipCache.loadFromRegion(r);
                }
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
        synchronized (databaseManager) {
            Connection conn = databaseManager.getConnection();
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                Region region = regionCache.get(op.getRegionId());
                if (region == null) {
                    List<Region> allRegions = regionRepository.loadAll();
                    for (Region r : allRegions) {
                        regionCache.add(r);
                        membershipCache.loadFromRegion(r);
                    }
                    region = regionCache.get(op.getRegionId());
                }
                if (region == null) {
                    throw new IllegalStateException("Region not found: " + op.getRegionId());
                }

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
            UUID.fromString(op.getOperationId()),
            op.getGemsReservationId(),
            captureKey
        );

        LandPaymentOperationResult result = paymentGateway.capture(req);
        if (result.isSuccess()
            || result.getFailure() == LandPaymentFailure.ALREADY_CAPTURED) {
            op.setPaymentCapturedAt(System.currentTimeMillis());
            op.transitionTo(RegionExpansionState.COMPLETED);
            expansionRepository.save(op);
            LOGGER.info("Expansion completed: op={}, region={}, size={}x{}",
                op.getOperationId(), op.getRegionId(), op.getTargetSize(), op.getTargetSize());
            return 1;
        }

        if (result.getFailure() == LandPaymentFailure.RESERVATION_EXPIRED) {
            op.forceTransitionTo(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION);
            op.setFailureCode("PAYMENT_RESERVATION_EXPIRED_AFTER_RESIZE");
            op.setFailureDetail("Reserva de Gems expirou apos o resize. Revisao administrativa necessaria.");
            expansionRepository.save(op);
            LOGGER.error("Expansion payment reservation expired after resize: op={}", op.getOperationId());
            return 1;
        }

        if (result.isTransient()) {
            op.incrementRetryCount();
            op.setNextRetryAt(System.currentTimeMillis() + ec.getRetryBackoffSeconds() * 1000L);
            expansionRepository.save(op);
            return 1;
        }

        op.incrementRetryCount();
        if (op.getRetryCount() >= ec.getMaxPaymentRetriesBeforeManualBlock()) {
            op.forceTransitionTo(RegionExpansionState.BLOCKED_FOR_MANUAL_RECONCILIATION);
            op.setFailureCode("CAPTURE_MAX_RETRIES_EXCEEDED");
            op.setFailureDetail("Falha ao capturar pagamento apos " + op.getRetryCount() + " tentativas.");
            expansionRepository.save(op);
        } else {
            op.setNextRetryAt(System.currentTimeMillis() + ec.getRetryBackoffSeconds() * 1000L);
            expansionRepository.save(op);
        }
        return 1;
    }

    private int handlePaymentFailure(RegionExpansionOperation op, LandPaymentOperationResult result,
                                      Config.RegionExpansionConfig ec) {
        if (result.isInsufficientBalance()) {
            return failOperation(op, RegionExpansionState.CANCELLED_BEFORE_RESIZE,
                "INSUFFICIENT_BALANCE", "Saldo de Gems insuficiente. Voce precisa de " + op.getPriceGems() + " gems.");
        }

        if (result.getFailure() == LandPaymentFailure.RESERVATION_EXPIRED
            || result.getFailure() == LandPaymentFailure.RESERVATION_NOT_FOUND) {
            return failOperation(op, RegionExpansionState.CANCELLED_BEFORE_RESIZE,
                String.valueOf(result.getFailure()), "Reserva de Gems expirada.");
        }

        if (result.isTransient()) {
            op.incrementRetryCount();
            op.setNextRetryAt(System.currentTimeMillis() + ec.getRetryBackoffSeconds() * 1000L);
            expansionRepository.save(op);
            return 1;
        }

        op.incrementRetryCount();
        if (op.getRetryCount() >= ec.getMaxPaymentRetriesBeforeManualBlock()) {
            return failOperation(op, RegionExpansionState.FAILED_ECONOMY_UNAVAILABLE,
                "PAYMENT_PROVIDER_FAILURE", "Sistema de pagamento indisponivel.");
        }
        op.setNextRetryAt(System.currentTimeMillis() + ec.getRetryBackoffSeconds() * 1000L);
        expansionRepository.save(op);
        return 1;
    }

    private void beginReleaseBeforeResize(RegionExpansionOperation op, String reason) {
        String releaseKey = generateIdempotencyKey(op.getOperationId(), "expand_release");
        op.setReleaseIdempotencyKey(releaseKey);
        op.forceTransitionTo(RegionExpansionState.RELEASE_PENDING);
        expansionRepository.save(op);

        LandPaymentReleaseRequest req = new LandPaymentReleaseRequest(
            UUID.fromString(op.getOperationId()),
            op.getGemsReservationId(),
            releaseKey
        );

        LandPaymentOperationResult result = paymentGateway.release(req);
        if (result.isSuccess() || result.getFailure() == LandPaymentFailure.ALREADY_RELEASED) {
            op.setGemsReservationId(null);
            op.forceTransitionTo(RegionExpansionState.CANCELLED_BEFORE_RESIZE);
            op.setFailureDetail(reason);
            expansionRepository.save(op);
            LOGGER.info("Expansion cancelled before resize: op={}, reason={}", op.getOperationId(), reason);
        } else {
            op.incrementRetryCount();
            op.setNextRetryAt(System.currentTimeMillis() + 5000);
            expansionRepository.save(op);
            LOGGER.warn("Failed to release expansion payment: op={}, failure={}. Staying in RELEASE_PENDING.",
                op.getOperationId(), result.getFailure());
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
