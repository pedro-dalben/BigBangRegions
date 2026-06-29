package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.flag.RegionFlagDefinition;
import com.bigbangcraft.regions.flag.RegionFlagRegistry;
import com.bigbangcraft.regions.flag.RegionFlagValueType;
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

public class RegionFlagCategoryMenu extends ChestMenu {
    private final ServerPlayer player;
    private final Region region;
    private final String category;

    public RegionFlagCategoryMenu(int containerId, Inventory playerInventory, ServerPlayer player, Region region, String category) {
        super(MenuType.GENERIC_9x3, containerId, playerInventory, new SimpleContainer(27), 3);
        this.player = player;
        this.region = region;
        this.category = category;
        populateItems();
    }

    private void populateItems() {
        Container container = getContainer();
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 27; i++) {
            container.setItem(i, glass.copy());
        }

        List<RegionFlagDefinition> flags = RegionFlagRegistry.getByCategory(category);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < flags.size() && i < slots.length; i++) {
            RegionFlagDefinition def = flags.get(i);
            ItemStack stack = new ItemStack(def.getValueType() == RegionFlagValueType.BOOLEAN ? Items.LEVER : Items.PAPER);
            String current = region.getFlagValue(def.getId());
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("§e" + def.getId() + " §7[" + current + "]"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7" + def.getDescription()));
            lore.add(Component.literal("§7Clique para alternar/gerenciar"));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(slots[i], stack);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        List<RegionFlagDefinition> flags = RegionFlagRegistry.getByCategory(category);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < flags.size() && i < slots.length; i++) {
            if (slotId == slots[i]) {
                RegionFlagDefinition def = flags.get(i);
                if (def.getValueType() != RegionFlagValueType.BOOLEAN) {
                    serverPlayer.sendSystemMessage(Component.literal("§eFlag de tipo " + def.getValueType() + " ainda nao possui editor visual."));
                    return;
                }
                String current = region.getFlagValue(def.getId());
                String next = "ALLOW".equalsIgnoreCase(current) ? "DENY" : "ALLOW";
                region.setFlag(def.getId(), next);
                BigBangRegions.getRegionRepository().save(region);
                serverPlayer.sendSystemMessage(Component.literal("§aFlag " + def.getId() + " alterada para " + next));
                RegionGuiHandler.openFlagsCategoryMenu(serverPlayer, region, category);
                return;
            }
        }
    }
}
