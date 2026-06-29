package com.bigbangcraft.regions.journeymap;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerMapPreference {
    private static final Map<UUID, Boolean> showOwnRegion = new ConcurrentHashMap<>();

    public static boolean isShowOwnRegion(UUID playerUuid) {
        return showOwnRegion.getOrDefault(playerUuid, true);
    }

    public static void setShowOwnRegion(UUID playerUuid, boolean show) {
        if (show) {
            showOwnRegion.remove(playerUuid);
        } else {
            showOwnRegion.put(playerUuid, false);
        }
    }

    public static void removePlayer(UUID playerUuid) {
        showOwnRegion.remove(playerUuid);
    }
}
