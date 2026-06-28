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

public class RegionManagementMenu extends ChestMenu {
    private final ServerPlayer player;
    private final Region region;

    public RegionManagementMenu(int containerId, Inventory playerInventory, ServerPlayer player, Region region) {
        super(MenuType.GENERIC_9x3, containerId, playerInventory, new SimpleContainer(27), 3);
        this.player = player;
        this.region = region;
        populateItems();
    }

    private void populateItems() {
        Container container = getContainer();
        // Decorate all slots with Gray Stained Glass Pane
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 27; i++) {
            container.setItem(i, glass);
        }

        // Slot 11: Teleport
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponents.CUSTOM_NAME, Component.literal("§a§lIr para meu terreno"));
        List<Component> compassLore = new ArrayList<>();
        compassLore.add(Component.literal("§7Clique para ser teleportado para"));
        compassLore.add(Component.literal("§7o spawn seguro do seu terreno."));
        compass.set(DataComponents.LORE, new ItemLore(compassLore));
        container.setItem(11, compass);

        // Slot 13: Info
        ItemStack book = new ItemStack(Items.BOOK);
        book.set(DataComponents.CUSTOM_NAME, Component.literal("§e§lVer informações"));
        List<Component> bookLore = new ArrayList<>();
        bookLore.add(Component.literal("§7Clique para exibir as informações"));
        bookLore.add(Component.literal("§7e limites do seu terreno no chat."));
        book.set(DataComponents.LORE, new ItemLore(bookLore));
        container.setItem(13, book);

        // Slot 15: Members
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.CUSTOM_NAME, Component.literal("§b§lMembros"));
        List<Component> headLore = new ArrayList<>();
        headLore.add(Component.literal("§7Clique para gerenciar ou ver a"));
        headLore.add(Component.literal("§7lista de membros do seu terreno no chat."));
        head.set(DataComponents.LORE, new ItemLore(headLore));
        container.setItem(15, head);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Cancel all movements
        broadcastChanges();

        if (slotId < 0 || slotId >= 27) {
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (slotId == 11) {
                serverPlayer.closeContainer();
                try {
                    BigBangRegions.getAllocationCoordinator().teleportToHome(serverPlayer);
                    serverPlayer.sendSystemMessage(Component.literal("§aTeleportando para sua região..."));
                } catch (Exception e) {
                    serverPlayer.sendSystemMessage(Component.literal("§c" + e.getMessage()));
                }
            } else if (slotId == 13) {
                serverPlayer.closeContainer();
                serverPlayer.getServer().getCommands().performPrefixedCommand(
                    serverPlayer.createCommandSourceStack(), "regions info"
                );
            } else if (slotId == 15) {
                serverPlayer.closeContainer();
                serverPlayer.getServer().getCommands().performPrefixedCommand(
                    serverPlayer.createCommandSourceStack(), "regions membros listar"
                );
            }
        }
    }
}
