package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.allocation.BiomeOption;
import com.bigbangcraft.regions.config.Config;
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
import java.util.List;

public class BiomeSelectionMenu extends ChestMenu {
    private static final int ROWS = 6;
    private static final int SLOTS = ROWS * 9;
    private static final int BIOMES_PER_PAGE = 28;

    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private static final int SLOT_PREV_PAGE = 48;
    private static final int SLOT_NEXT_PAGE = 50;
    private static final int SLOT_PAGE_INFO = 4;

    private final ServerPlayer player;
    private final List<BiomeOption> allOptions;
    private final int initialClaimSize;
    private int currentPage = 0;
    private final int maxPage;

    public BiomeSelectionMenu(int containerId, Inventory playerInventory, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, new SimpleContainer(SLOTS), ROWS);
        this.player = player;
        Config.PlayerLandAllocationConfig lac = BigBangRegions.getConfigManager().getConfig().getPlayerLandAllocation();
        this.initialClaimSize = lac.getInitialClaimSize();
        this.allOptions = new ArrayList<>(BigBangRegions.getAllocationCoordinator().getBiomeOptions());
        this.maxPage = Math.max(0, (int) Math.ceil((double) allOptions.size() / BIOMES_PER_PAGE) - 1);
        populateItems();
    }

    private void populateItems() {
        Container container = getContainer();

        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < SLOTS; i++) {
            container.setItem(i, glass);
        }

        if (maxPage > 0) {
            ItemStack pageInfo = new ItemStack(Items.PAPER);
            pageInfo.set(DataComponents.CUSTOM_NAME, Component.literal("§7Página §e" + (currentPage + 1) + "§7/§e" + (maxPage + 1)));
            container.setItem(SLOT_PAGE_INFO, pageInfo);
        }

        int startIndex = currentPage * BIOMES_PER_PAGE;
        int endIndex = Math.min(startIndex + BIOMES_PER_PAGE, allOptions.size());

        for (int i = startIndex; i < endIndex; i++) {
            BiomeOption option = allOptions.get(i);
            int slot = CONTENT_SLOTS[i - startIndex];

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

            List<Component> loreComponents = new ArrayList<>();
            loreComponents.add(Component.literal("§7Bioma do terreno: §f" + option.getDisplayName()));
            loreComponents.add(Component.literal("§7Tamanho inicial: §f" + initialClaimSize + "x" + initialClaimSize));
            loreComponents.add(Component.literal(""));
            loreComponents.add(Component.literal("§aClique para criar seu terreno neste bioma."));

            stack.set(DataComponents.LORE, new ItemLore(loreComponents));

            container.setItem(slot, stack);
        }

        if (currentPage > 0) {
            ItemStack prevButton = new ItemStack(Items.ARROW);
            prevButton.set(DataComponents.CUSTOM_NAME, Component.literal("§a« Anterior"));
            container.setItem(SLOT_PREV_PAGE, prevButton);
        }

        if (currentPage < maxPage) {
            ItemStack nextButton = new ItemStack(Items.ARROW);
            nextButton.set(DataComponents.CUSTOM_NAME, Component.literal("§aPróximo »"));
            container.setItem(SLOT_NEXT_PAGE, nextButton);
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        broadcastChanges();

        if (slotId < 0 || slotId >= SLOTS) {
            return;
        }

        if (slotId == SLOT_PREV_PAGE && currentPage > 0) {
            currentPage--;
            populateItems();
            return;
        }

        if (slotId == SLOT_NEXT_PAGE && currentPage < maxPage) {
            currentPage++;
            populateItems();
            return;
        }

        int startIndex = currentPage * BIOMES_PER_PAGE;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == slotId) {
                int optionIndex = startIndex + i;
                if (optionIndex < allOptions.size()) {
                    BiomeOption option = allOptions.get(optionIndex);
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.closeContainer();
                        try {
                            BigBangRegions.getAllocationCoordinator().createRequest(serverPlayer, option.getKey(), "player_gui");
                            serverPlayer.sendSystemMessage(Component.literal("§aSolicitação de terreno enviada! Processando..."));
                        } catch (Exception e) {
                            serverPlayer.sendSystemMessage(Component.literal("§c" + e.getMessage()));
                        }
                    }
                }
                break;
            }
        }
    }
}
