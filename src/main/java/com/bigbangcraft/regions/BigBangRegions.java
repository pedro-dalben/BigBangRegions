package com.bigbangcraft.regions;

import com.bigbangcraft.regions.api.BigBangRegionsApi;
import com.bigbangcraft.regions.api.BigBangRegionsApiImpl;
import com.bigbangcraft.regions.audit.AuditService;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.command.RegionsCommand;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.flag.FlagResolver;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.protection.*;
import com.bigbangcraft.regions.region.RegionResolver;
import com.bigbangcraft.regions.repository.AuditRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.storage.DatabaseManager;
import com.bigbangcraft.regions.util.MessageHelper;
import com.bigbangcraft.regions.util.SelectionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

public class BigBangRegions implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions");

    private static BigBangRegionsApi api;
    private static ProtectionService protectionService;
    private static ConfigManager configManager;
    private static DatabaseManager databaseManager;
    private static RegionRepository regionRepository;
    private static AuditService auditService;
    private static SelectionManager selectionManager;

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
        RegionCache regionCache = new RegionCache();
        RegionResolver regionResolver = new RegionResolver(regionCache);
        FlagResolver flagResolver = new FlagResolver();

        // Load all regions into cache
        List<Region> regions = regionRepository.loadAll();
        for (Region r : regions) {
            regionCache.add(r);
        }
        LOGGER.info("Loaded {} regions into cache.", regions.size());

        // 5. Services and Managers
        selectionManager = new SelectionManager();
        int operatorFallback = configManager.getConfig().getPermissions().getOperatorFallbackLevel();
        PermissionManager permissionManager = new PermissionManager(operatorFallback);
        protectionService = new ProtectionService(regionResolver, flagResolver, permissionManager, configManager);

        // 6. Public API
        api = new BigBangRegionsApiImpl(regionResolver, protectionService);

        // 7. Command registration
        RegionsCommand.initialize(permissionManager, selectionManager, regionCache, regionRepository, regionResolver, auditService, configManager);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            RegionsCommand.register(dispatcher);
        });

        // 8. Register Event Listeners
        registerListeners();

        // 9. Clean shutdown handler
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

    private void registerListeners() {
        // Block break
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                return handlePlayerAction(serverPlayer, pos, RegionAction.BLOCK_BREAK);
            }
            return true;
        });

        // Block use & place & interactions
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();

            // 1. Check Container
            BlockEntity be = world.getBlockEntity(pos);
            if (be != null) {
                if (be instanceof net.minecraft.world.Container || be instanceof net.minecraft.world.MenuProvider) {
                    if (!handlePlayerAction(serverPlayer, pos, RegionAction.CONTAINER)) {
                        return InteractionResult.FAIL;
                    }
                }
            }

            // 2. Check Doors / Trapdoors / Gates
            if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
                if (!handlePlayerAction(serverPlayer, pos, RegionAction.DOOR)) {
                    return InteractionResult.FAIL;
                }
            }

            // 3. Check Redstone interactables (Buttons, Levers)
            if (block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof DiodeBlock) {
                if (!handlePlayerAction(serverPlayer, pos, RegionAction.REDSTONE)) {
                    return InteractionResult.FAIL;
                }
            }

            // 4. Check if they are placing blocks/buckets (BLOCK_PLACE)
            ItemStack heldItem = player.getItemInHand(hand);
            if (!heldItem.isEmpty()) {
                if (heldItem.getItem() instanceof BlockItem || heldItem.getItem() instanceof BucketItem) {
                    BlockPos placePos = pos.relative(hitResult.getDirection());
                    if (!handlePlayerAction(serverPlayer, placePos, RegionAction.BLOCK_PLACE)) {
                        return InteractionResult.FAIL;
                    }
                }
            }

            // 5. Fallback interaction check
            if (!handlePlayerAction(serverPlayer, pos, RegionAction.INTERACT)) {
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
}
