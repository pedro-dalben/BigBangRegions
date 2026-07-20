package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.expansion.ExpansionDirection;
import com.bigbangcraft.regions.expansion.RegionExpansionCoordinator;
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

public class RegionExpansionMenu extends ChestMenu {
    private final ServerPlayer player;
    private final Region region;
    private final int page;
    private final ExpansionDirection direction;
    private final int increment;

    public RegionExpansionMenu(int id, Inventory inventory, ServerPlayer player, Region region,
                               int page, ExpansionDirection direction, int increment) {
        super(MenuType.GENERIC_9x3, id, inventory, new SimpleContainer(27), 3);
        this.player = player; this.region = region; this.page = page;
        this.direction = direction; this.increment = increment;
        populate();
    }

    private void populate() {
        Container c = getContainer();
        ItemStack filler = button(Items.GRAY_STAINED_GLASS_PANE, "");
        for (int i = 0; i < 27; i++) c.setItem(i, filler.copy());
        if (page == 0) {
            c.setItem(10, button(Items.ARROW, "§aNorte", "§7Expandir para o norte"));
            c.setItem(12, button(Items.ARROW, "§aOeste", "§7Expandir para o oeste"));
            c.setItem(14, button(Items.ARROW, "§aLeste", "§7Expandir para o leste"));
            c.setItem(16, button(Items.ARROW, "§aSul", "§7Expandir para o sul"));
            c.setItem(13, button(Items.NETHER_STAR, "§bTodos os lados", "§7Expandir igualmente nos quatro lados"));
            c.setItem(22, button(Items.BOOK, "§eAtual: " + width(region.getBounds()) + "x" + depth(region.getBounds()),
                "§7Máximo por eixo: " + BigBangRegions.getConfigManager().getConfig().getPlayerLandAllocation().getFutureMaximumClaimSize()));
        } else if (page == 1) {
            RegionBounds target = RegionExpansionCoordinator.directionalBounds(region.getBounds(), direction, 1);
            c.setItem(11, sizeButton(1, target));
            c.setItem(13, sizeButton(5, RegionExpansionCoordinator.directionalBounds(region.getBounds(), direction, 5)));
            c.setItem(15, sizeButton(10, RegionExpansionCoordinator.directionalBounds(region.getBounds(), direction, 10)));
            c.setItem(22, button(Items.ARROW, "§7Voltar", "§7Escolher outro lado"));
        } else {
            RegionBounds target = RegionExpansionCoordinator.directionalBounds(region.getBounds(), direction, increment);
            long added = area(target) - area(region.getBounds());
            long price = added * BigBangRegions.getConfigManager().getConfig().getRegionExpansion().getPricePerAddedBlock();
            c.setItem(11, button(Items.EMERALD, "§a§lConfirmar expansão", "§7Área nova: " + added + " blocos", "§7Custo: " + price + " gemas"));
            c.setItem(15, button(Items.BARRIER, "§cCancelar", "§7Voltar"));
        }
    }

    private ItemStack sizeButton(int amount, RegionBounds target) {
        long added = area(target) - area(region.getBounds());
        long price = added * BigBangRegions.getConfigManager().getConfig().getRegionExpansion().getPricePerAddedBlock();
        return button(Items.GOLD_INGOT, "§e+" + amount + " blocos", "§7Área nova: " + added, "§7Custo: " + price + " gemas");
    }

    private ItemStack button(net.minecraft.world.item.Item item, String name, String... lines) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) lore.add(Component.literal(line));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    @Override public void clicked(int slot, int button, ClickType clickType, Player clicked) {
        if (!(clicked instanceof ServerPlayer sp) || !sp.getUUID().equals(player.getUUID())) return;
        if (page == 0) {
            ExpansionDirection selected = switch (slot) {
                case 10 -> ExpansionDirection.NORTH; case 12 -> ExpansionDirection.WEST;
                case 14 -> ExpansionDirection.EAST; case 16 -> ExpansionDirection.SOUTH;
                case 13 -> ExpansionDirection.ALL; default -> null;
            };
            if (selected != null) RegionGuiHandler.openExpansionSizes(sp, region, selected);
        } else if (page == 1) {
            if (slot == 22) RegionGuiHandler.openExpansionDirections(sp, region);
            else if (slot == 11 || slot == 13 || slot == 15) RegionGuiHandler.openExpansionConfirm(sp, region, direction, slot == 11 ? 1 : slot == 13 ? 5 : 10);
        } else if (page == 2) {
            if (slot == 15) RegionGuiHandler.openExpansionSizes(sp, region, direction);
            else if (slot == 11) {
                sp.closeContainer();
                try { BigBangRegions.getExpansionCoordinator().beginExpansion(sp, direction, increment); sp.sendSystemMessage(Component.literal("§aExpansão iniciada.")); }
                catch (Exception e) { sp.sendSystemMessage(Component.literal("§c" + e.getMessage())); }
            }
        }
    }

    private static long area(RegionBounds b) { return (long) width(b) * depth(b); }
    private static int width(RegionBounds b) { return b.getMaxX() - b.getMinX() + 1; }
    private static int depth(RegionBounds b) { return b.getMaxZ() - b.getMinZ() + 1; }
}
