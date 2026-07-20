package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.domain.Region;
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

public class RegionFriendsMenu extends ChestMenu {
    private final ServerPlayer player;
    private final List<Region> regions;

    public RegionFriendsMenu(int containerId, Inventory playerInventory, ServerPlayer player) {
        super(MenuType.GENERIC_9x3, containerId, playerInventory, new SimpleContainer(27), 3);
        this.player = player;
        this.regions = RegionGuiHandler.findFriendRegions(player.getUUID());
        populateItems();
    }

    private void populateItems() {
        Container container = getContainer();
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 27; i++) container.setItem(i, glass.copy());

        int slot = 10;
        for (Region region : regions) {
            if (slot >= 27) break;
            ItemStack item = new ItemStack(Items.COMPASS);
            item.set(DataComponents.CUSTOM_NAME, Component.literal("§b" + region.getName()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7Região: §f" + region.getId()));
            lore.add(Component.literal("§7Dono: §f" + playerName(region.getOwnerUuid())));
            lore.add(Component.literal("§7Cargo: §f" + region.getRole(player.getUUID()).name()));
            lore.add(Component.literal("§eMembros possuem permissão para modificar esta região"));
            lore.add(Component.literal("§aClique para Teleportar"));
            item.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(slot, item);
            slot += slot == 16 ? 3 : 1;
        }
        if (regions.isEmpty()) {
            container.setItem(13, button(Items.BARRIER, "§cNenhuma região de amigo",
                "§7Você ainda não é membro de uma região ativa."));
        }
        container.setItem(26, button(Items.PAPER, "§e§lConvites recebidos",
            "§7Aceitar ou recusar convites"));
    }

    private ItemStack button(net.minecraft.world.item.Item item, String name, String loreLine) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(loreLine))));
        return stack;
    }

    private String playerName(java.util.UUID uuid) {
        ServerPlayer online = player.getServer().getPlayerList().getPlayer(uuid);
        return online != null ? online.getGameProfile().getName() : uuid.toString().substring(0, 8);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player clickedBy) {
        broadcastChanges();
        if (!(clickedBy instanceof ServerPlayer serverPlayer)) return;
        if (slotId == 26) {
            RegionGuiHandler.openInviteInboxMenu(serverPlayer);
            return;
        }
        if (!((slotId >= 10 && slotId <= 16) || (slotId >= 19 && slotId <= 25))) return;
        int index = slotId < 17 ? slotId - 10 : slotId - 12;
        if (index < 0 || index >= regions.size()) return;
        try {
            serverPlayer.closeContainer();
            BigBangRegions.getAllocationCoordinator().teleportToRegionHome(serverPlayer, regions.get(index));
        } catch (Exception e) {
            serverPlayer.sendSystemMessage(Component.literal("§c" + e.getMessage()));
        }
    }
}
