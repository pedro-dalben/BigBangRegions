package com.bigbangcraft.regions.gui;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

public class RegionMembersMenu extends ChestMenu {
    private final ServerPlayer player;
    private final Region region;
    private final Map<Integer, UUID> slotToMember = new HashMap<>();

    public RegionMembersMenu(int containerId, Inventory playerInventory, ServerPlayer player, Region region) {
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

        container.setItem(11, button(Items.PLAYER_HEAD, "§a§lConvidar membro", "§7Convidar jogador online"));
        container.setItem(13, button(Items.BARRIER, "§c§lRemover membro", "§7Remove um membro clicando em um item"));
        container.setItem(15, button(Items.NAME_TAG, "§e§lAlterar cargo", "§7Ainda em desenvolvimento"));
        container.setItem(22, button(Items.BOOK, "§b§lListar membros", "§7Mostra membros no chat"));

        int slot = 18;
        List<RegionMember> members = new ArrayList<>(region.getMembers().values());
        members.sort(Comparator.comparing(m -> m.getRole().getLevel(), Comparator.reverseOrder()));
        for (RegionMember member : members) {
            if (slot >= 27) break;
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            ServerPlayer online = player.getServer().getPlayerList().getPlayer(member.getUuid());
            String displayName = online != null ? online.getGameProfile().getName() : member.getUuid().toString();
            head.set(DataComponents.CUSTOM_NAME, Component.literal(member.getRole().name() + " - " + displayName));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("§7Status: " + (online != null ? "§aOnline" : "§7Offline")));
            lore.add(Component.literal("§7Cargo: §f" + member.getRole().name()));
            lore.add(Component.literal("§7Clique esquerdo para remover"));
            lore.add(Component.literal("§7Clique direito para transferir dono"));
            head.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(slot, head);
            slotToMember.put(slot, member.getUuid());
            slot++;
        }
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
            RegionGuiHandler.openInvitesMenu(serverPlayer, region);
            return;
        }
        if (slotId == 22) {
            serverPlayer.closeContainer();
            serverPlayer.sendSystemMessage(Component.literal("§eMembros da região:"));
            for (RegionMember member : region.getMembers().values()) {
                ServerPlayer online = serverPlayer.getServer().getPlayerList().getPlayer(member.getUuid());
                String name = online != null ? online.getGameProfile().getName() : member.getUuid().toString();
                serverPlayer.sendSystemMessage(Component.literal("§7- " + name + " (" + member.getRole().name() + ")"));
            }
            return;
        }

        if (slotId >= 18 && slotId < 27) {
            UUID target = slotToMember.get(slotId);
            if (target == null) {
                return;
            }
            try {
                if (button == 1) {
                    BigBangRegions.getInviteService().sendInvite(region, serverPlayer.getUUID(), target, RegionRole.OWNER, 86400000L);
                    serverPlayer.sendSystemMessage(Component.literal("§aPedido de transferência enviado. O novo dono precisa aceitar."));
                } else {
                    BigBangRegions.getMembershipService().removeMember(region, serverPlayer.getUUID(), target, false);
                    serverPlayer.sendSystemMessage(Component.literal("§aMembro removido."));
                }
                RegionGuiHandler.openMembersMenu(serverPlayer, region);
            } catch (Exception e) {
                serverPlayer.sendSystemMessage(Component.literal("§c" + e.getMessage()));
            }
        }
    }
}
