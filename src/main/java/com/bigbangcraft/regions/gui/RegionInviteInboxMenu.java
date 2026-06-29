package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.invite.RegionInvite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public class RegionInviteInboxMenu extends ChestMenu {
    private final ServerPlayer player;

    public RegionInviteInboxMenu(int containerId, Inventory playerInventory, ServerPlayer player) {
        super(MenuType.GENERIC_9x3, containerId, playerInventory, new SimpleContainer(27), 3);
        this.player = player;
        populateItems();
    }

    private void populateItems() {
        Container container = getContainer();
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 27; i++) {
            container.setItem(i, glass.copy());
        }

        List<RegionInvite> invites = BigBangRegions.getInviteService().getPendingInvitesForPlayer(player.getUUID());
        int slot = 10;
        for (RegionInvite invite : invites) {
            if (slot >= 27) break;
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("§e" + invite.getRegionId()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7Cargo: §f" + invite.getRole().name()));
            lore.add(Component.literal("§7Clique esquerdo para aceitar"));
            lore.add(Component.literal("§7Clique direito para recusar"));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(slot++, stack);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        List<RegionInvite> invites = BigBangRegions.getInviteService().getPendingInvitesForPlayer(serverPlayer.getUUID());
        int inviteSlot = 10;
        for (RegionInvite invite : invites) {
            if (slotId == inviteSlot) {
                try {
                    if (button == 1) {
                        BigBangRegions.getInviteService().declineInvite(invite.getId(), serverPlayer.getUUID());
                        serverPlayer.sendSystemMessage(Component.literal("§cConvite recusado."));
                    } else {
                        BigBangRegions.getInviteService().acceptInvite(invite.getId(), serverPlayer.getUUID());
                        serverPlayer.sendSystemMessage(Component.literal("§aConvite aceito."));
                    }
                } catch (Exception e) {
                    serverPlayer.sendSystemMessage(Component.literal("§c" + e.getMessage()));
                }
                RegionGuiHandler.openMenu(serverPlayer);
                return;
            }
            inviteSlot++;
        }
    }
}
