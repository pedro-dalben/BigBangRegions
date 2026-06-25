package com.bigbangcraft.regions.util;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SelectionManager {
    private final Map<UUID, BlockPos> pos1Map = new ConcurrentHashMap<>();
    private final Map<UUID, BlockPos> pos2Map = new ConcurrentHashMap<>();

    public void setPos1(UUID playerId, BlockPos pos) {
        if (pos == null) {
            pos1Map.remove(playerId);
        } else {
            pos1Map.put(playerId, pos);
        }
    }

    public void setPos2(UUID playerId, BlockPos pos) {
        if (pos == null) {
            pos2Map.remove(playerId);
        } else {
            pos2Map.put(playerId, pos);
        }
    }

    public BlockPos getPos1(UUID playerId) {
        return pos1Map.get(playerId);
    }

    public BlockPos getPos2(UUID playerId) {
        return pos2Map.get(playerId);
    }

    public boolean hasSelection(UUID playerId) {
        return pos1Map.containsKey(playerId) && pos2Map.containsKey(playerId);
    }

    public void clear(UUID playerId) {
        pos1Map.remove(playerId);
        pos2Map.remove(playerId);
    }
}
