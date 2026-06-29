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

public class RegionAdminMenu extends ChestMenu {
    private final ServerPlayer player;
    private final Region region;

    public RegionAdminMenu(int containerId, Inventory playerInventory, ServerPlayer player, Region region) {
        super(MenuType.GENERIC_9x3, containerId, playerInventory, new SimpleContainer(27), 3);
        this.player = player;
        this.region = region;
        populateItems();
    }

    private void populateItems() {
        Container container = getContainer();
        ItemStack glass = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        glass.set(DataComponents.CUSTOM_NAME, Component.literal(""));
        for (int i = 0; i < 27; i++) {
            container.setItem(i, glass.copy());
        }

        container.setItem(10, button(Items.BOOK, "§6§lInspecionar", "§7Mostra detalhes no chat"));
        container.setItem(11, button(Items.COMPASS, "§a§lTeleportar", "§7Ir para a regiao"));
        container.setItem(12, button(Items.PLAYER_HEAD, "§b§lMembros", "§7Gerenciar membros"));
        container.setItem(13, button(Items.COMPARATOR, "§d§lFlags", "§7Editar flags"));
        container.setItem(14, button(Items.ANVIL, "§e§lReparar borda", "§7Ainda em desenvolvimento"));
        container.setItem(15, button(Items.GRASS_BLOCK, "§e§lReparar spawn", "§7Ainda em desenvolvimento"));
        container.setItem(16, button(Items.LAVA_BUCKET, "§c§lExcluir", "§7Excluir regiao"));
        container.setItem(22, button(Items.WRITABLE_BOOK, "§f§lAuditoria", "§7Exibir ultimos eventos"));
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

        if (slotId == 10) {
            serverPlayer.closeContainer();
            serverPlayer.sendSystemMessage(Component.literal("§eRegiao: " + region));
        } else if (slotId == 11) {
            try {
                BigBangRegions.getAllocationCoordinator().teleportToHome(serverPlayer);
            } catch (Exception e) {
                serverPlayer.sendSystemMessage(Component.literal("§c" + e.getMessage()));
            }
        } else if (slotId == 12) {
            RegionGuiHandler.openMembersMenu(serverPlayer, region);
        } else if (slotId == 13) {
            RegionGuiHandler.openFlagsMenu(serverPlayer, region);
        } else if (slotId == 14) {
            serverPlayer.sendSystemMessage(Component.literal("§eReparo de borda ainda nao implementado."));
        } else if (slotId == 15) {
            serverPlayer.sendSystemMessage(Component.literal("§eReparo de spawn ainda nao implementado."));
        } else if (slotId == 16) {
            serverPlayer.sendSystemMessage(Component.literal("§cUse o comando ou a acao confirmada de exclusao."));
        } else if (slotId == 22) {
            serverPlayer.sendSystemMessage(Component.literal("§7Auditoria ainda depende do painel de logs."));
        }
    }
}
