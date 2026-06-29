package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionType;
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

public class RegionMainMenu extends ChestMenu {
    private final ServerPlayer player;
    private final Region region;

    public RegionMainMenu(int containerId, Inventory playerInventory, ServerPlayer player, Region region) {
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

        container.setItem(11, button(Items.COMPASS, "§a§lIr para meu terreno",
            "§7Teleportar para o spawn seguro"));
        container.setItem(13, button(Items.PLAYER_HEAD, "§b§lMembros",
            "§7Ver e gerenciar membros"));
        container.setItem(15, button(Items.PAPER, "§d§lConvites",
            "§7Ver convites pendentes e enviar convites"));
        container.setItem(17, button(Items.COMPARATOR, "§e§lFlags",
            "§7Configurar permissões da região"));
        container.setItem(22, button(Items.BOOK, "§6§lInformações",
            "§7Ver informações e limites do terreno"));
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
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (slotId == 11) {
            serverPlayer.closeContainer();
            try {
                BigBangRegions.getAllocationCoordinator().teleportToHome(serverPlayer);
            } catch (Exception e) {
                serverPlayer.sendSystemMessage(Component.literal("§c" + e.getMessage()));
            }
        } else if (slotId == 13) {
            RegionGuiHandler.openMembersMenu(serverPlayer, region);
        } else if (slotId == 15) {
            RegionGuiHandler.openInvitesMenu(serverPlayer, region);
        } else if (slotId == 17) {
            RegionGuiHandler.openFlagsMenu(serverPlayer, region);
        } else if (slotId == 22) {
            serverPlayer.closeContainer();
            serverPlayer.sendSystemMessage(Component.literal("§eRegiao: " + region.getId()));
            serverPlayer.sendSystemMessage(Component.literal("§eTipo: " + region.getType()));
            serverPlayer.sendSystemMessage(Component.literal("§eDimensao: " + region.getBounds().getDimension()));
            serverPlayer.sendSystemMessage(Component.literal("§eLimites: " +
                region.getBounds().getMinX() + ", " + region.getBounds().getMinY() + ", " + region.getBounds().getMinZ() +
                " -> " + region.getBounds().getMaxX() + ", " + region.getBounds().getMaxY() + ", " + region.getBounds().getMaxZ()));
        }
    }
}
