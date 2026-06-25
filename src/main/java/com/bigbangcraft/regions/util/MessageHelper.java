package com.bigbangcraft.regions.util;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.protection.RegionAction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MessageHelper {
    private static final long COOLDOWN_MS = 1500; // 1.5 seconds cooldown
    private static final Map<String, Long> lastMessageTimes = new ConcurrentHashMap<>();

    public static void sendDenial(ServerPlayer player, RegionAction action, Region region) {
        if (player == null) return;

        UUID playerUuid = player.getUUID();
        String regionId = region != null ? region.getId() : "global";
        String key = playerUuid.toString() + ":" + action.name() + ":" + regionId;

        long now = System.currentTimeMillis();
        Long lastTime = lastMessageTimes.get(key);

        if (lastTime != null && (now - lastTime) < COOLDOWN_MS) {
            return;
        }

        lastMessageTimes.put(key, now);

        String message = getActionMessage(action, region);
        player.sendSystemMessage(
            Component.literal(message).withStyle(ChatFormatting.RED),
            true // True displays on action bar
        );
    }

    private static String getActionMessage(RegionAction action, Region region) {
        String suffix = region != null ? " (" + region.getName() + ")" : "";
        switch (action) {
            case BLOCK_BREAK:
            case BLOCK_PLACE:
                return "Você não pode construir nesta região" + suffix + ".";
            case CONTAINER:
                return "Você não pode acessar este container" + suffix + ".";
            case DOOR:
                return "Você não pode usar portas nesta região" + suffix + ".";
            case REDSTONE:
                return "Você não pode usar mecanismos de redstone nesta região" + suffix + ".";
            case PVP:
                return "PvP está desativado nesta região" + suffix + ".";
            case ITEM_PICKUP:
                return "Você não pode coletar itens nesta região" + suffix + ".";
            case ITEM_DROP:
                return "Você não pode dropar itens nesta região" + suffix + ".";
            case ENTITY_INTERACT:
                return "Você não pode interagir com entidades nesta região" + suffix + ".";
            default:
                return "Ação bloqueada nesta região" + suffix + ".";
        }
    }

    // Clean up cache to prevent memory leak
    public static void cleanCache() {
        long now = System.currentTimeMillis();
        lastMessageTimes.entrySet().removeIf(entry -> (now - entry.getValue()) > COOLDOWN_MS * 2);
    }
}
