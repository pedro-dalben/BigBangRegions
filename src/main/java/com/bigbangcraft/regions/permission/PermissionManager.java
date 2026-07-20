package com.bigbangcraft.regions.permission;

import net.minecraft.server.level.ServerPlayer;
import me.lucko.fabric.api.permissions.v0.Permissions;

public class PermissionManager {
    private final int operatorLevelFallback;

    public PermissionManager(int operatorLevelFallback) {
        this.operatorLevelFallback = operatorLevelFallback;
    }

    public boolean hasPermission(ServerPlayer player, String node) {
        if (player == null) return false;
        
        try {
            // Check via fabric-permissions-api, falling back to OP status
            return Permissions.check(player, node, player.hasPermissions(operatorLevelFallback));
        } catch (Throwable t) {
            // Safe fallback if fabric-permissions-api is not active or throws
            return player.hasPermissions(operatorLevelFallback);
        }
    }

    public boolean hasBypass(ServerPlayer player, String flagId) {
        if (player == null) return false;
        return hasPermission(player, "bigbangregions.bypass") || 
               hasPermission(player, "bigbangregions.bypass." + flagId.toLowerCase());
    }

    public int chunkLoaderPermissionCredits(ServerPlayer player) {
        int credits = 0;
        for (int i = 1; i <= 256; i++) {
            if (hasPermission(player, "bigbangregions.chunkloader." + i)) credits = Math.max(credits, i);
        }
        return credits;
    }
}
