package com.bigbangcraft.regions.journeymap;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface RegionMapIntegration {
    void onPlayerJoin(ServerPlayer player);
}
