package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.domain.Region;
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

public class RegionInviteSentMenu extends ChestMenu {
    private final ServerPlayer player;
    private final Region region;

    public RegionInviteSentMenu(int containerId, Inventory playerInventory, ServerPlayer player, Region region) {
        super(MenuType.GENERIC_9x3, containerId, playerInventory, new SimpleContainer(27), 3);
        this.player = player;
        this.region = region;
        populateItems();
    }

    private void populateItems() {
        Container container = getContainer();
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 27; i++) {
            container.setItem(i, glass.copy());
        }

        List<RegionInvite> invites = BigBangRegions.getInviteService().getPendingInvitesSentBy(player.getUUID(), region.getId());
        int slot = 10;
        for (RegionInvite invite : invites) {
            if (slot >= 27) break;
            ItemStack stack = new ItemStack(Items.PAPER);

            String invitedName = playerName(invite.getInvitedUuid());

            stack.set(DataComponents.CUSTOM_NAME, Component.literal("§e" + invitedName));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7Cargo oferecido: §f" + invite.getRole().name()));
            lore.add(Component.literal("§7Clique para cancelar"));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(slot++, stack);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        List<RegionInvite> invites = BigBangRegions.getInviteService().getPendingInvitesSentBy(serverPlayer.getUUID(), region.getId());
        int inviteSlot = 10;
        for (RegionInvite invite : invites) {
            if (slotId == inviteSlot) {
                try {
                    BigBangRegions.getInviteService().cancelInvite(invite.getId(), serverPlayer.getUUID());
                    serverPlayer.sendSystemMessage(Component.literal("§aConvite cancelado."));
                } catch (Exception e) {
                    serverPlayer.sendSystemMessage(Component.literal("§c" + e.getMessage()));
                }
                RegionGuiHandler.openSentInvitesMenu(serverPlayer, region);
                return;
            }
            inviteSlot++;
        }
    }

    private String playerName(java.util.UUID uuid) {
        ServerPlayer online = player.getServer().getPlayerList().getPlayer(uuid);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return uuid.toString().substring(0, 8);
    }
}
