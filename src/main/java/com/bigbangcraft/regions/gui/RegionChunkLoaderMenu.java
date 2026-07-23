package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.chunkloader.RegionChunkLoaderService;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RegionChunkLoaderMenu extends ChestMenu {
    private static final int ROWS = 6;
    private static final int SLOTS = ROWS * 9;
    private static final int VIEW_WIDTH = 7;
    private static final int VIEW_HEIGHT = 4;

    private final ServerPlayer player;
    private final Region region;
    private final int pageX, pageZ;

    public RegionChunkLoaderMenu(int id, Inventory inventory, ServerPlayer player, Region region, int pageX, int pageZ) {
        super(MenuType.GENERIC_9x6, id, inventory, new SimpleContainer(SLOTS), ROWS);
        this.player = player;
        this.region = region;
        this.pageX = Math.max(0, pageX);
        this.pageZ = Math.max(0, pageZ);
        populate();
    }

    private void populate() {
        Container container = getContainer();
        ItemStack filler = item(Items.GRAY_STAINED_GLASS_PANE, "");
        for (int i = 0; i < SLOTS; i++) {
            container.setItem(i, filler.copy());
        }

        RegionBounds b = region.getBounds();
        int minChunkX = b.getMinX() >> 4, maxChunkX = b.getMaxX() >> 4;
        int minChunkZ = b.getMinZ() >> 4, maxChunkZ = b.getMaxZ() >> 4;
        int totalChunksX = maxChunkX - minChunkX + 1;
        int totalChunksZ = maxChunkZ - minChunkZ + 1;

        int pageMinChunkX = minChunkX + pageX * VIEW_WIDTH;
        int pageMinChunkZ = minChunkZ + pageZ * VIEW_HEIGHT;
        int visibleX = Math.min(VIEW_WIDTH, maxChunkX - pageMinChunkX + 1);
        int visibleZ = Math.min(VIEW_HEIGHT, maxChunkZ - pageMinChunkZ + 1);

        Set<ChunkPos> selected = BigBangRegions.getChunkLoaderService().selected(region);

        ChunkPos playerChunk = new ChunkPos(player.getBlockX() >> 4, player.getBlockZ() >> 4);
        boolean sameDimension = player.level().dimension().location().toString().equals(b.getDimension());

        if (visibleX > 0 && visibleZ > 0) {
            int startCol = 1 + (VIEW_WIDTH - Math.min(visibleX, VIEW_WIDTH)) / 2;
            int startRow = (visibleZ == 5) ? 0 : (1 + (4 - Math.min(visibleZ, 4)) / 2);

            // Cardinal compass marker in top row center
            if (startRow > 0) {
                container.setItem(4, item(Items.COMPASS, "§e↑ NORTE (Z -)", "§7Bússola de Orientação do Grid"));
            }

            for (int gridRow = 0; gridRow < visibleZ; gridRow++) {
                for (int gridCol = 0; gridCol < visibleX; gridCol++) {
                    int chunkX = pageMinChunkX + gridCol;
                    int chunkZ = pageMinChunkZ + gridRow;
                    if (chunkX > maxChunkX || chunkZ > maxChunkZ) continue;

                    ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                    boolean isLoaded = selected.contains(chunk);
                    boolean isHere = sameDimension && chunk.equals(playerChunk);

                    int slot = (startRow + gridRow) * 9 + (startCol + gridCol);
                    int blockMinX = chunkX * 16;
                    int blockMaxX = chunkX * 16 + 15;
                    int blockMinZ = chunkZ * 16;
                    int blockMaxZ = chunkZ * 16 + 15;

                    String name = (isHere ? "§a📍 " : "") + (isLoaded ? "§a§lChunk Loader ATIVO" : "§c§lChunk Loader INATIVO");
                    String stateText = isLoaded ? "§a● Ativo (Carregado na memória)" : "§c○ Inativo";

                    List<String> loreLines = new ArrayList<>();
                    loreLines.add("§7Status: " + stateText);
                    if (isHere) {
                        loreLines.add("§a📍 §lVOCÊ ESTÁ ATUALMENTE NESTE CHUNK!");
                    }
                    loreLines.add("§7Grid: §fLinha " + (gridRow + 1) + ", Coluna " + (gridCol + 1));
                    loreLines.add("§7Chunk Pos: §f(" + chunkX + ", " + chunkZ + ")");
                    loreLines.add("§7Blocos X: §f" + blockMinX + " a " + blockMaxX);
                    loreLines.add("§7Blocos Z: §f" + blockMinZ + " a " + blockMaxZ);
                    loreLines.add("");
                    loreLines.add("§e[Clique Esquerdo] §7para " + (isLoaded ? "DESATIVAR" : "ATIVAR"));
                    loreLines.add("§e[Clique Direito]  §7para TELEPORTAR até o chunk");

                    ItemStack stack = item(
                        isLoaded ? Items.LIME_WOOL : Items.RED_WOOL,
                        name,
                        loreLines.toArray(new String[0])
                    );

                    if (isHere) {
                        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                    }

                    container.setItem(slot, stack);
                }
            }
        }

        // Navigation controls in row 5
        if (pageX > 0) {
            container.setItem(45, item(Items.ARROW, "§e← Navegar Oeste", "§7Ir para a página anterior a oeste"));
        }
        if (pageZ > 0) {
            container.setItem(46, item(Items.ARROW, "§e↑ Navegar Norte", "§7Ir para a página anterior ao norte"));
        }
        if (pageMinChunkZ + VIEW_HEIGHT <= maxChunkZ) {
            container.setItem(47, item(Items.ARROW, "§e↓ Navegar Sul", "§7Ir para a próxima página ao sul"));
        }
        if (pageMinChunkX + VIEW_WIDTH <= maxChunkX) {
            container.setItem(48, item(Items.ARROW, "§eLeste → Navegar", "§7Ir para a próxima página a leste"));
        }

        RegionChunkLoaderService service = BigBangRegions.getChunkLoaderService();
        int permissionCredits = service.permissionCredits(player);
        int extraCredits = service.extraCredits(player.getUUID());
        int totalQuota = permissionCredits + extraCredits;
        int usedCount = selected.size();
        int remaining = Math.max(0, totalQuota - usedCount);

        container.setItem(49, item(Items.BOOK, "§b§lStatus dos Chunk Loaders",
            "§7Dimensão do terreno: §f" + (b.getMaxX() - b.getMinX() + 1) + " x " + (b.getMaxZ() - b.getMinZ() + 1) + " blocos",
            "§7Grid total de chunks: §f" + totalChunksX + " x " + totalChunksZ + " (" + (totalChunksX * totalChunksZ) + " chunks)",
            "§7Chunks selecionados: §a" + usedCount + "§7 / §f" + (totalChunksX * totalChunksZ),
            "§7Chunks ativos no servidor: §a" + service.loadedCount(region),
            "",
            "§7Créditos por permissão: §e" + permissionCredits,
            "§7Créditos extras: §e" + extraCredits,
            "§7Quota total: §a" + totalQuota,
            "§7Créditos disponíveis: §b" + remaining
        ));

        // Bulk action buttons
        container.setItem(50, item(Items.LIME_CONCRETE, "§a§lAtivar Todos os Chunks",
            "§7Clique para ativar o máximo de chunks",
            "§7respeitando seu limite de créditos.",
            "",
            "§e▶ Clique para ativar todos"
        ));

        container.setItem(51, item(Items.RED_CONCRETE, "§c§lDesativar Todos os Chunks",
            "§7Clique para desativar todos os chunks",
            "§7atualmente selecionados.",
            "",
            "§e▶ Clique para desativar todos"
        ));

        container.setItem(53, item(Items.BARRIER, "§cVoltar", "§7Clique para retornar ao menu principal do terreno"));
    }

    @Override
    public void clicked(int slot, int button, ClickType clickType, Player clicked) {
        if (!(clicked instanceof ServerPlayer sp) || !sp.getUUID().equals(player.getUUID())) return;

        if (slot == 53) {
            RegionGuiHandler.openMainMenu(sp, region);
            return;
        }
        if (slot == 45 && pageX > 0) {
            RegionGuiHandler.openChunkLoaderMenu(sp, region, pageX - 1, pageZ);
            return;
        }
        if (slot == 46 && pageZ > 0) {
            RegionGuiHandler.openChunkLoaderMenu(sp, region, pageX, pageZ - 1);
            return;
        }

        RegionBounds b = region.getBounds();
        int minChunkX = b.getMinX() >> 4, maxChunkX = b.getMaxX() >> 4;
        int minChunkZ = b.getMinZ() >> 4, maxChunkZ = b.getMaxZ() >> 4;
        int pageMinChunkX = minChunkX + pageX * VIEW_WIDTH;
        int pageMinChunkZ = minChunkZ + pageZ * VIEW_HEIGHT;
        int visibleX = Math.min(VIEW_WIDTH, maxChunkX - pageMinChunkX + 1);
        int visibleZ = Math.min(VIEW_HEIGHT, maxChunkZ - pageMinChunkZ + 1);

        if (slot == 47 && (pageMinChunkZ + VIEW_HEIGHT <= maxChunkZ)) {
            RegionGuiHandler.openChunkLoaderMenu(sp, region, pageX, pageZ + 1);
            return;
        }
        if (slot == 48 && (pageMinChunkX + VIEW_WIDTH <= maxChunkX)) {
            RegionGuiHandler.openChunkLoaderMenu(sp, region, pageX + 1, pageZ);
            return;
        }

        // Bulk action clicks
        if (slot == 50) {
            int added = BigBangRegions.getChunkLoaderService().activateAll(sp, region);
            if (added > 0) {
                sp.sendSystemMessage(Component.literal("§aForam ativados " + added + " chunks no seu terreno!"));
            } else {
                sp.sendSystemMessage(Component.literal("§cSem créditos suficientes ou todos os chunks já estão ativos."));
            }
            RegionGuiHandler.openChunkLoaderMenu(sp, region, pageX, pageZ);
            return;
        }
        if (slot == 51) {
            int removed = BigBangRegions.getChunkLoaderService().deactivateAll(sp, region);
            if (removed > 0) {
                sp.sendSystemMessage(Component.literal("§e" + removed + " chunks foram desativados do seu terreno."));
            } else {
                sp.sendSystemMessage(Component.literal("§cNão há chunks ativos para desativar."));
            }
            RegionGuiHandler.openChunkLoaderMenu(sp, region, pageX, pageZ);
            return;
        }

        int r = slot / 9;
        int c = slot % 9;

        int startCol = 1 + (VIEW_WIDTH - Math.min(visibleX, VIEW_WIDTH)) / 2;
        int startRow = (visibleZ == 5) ? 0 : (1 + (4 - Math.min(visibleZ, 4)) / 2);

        if (r >= startRow && r < startRow + visibleZ && c >= startCol && c < startCol + visibleX) {
            int gridCol = c - startCol;
            int gridRow = r - startRow;
            int targetChunkX = pageMinChunkX + gridCol;
            int targetChunkZ = pageMinChunkZ + gridRow;

            if (targetChunkX <= maxChunkX && targetChunkZ <= maxChunkZ) {
                // Right click teleport
                if (button == 1) {
                    ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(b.getDimension()));
                    ServerLevel level = sp.getServer() != null ? sp.getServer().getLevel(levelKey) : null;
                    if (level != null) {
                        int tpX = targetChunkX * 16 + 8;
                        int tpZ = targetChunkZ * 16 + 8;
                        int tpY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tpX, tpZ) + 1;
                        sp.teleportTo(level, tpX + 0.5, tpY, tpZ + 0.5, sp.getYRot(), sp.getXRot());
                        sp.sendSystemMessage(Component.literal("§aTeleportado para o chunk (" + targetChunkX + ", " + targetChunkZ + ")!"));
                        sp.closeContainer();
                    }
                    return;
                }

                // Left click toggle
                ChunkPos chunk = new ChunkPos(targetChunkX, targetChunkZ);
                if (!BigBangRegions.getChunkLoaderService().toggle(sp, region, chunk)) {
                    sp.sendSystemMessage(Component.literal("§cSem créditos de chunk loader disponíveis ou chunk inválido."));
                }
                RegionGuiHandler.openChunkLoaderMenu(sp, region, pageX, pageZ);
            }
        }
    }

    private ItemStack item(net.minecraft.world.item.Item item, String name, String... lines) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Component.literal(line));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }
}


