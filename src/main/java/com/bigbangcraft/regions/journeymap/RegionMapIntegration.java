package com.bigbangcraft.regions.journeymap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public interface RegionMapIntegration {
    void onPlayerJoin(ServerPlayer player);

    default void onPlayerDisconnect(ServerPlayer player) {
    }

    default void clearAllPlayers(MinecraftServer server) {
    }

    default void close() {
    }
}
