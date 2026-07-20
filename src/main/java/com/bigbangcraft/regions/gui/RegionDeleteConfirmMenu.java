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

public class RegionDeleteConfirmMenu extends ChestMenu {
    private final ServerPlayer player;
    private final Region region;

    public RegionDeleteConfirmMenu(int containerId, Inventory playerInventory, ServerPlayer player, Region region) {
        super(MenuType.GENERIC_9x3, containerId, playerInventory, new SimpleContainer(27), 3);
        this.player = player;
        this.region = region;
        populateItems();
    }

    private void populateItems() {
        Container container = getContainer();
        ItemStack glass = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        glass.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 27; i++) {
            container.setItem(i, glass.copy());
        }

        container.setItem(13, button(
            Items.BARRIER,
            "§c§lExcluir terreno",
            "§7Essa ação é permanente.",
            "§7Todos os blocos, baús, itens e construções",
            "§7dentro da região serão removidos.",
            "§7O terreno será restaurado ao estado original."
        ));
        container.setItem(11, button(
            Items.LIME_STAINED_GLASS_PANE,
            "§a§lCancelar",
            "§7Voltar sem excluir nada."
        ));
        container.setItem(15, button(
            Items.TNT,
            "§c§lConfirmar exclusão",
            "§7Clique para apagar sua região."
        ));
    }

    private ItemStack button(net.minecraft.world.item.Item item, String name, String... loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        List<Component> lore = new ArrayList<>();
        for (String loreLine : loreLines) {
            lore.add(Component.literal(loreLine));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (slotId == 11) {
            RegionGuiHandler.openMainMenu(serverPlayer, region);
            return;
        }

        if (slotId == 15) {
            try {
                serverPlayer.closeContainer();
                BigBangRegions.getChunkLoaderService().onRegionDeleted(serverPlayer.getServer(), region);
                boolean restored = BigBangRegions.getAllocationCoordinator().deletePlayerOwnedRegion(serverPlayer, region);
                if (BigBangRegions.getAuditService() != null) {
                    try {
                        BigBangRegions.getAuditService().log(region.getId(), serverPlayer.getUUID(), "DELETE_REGION", region.getType().name(), null, "player_confirmed_delete");
                    } catch (Exception ignored) {
                        // Audit must not block deletion feedback.
                    }
                }
                if (restored) {
                    serverPlayer.sendSystemMessage(Component.literal("§aSeu terreno foi excluido e o terreno original foi restaurado."));
                } else {
                    serverPlayer.sendSystemMessage(Component.literal("§eSeu terreno foi excluido, mas a restauracao completa nao concluiu."));
                }
            } catch (Exception e) {
                serverPlayer.sendSystemMessage(Component.literal("§c" + e.getMessage()));
            }
        }
    }
}
