package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.invite.RegionInvite;
import com.bigbangcraft.regions.expansion.ExpansionDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class RegionGuiHandler {
    public static void openMenu(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Region activeRegion = findOwnedRegion(uuid);

        if (activeRegion != null) {
            openMainMenu(player, activeRegion);
        } else if (!findFriendRegions(uuid).isEmpty()) {
            openFriendsMenu(player);
        } else if (!BigBangRegions.getInviteService().getPendingInvitesForPlayer(uuid).isEmpty()) {
            openInviteInboxMenu(player);
        } else {
            // Check if they have an active request in progress
            var request = BigBangRegions.getAllocationCoordinator().getActiveRequest(uuid);
            if (request != null && request.getState().isPreRegionCreation()) {
                player.sendSystemMessage(Component.literal("§eSolicitação em andamento (Status: " + request.getState() + ")."));
                player.sendSystemMessage(Component.literal("§7Use §f/region status§7 para ver o progresso ou §f/region cancelar§7 para cancelar."));
                return;
            }

            SimpleMenuProvider menuProvider = new SimpleMenuProvider(
                (containerId, playerInventory, playerEntity) -> new BiomeSelectionMenu(containerId, playerInventory, player),
                Component.literal("§8Escolha o bioma do seu terreno")
            );
            player.openMenu(menuProvider);
        }
    }

    public static void openMainMenu(ServerPlayer player, Region region) {
        SimpleMenuProvider menuProvider = new SimpleMenuProvider(
            (containerId, playerInventory, playerEntity) -> new RegionMainMenu(containerId, playerInventory, player, region),
            Component.literal("§8Seu terreno")
        );
        player.openMenu(menuProvider);
    }

    public static void openExpansionDirections(ServerPlayer player, Region region) {
        player.openMenu(new SimpleMenuProvider((id, inv, entity) ->
            new RegionExpansionMenu(id, inv, player, region, 0, null, 0), Component.literal("§8Expandir terreno")));
    }

    public static void openExpansionSizes(ServerPlayer player, Region region, ExpansionDirection direction) {
        player.openMenu(new SimpleMenuProvider((id, inv, entity) ->
            new RegionExpansionMenu(id, inv, player, region, 1, direction, 0), Component.literal("§8Escolha o incremento")));
    }

    public static void openExpansionConfirm(ServerPlayer player, Region region, ExpansionDirection direction, int increment) {
        player.openMenu(new SimpleMenuProvider((id, inv, entity) ->
            new RegionExpansionMenu(id, inv, player, region, 2, direction, increment), Component.literal("§8Confirmar expansão")));
    }

    public static void openChunkLoaderMenu(ServerPlayer player, Region region, int pageX, int pageZ) {
        player.openMenu(new SimpleMenuProvider((id, inv, entity) ->
            new RegionChunkLoaderMenu(id, inv, player, region, pageX, pageZ), Component.literal("§8Chunk loader")));
    }

    public static void openDeleteConfirmationMenu(ServerPlayer player, Region region) {
        SimpleMenuProvider menuProvider = new SimpleMenuProvider(
            (containerId, playerInventory, playerEntity) -> new RegionDeleteConfirmMenu(containerId, playerInventory, player, region),
            Component.literal("§8Confirmar exclusão")
        );
        player.openMenu(menuProvider);
    }

    public static void openMembersMenu(ServerPlayer player, Region region) {
        SimpleMenuProvider menuProvider = new SimpleMenuProvider(
            (containerId, playerInventory, playerEntity) -> new RegionMembersMenu(containerId, playerInventory, player, region),
            Component.literal("§8Membros")
        );
        player.openMenu(menuProvider);
    }

    public static void openInvitesMenu(ServerPlayer player, Region region) {
        SimpleMenuProvider menuProvider = new SimpleMenuProvider(
            (containerId, playerInventory, playerEntity) -> new RegionInvitesMenu(containerId, playerInventory, player, region),
            Component.literal("§8Convites")
        );
        player.openMenu(menuProvider);
    }

    public static void openInviteInboxMenu(ServerPlayer player) {
        SimpleMenuProvider menuProvider = new SimpleMenuProvider(
            (containerId, playerInventory, playerEntity) -> new RegionInviteInboxMenu(containerId, playerInventory, player),
            Component.literal("§8Convites recebidos")
        );
        player.openMenu(menuProvider);
    }

    public static void openSentInvitesMenu(ServerPlayer player, Region region) {
        SimpleMenuProvider menuProvider = new SimpleMenuProvider(
            (containerId, playerInventory, playerEntity) -> new RegionInviteSentMenu(containerId, playerInventory, player, region),
            Component.literal("§8Convites enviados")
        );
        player.openMenu(menuProvider);
    }

    public static void openFriendsMenu(ServerPlayer player) {
        SimpleMenuProvider menuProvider = new SimpleMenuProvider(
            (containerId, playerInventory, playerEntity) -> new RegionFriendsMenu(containerId, playerInventory, player),
            Component.literal("§8Regiões de amigos")
        );
        player.openMenu(menuProvider);
    }

    public static void sendInviteNotification(ServerPlayer player, RegionInvite invite) {
        if (player == null || invite == null) return;
        Region region = BigBangRegions.getRegionCache().get(invite.getRegionId());
        String regionName = region == null ? invite.getRegionId() : region.getName();
        Component message = Component.literal("§eConvite para " + regionName + ". Aceitar concede permissão para modificar esta região. ")
            .append(Component.literal("[Aceitar]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/region convite aceitar " + invite.getId()))))
            .append(Component.literal(" "))
            .append(Component.literal("[Recusar]").withStyle(style -> style.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/region convite recusar " + invite.getId()))));
        player.sendSystemMessage(message);
    }

    public static void openFlagsMenu(ServerPlayer player, Region region) {
        SimpleMenuProvider menuProvider = new SimpleMenuProvider(
            (containerId, playerInventory, playerEntity) -> new RegionFlagsMenu(containerId, playerInventory, player, region),
            Component.literal("§8Flags")
        );
        player.openMenu(menuProvider);
    }

    public static void openFlagsCategoryMenu(ServerPlayer player, Region region, String category) {
        SimpleMenuProvider menuProvider = new SimpleMenuProvider(
            (containerId, playerInventory, playerEntity) -> new RegionFlagCategoryMenu(containerId, playerInventory, player, region, category),
            Component.literal("§8" + category)
        );
        player.openMenu(menuProvider);
    }

    public static void openAdminMenu(ServerPlayer player, Region region) {
        SimpleMenuProvider menuProvider = new SimpleMenuProvider(
            (containerId, playerInventory, playerEntity) -> new RegionAdminMenu(containerId, playerInventory, player, region),
            Component.literal("§8Painel administrativo")
        );
        player.openMenu(menuProvider);
    }

    public static Region findPlayerRegion(UUID uuid) {
        Region activeRegion = null;
        for (Region r : BigBangRegions.getRegionCache().getAll()) {
            if (r.getType() == RegionType.PLAYER_REGION && "ACTIVE".equals(r.getStatus())) {
                if (uuid.equals(r.getOwnerUuid())) {
                    activeRegion = r;
                    break;
                }
                RegionRole role = BigBangRegions.getRoleResolver().resolveRole(r, uuid);
                if (role == RegionRole.OWNER || role == RegionRole.LEADER || role == RegionRole.MANAGER || role == RegionRole.MEMBER) {
                    activeRegion = r;
                    break;
                }
            }
        }
        return activeRegion;
    }

    public static List<Region> findFriendRegions(UUID uuid) {
        List<Region> regions = new ArrayList<>();
        for (Region region : BigBangRegions.getRegionCache().getAll()) {
            if (region.getType() == RegionType.PLAYER_REGION && "ACTIVE".equals(region.getStatus())
                && !uuid.equals(region.getOwnerUuid())
                && BigBangRegions.getRoleResolver().resolveRole(region, uuid) != RegionRole.VISITOR) {
                regions.add(region);
            }
        }
        return regions;
    }

    private static Region findOwnedRegion(UUID uuid) {
        for (Region region : BigBangRegions.getRegionCache().getAll()) {
            if (region.getType() == RegionType.PLAYER_REGION && "ACTIVE".equals(region.getStatus())
                && uuid.equals(region.getOwnerUuid())) {
                return region;
            }
        }
        return null;
    }
}
