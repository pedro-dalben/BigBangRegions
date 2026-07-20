package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.journeymap.PlayerMapPreference;
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
            "§7Gerenciar membros, convites e cargos"));
        container.setItem(15, button(Items.COMPARATOR, "§e§lFlags",
            "§7Configurar permissões da região"));
        container.setItem(17, button(Items.COMPASS, "§b§lRegiões de amigos",
            "§7Ver regiões ativas das quais você é membro"));
        if (player.getUUID().equals(region.getOwnerUuid())) {
            container.setItem(19, button(Items.EMERALD, "§a§lExpandir terreno", "§7Escolher lado e quantidade de blocos"));
            container.setItem(21, button(Items.ENDER_EYE, "§b§lChunk loader", "§7Selecionar chunks carregados", "§7Somente o owner usa sua quota"));
        }
        container.setItem(26, button(Items.PAPER, "§e§lConvites recebidos",
            "§7Aceitar ou recusar convites"));
        container.setItem(22, button(Items.BOOK, "§6§lInformações",
            "§7Ver informações e limites do terreno"));
        container.setItem(24, buildDeleteButton());

        boolean showMap = PlayerMapPreference.isShowOwnRegion(player.getUUID());
        container.setItem(20, button(
            showMap ? Items.FILLED_MAP : Items.MAP,
            (showMap ? "§a§lMapa: Visível" : "§7§lMapa: Oculta"),
            "§7Clique para " + (showMap ? "ocultar" : "mostrar") + " sua região no JourneyMap"
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

    private ItemStack buildDeleteButton() {
        boolean owner = player.getUUID().equals(region.getOwnerUuid());
        long remainingMs = BigBangRegions.getAllocationCoordinator().getPlayerRegionDeleteCooldownRemainingMillis(region);
        if (!owner) {
            return button(
                Items.BARRIER,
                "§8§lExcluir terreno",
                "§7Apenas o dono pode excluir esta região."
            );
        }
        if (remainingMs > 0) {
            return button(
                Items.CLOCK,
                "§6§lExcluir terreno",
                "§7Disponível em " + formatDuration(remainingMs) + ".",
                "§7Você precisa aguardar 1 hora após a criação."
            );
        }
        return button(
            Items.TNT,
            "§c§lExcluir terreno",
            "§7Isto é irreversível.",
            "§7Itens, baús, construções e entidades da área serão removidos.",
            "§7Clique para confirmar."
        );
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
            RegionGuiHandler.openFlagsMenu(serverPlayer, region);
        } else if (slotId == 17) {
            RegionGuiHandler.openFriendsMenu(serverPlayer);
        } else if (slotId == 19 && serverPlayer.getUUID().equals(region.getOwnerUuid())) {
            RegionGuiHandler.openExpansionDirections(serverPlayer, region);
        } else if (slotId == 21 && serverPlayer.getUUID().equals(region.getOwnerUuid())) {
            RegionGuiHandler.openChunkLoaderMenu(serverPlayer, region, 0, 0);
        } else if (slotId == 26) {
            RegionGuiHandler.openInviteInboxMenu(serverPlayer);
        } else if (slotId == 22) {
            serverPlayer.closeContainer();
            serverPlayer.sendSystemMessage(Component.literal("§eRegiao: " + region.getId()));
            serverPlayer.sendSystemMessage(Component.literal("§eTipo: " + region.getType()));
            serverPlayer.sendSystemMessage(Component.literal("§eDimensao: " + region.getBounds().getDimension()));
            serverPlayer.sendSystemMessage(Component.literal("§eLimites: " +
                region.getBounds().getMinX() + ", " + region.getBounds().getMinY() + ", " + region.getBounds().getMinZ() +
                " -> " + region.getBounds().getMaxX() + ", " + region.getBounds().getMaxY() + ", " + region.getBounds().getMaxZ()));
        } else if (slotId == 24) {
            if (!serverPlayer.getUUID().equals(region.getOwnerUuid())) {
                serverPlayer.sendSystemMessage(Component.literal("§cApenas o dono pode excluir este terreno."));
                return;
            }
            long remainingMs = BigBangRegions.getAllocationCoordinator().getPlayerRegionDeleteCooldownRemainingMillis(region);
            if (remainingMs > 0) {
                serverPlayer.sendSystemMessage(Component.literal("§eVoce ainda precisa aguardar " + formatDuration(remainingMs) + " para excluir este terreno."));
                return;
            }
            RegionGuiHandler.openDeleteConfirmationMenu(serverPlayer, region);
        } else if (slotId == 20) {
            boolean show = PlayerMapPreference.isShowOwnRegion(serverPlayer.getUUID());
            PlayerMapPreference.setShowOwnRegion(serverPlayer.getUUID(), !show);
            RegionGuiHandler.openMainMenu(serverPlayer, region);
        }
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
