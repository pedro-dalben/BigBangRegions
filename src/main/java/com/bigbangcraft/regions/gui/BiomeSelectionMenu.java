package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.allocation.BiomeOption;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BiomeSelectionMenu extends ChestMenu {
    private final ServerPlayer player;
    private final List<BiomeOption> slotBiomes = new ArrayList<>();

    public BiomeSelectionMenu(int containerId, Inventory playerInventory, ServerPlayer player) {
        super(MenuType.GENERIC_9x3, containerId, playerInventory, new SimpleContainer(27), 3);
        this.player = player;
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

        // Get biome options
        Collection<BiomeOption> options = BigBangRegions.getAllocationCoordinator().getBiomeOptions();

        // Slots to place biomes in the center row(s) to look organized
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int index = 0;

        for (BiomeOption option : options) {
            if (index >= slots.length) break;
            int slot = slots[index];

            Item item = Items.MAP;
            try {
                item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(option.getIcon()));
                if (item == Items.AIR) {
                    item = Items.MAP;
                }
            } catch (Exception e) {
                // fallback
            }

            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("§e" + option.getDisplayName()));

            // Set lore components
            List<Component> loreComponents = new ArrayList<>();
            loreComponents.add(Component.literal("§7Bioma do terreno: §f" + option.getDisplayName()));
            loreComponents.add(Component.literal("§7Tamanho inicial: §f50x50"));
            loreComponents.add(Component.literal(""));
            loreComponents.add(Component.literal("§aClique para criar seu terreno neste bioma."));

            stack.set(DataComponents.LORE, new ItemLore(loreComponents));

            container.setItem(slot, stack);

            // Map slot to biome
            while (slotBiomes.size() <= slot) {
                slotBiomes.add(null);
            }
            slotBiomes.set(slot, option);
            index++;
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Cancel all slot movements and drag drops
        broadcastChanges();

        if (slotId < 0 || slotId >= 27) {
            return;
        }

        if (slotId < slotBiomes.size()) {
            BiomeOption option = slotBiomes.get(slotId);
            if (option != null && player instanceof ServerPlayer serverPlayer) {
                // Instantly close container to prevent double click exploits
                serverPlayer.closeContainer();
                
                try {
                    String requestId = BigBangRegions.getAllocationCoordinator().createRequest(serverPlayer, option.getKey(), "player_gui");
                    serverPlayer.sendSystemMessage(Component.literal("§aSolicitação de terreno enviada! Processando..."));
                } catch (Exception e) {
                    serverPlayer.sendSystemMessage(Component.literal("§c" + e.getMessage()));
                }
            }
        }
    }
}
