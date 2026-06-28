package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.UUID;

public class RegionGuiHandler {
    public static void openMenu(ServerPlayer player) {
        UUID uuid = player.getUUID();
        
        // Find if they already possess an active region (as owner or member/leader)
        Region activeRegion = null;
        for (Region r : BigBangRegions.getRegionCache().getAll()) {
            if (r.getType() == RegionType.PLAYER_REGION && "ACTIVE".equals(r.getStatus())) {
                if (uuid.equals(r.getOwnerUuid())) {
                    activeRegion = r;
                    break;
                }
                var role = BigBangRegions.getRoleResolver().resolveRole(r, uuid);
                if (role == com.bigbangcraft.regions.domain.RegionRole.LEADER || role == com.bigbangcraft.regions.domain.RegionRole.MEMBER) {
                    activeRegion = r;
                    break;
                }
            }
        }

        if (activeRegion != null) {
            Region finalRegion = activeRegion;
            SimpleMenuProvider menuProvider = new SimpleMenuProvider(
                (containerId, playerInventory, playerEntity) -> new RegionManagementMenu(containerId, playerInventory, player, finalRegion),
                Component.literal("§8Gerenciamento do seu terreno")
            );
            player.openMenu(menuProvider);
        } else {
            // Check if they have an active request in progress
            var request = BigBangRegions.getAllocationCoordinator().getActiveRequest(uuid);
            if (request != null && request.getState().isPreRegionCreation()) {
                player.sendSystemMessage(Component.literal("§eVocê já possui uma solicitação de terreno em andamento (Status: " + request.getState() + "). Aguarde."));
                return;
            }

            SimpleMenuProvider menuProvider = new SimpleMenuProvider(
                (containerId, playerInventory, playerEntity) -> new BiomeSelectionMenu(containerId, playerInventory, player),
                Component.literal("§8Escolha o bioma do seu terreno")
            );
            player.openMenu(menuProvider);
        }
    }
}
