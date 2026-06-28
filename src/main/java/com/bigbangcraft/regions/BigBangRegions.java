package com.bigbangcraft.regions;

import com.bigbangcraft.regions.allocation.*;
import com.bigbangcraft.regions.api.BigBangRegionsApi;
import com.bigbangcraft.regions.api.BigBangRegionsApiImpl;
import com.bigbangcraft.regions.audit.AuditService;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.command.RegionsCommand;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.flag.FlagResolver;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.protection.*;
import com.bigbangcraft.regions.region.*;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.payment.NoPaymentGateway;
import com.bigbangcraft.regions.payment.api.LandPaymentGateway;
import com.bigbangcraft.regions.recovery.LandOperationRecoveryService;
import com.bigbangcraft.regions.expansion.RegionExpansionCoordinator;
import com.bigbangcraft.regions.expansion.RegionExpansionOperationRepository;
import com.bigbangcraft.regions.expansion.RegionExpansionRecoveryService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.bigbangcraft.regions.repository.AllocationRequestRepository;
import com.bigbangcraft.regions.repository.AuditRepository;
import com.bigbangcraft.regions.repository.PlayerRegionHomeRepository;
import com.bigbangcraft.regions.repository.PlotSlotRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.storage.DatabaseManager;
import com.bigbangcraft.regions.util.MessageHelper;
import com.bigbangcraft.regions.util.SelectionManager;
import com.bigbangcraft.regions.config.Config;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class BigBangRegions implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions");

    private static BigBangRegionsApi api;
    private static ProtectionService protectionService;
    private static ConfigManager configManager;
    private static DatabaseManager databaseManager;
    private static RegionRepository regionRepository;
    private static AuditService auditService;
    private static SelectionManager selectionManager;
    private static RegionMembershipCache membershipCache;
    private static RegionRoleResolver roleResolver;
    private static RegionMembershipService membershipService;
    private static RegionAccessService regionAccessService;
    private static TerrainAllocationCoordinator allocationCoordinator;
    private static AllocationScheduler allocationScheduler;
    private static RegionCache regionCache;
    private static ExplorationZoneService explorationZoneService;
    private static RegionEntryExitService entryExitService;
    private static RegionBoundaryRenderer boundaryRenderer;
    private static RegionContainmentService containmentService;
    private static LandPaymentGateway paymentGateway;
    private static LandOperationRecoveryService recoveryService;
    private static RegionExpansionOperationRepository expansionOperationRepository;
    private static RegionExpansionCoordinator expansionCoordinator;
    private static RegionExpansionRecoveryService expansionRecoveryService;

    public static RegionMembershipCache getMembershipCache() {
        return membershipCache;
    }

    public static RegionRoleResolver getRoleResolver() {
        return roleResolver;
    }

    public static RegionMembershipService getMembershipService() {
        return membershipService;
    }

    public static RegionAccessService getRegionAccessService() {
        return regionAccessService;
    }

    public static TerrainAllocationCoordinator getAllocationCoordinator() {
        return allocationCoordinator;
    }

    public static RegionCache getRegionCache() {
        return regionCache;
    }

    public static RegionEntryExitService getEntryExitService() {
        return entryExitService;
    }

    public static RegionBoundaryRenderer getBoundaryRenderer() {
        return boundaryRenderer;
    }

    public static RegionContainmentService getContainmentService() {
        return containmentService;
    }

    public static ExplorationZoneService getExplorationZoneService() {
        return explorationZoneService;
    }
    
    public static LandPaymentGateway getPaymentGateway() {
        return paymentGateway;
    }

    public static RegionExpansionCoordinator getExpansionCoordinator() {
        return expansionCoordinator;
    }

    public static RegionExpansionRecoveryService getExpansionRecoveryService() {
        return expansionRecoveryService;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing BigBang Regions...");

        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("bigbangregions");
        
        // 1. Config Manager
        configManager = new ConfigManager(configDir);
        configManager.load();

        // 2. Database Manager
        Path dbFile = configDir.resolve("regions.db");
        databaseManager = new DatabaseManager(dbFile);
        try {
            databaseManager.initialize();
        } catch (SQLException e) {
            LOGGER.error("Failed to initialize SQLite database: ", e);
            throw new RuntimeException("Database initialization failed", e);
        }

        // 3. Repositories
        regionRepository = new RegionRepository(databaseManager);
        AuditRepository auditRepository = new AuditRepository(databaseManager);
        auditService = new AuditService(auditRepository);

        // 4. Cache and Resolver
        regionCache = new RegionCache();
        RegionResolver regionResolver = new RegionResolver(regionCache);
        FlagResolver flagResolver = new FlagResolver();
        membershipCache = new RegionMembershipCache();

        // Load all regions into cache
        List<Region> regions = regionRepository.loadAll();
        for (Region r : regions) {
            regionCache.add(r);
            membershipCache.loadFromRegion(r);
        }
        LOGGER.info("Loaded {} regions into cache.", regions.size());

        // 5. Allocation repositories
        AllocationRequestRepository allocationRequestRepository = new AllocationRequestRepository(databaseManager);
        PlotSlotRepository plotSlotRepository = new PlotSlotRepository(databaseManager);
        PlayerRegionHomeRepository playerRegionHomeRepository = new PlayerRegionHomeRepository(databaseManager);

        // 6. Allocation services
        BiomeOptionRegistry biomeOptionRegistry = new BiomeOptionRegistry(configManager);
        biomeOptionRegistry.load();
        BiomeSearchService biomeSearchService = new BiomeSearchService(configManager);
        PlotSlotService plotSlotService = new PlotSlotService(configManager, plotSlotRepository, regionCache);
        
        // 7. Payment Gateway initialization (only used for region expansion, not initial allocation)
        paymentGateway = initializePaymentGateway();
        
        allocationCoordinator = new TerrainAllocationCoordinator(
            configManager, databaseManager,
            allocationRequestRepository, plotSlotRepository, plotSlotService,
            playerRegionHomeRepository, regionRepository, biomeSearchService, biomeOptionRegistry,
            regionCache, membershipCache
        );
        allocationScheduler = new AllocationScheduler(allocationCoordinator, configManager);
        
        // 7b. Recovery service
        recoveryService = new LandOperationRecoveryService(
            allocationRequestRepository, plotSlotRepository, regionRepository,
            regionCache, membershipCache
        );

        // 7c. Expansion services
        expansionOperationRepository = new RegionExpansionOperationRepository(databaseManager);
        expansionCoordinator = new RegionExpansionCoordinator(
            configManager, databaseManager,
            expansionOperationRepository, regionRepository, plotSlotRepository,
            regionCache, membershipCache, paymentGateway
        );
        expansionRecoveryService = new RegionExpansionRecoveryService(
            expansionOperationRepository, regionRepository, plotSlotRepository,
            regionCache, membershipCache, paymentGateway, configManager, databaseManager
        );

        // 7. Services and Managers
        selectionManager = new SelectionManager();
        int operatorFallback = configManager.getConfig().getPermissions().getOperatorFallbackLevel();
        PermissionManager permissionManager = new PermissionManager(operatorFallback);
        
        roleResolver = new RegionRoleResolver(membershipCache);
        membershipService = new RegionMembershipService(regionRepository, membershipCache, auditService, roleResolver);
        regionAccessService = new RegionAccessService(roleResolver, flagResolver, configManager);
        protectionService = new ProtectionService(regionResolver, permissionManager, regionAccessService);

        // 8. Region Entry/Exit notification service
        entryExitService = new RegionEntryExitService(regionCache, roleResolver, configManager);

        // 9. Exploration Zone Service
        explorationZoneService = new ExplorationZoneService(configManager);

        // 10. Region Boundary Renderer (visual particles)
        boundaryRenderer = new RegionBoundaryRenderer(regionCache, roleResolver);

        // 10.5 Region Containment Service
        containmentService = new RegionContainmentService(configManager);

        // 11. Public API
        api = new BigBangRegionsApiImpl(regionResolver, protectionService);

        // 10. Command registration
        RegionsCommand.initialize(permissionManager, selectionManager, regionCache, regionRepository, regionResolver, auditService, configManager);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            RegionsCommand.register(dispatcher);
        });

        // 11. Register Event Listeners
        registerListeners();

        // 11. Server tick scheduler for allocation processing + entry/exit tracking
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Server started. Running land operation recovery...");
            if (recoveryService != null) {
                recoveryService.recover();
            }
            if (expansionRecoveryService != null) {
                expansionRecoveryService.recover();
            }
            LOGGER.info("Allocation scheduler started.");
            LOGGER.info("Payment gateway status: {}", paymentGateway.getProviderStatus());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (allocationScheduler != null) {
                allocationScheduler.tick(server);
            }
            if (expansionCoordinator != null) {
                expansionCoordinator.processNextExpansion();
            }
            if (entryExitService != null) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    entryExitService.tick(p);
                }
            }
            if (boundaryRenderer != null) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    boundaryRenderer.tick(p);
                }
            }
            if (containmentService != null) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    containmentService.tick(p);
                }
            }
            if (server.getTickCount() % 200 == 0) {
                MessageHelper.cleanCache();
                if (allocationCoordinator != null) {
                    allocationCoordinator.cleanCooldowns();
                }
            }
        });

        // 12. Player disconnect cleanup
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.getPlayer() != null) {
                UUID uuid = handler.getPlayer().getUUID();
                if (entryExitService != null) {
                    entryExitService.removePlayer(uuid);
                }
                if (boundaryRenderer != null) {
                    boundaryRenderer.setVisibility(uuid, false);
                }
                if (containmentService != null) {
                    containmentService.removePlayer(uuid);
                }
            }
        });

        // 12.5 Player connect reposition check
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.getPlayer() != null && containmentService != null) {
                containmentService.onPlayerJoin(handler.getPlayer());
            }
        });

        // 13. Clean shutdown handler
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Server is stopping. Shutting down BigBang Regions services...");
            if (auditService != null) {
                auditService.shutdown();
            }
            if (databaseManager != null) {
                databaseManager.close();
            }
        });

        LOGGER.info("BigBang Regions initialized successfully.");
    }

    public static BigBangRegionsApi getApi() {
        return api;
    }

    public static boolean handlePlayerAction(ServerPlayer player, BlockPos pos, RegionAction action) {
        String dimension = player.level().dimension().location().toString();
        if (isBoundaryBlock(dimension, pos)) {
            boolean hasBypass = false;
            try {
                hasBypass = me.lucko.fabric.api.permissions.v0.Permissions.check(player, "bigbangregions.bypass.boundary", false);
                if (!hasBypass) {
                    hasBypass = me.lucko.fabric.api.permissions.v0.Permissions.check(player, "bigbangregions.bypass", false);
                }
            } catch (Throwable t) {
                hasBypass = player.hasPermissions(2);
            }
            if (!hasBypass) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cVocê não pode modificar as fronteiras do terreno."));
                return false;
            }
        }

        ProtectionContext context = new ProtectionContext.Builder(action, player.level(), pos)
                .player(player)
                .build();
        ProtectionResult result = protectionService.check(context);
        if (!result.isAllowed()) {
            MessageHelper.sendDenial(player, action, result.getRegion());
            return false;
        }
        return true;
    }
    
    private LandPaymentGateway initializePaymentGateway() {
        String provider = configManager.getConfig().getPlayerLandAllocation().getPayment().getProvider();
        
        if ("none".equals(provider) || provider == null || provider.isEmpty()) {
            LOGGER.info("Payment provider: none (no payment required)");
            return new NoPaymentGateway();
        }
        
        // Check if BigBang Essentials is available
        boolean essentialsLoaded = FabricLoader.getInstance().isModLoaded("bigbangessentials");
        
        if (!essentialsLoaded) {
            LOGGER.warn("Payment provider '{}' configured but BigBang Essentials is not installed.", provider);
            LOGGER.warn("Payment operations will be unavailable.");
            return new NoPaymentGateway();
        }
        
        // Try to load the BigBang Essentials adapter
        try {
            // Use reflection to load the adapter to avoid classloading issues
            Class<?> adapterClass = Class.forName("com.bigbangcraft.regions.payment.essentials.BigBangEssentialsGemsGateway");
            LandPaymentGateway gateway = (LandPaymentGateway) adapterClass.getDeclaredConstructor(ConfigManager.class).newInstance(configManager);
            LOGGER.info("Payment provider '{}' initialized successfully.", provider);
            return gateway;
        } catch (ClassNotFoundException e) {
            LOGGER.warn("BigBang Essentials adapter not found. Payment operations will be unavailable.", e);
            return new NoPaymentGateway();
        } catch (Exception e) {
            LOGGER.error("Failed to initialize BigBang Essentials adapter: {}", e.getMessage(), e);
            return new NoPaymentGateway();
        }
    }

    private void registerListeners() {
        // Block break
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                return handlePlayerAction(serverPlayer, pos, RegionAction.BLOCK_BREAK);
            }
            return true;
        });

        // Block use & place & interactions
        // Classification Precedence:
        // 1. Container (any block with Container or MenuProvider block entity) -> container-access
        // 2. Door (any DoorBlock, TrapDoorBlock, FenceGateBlock) -> door-use
        // 3. Redstone (any ButtonBlock, LeverBlock, DiodeBlock) -> redstone-use
        // 4. Block Placement (if carrying a block/bucket item) -> player-build
        // 5. Generic Interaction (Fallback) -> player-interact
        // Once an interaction is classified into a specific category, the handler returns PASS or FAIL immediately,
        // preventing it from being evaluated again under the fallback player-interact check.
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            com.bigbangcraft.regions.protection.BlockInteractionClassifier.ClassifiedInteraction classified = 
                    com.bigbangcraft.regions.protection.BlockInteractionClassifier.classify(world, pos, state, serverPlayer, hand, hitResult);

            if (!handlePlayerAction(serverPlayer, classified.getTargetPos(), classified.getAction())) {
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });

        // Entity use/interact
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                if (isProtectedEntity(entity)) {
                    if (!handlePlayerAction(serverPlayer, entity.blockPosition(), RegionAction.ENTITY_INTERACT)) {
                        return InteractionResult.FAIL;
                    }
                }
            }
            return InteractionResult.PASS;
        });

        // Entity attack (melee attack)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                if (entity instanceof ServerPlayer targetPlayer) {
                    if (!handlePlayerAction(serverPlayer, targetPlayer.blockPosition(), RegionAction.PVP) ||
                        !handlePlayerAction(serverPlayer, serverPlayer.blockPosition(), RegionAction.PVP)) {
                        return InteractionResult.FAIL;
                    }
                } else if (isProtectedEntity(entity)) {
                    if (!handlePlayerAction(serverPlayer, entity.blockPosition(), RegionAction.ENTITY_INTERACT)) {
                        return InteractionResult.FAIL;
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }

    private boolean isProtectedEntity(Entity entity) {
        return entity instanceof ArmorStand ||
               entity instanceof ItemFrame ||
               entity instanceof Boat ||
               entity instanceof AbstractMinecart;
    }

    public static boolean isBoundaryBlock(String dimension, BlockPos pos) {
        if (configManager == null) return false;
        Config config = configManager.getConfig();
        if (config == null || !config.getPlayerLandAllocation().getBorder().isProtect()) {
            return false;
        }

        RegionCache cache = getRegionCache();
        if (cache == null) return false;

        for (Region r : cache.getRegionsAt(dimension, pos.getX(), pos.getY(), pos.getZ())) {
            RegionBounds bounds = r.getBounds();
            if (pos.getX() == bounds.getMinX() || pos.getX() == bounds.getMaxX() ||
                pos.getZ() == bounds.getMinZ() || pos.getZ() == bounds.getMaxZ()) {
                return true;
            }
            if (config.getPlayerLandAllocation().getBorder().isCreateCeiling() && pos.getY() == bounds.getMaxY()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPistonAllowed(Level level, BlockPos pos, net.minecraft.core.Direction direction) {
        String dimension = level.dimension().location().toString();
        BlockPos dest = pos.relative(direction);

        if (isBoundaryBlock(dimension, pos) || isBoundaryBlock(dimension, dest)) {
            return false;
        }

        RegionCache cache = getRegionCache();
        if (cache == null) return true;

        var regionsSource = cache.getRegionsAt(dimension, pos.getX(), pos.getY(), pos.getZ());
        var regionsDest = cache.getRegionsAt(dimension, dest.getX(), dest.getY(), dest.getZ());

        if (regionsSource.isEmpty() && !regionsDest.isEmpty()) {
            return false;
        }
        if (!regionsSource.isEmpty() && regionsDest.isEmpty()) {
            return false;
        }
        if (!regionsSource.isEmpty() && !regionsDest.isEmpty()) {
            boolean matches = false;
            for (Region rSource : regionsSource) {
                for (Region rDest : regionsDest) {
                    if (rSource.getId().equals(rDest.getId())) {
                        matches = true;
                        break;
                    }
                }
            }
            if (!matches) {
                return false;
            }
        }
        return true;
    }
}
