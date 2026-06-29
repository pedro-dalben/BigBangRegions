package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionRole;
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

public class RegionInvitesMenu extends ChestMenu {
    private final ServerPlayer player;
    private final Region region;

    public RegionInvitesMenu(int containerId, Inventory playerInventory, ServerPlayer player, Region region) {
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

        container.setItem(11, button(Items.PLAYER_HEAD, "§a§lConvidar online", "§7Clique em um jogador online"));
        container.setItem(13, button(Items.PAPER, "§e§lConvites recebidos", "§7Aceitar ou recusar convites"));
        container.setItem(15, button(Items.REDSTONE, "§c§lConvites enviados", "§7Cancelar convites pendentes"));

        int slot = 18;
        for (ServerPlayer online : player.getServer().getPlayerList().getPlayers()) {
            if (slot >= 27) break;
            if (online.getUUID().equals(player.getUUID())) continue;
            if (region.getRole(online.getUUID()) != RegionRole.VISITOR) continue;
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.CUSTOM_NAME, Component.literal("§b" + online.getGameProfile().getName()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7Clique para enviar convite como MEMBER"));
            head.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(slot++, head);
        }
    }

    private ItemStack button(net.minecraft.world.item.Item item, String name, String loreLine) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(loreLine));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        if (slotId == 13) {
            RegionGuiHandler.openInviteInboxMenu(serverPlayer);
            return;
        }

        if (slotId == 15) {
            RegionGuiHandler.openSentInvitesMenu(serverPlayer, region);
            return;
        }

        if (slotId >= 18 && slotId < 27) {
            ItemStack stack = getSlot(slotId).getItem();
            if (stack == null || stack.isEmpty()) return;
            String playerName = stack.getHoverName().getString().replace("§b", "");
            ServerPlayer target = serverPlayer.getServer().getPlayerList().getPlayerByName(playerName);
            if (target == null) {
                serverPlayer.sendSystemMessage(Component.literal("§cJogador offline."));
                return;
            }
            try {
                BigBangRegions.getInviteService().sendInvite(region, serverPlayer.getUUID(), target.getUUID(), RegionRole.MEMBER, 86400000L);
                serverPlayer.sendSystemMessage(Component.literal("§aConvite enviado para " + target.getGameProfile().getName()));
            } catch (Exception e) {
                serverPlayer.sendSystemMessage(Component.literal("§c" + e.getMessage()));
            }
            RegionGuiHandler.openInvitesMenu(serverPlayer, region);
        }
    }
}
