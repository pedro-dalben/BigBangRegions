package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.flag.RegionFlagRegistry;
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

public class RegionFlagsMenu extends ChestMenu {
    private final ServerPlayer player;
    private final Region region;

    public RegionFlagsMenu(int containerId, Inventory playerInventory, ServerPlayer player, Region region) {
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

        List<String> categories = RegionFlagRegistry.getCategories();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};
        for (int i = 0; i < categories.size() && i < slots.length; i++) {
            String category = categories.get(i);
            ItemStack stack = new ItemStack(Items.KNOWLEDGE_BOOK);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("§e" + category));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7Abrir flags desta categoria"));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(slots[i], stack);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        List<String> categories = RegionFlagRegistry.getCategories();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};
        for (int i = 0; i < categories.size() && i < slots.length; i++) {
            if (slotId == slots[i]) {
                RegionGuiHandler.openFlagsCategoryMenu(serverPlayer, region, categories.get(i));
                return;
            }
        }
    }
}
