package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.domain.RegionRole;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.UUID;

public class RegionGuiHandler {
    public static void openMenu(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Region activeRegion = findPlayerRegion(uuid);

        if (activeRegion != null) {
            openMainMenu(player, activeRegion);
        } else {
            // Check if they have an active request in progress
            var request = BigBangRegions.getAllocationCoordinator().getActiveRequest(uuid);
            if (request != null && request.getState().isPreRegionCreation()) {
                player.sendSystemMessage(Component.literal("§eVocê já possui uma solicitação de terreno em andamento (Status: " + request.getState() + ")."));
                player.sendSystemMessage(Component.literal("§7Use §f/regions criar status§7 para ver o progresso ou §f/regions criar cancelar§7 para cancelar."));
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
}
