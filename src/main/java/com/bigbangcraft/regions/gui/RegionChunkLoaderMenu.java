package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.chunkloader.RegionChunkLoaderService;
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
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RegionChunkLoaderMenu extends ChestMenu {
    private final ServerPlayer player;
    private final Region region;
    private final int pageX, pageZ;

    public RegionChunkLoaderMenu(int id, Inventory inventory, ServerPlayer player, Region region, int pageX, int pageZ) {
        super(MenuType.GENERIC_9x3, id, inventory, new SimpleContainer(27), 3);
        this.player = player; this.region = region; this.pageX = Math.max(0, pageX); this.pageZ = Math.max(0, pageZ);
        populate();
    }

    private void populate() {
        Container c = getContainer();
        ItemStack filler = item(Items.GRAY_STAINED_GLASS_PANE, "");
        for (int i = 0; i < 27; i++) c.setItem(i, filler.copy());
        var b = region.getBounds();
        int minX = b.getMinX() >> 4, minZ = b.getMinZ() >> 4;
        int maxX = b.getMaxX() >> 4, maxZ = b.getMaxZ() >> 4;
        Set<ChunkPos> selected = BigBangRegions.getChunkLoaderService().selected(region);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 17, 18};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            int x = minX + pageX * 3 + col, z = minZ + pageZ * 3 + row;
            if (x <= maxX && z <= maxZ) {
                ChunkPos chunk = new ChunkPos(x, z);
                c.setItem(slots[row * 3 + col], item(selected.contains(chunk) ? Items.LIME_CONCRETE : Items.RED_CONCRETE,
                    selected.contains(chunk) ? "§aChunk carregado" : "§cChunk descarregado",
                    "§7Chunk: " + x + ", " + z, "§eClique para alternar"));
            }
        }
        if (pageX > 0) c.setItem(0, item(Items.ARROW, "§e← Oeste"));
        if (pageZ > 0) c.setItem(1, item(Items.ARROW, "§e↑ Norte"));
        if (minX + (pageX + 1) * 3 <= maxX) c.setItem(2, item(Items.ARROW, "§eLeste →"));
        if (minZ + (pageZ + 1) * 3 <= maxZ) c.setItem(3, item(Items.ARROW, "§eSul ↓"));
        RegionChunkLoaderService service = BigBangRegions.getChunkLoaderService();
        int permissionCredits = service.permissionCredits(player);
        int extraCredits = service.extraCredits(player.getUUID());
        c.setItem(24, item(Items.BOOK, "§bStatus da região",
            "§7Tamanho: " + (b.getMaxX() - b.getMinX() + 1) + " x " + (b.getMaxZ() - b.getMinZ() + 1) + " blocos",
            "§7Chunks na região: " + service.regionChunkCount(region),
            "§7Chunks selecionados: " + selected.size(),
            "§7Chunks carregados: " + service.loadedCount(region),
            "§7Permissão: " + permissionCredits,
            "§7Extras: " + extraCredits,
            "§7Ainda disponíveis: " + Math.max(0, permissionCredits + extraCredits - selected.size())));
        c.setItem(26, item(Items.BARRIER, "§7Voltar"));
    }

    @Override public void clicked(int slot, int button, ClickType clickType, Player clicked) {
        if (!(clicked instanceof ServerPlayer sp) || !sp.getUUID().equals(player.getUUID())) return;
        if (slot == 26) { RegionGuiHandler.openMainMenu(sp, region); return; }
        if (slot == 0 && pageX > 0) { RegionGuiHandler.openChunkLoaderMenu(sp, region, pageX - 1, pageZ); return; }
        if (slot == 1 && pageZ > 0) { RegionGuiHandler.openChunkLoaderMenu(sp, region, pageX, pageZ - 1); return; }
        var b = region.getBounds();
        int minX = b.getMinX() >> 4, minZ = b.getMinZ() >> 4;
        int cell = switch (slot) { case 10,11,12,13,14,15,16,17,18 -> slot - 10; default -> -1; };
        if (cell < 0) {
            if (slot == 2) RegionGuiHandler.openChunkLoaderMenu(sp, region, pageX + 1, pageZ);
            else if (slot == 3) RegionGuiHandler.openChunkLoaderMenu(sp, region, pageX, pageZ + 1);
            return;
        }
        ChunkPos chunk = new ChunkPos(minX + pageX * 3 + cell % 3, minZ + pageZ * 3 + cell / 3);
        if (!BigBangRegions.getChunkLoaderService().toggle(sp, region, chunk))
            sp.sendSystemMessage(Component.literal("§cSem créditos de chunk loader disponíveis ou chunk inválido."));
        RegionGuiHandler.openChunkLoaderMenu(sp, region, pageX, pageZ);
    }

    private ItemStack item(net.minecraft.world.item.Item item, String name, String... lines) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        List<Component> lore = new ArrayList<>(); for (String line : lines) lore.add(Component.literal(line));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }
}
