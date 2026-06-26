package com.bigbangcraft.regions.command;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.audit.AuditService;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.flag.FlagPolicy;
import com.bigbangcraft.regions.flag.FlagRegistry;
import com.bigbangcraft.regions.flag.RegionFlag;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.region.RegionResolver;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.util.SelectionManager;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class RegionsCommand {
    private static PermissionManager permissionManager;
    private static SelectionManager selectionManager;
    private static RegionCache regionCache;
    private static RegionRepository regionRepository;
    private static RegionResolver regionResolver;
    private static AuditService auditService;
    private static ConfigManager configManager;

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
                .then(Commands.literal("admin")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> createAdmin(context, 1000))
                        .then(Commands.argument("priority", IntegerArgumentType.integer())
                            .executes(context -> createAdmin(context, IntegerArgumentType.getInteger(context, "priority")))
                        )
                    )
                )
            )
            .then(Commands.literal("delete")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(RegionsCommand::deleteRegion)
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
                .then(Commands.argument("regionId", StringArgumentType.word())
                    .executes(RegionsCommand::listFlags)
                )
            )
            .then(Commands.literal("reload").executes(RegionsCommand::reloadMod));

        LiteralCommandNode<CommandSourceStack> mainNode = dispatcher.register(builder);
        dispatcher.register(Commands.literal("regiao").redirect(mainNode));
        dispatcher.register(Commands.literal("regioes").redirect(mainNode));
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

    private static int createAdmin(CommandContext<CommandSourceStack> context, int priority) {
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
        
        long now = System.currentTimeMillis();
        Region region = new Region(id, id, RegionType.ADMIN_REGION, bounds, priority, null, uuid, now, now, "ACTIVE");
        
        regionRepository.save(region);
        regionCache.add(region);
        auditService.log(id, uuid, "CREATE_REGION", null, "ADMIN_REGION", null);

        source.sendSuccess(() -> Component.literal("Região administrativa '" + id + "' criada com sucesso! Prioridade: " + priority).withStyle(ChatFormatting.GREEN), false);
        selectionManager.clear(uuid);
        return 1;
    }

    private static int deleteRegion(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.admin.delete")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }

        String id = StringArgumentType.getString(context, "id");
        Region region = regionCache.get(id);
        if (region == null) {
            source.sendFailure(Component.literal("Região '" + id + "' não encontrada."));
            return 0;
        }

        regionRepository.delete(id);
        regionCache.remove(id);

        UUID actorUuid = source.getPlayer() != null ? source.getPlayer().getUUID() : null;
        auditService.log(id, actorUuid, "DELETE_REGION", region.getType().name(), null, null);

        source.sendSuccess(() -> Component.literal("Região '" + id + "' deletada com sucesso.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!checkPermission(source, "bigbangregions.inspect")) {
            source.sendFailure(Component.literal("Você não tem permissão para usar este comando."));
            return 0;
        }

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
        source.sendSuccess(() -> Component.literal("=== Região Efetiva ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("ID: " + region.getId()).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Tipo: " + region.getType()).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Prioridade: " + region.getPriority()).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Dimensão: " + region.getBounds().getDimension()).withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal(String.format("Limites: (%d, %d, %d) a (%d, %d, %d)",
                region.getBounds().getMinX(), region.getBounds().getMinY(), region.getBounds().getMinZ(),
                region.getBounds().getMaxX(), region.getBounds().getMaxY(), region.getBounds().getMaxZ())).withStyle(ChatFormatting.YELLOW), false);

        // Check if there are overlapping regions
        List<Region> allAtPos = regionCache.getRegionsAt(dimension, pos.getX(), pos.getY(), pos.getZ());
        if (allAtPos.size() > 1) {
            source.sendSuccess(() -> Component.literal("Outras regiões sobrepostas neste ponto:").withStyle(ChatFormatting.GRAY), false);
            for (Region r : allAtPos) {
                if (!r.getId().equals(region.getId())) {
                    source.sendSuccess(() -> Component.literal(" - " + r.getId() + " (Prioridade: " + r.getPriority() + ")").withStyle(ChatFormatting.GRAY), false);
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
            List<Region> reloaded = regionRepository.loadAll();
            for (Region region : reloaded) {
                regionCache.add(region);
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
}
