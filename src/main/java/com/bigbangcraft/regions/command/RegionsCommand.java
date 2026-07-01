package com.bigbangcraft.regions.command;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.audit.AuditService;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.allocation.BiomeOption;
import com.bigbangcraft.regions.event.RegionChangeEvent;
import com.bigbangcraft.regions.event.RegionEventBus;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.flag.FlagPolicy;
import com.bigbangcraft.regions.flag.FlagRegistry;
import com.bigbangcraft.regions.expansion.RegionExpansionCoordinator;
import com.bigbangcraft.regions.expansion.RegionExpansionOperation;
import com.bigbangcraft.regions.flag.RegionFlag;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.region.RegionResolver;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.util.SelectionManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.*;

public class RegionsCommand {
    private static PermissionManager permissionManager;
    private static SelectionManager selectionManager;
    private static RegionCache regionCache;
    private static RegionRepository regionRepository;
    private static RegionResolver regionResolver;
    private static AuditService auditService;
    private static ConfigManager configManager;

    private static boolean isCommandEnabled(String name) {
        return configManager != null && !configManager.getConfig().isCommandDisabled(name);
    }

    public static void initialize(PermissionManager pm, SelectionManager sm, RegionCache rc,
                                   RegionRepository repo, RegionResolver rr, AuditService as, ConfigManager cm) {
        permissionManager = pm;
        selectionManager = sm;
        regionCache = rc;
        regionRepository = repo;
        regionResolver = rr;
        auditService = as;
        configManager = cm;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("regions")
            .then(Commands.literal("pos1").executes(context -> setPos(context, true)))
            .then(Commands.literal("pos2").executes(context -> setPos(context, false)))
            .then(Commands.literal("create")
                .requires(source -> checkPermission(source, "bigbangregions.admin.create"))
                .then(Commands.literal("admin")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> createAdmin(context, 1000))
                        .then(Commands.argument("priority", IntegerArgumentType.integer())
                            .executes(context -> createAdmin(context, IntegerArgumentType.getInteger(context, "priority")))
                        )
                    )
                )
                .then(Commands.literal("player")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("owner", StringArgumentType.word())
                            .executes(context -> createPlayerRegion(context, configManager.getConfig().getDefaultPriorities().getPlayerRegion()))
                            .then(Commands.argument("priority", IntegerArgumentType.integer())
                                .executes(context -> createPlayerRegion(context, IntegerArgumentType.getInteger(context, "priority")))
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("player")
                .then(Commands.literal("owner")
                    .then(Commands.argument("regionId", StringArgumentType.word())
                        .executes(RegionsCommand::showAdminOwner)
                    )
                )
                .then(Commands.literal("members")
                    .then(Commands.argument("regionId", StringArgumentType.word())
                        .executes(RegionsCommand::listAdminMembers)
                    )
                )
                .then(Commands.literal("addmember")
                    .then(Commands.argument("regionId", StringArgumentType.word())
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(context -> addAdminMember(context, RegionRole.MEMBER))
                        )
                    )
                )
                .then(Commands.literal("removemember")
                    .then(Commands.argument("regionId", StringArgumentType.word())
                        .then(Commands.argument("player", StringArgumentType.word())
                            .executes(RegionsCommand::removeAdminMember)
                        )
                    )
                )
                .then(Commands.literal("recycle")
                    .then(Commands.argument("slotId", StringArgumentType.word())
                        .executes(RegionsCommand::adminRecycleSlot)
                    )
                )
                .then(Commands.literal("setrole")
                    .then(Commands.argument("regionId", StringArgumentType.word())
                        .then(Commands.argument("player", StringArgumentType.word())
                            .then(Commands.argument("role", StringArgumentType.word())
                                .executes(RegionsCommand::setAdminRole)
                            )
                        )
                    )
                )
                .then(Commands.literal("allocate")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("bioma", StringArgumentType.greedyString())
                            .executes(RegionsCommand::adminAllocate)
                        )
                    )
                )
                .then(Commands.literal("allocation")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(RegionsCommand::adminAllocationStatus)
                        .then(Commands.literal("cancel")
                            .executes(RegionsCommand::adminCancelAllocation)
                        )
                    )
                )
            )
            .then(Commands.literal("admin")
                .requires(source -> checkPermission(source, "bigbangregions.admin.create"))
                .then(Commands.literal("create")
                    .then(Commands.argument("sizeX", IntegerArgumentType.integer(1))
                        .then(Commands.argument("sizeZ", IntegerArgumentType.integer(1))
                            .executes(context -> createAdminCentered(context,
                                IntegerArgumentType.getInteger(context, "sizeX"),
                                IntegerArgumentType.getInteger(context, "sizeZ"),
                                null,
                                configManager.getConfig().getDefaultPriorities().getAdminRegion()))
                            .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> createAdminCentered(context,
                                    IntegerArgumentType.getInteger(context, "sizeX"),
                                    IntegerArgumentType.getInteger(context, "sizeZ"),
                                    StringArgumentType.getString(context, "id"),
                                    configManager.getConfig().getDefaultPriorities().getAdminRegion()))
                                .then(Commands.argument("priority", IntegerArgumentType.integer())
                                    .executes(context -> createAdminCentered(context,
                                        IntegerArgumentType.getInteger(context, "sizeX"),
                                        IntegerArgumentType.getInteger(context, "sizeZ"),
                                        StringArgumentType.getString(context, "id"),
                                        IntegerArgumentType.getInteger(context, "priority")))
                                )
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("delete")
                .then(Commands.argument("target", StringArgumentType.word())
                    .executes(RegionsCommand::deleteRegion)
                )
            )
            .then(Commands.literal("rename")
                .requires(source -> checkPermission(source, "bigbangregions.admin.create"))
                .then(Commands.argument("newName", StringArgumentType.greedyString())
                    .executes(RegionsCommand::renameCurrentRegion)
                )
            )
            .then(Commands.literal("info").executes(RegionsCommand::showInfo))
            .then(Commands.literal("list")
                .executes(context -> listRegions(context, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> listRegions(context, IntegerArgumentType.getInteger(context, "page")))
                )
            )
            .then(Commands.literal("flag")
                .then(Commands.literal("set")
                    .then(Commands.argument("regionId", StringArgumentType.word())
                        .then(Commands.argument("flag", StringArgumentType.word())
                            .then(Commands.argument("value", StringArgumentType.word())
                                .executes(RegionsCommand::setFlag)
                            )
                        )
                    )
                )
                .then(Commands.literal("get")
                    .then(Commands.argument("regionId", StringArgumentType.word())
                        .then(Commands.argument("flag", StringArgumentType.word())
                            .executes(RegionsCommand::getFlag)
                        )
                    )
                )
            )
            .then(Commands.literal("flags")
                .then(Commands.literal("listar")
                    .executes(RegionsCommand::listPlayerFlags)
                )
                .then(Commands.literal("ver")
                    .then(Commands.argument("flag", StringArgumentType.word())
                        .executes(RegionsCommand::verPlayerFlag)
                    )
                )
                .then(Commands.literal("definir")
                    .then(Commands.argument("flag", StringArgumentType.word())
                        .then(Commands.argument("value", StringArgumentType.word())
                            .executes(RegionsCommand::definirPlayerFlag)
                        )
                    )
                )
                .then(Commands.argument("regionId", StringArgumentType.word())
                    .executes(RegionsCommand::listFlags)
                )
            )
            .then(Commands.literal("membros")
                .then(Commands.literal("listar")
                    .executes(RegionsCommand::listPlayerMembers)
                )
                .then(Commands.literal("adicionar")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(RegionsCommand::addPlayerMember)
                    )
                )
                .then(Commands.literal("remover")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(RegionsCommand::removePlayerMember)
                    )
                )
                .then(Commands.literal("promover")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(RegionsCommand::promotePlayerMember)
                    )
                )
                .then(Commands.literal("rebaixar")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(RegionsCommand::demotePlayerMember)
                    )
                )
            )
            .then(Commands.literal("sair")
                .executes(RegionsCommand::leavePlayerRegion)
            )
            .then(Commands.literal("reload").executes(RegionsCommand::reloadMod));

        if (isCommandEnabled("casa")) {
            builder.then(Commands.literal("casa")
                .executes(RegionsCommand::teleportHome)
            );
        }
        if (isCommandEnabled("sethome")) {
            builder.then(Commands.literal("sethome")
                .executes(RegionsCommand::setHome)
            );
        }
        if (isCommandEnabled("biomas")) {
            builder.then(Commands.literal("biomas")
                .executes(RegionsCommand::listBiomes)
            );
        }
        if (isCommandEnabled("criar")) {
            builder.then(Commands.literal("criar")
                .requires(source -> checkPermission(source, "bigbangregions.admin.create"))
                .then(Commands.argument("bioma", StringArgumentType.greedyString())
                    .executes(RegionsCommand::createPlayerAllocation)
                )
                .then(Commands.literal("status")
                    .executes(RegionsCommand::allocationStatus)
                )
                .then(Commands.literal("cancelar")
                    .executes(RegionsCommand::cancelAllocation)
                )
            );
        }
        if (isCommandEnabled("limites")) {
            builder.then(Commands.literal("limites")
                .executes(RegionsCommand::toggleBoundaries)
                .then(Commands.literal("on")
                    .executes(context -> setBoundaries(context, true))
                )
                .then(Commands.literal("off")
                    .executes(context -> setBoundaries(context, false))
                )
            );
        }
        if (isCommandEnabled("explorar")) {
            builder.then(Commands.literal("explorar")
                .executes(RegionsCommand::teleportToExplorationZone)
            );
        }
        if (isCommandEnabled("expandir")) {
            builder.then(Commands.literal("expandir")
                .then(Commands.argument("tamanho", IntegerArgumentType.integer(1, 256))
                    .executes(RegionsCommand::beginExpansion)
                )
                .then(Commands.literal("status")
                    .executes(RegionsCommand::expansionStatus)
                )
                .then(Commands.literal("cancelar")
                    .executes(RegionsCommand::cancelExpansion)
                )
            );
        }
        if (isCommandEnabled("expansion")) {
            builder.then(Commands.literal("expansion")
                .then(Commands.literal("list")
                    .executes(RegionsCommand::adminExpansionList)
                )
                .then(Commands.literal("inspect")
                    .then(Commands.argument("operationId", StringArgumentType.word())
                        .executes(RegionsCommand::adminExpansionInspect)
                    )
                )
                .then(Commands.literal("retry")
                    .then(Commands.argument("operationId", StringArgumentType.word())
                        .executes(RegionsCommand::adminExpansionRetry)
                    )
                )
                .then(Commands.literal("reconcile")
                    .executes(RegionsCommand::adminExpansionReconcile)
                )
                .then(Commands.literal("block")
                    .then(Commands.argument("operationId", StringArgumentType.word())
                        .then(Commands.literal("confirm")
                            .executes(RegionsCommand::adminExpansionBlock)
                        )
                    )
                )
            );
        }
        if (isCommandEnabled("mapa")) {
            builder.then(Commands.literal("mapa")
                .executes(RegionsCommand::showMapVisibility)
                .then(Commands.literal("publico")
                    .executes(context -> setMapVisibility(context, "PUBLIC"))
                )
                .then(Commands.literal("privado")
                    .executes(context -> setMapVisibility(context, "PRIVATE"))
                )
                .then(Commands.literal("membros")
                    .executes(context -> setMapVisibility(context, "MEMBERS"))
                )
            );
        }

        LiteralCommandNode<CommandSourceStack> mainNode = dispatcher.register(builder);
        dispatcher.register(Commands.literal("regiao").redirect(mainNode));
        dispatcher.register(Commands.literal("regioes").redirect(mainNode));

        dispatcher.register(Commands.literal("region")
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                ServerPlayer player = source.getPlayer();
                if (player == null) {
                    source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
                    return 0;
                }
                com.bigbangcraft.regions.gui.RegionGuiHandler.openMenu(player);
                return 1;
            })
        );

        dispatcher.register(Commands.literal("regionadmin")
            .requires(source -> checkPermission(source, "bigbangregions.admin.panel"))
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                ServerPlayer player = source.getPlayer();
                if (player == null) {
                    source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
                    return 0;
                }

                Region current = com.bigbangcraft.regions.gui.RegionGuiHandler.findPlayerRegion(player.getUUID());

                if (current == null) {
                    BlockPos pos = player.blockPosition();
                    String dim = player.level().dimension().location().toString();
                    current = regionResolver.resolveRegionAt(dim, pos.getX(), pos.getY(), pos.getZ()).orElse(null);
                }

                if (current == null) {
                    source.sendFailure(Component.literal("Nenhuma região encontrada na sua posição atual para abrir o painel."));
                    return 0;
                }

                com.bigbangcraft.regions.gui.RegionGuiHandler.openAdminMenu(player, current);
                return 1;
            })
        );
    }

    private static boolean checkPermission(CommandSourceStack source, String permission) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            // Console bypass
            return true;
        }
        return permissionManager.hasPermission(player, permission);
    }

    private static int setPos(CommandContext<CommandSourceStack> context, boolean isPos1) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem definir posições."));
            return 0;
        }

        if (!checkPermission(source, "bigbangregions.admin.create") && !checkPermission(source, "bigbangregions.admin.edit")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }

        BlockPos pos = player.blockPosition();
        UUID uuid = player.getUUID();
        String dimension = player.level().dimension().location().toString();

        if (isPos1) {
            selectionManager.setPos1(uuid, pos, dimension);
            source.sendSuccess(() -> Component.literal("Posição 1 definida para " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " (" + dimension + ").").withStyle(ChatFormatting.GREEN), false);
        } else {
            selectionManager.setPos2(uuid, pos, dimension);
            source.sendSuccess(() -> Component.literal("Posição 2 definida para " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " (" + dimension + ").").withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    static int createAdmin(CommandContext<CommandSourceStack> context, int priority) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem criar regiões administrativamente."));
            return 0;
        }

        if (!checkPermission(source, "bigbangregions.admin.create")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }

        UUID uuid = player.getUUID();
        SelectionManager.Selection p1 = selectionManager.getPos1(uuid);
        SelectionManager.Selection p2 = selectionManager.getPos2(uuid);

        if (p1 == null || p2 == null) {
            source.sendFailure(Component.literal("Defina pos1 e pos2 primeiro."));
            return 0;
        }

        if (!p1.getDimension().equals(p2.getDimension())) {
            source.sendFailure(Component.literal("A Posição 1 e a Posição 2 devem estar na mesma dimensão."));
            return 0;
        }

        String id = StringArgumentType.getString(context, "id");
        if (regionCache.get(id) != null) {
            source.sendFailure(Component.literal("Uma região com ID '" + id + "' já existe."));
            return 0;
        }

        String dimension = p1.getDimension();
        BlockPos pos1 = p1.getPos();
        BlockPos pos2 = p2.getPos();
        RegionBounds bounds = new RegionBounds(dimension, pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ());

        // Check overlap respecting region type rules
        if (checkPlayerRegionOverlap(bounds, id)) {
            source.sendFailure(Component.literal("A região se sobrepõe com uma região existente e foi bloqueada pela configuração."));
            return 0;
        }

        long now = System.currentTimeMillis();
        Region region = new Region(id, id, RegionType.ADMIN_REGION, bounds, priority, null, uuid, now, now, "ACTIVE");
        
        regionRepository.save(region);
        regionCache.add(region);
        auditService.log(id, uuid, "CREATE_REGION", null, "ADMIN_REGION", null);

        source.sendSuccess(() -> Component.literal("Região administrativa '" + id + "' criada com sucesso! Prioridade: " + priority).withStyle(ChatFormatting.GREEN), false);
        selectionManager.clear(uuid);
        return 1;
    }

    static int createAdminCentered(CommandContext<CommandSourceStack> context, int sizeX, int sizeZ, String id, int priority) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }

        if (!checkPermission(source, "bigbangregions.admin.create")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }

        UUID uuid = player.getUUID();
        BlockPos center = player.blockPosition();
        String dimension = player.level().dimension().location().toString();

        String regionId = id;
        if (regionId == null) {
            regionId = "admin_" + System.currentTimeMillis();
        }

        if (regionCache.get(regionId) != null) {
            source.sendFailure(Component.literal("Uma região com ID '" + regionId + "' já existe."));
            return 0;
        }

        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;
        RegionBounds bounds = new RegionBounds(dimension,
            center.getX() - halfX, 0, center.getZ() - halfZ,
            center.getX() + halfX, 255, center.getZ() + halfZ);

        if (checkPlayerRegionOverlap(bounds, regionId)) {
            source.sendFailure(Component.literal("A região se sobrepõe com uma região existente e foi bloqueada pela configuração."));
            return 0;
        }

        long now = System.currentTimeMillis();
        Region region = new Region(regionId, regionId, RegionType.ADMIN_REGION, bounds, priority, null, uuid, now, now, "ACTIVE");

        regionRepository.save(region);
        regionCache.add(region);
        auditService.log(regionId, uuid, "CREATE_REGION", null, "ADMIN_REGION", null);

        String finalRegionId = regionId;
        source.sendSuccess(() -> Component.literal("Região administrativa '" + finalRegionId + "' criada com sucesso! Tamanho: " + sizeX + "x" + sizeZ + ", Prioridade: " + priority).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int deleteRegion(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.delete")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }

        String target = StringArgumentType.getString(context, "target");
        List<Region> targets = resolveDeleteTargets(source, target);
        if (targets.isEmpty()) {
            source.sendFailure(Component.literal("Nenhuma região encontrada para '" + target + "'."));
            return 0;
        }

        int deleted = 0;
        for (Region region : targets) {
            if (region.getType() == RegionType.PLAYER_REGION) {
                ResourceKey<net.minecraft.world.level.Level> dimensionKey = ResourceKey.create(
                    Registries.DIMENSION,
                    ResourceLocation.parse(region.getBounds().getDimension())
                );
                net.minecraft.server.level.ServerLevel level = source.getServer().getLevel(dimensionKey);
                if (level != null) {
                    BigBangRegions.getAllocationCoordinator().restorePlayerRegionTerrain(region, level);
                }
            }

            regionRepository.delete(region.getId());
            RegionEventBus.fire(new RegionChangeEvent(RegionChangeEvent.ChangeType.DELETED, region));
            regionCache.remove(region.getId());
            BigBangRegions.getMembershipCache().removeRegion(region.getId());

            if (region.getType() == RegionType.PLAYER_REGION) {
                releaseSlotForRegion(region.getId());
            }

            UUID actorUuid = source.getPlayer() != null ? source.getPlayer().getUUID() : null;
            auditService.log(region.getId(), actorUuid, "DELETE_REGION", region.getType().name(), null, null);
            deleted++;
        }

        if (deleted == 1) {
            source.sendSuccess(() -> Component.literal("Região deletada com sucesso.").withStyle(ChatFormatting.GREEN), false);
        } else {
            int deletedCount = deleted;
            source.sendSuccess(() -> Component.literal(deletedCount + " regiões deletadas com sucesso.").withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    private static List<Region> resolveDeleteTargets(CommandSourceStack source, String target) {
        Region exact = regionCache.get(target);
        if (exact != null) {
            return List.of(exact);
        }

        GameProfile profile = source.getServer().getProfileCache().get(target).orElse(null);
        if (profile == null) {
            return List.of();
        }

        UUID ownerUuid = profile.getId();
        return regionCache.getAll().stream()
            .filter(r -> r.getType() == RegionType.PLAYER_REGION && ownerUuid.equals(r.getOwnerUuid()))
            .toList();
    }

    private static void releaseSlotForRegion(String regionId) {
        BigBangRegions.getAllocationCoordinator().releaseSlotForRegion(regionId);
    }

    private static int renameCurrentRegion(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }

        String newName = StringArgumentType.getString(context, "newName");

        BlockPos pos = player.blockPosition();
        String dim = player.level().dimension().location().toString();
        Region region = regionResolver.resolveRegionAt(dim, pos.getX(), pos.getY(), pos.getZ()).orElse(null);
        if (region == null) {
            source.sendFailure(Component.literal("Nenhuma região encontrada na sua posição atual."));
            return 0;
        }

        String id = region.getId();
        region.setName(newName);
        regionRepository.save(region);

        UUID actorUuid = player.getUUID();
        auditService.log(id, actorUuid, "RENAME_REGION", region.getType().name(), null, "newName=" + newName);

        source.sendSuccess(() -> Component.literal("Região §e" + id + "§r renomeada para §e" + newName + "§r com sucesso.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        BlockPos pos;
        String dimension;
        if (player != null) {
            pos = player.blockPosition();
            dimension = player.level().dimension().location().toString();
        } else {
            source.sendFailure(Component.literal("Console deve especificar uma posição para inspecionar (não suportado)."));
            return 0;
        }

        Optional<Region> optRegion = regionResolver.resolveRegionAt(dimension, pos.getX(), pos.getY(), pos.getZ());
        if (optRegion.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nenhuma região nesta posição.").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        Region region = optRegion.get();
        UUID playerUuid = player.getUUID();
        RegionRole role = BigBangRegions.getRoleResolver().resolveRole(region, playerUuid);
        boolean hasInspectPerm = permissionManager.hasPermission(player, "bigbangregions.inspect");

        // "visitante deve receber apenas informações públicas;
        // owner e leader podem receber dados completos;
        // admin com permissão inspect recebe dados completos."
        boolean showComplete = hasInspectPerm || role == RegionRole.OWNER || role == RegionRole.LEADER;

        source.sendSuccess(() -> Component.literal("=== Informações da Região ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("ID: " + region.getId()).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Tipo: " + region.getType()).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Dono (OWNER): " + getPlayerName(source, region.getOwnerUuid())).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Dimensão: " + region.getBounds().getDimension()).withStyle(ChatFormatting.YELLOW), false);

        if (region.getType() == RegionType.PLAYER_REGION) {
            source.sendSuccess(() -> Component.literal("Seu Papel: " + role.name()).withStyle(ChatFormatting.YELLOW), false);
        }

        if (showComplete) {
            source.sendSuccess(() -> Component.literal("Prioridade: " + region.getPriority()).withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal(String.format("Limites: (%d, %d, %d) a (%d, %d, %d)",
                    region.getBounds().getMinX(), region.getBounds().getMinY(), region.getBounds().getMinZ(),
                    region.getBounds().getMaxX(), region.getBounds().getMaxY(), region.getBounds().getMaxZ())).withStyle(ChatFormatting.YELLOW), false);
            
            // Show members
            if (region.getType() == RegionType.PLAYER_REGION && !region.getMembers().isEmpty()) {
                source.sendSuccess(() -> Component.literal("Membros:").withStyle(ChatFormatting.GOLD), false);
                for (RegionMember member : region.getMembers().values()) {
                    String name = getPlayerName(source, member.getUuid());
                    source.sendSuccess(() -> Component.literal(" - " + name + " (" + member.getRole().name() + ")").withStyle(ChatFormatting.YELLOW), false);
                }
            }

            // Show flags
            Map<String, String> flags = region.getFlags();
            if (!flags.isEmpty()) {
                source.sendSuccess(() -> Component.literal("Flags:").withStyle(ChatFormatting.GOLD), false);
                for (Map.Entry<String, String> entry : flags.entrySet()) {
                    source.sendSuccess(() -> Component.literal(" - " + entry.getKey() + ": " + entry.getValue()).withStyle(ChatFormatting.YELLOW), false);
                }
            }
        }

        // Show overlapping regions for inspectors
        if (hasInspectPerm) {
            List<Region> allAtPos = regionCache.getRegionsAt(dimension, pos.getX(), pos.getY(), pos.getZ());
            if (allAtPos.size() > 1) {
                source.sendSuccess(() -> Component.literal("Outras regiões sobrepostas neste ponto:").withStyle(ChatFormatting.GRAY), false);
                for (Region r : allAtPos) {
                    if (!r.getId().equals(region.getId())) {
                        source.sendSuccess(() -> Component.literal(" - " + r.getId() + " (Prioridade: " + r.getPriority() + ")").withStyle(ChatFormatting.GRAY), false);
                    }
                }
            }
        }

        return 1;
    }

    private static int listRegions(CommandContext<CommandSourceStack> context, int page) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.list")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }

        Collection<Region> all = regionCache.getAll();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nenhuma região registrada.").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        List<Region> list = new ArrayList<>(all);
        list.sort(RegionResolver.REGION_PRIORITY_COMPARATOR);

        int itemsPerPage = 10;
        int maxPage = (int) Math.ceil((double) list.size() / itemsPerPage);
        if (page > maxPage) page = maxPage;
        if (page < 1) page = 1;

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, list.size());

        final int finalPage = page;
        final int finalMaxPage = maxPage;
        source.sendSuccess(() -> Component.literal(String.format("=== Regiões (%d/%d) ===", finalPage, finalMaxPage)).withStyle(ChatFormatting.GOLD), false);
        for (int i = startIndex; i < endIndex; i++) {
            Region r = list.get(i);
            source.sendSuccess(() -> Component.literal(String.format(" - %s [%s] (Pri: %d)", r.getId(), r.getType().name(), r.getPriority())).withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int setFlag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.flags") && !checkPermission(source, "bigbangregions.admin.edit")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }

        String regionId = StringArgumentType.getString(context, "regionId");
        Region region = regionCache.get(regionId);
        if (region == null) {
            source.sendFailure(Component.literal("Região '" + regionId + "' não encontrada."));
            return 0;
        }

        String flagStr = StringArgumentType.getString(context, "flag").toLowerCase();
        Optional<RegionFlag> optFlag = FlagRegistry.get(flagStr);
        if (optFlag.isEmpty()) {
            source.sendFailure(Component.literal("Flag '" + flagStr + "' não está registrada."));
            return 0;
        }

        RegionFlag flag = optFlag.get();
        String valueStr = StringArgumentType.getString(context, "value").toUpperCase();
        
        FlagPolicy policy;
        try {
            policy = FlagPolicy.valueOf(valueStr);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Valor inválido: " + valueStr + ". Use: ALLOW, DENY, INHERIT"));
            return 0;
        }

        String before = region.getFlagValue(flag.getId());
        region.setFlag(flag.getId(), policy.name());
        regionRepository.updateFlags(region);

        UUID actorUuid = source.getPlayer() != null ? source.getPlayer().getUUID() : null;
        auditService.log(regionId, actorUuid, "SET_FLAG", flag.getId() + "=" + before, flag.getId() + "=" + policy.name(), null);

        source.sendSuccess(() -> Component.literal("Flag '" + flag.getId() + "' definida para " + policy + " na região '" + regionId + "'.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int getFlag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.flags") && !checkPermission(source, "bigbangregions.inspect")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }

        String regionId = StringArgumentType.getString(context, "regionId");
        Region region = regionCache.get(regionId);
        if (region == null) {
            source.sendFailure(Component.literal("Região '" + regionId + "' não encontrada."));
            return 0;
        }

        String flagStr = StringArgumentType.getString(context, "flag").toLowerCase();
        Optional<RegionFlag> optFlag = FlagRegistry.get(flagStr);
        if (optFlag.isEmpty()) {
            source.sendFailure(Component.literal("Flag '" + flagStr + "' não está registrada."));
            return 0;
        }

        RegionFlag flag = optFlag.get();
        String val = region.getFlagValue(flag.getId());
        
        source.sendSuccess(() -> Component.literal("Região: " + regionId).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Flag: " + flag.getId() + " (" + (flag.isSupported() ? "Suportada" : "Planejada") + ")").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Valor: " + val).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int listFlags(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.flags") && !checkPermission(source, "bigbangregions.inspect")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }

        String regionId = StringArgumentType.getString(context, "regionId");
        Region region = regionCache.get(regionId);
        if (region == null) {
            source.sendFailure(Component.literal("Região '" + regionId + "' não encontrada."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== Flags de '" + regionId + "' ===").withStyle(ChatFormatting.GOLD), false);
        for (RegionFlag flag : FlagRegistry.getAll()) {
            String val = region.getFlagValue(flag.getId());
            if (!val.equalsIgnoreCase("INHERIT")) {
                source.sendSuccess(() -> Component.literal(String.format(" - %s: %s (%s)", flag.getId(), val, flag.isSupported() ? "✓" : "⚡")).withStyle(ChatFormatting.YELLOW), false);
            }
        }
        return 1;
    }

    private static int reloadMod(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.reload")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }

        try {
            configManager.load();
            regionCache.clear();
            BigBangRegions.getMembershipCache().clear();
            List<Region> reloaded = regionRepository.loadAll();
            for (Region region : reloaded) {
                regionCache.add(region);
                BigBangRegions.getMembershipCache().loadFromRegion(region);
            }

            UUID actorUuid = source.getPlayer() != null ? source.getPlayer().getUUID() : null;
            auditService.log(null, actorUuid, "RELOAD", null, null, null);

            source.sendSuccess(() -> Component.literal("Configuração e regiões recarregadas com sucesso.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Erro ao recarregar o mod. Veja os logs para mais detalhes."));
            return 0;
        }
    }

    private static int listBiomes(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            var options = BigBangRegions.getAllocationCoordinator().getBiomeOptions();
            if (options.isEmpty()) {
                source.sendSuccess(() -> Component.literal("Nenhuma opção de bioma disponível.").withStyle(ChatFormatting.YELLOW), false);
                return 1;
            }
            StringBuilder sb = new StringBuilder("§6Opções de biomas disponíveis:\n");
            for (var opt : options) {
                sb.append("§e- ").append(opt.getKey()).append(" §7(").append(opt.getDisplayName()).append(")\n");
            }
            String result = sb.toString();
            source.sendSuccess(() -> Component.literal(result), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Erro ao listar biomas: " + e.getMessage()));
            return 0;
        }
    }

    private static int createPlayerAllocation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        if (!checkPermission(source, "bigbangregions.player.create")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        String biome = StringArgumentType.getString(context, "bioma");
        try {
            String requestId = BigBangRegions.getAllocationCoordinator().createRequest(player, biome, "player_command");
            source.sendSuccess(() -> Component.literal("§aPedido de alocação criado! ID: " + requestId)
                .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static int teleportHome(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        if (!checkPermission(source, "bigbangregions.player.home")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        try {
            boolean success = BigBangRegions.getAllocationCoordinator().teleportToHome(player);
            if (success) {
                source.sendSuccess(() -> Component.literal("§aTeleportado para sua casa na região!").withStyle(ChatFormatting.GREEN), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static int setHome(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        if (!checkPermission(source, "bigbangregions.player.home")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        try {
            boolean success = BigBangRegions.getAllocationCoordinator().setHome(player);
            if (success) {
                source.sendSuccess(() -> Component.literal("§aCasa definida na sua posicao atual!").withStyle(ChatFormatting.GREEN), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static int allocationStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        if (!checkPermission(source, "bigbangregions.player.create")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        try {
            var request = BigBangRegions.getAllocationCoordinator().getActiveRequest(player.getUUID());
            long creationCooldown = BigBangRegions.getAllocationCoordinator().getCreationCooldownRemaining(player.getUUID());
            long homeCooldown = BigBangRegions.getAllocationCoordinator().getHomeTeleportCooldownRemaining(player.getUUID());
            if (request == null) {
                source.sendSuccess(() -> Component.literal("§eVoce nao possui um pedido de alocacao ativo."), false);
            } else {
                source.sendSuccess(() -> Component.literal("§6Pedido de alocacao:\n" +
                    "§eID: §f" + request.getId() + "\n" +
                    "§eEstado: §f" + request.getState() + "\n" +
                    "§eBioma: §f" + request.getRequestedBiomeOption() + "\n" +
                    (request.getFailureReason() != null ? "§eMotivo: §c" + request.getFailureReason() + "\n" : "") +
                    "§eCriado em: §f" + new java.util.Date(request.getCreatedAt())), false);
            }
            if (creationCooldown > 0) {
                source.sendSuccess(() -> Component.literal("§eCooldown de criacao: §f" + creationCooldown + "s"), false);
            }
            if (homeCooldown > 0) {
                source.sendSuccess(() -> Component.literal("§eCooldown de /casa: §f" + homeCooldown + "s"), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static int cancelAllocation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        if (!checkPermission(source, "bigbangregions.player.create")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        try {
            BigBangRegions.getAllocationCoordinator().cancelRequest(player.getUUID());
            source.sendSuccess(() -> Component.literal("§aPedido de alocacao cancelado!").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static int toggleBoundaries(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        if (!checkPermission(source, "bigbangregions.player.boundaries")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        UUID uuid = player.getUUID();
        boolean newState = !BigBangRegions.getBoundaryRenderer().isVisibilityEnabled(uuid);
        BigBangRegions.getBoundaryRenderer().setVisibility(uuid, newState);
        if (newState) {
            source.sendSuccess(() -> Component.literal("§aLimites visiveis ativados!").withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("§eLimites visiveis desativados.").withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int setBoundaries(CommandContext<CommandSourceStack> context, boolean enabled) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        if (!checkPermission(source, "bigbangregions.player.boundaries")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        BigBangRegions.getBoundaryRenderer().setVisibility(player.getUUID(), enabled);
        if (enabled) {
            source.sendSuccess(() -> Component.literal("§aLimites visiveis ativados!").withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("§eLimites visiveis desativados.").withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int teleportToExplorationZone(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        if (!checkPermission(source, "bigbangregions.player.explore")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        try {
            BigBangRegions.getExplorationZoneService().teleportToExplorationZone(player);
            source.sendSuccess(() -> Component.literal("§aTeleportado para a zona de exploracao!").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static RegionExpansionCoordinator expansionCoordinator() {
        return BigBangRegions.getExpansionCoordinator();
    }

    private static int beginExpansion(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        if (!checkPermission(source, "bigbangregions.player.expand")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        int targetSize = IntegerArgumentType.getInteger(context, "tamanho");
        try {
            RegionExpansionOperation op = expansionCoordinator().beginExpansion(player, targetSize);
            source.sendSuccess(() -> Component.literal("§aExpansao iniciada para " + targetSize + "x" + targetSize
                + " blocos! Custo: " + op.getPriceGems() + " gems. ID: " + op.getOperationId())
                .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static int expansionStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        RegionExpansionOperation op = expansionCoordinator().getActiveExpansion(player.getUUID());
        if (op == null) {
            source.sendFailure(Component.literal("Voce nao possui uma operacao de expansao ativa."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§7Expansao " + op.getOperationId()
            + ": " + op.getCurrentSize() + "x" + op.getCurrentSize()
            + " → " + op.getTargetSize() + "x" + op.getTargetSize()
            + " (Estado: " + op.getState() + ", Custo: " + op.getPriceGems() + " gems)"), false);
        return 1;
    }

    private static int cancelExpansion(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        try {
            expansionCoordinator().cancelExpansion(player);
            source.sendSuccess(() -> Component.literal("§aExpansao cancelada.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static int adminExpansionList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.expansion")) {
            source.sendFailure(Component.literal("Sem permissao."));
            return 0;
        }
        List<RegionExpansionOperation> ops = expansionCoordinator().getActiveExpansions();
        if (ops.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nenhuma operacao de expansao ativa."), false);
            return 1;
        }
        for (RegionExpansionOperation op : ops) {
            source.sendSuccess(() -> Component.literal("§7" + op.getOperationId()
                + " | Regiao: " + op.getRegionId()
                + " | " + op.getCurrentSize() + "x" + op.getCurrentSize()
                + " → " + op.getTargetSize() + "x" + op.getTargetSize()
                + " | Estado: " + op.getState()
                + " | Gems: " + op.getPriceGems()), false);
        }
        return 1;
    }

    private static int adminExpansionInspect(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.expansion")) {
            source.sendFailure(Component.literal("Sem permissao."));
            return 0;
        }
        String operationId = StringArgumentType.getString(context, "operationId");
        RegionExpansionOperation op = expansionCoordinator().getExpansion(operationId);
        if (op == null) {
            source.sendFailure(Component.literal("Operacao nao encontrada: " + operationId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§7Operacao: " + op.getOperationId()
            + "\n  Regiao: " + op.getRegionId()
            + "\n  Owner: " + op.getOwnerUuid()
            + "\n  Tamanho: " + op.getCurrentSize() + "x" + op.getCurrentSize()
            + " → " + op.getTargetSize() + "x" + op.getTargetSize()
            + "\n  Bounds: (" + op.getOldMinX() + "," + op.getOldMinZ() + ") → ("
            + op.getTargetMinX() + "," + op.getTargetMinZ() + ")"
            + "\n  Estado: " + op.getState()
            + "\n  Gems: " + op.getPriceGems()
            + "\n  ReservationId: " + op.getGemsReservationId()
            + "\n  RetryCount: " + op.getRetryCount()
            + "\n  Falha: " + (op.getFailureCode() != null ? op.getFailureCode() + " - " + op.getFailureDetail() : "nenhuma")), false);
        return 1;
    }

    private static int adminExpansionRetry(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.expansion")) {
            source.sendFailure(Component.literal("Sem permissao."));
            return 0;
        }
        String operationId = StringArgumentType.getString(context, "operationId");
        try {
            expansionCoordinator().adminScheduleRetry(operationId);
            source.sendSuccess(() -> Component.literal("§aRetry agendado para operacao " + operationId + "."), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static int adminExpansionReconcile(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.expansion")) {
            source.sendFailure(Component.literal("Sem permissao."));
            return 0;
        }
        if (BigBangRegions.getExpansionRecoveryService() != null) {
            BigBangRegions.getExpansionRecoveryService().recover();
            source.sendSuccess(() -> Component.literal("§aReconciliacao de expansoes concluida."), false);
        } else {
            source.sendFailure(Component.literal("Servico de recovery indisponivel."));
        }
        return 1;
    }

    private static int adminExpansionBlock(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.expansion")) {
            source.sendFailure(Component.literal("Sem permissao."));
            return 0;
        }
        String operationId = StringArgumentType.getString(context, "operationId");
        try {
            expansionCoordinator().adminBlockOperation(operationId);
            source.sendSuccess(() -> Component.literal("§aOperacao " + operationId + " bloqueada para revisao administrativa."), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static int adminRecycleSlot(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.slot.recycle")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        String slotId = StringArgumentType.getString(context, "slotId");
        try {
            BigBangRegions.getAllocationCoordinator().recycleSlot(slotId);
            source.sendSuccess(() -> Component.literal("§aSlot " + slotId + " reciclado com sucesso!").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static int showMapVisibility(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        if (!checkPermission(source, "bigbangregions.player.mapvisibility")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        Region region = resolvePlayerRegion(player);
        if (region == null) {
            source.sendFailure(Component.literal("Voce nao possui ou lidera nenhuma regiao de jogador."));
            return 0;
        }
        String vis = region.getFlagValue("map-visibility");
        source.sendSuccess(() -> Component.literal("§6Visibilidade do mapa: §f" + vis).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("§7Opcoes: /regiao mapa publico | privado | membros"), false);
        return 1;
    }

    private static int setMapVisibility(CommandContext<CommandSourceStack> context, String visibility) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."));
            return 0;
        }
        if (!checkPermission(source, "bigbangregions.player.mapvisibility")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        Region region = resolvePlayerRegion(player);
        if (region == null) {
            source.sendFailure(Component.literal("Voce nao possui ou lidera nenhuma regiao de jogador."));
            return 0;
        }
        region.setFlag("map-visibility", visibility);
        regionRepository.updateFlags(region);
        source.sendSuccess(() -> Component.literal("§aVisibilidade do mapa alterada para: " + visibility).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int adminAllocate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.player.allocate")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        String playerName = StringArgumentType.getString(context, "player");
        String biome = StringArgumentType.getString(context, "bioma");
        try {
            var opt = lookupProfile(source, playerName);
            if (opt.isEmpty()) {
                source.sendFailure(Component.literal("Jogador não encontrado: " + playerName));
                return 0;
            }
            ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayer(opt.get().getId());
            if (targetPlayer == null) {
                source.sendFailure(Component.literal("Jogador '" + playerName + "' não está online."));
                return 0;
            }
            String requestId = BigBangRegions.getAllocationCoordinator().createRequest(targetPlayer, biome, "admin_command");
            source.sendSuccess(() -> Component.literal("§aPedido de alocação criado para " + playerName + "! ID: " + requestId)
                .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static int adminAllocationStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.player.allocation.inspect")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        String playerName = StringArgumentType.getString(context, "player");
        try {
            var opt = lookupProfile(source, playerName);
            if (opt.isEmpty()) {
                source.sendFailure(Component.literal("Jogador não encontrado: " + playerName));
                return 0;
            }
            var request = BigBangRegions.getAllocationCoordinator().getActiveRequest(opt.get().getId());
            if (request == null) {
                source.sendSuccess(() -> Component.literal("Jogador '" + playerName + "' não possui pedido de alocação ativo.")
                    .withStyle(ChatFormatting.YELLOW), false);
                return 1;
            }
            source.sendSuccess(() -> Component.literal("§6Pedido de alocação:\n" +
                "§eID: §f" + request.getId() + "\n" +
                "§eEstado: §f" + request.getState() + "\n" +
                "§eBioma: §f" + request.getRequestedBiomeOption() + "\n" +
                "§eTentativas: §f" + request.getAttempts() + "\n" +
                "§eCriado em: §f" + new java.util.Date(request.getCreatedAt())), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static int adminCancelAllocation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.player.allocation.cancel")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        String playerName = StringArgumentType.getString(context, "player");
        try {
            var opt = lookupProfile(source, playerName);
            if (opt.isEmpty()) {
                source.sendFailure(Component.literal("Jogador não encontrado: " + playerName));
                return 0;
            }
            BigBangRegions.getAllocationCoordinator().cancelRequest(opt.get().getId());
            source.sendSuccess(() -> Component.literal("§aPedido de alocação cancelado para " + playerName + ".")
                .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c" + e.getMessage()));
            return 0;
        }
    }

    private static Optional<String> resolveBiomeOptionKey(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String normalized = query.trim();
        for (BiomeOption option : BigBangRegions.getAllocationCoordinator().getBiomeOptions()) {
            if (option.matches(normalized)) {
                return Optional.of(option.getKey());
            }
        }
        return Optional.empty();
    }

    private static Optional<GameProfile> lookupProfile(CommandSourceStack source, String name) {
        if (source.getServer() == null) return Optional.empty();
        var cache = source.getServer().getProfileCache();
        if (cache == null) return Optional.empty();
        Optional<GameProfile> profile = cache.get(name);
        if (profile.isPresent()) {
            return profile;
        }
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(online.getGameProfile());
        }
        return Optional.empty();
    }

    private static String getPlayerName(CommandSourceStack source, UUID uuid) {
        if (uuid == null) return "Nenhum";
        var cache = source.getServer().getProfileCache();
        if (cache != null) {
            var profile = cache.get(uuid);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        ServerPlayer player = source.getServer().getPlayerList().getPlayer(uuid);
        if (player != null) {
            return player.getGameProfile().getName();
        }
        return uuid.toString();
    }

    private static int countPlayerRegions(UUID ownerUuid) {
        int count = 0;
        for (Region r : regionCache.getAll()) {
            if (r.getType() == RegionType.PLAYER_REGION && ownerUuid.equals(r.getOwnerUuid())) {
                count++;
            }
        }
        return count;
    }

    private static boolean checkPlayerRegionOverlap(RegionBounds bounds, String id) {
        var config = configManager.getConfig().getPlayerRegions();
        for (Region region : regionCache.getAll()) {
            if (region.getId().equalsIgnoreCase(id)) {
                continue;
            }
            if (region.getBounds().intersects(bounds)) {
                if (region.getType() == RegionType.SYSTEM_REGION && config.isRejectOverlapWithSystemRegions()) {
                    return true;
                }
                if (region.getType() == RegionType.ADMIN_REGION && config.isRejectOverlapWithAdminRegions()) {
                    return true;
                }
                if (region.getType() == RegionType.PLAYER_REGION && config.isRejectOverlapWithPlayerRegions()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int createPlayerRegion(CommandContext<CommandSourceStack> context, int priority) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem criar regiões administrativamente."));
            return 0;
        }

        if (!checkPermission(source, "bigbangregions.admin.player.create")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }

        UUID uuid = player.getUUID();
        SelectionManager.Selection p1 = selectionManager.getPos1(uuid);
        SelectionManager.Selection p2 = selectionManager.getPos2(uuid);

        if (p1 == null || p2 == null) {
            source.sendFailure(Component.literal("Defina pos1 e pos2 primeiro."));
            return 0;
        }

        if (!p1.getDimension().equals(p2.getDimension())) {
            source.sendFailure(Component.literal("A Posição 1 e a Posição 2 devem estar na mesma dimensão."));
            return 0;
        }

        String id = StringArgumentType.getString(context, "id");
        if (regionCache.get(id) != null) {
            source.sendFailure(Component.literal("Uma região com ID '" + id + "' já existe."));
            return 0;
        }

        if (id.trim().isEmpty() || !id.matches("^[a-zA-Z0-9_\\-]+$")) {
            source.sendFailure(Component.literal("ID de região inválido. Use apenas letras, números, underline e hífen."));
            return 0;
        }

        String ownerName = StringArgumentType.getString(context, "owner");
        Optional<GameProfile> ownerProfileOpt = lookupProfile(source, ownerName);
        if (ownerProfileOpt.isEmpty()) {
            source.sendFailure(Component.literal("Não foi possível encontrar o jogador '" + ownerName + "'."));
            return 0;
        }
        GameProfile ownerProfile = ownerProfileOpt.get();
        UUID ownerUuid = ownerProfile.getId();

        int max = configManager.getConfig().getPlayerRegions().getMaxRegionsPerOwner();
        if (max < 1) max = 1;
        if (countPlayerRegions(ownerUuid) >= max) {
            source.sendFailure(Component.literal("O jogador " + ownerProfile.getName() + " já atingiu o limite de regiões (" + max + ")."));
            return 0;
        }

        String dimension = p1.getDimension();
        BlockPos pos1 = p1.getPos();
        BlockPos pos2 = p2.getPos();
        RegionBounds bounds = new RegionBounds(dimension, pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ());

        if (checkPlayerRegionOverlap(bounds, id)) {
            source.sendFailure(Component.literal("A região se sobrepõe com uma região existente e foi bloqueada pela configuração."));
            return 0;
        }

        long now = System.currentTimeMillis();
        Region region = new Region(id, id, RegionType.PLAYER_REGION, bounds, priority, ownerUuid, uuid, now, now, "ACTIVE");

        regionRepository.save(region);
        regionCache.add(region);
        BigBangRegions.getMembershipCache().loadFromRegion(region);

        auditService.log(id, uuid, "CREATE_PLAYER_REGION", null, "PLAYER_REGION", "{\"ownerUuid\":\"" + ownerUuid + "\"}");

        source.sendSuccess(() -> Component.literal(
            String.format("Região de jogador '%s' criada com sucesso!\nDono: %s (%s)\nDimensão: %s\nTamanho: %d blocos",
                id, ownerProfile.getName(), ownerUuid, dimension, bounds.volume())
        ).withStyle(ChatFormatting.GREEN), false);

        selectionManager.clear(uuid);
        return 1;
    }

    private static int showAdminOwner(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.player.owner")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        String id = StringArgumentType.getString(context, "regionId");
        Region region = regionCache.get(id);
        if (region == null) {
            source.sendFailure(Component.literal("Região '" + id + "' não encontrada."));
            return 0;
        }
        if (region.getType() != RegionType.PLAYER_REGION) {
            source.sendFailure(Component.literal("Esta região não é do tipo PLAYER_REGION."));
            return 0;
        }
        String ownerName = getPlayerName(source, region.getOwnerUuid());
        source.sendSuccess(() -> Component.literal("Dono atual da região '" + id + "': " + ownerName + " (" + region.getOwnerUuid() + ")").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }


    private static int listAdminMembers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.player.members")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        String id = StringArgumentType.getString(context, "regionId");
        Region region = regionCache.get(id);
        if (region == null) {
            source.sendFailure(Component.literal("Região '" + id + "' não encontrada."));
            return 0;
        }
        if (region.getType() != RegionType.PLAYER_REGION) {
            source.sendFailure(Component.literal("Esta região não é do tipo PLAYER_REGION."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== Membros da Região '" + id + "' ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Dono (OWNER): " + getPlayerName(source, region.getOwnerUuid())).withStyle(ChatFormatting.YELLOW), false);
        for (RegionMember member : region.getMembers().values()) {
            String name = getPlayerName(source, member.getUuid());
            String addedBy = getPlayerName(source, member.getAddedByUuid());
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateStr = sdf.format(new java.util.Date(member.getCreatedAt()));
            source.sendSuccess(() -> Component.literal(String.format(" - %s (%s) - Adicionado por %s em %s", name, member.getRole().name(), addedBy, dateStr)).withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int addAdminMember(CommandContext<CommandSourceStack> context, RegionRole role) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.player.members")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        String id = StringArgumentType.getString(context, "regionId");
        Region region = regionCache.get(id);
        if (region == null) {
            source.sendFailure(Component.literal("Região '" + id + "' não encontrada."));
            return 0;
        }
        if (region.getType() != RegionType.PLAYER_REGION) {
            source.sendFailure(Component.literal("Esta região não é do tipo PLAYER_REGION."));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "player");
        Optional<GameProfile> profileOpt = lookupProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.literal("Não foi possível encontrar o jogador '" + targetName + "'."));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        UUID targetUuid = profile.getId();
        UUID actorUuid = source.getPlayer() != null ? source.getPlayer().getUUID() : null;

        try {
            BigBangRegions.getMembershipService().addMember(region, actorUuid, targetUuid, role, true);
            source.sendSuccess(() -> Component.literal("Jogador " + profile.getName() + " adicionado como " + role.name() + " à região '" + id + "'.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int removeAdminMember(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.player.members")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        String id = StringArgumentType.getString(context, "regionId");
        Region region = regionCache.get(id);
        if (region == null) {
            source.sendFailure(Component.literal("Região '" + id + "' não encontrada."));
            return 0;
        }
        if (region.getType() != RegionType.PLAYER_REGION) {
            source.sendFailure(Component.literal("Esta região não é do tipo PLAYER_REGION."));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "player");
        Optional<GameProfile> profileOpt = lookupProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.literal("Não foi possível encontrar o jogador '" + targetName + "'."));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        UUID targetUuid = profile.getId();
        UUID actorUuid = source.getPlayer() != null ? source.getPlayer().getUUID() : null;

        try {
            BigBangRegions.getMembershipService().removeMember(region, actorUuid, targetUuid, true);
            source.sendSuccess(() -> Component.literal("Jogador " + profile.getName() + " removido da região '" + id + "'.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int setAdminRole(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.player.members")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }
        String id = StringArgumentType.getString(context, "regionId");
        Region region = regionCache.get(id);
        if (region == null) {
            source.sendFailure(Component.literal("Região '" + id + "' não encontrada."));
            return 0;
        }
        if (region.getType() != RegionType.PLAYER_REGION) {
            source.sendFailure(Component.literal("Esta região não é do tipo PLAYER_REGION."));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "player");
        Optional<GameProfile> profileOpt = lookupProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.literal("Não foi possível encontrar o jogador '" + targetName + "'."));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        UUID targetUuid = profile.getId();

        String roleStr = StringArgumentType.getString(context, "role").toUpperCase();
        RegionRole role;
        if (roleStr.equalsIgnoreCase("LEADER")) {
            role = RegionRole.LEADER;
        } else if (roleStr.equalsIgnoreCase("MEMBER")) {
            role = RegionRole.MEMBER;
        } else {
            source.sendFailure(Component.literal("Papel inválido. Use LEADER ou MEMBER."));
            return 0;
        }

        UUID actorUuid = source.getPlayer() != null ? source.getPlayer().getUUID() : null;

        try {
            BigBangRegions.getMembershipService().setRole(region, actorUuid, targetUuid, role, true);
            source.sendSuccess(() -> Component.literal("Papel de " + profile.getName() + " atualizado para " + role.name() + " na região '" + id + "'.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static Region resolvePlayerRegion(ServerPlayer player) {
        UUID uuid = player.getUUID();
        for (Region r : regionCache.getAll()) {
            if (r.getType() == RegionType.PLAYER_REGION && uuid.equals(r.getOwnerUuid())) {
                return r;
            }
        }
        for (Region r : regionCache.getAll()) {
            if (r.getType() == RegionType.PLAYER_REGION) {
                RegionRole role = BigBangRegions.getRoleResolver().resolveRole(r, uuid);
                if (role == RegionRole.LEADER) {
                    return r;
                }
            }
        }
        return null;
    }

    private static Region resolvePlayerRegionForList(ServerPlayer player) {
        UUID uuid = player.getUUID();
        for (Region r : regionCache.getAll()) {
            if (r.getType() == RegionType.PLAYER_REGION && uuid.equals(r.getOwnerUuid())) {
                return r;
            }
        }
        for (Region r : regionCache.getAll()) {
            if (r.getType() == RegionType.PLAYER_REGION) {
                RegionRole role = BigBangRegions.getRoleResolver().resolveRole(r, uuid);
                if (role == RegionRole.LEADER || role == RegionRole.MEMBER) {
                    return r;
                }
            }
        }
        return null;
    }

    private static Region resolvePlayerRegionForLeave(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String dimension = player.level().dimension().location().toString();
        BlockPos pos = player.blockPosition();
        Optional<Region> currentRegion = regionResolver.resolveRegionAt(dimension, pos.getX(), pos.getY(), pos.getZ());
        if (currentRegion.isPresent()) {
            Region r = currentRegion.get();
            if (r.getType() == RegionType.PLAYER_REGION) {
                RegionRole role = BigBangRegions.getRoleResolver().resolveRole(r, uuid);
                if (role == RegionRole.MEMBER || role == RegionRole.LEADER) {
                    return r;
                }
            }
        }
        for (Region r : regionCache.getAll()) {
            if (r.getType() == RegionType.PLAYER_REGION) {
                RegionRole role = BigBangRegions.getRoleResolver().resolveRole(r, uuid);
                if (role == RegionRole.MEMBER || role == RegionRole.LEADER) {
                    return r;
                }
            }
        }
        return null;
    }

    private static int listPlayerMembers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem gerenciar membros."));
            return 0;
        }

        Region region = resolvePlayerRegionForList(player);
        if (region == null) {
            source.sendFailure(Component.literal("Você não pertence a nenhuma região de jogador."));
            return 0;
        }

        UUID uuid = player.getUUID();
        RegionRole role = BigBangRegions.getRoleResolver().resolveRole(region, uuid);

        source.sendSuccess(() -> Component.literal("=== Membros da Região " + region.getId() + " ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Dono (OWNER): " + getPlayerName(source, region.getOwnerUuid())).withStyle(ChatFormatting.YELLOW), false);
        
        for (RegionMember member : region.getMembers().values()) {
            String name = getPlayerName(source, member.getUuid());
            if (role == RegionRole.OWNER || role == RegionRole.LEADER) {
                String addedBy = getPlayerName(source, member.getAddedByUuid());
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String dateStr = sdf.format(new java.util.Date(member.getCreatedAt()));
                source.sendSuccess(() -> Component.literal(String.format(" - %s (%s) - Adicionado por %s em %s", name, member.getRole().name(), addedBy, dateStr)).withStyle(ChatFormatting.YELLOW), false);
            } else {
                source.sendSuccess(() -> Component.literal(String.format(" - %s (%s)", name, member.getRole().name())).withStyle(ChatFormatting.YELLOW), false);
            }
        }
        return 1;
    }

    private static int addPlayerMember(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem gerenciar membros."));
            return 0;
        }

        Region region = resolvePlayerRegion(player);
        if (region == null) {
            source.sendFailure(Component.literal("Você não possui ou lidera nenhuma região de jogador."));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "player");
        Optional<GameProfile> profileOpt = lookupProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.literal("Não foi possível encontrar o jogador '" + targetName + "'."));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        UUID targetUuid = profile.getId();
        UUID actorUuid = player.getUUID();

        try {
            BigBangRegions.getMembershipService().addMember(region, actorUuid, targetUuid, RegionRole.MEMBER, false);
            source.sendSuccess(() -> Component.literal("Jogador " + profile.getName() + " adicionado como MEMBER à região.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int removePlayerMember(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem gerenciar membros."));
            return 0;
        }

        Region region = resolvePlayerRegion(player);
        if (region == null) {
            source.sendFailure(Component.literal("Você não possui ou lidera nenhuma região de jogador."));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "player");
        Optional<GameProfile> profileOpt = lookupProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.literal("Não foi possível encontrar o jogador '" + targetName + "'."));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        UUID targetUuid = profile.getId();
        UUID actorUuid = player.getUUID();

        try {
            BigBangRegions.getMembershipService().removeMember(region, actorUuid, targetUuid, false);
            source.sendSuccess(() -> Component.literal("Jogador " + profile.getName() + " removido da região.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int promotePlayerMember(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem gerenciar membros."));
            return 0;
        }

        Region region = resolvePlayerRegion(player);
        if (region == null) {
            source.sendFailure(Component.literal("Você não possui ou lidera nenhuma região de jogador."));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "player");
        Optional<GameProfile> profileOpt = lookupProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.literal("Não foi possível encontrar o jogador '" + targetName + "'."));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        UUID targetUuid = profile.getId();
        UUID actorUuid = player.getUUID();

        try {
            BigBangRegions.getMembershipService().setRole(region, actorUuid, targetUuid, RegionRole.LEADER, false);
            source.sendSuccess(() -> Component.literal("Jogador " + profile.getName() + " promovido a LEADER na região.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int demotePlayerMember(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem gerenciar membros."));
            return 0;
        }

        Region region = resolvePlayerRegion(player);
        if (region == null) {
            source.sendFailure(Component.literal("Você não possui ou lidera nenhuma região de jogador."));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "player");
        Optional<GameProfile> profileOpt = lookupProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendFailure(Component.literal("Não foi possível encontrar o jogador '" + targetName + "'."));
            return 0;
        }
        GameProfile profile = profileOpt.get();
        UUID targetUuid = profile.getId();
        UUID actorUuid = player.getUUID();

        try {
            BigBangRegions.getMembershipService().setRole(region, actorUuid, targetUuid, RegionRole.MEMBER, false);
            source.sendSuccess(() -> Component.literal("Jogador " + profile.getName() + " rebaixado a MEMBER na região.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int leavePlayerRegion(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem sair de uma região."));
            return 0;
        }

        Region region = resolvePlayerRegionForLeave(player);
        if (region == null) {
            source.sendFailure(Component.literal("Você não é membro de nenhuma região de jogador."));
            return 0;
        }

        UUID uuid = player.getUUID();
        try {
            BigBangRegions.getMembershipService().leaveRegion(region, uuid);
            source.sendSuccess(() -> Component.literal("Você saiu voluntariamente da região '" + region.getId() + "'.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static final Set<String> EDITABLE_PLAYER_FLAGS = new HashSet<>(Arrays.asList(
        "player-build", "player-interact", "container-access", "door-use",
        "redstone-use", "entity-interact", "pvp", "item-pickup", "item-drop"
    ));

    private static int listPlayerFlags(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem gerenciar flags."));
            return 0;
        }

        Region region = resolvePlayerRegion(player);
        if (region == null) {
            source.sendFailure(Component.literal("Você não possui ou lidera nenhuma região de jogador."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== Flags da Região " + region.getId() + " ===").withStyle(ChatFormatting.GOLD), false);
        for (String flagId : EDITABLE_PLAYER_FLAGS) {
            String val = region.getFlagValue(flagId);
            source.sendSuccess(() -> Component.literal(" - " + flagId + ": " + val).withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int verPlayerFlag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem gerenciar flags."));
            return 0;
        }

        Region region = resolvePlayerRegion(player);
        if (region == null) {
            source.sendFailure(Component.literal("Você não possui ou lidera nenhuma região de jogador."));
            return 0;
        }

        String flagId = StringArgumentType.getString(context, "flag").toLowerCase();
        if (!EDITABLE_PLAYER_FLAGS.contains(flagId)) {
            source.sendFailure(Component.literal("Você não tem permissão para alterar ou ver esta flag."));
            return 0;
        }

        String val = region.getFlagValue(flagId);
        source.sendSuccess(() -> Component.literal("Flag: " + flagId + "\nValor: " + val).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int definirPlayerFlag(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Apenas jogadores podem gerenciar flags."));
            return 0;
        }

        Region region = resolvePlayerRegion(player);
        if (region == null) {
            source.sendFailure(Component.literal("Você não possui ou lidera nenhuma região de jogador."));
            return 0;
        }

        String flagId = StringArgumentType.getString(context, "flag").toLowerCase();
        if (!EDITABLE_PLAYER_FLAGS.contains(flagId)) {
            source.sendFailure(Component.literal("Você não tem permissão para alterar esta flag."));
            return 0;
        }

        String valueStr = StringArgumentType.getString(context, "value").toUpperCase();
        FlagPolicy policy;
        try {
            policy = FlagPolicy.valueOf(valueStr);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Valor inválido: " + valueStr + ". Use: ALLOW, DENY, INHERIT"));
            return 0;
        }

        String before = region.getFlagValue(flagId);
        region.setFlag(flagId, policy.name());
        regionRepository.updateFlags(region);

        UUID actorUuid = player.getUUID();
        auditService.log(region.getId(), actorUuid, "SET_PLAYER_REGION_FLAG", flagId + "=" + before, flagId + "=" + policy.name(), null);

        source.sendSuccess(() -> Component.literal("Flag '" + flagId + "' alterada de " + before + " para " + policy.name() + " na região '" + region.getId() + "'.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
