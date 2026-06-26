package com.bigbangcraft.regions.util;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SelectionManager {
    public static class Selection {
        private final BlockPos pos;
        private final String dimension;

        public Selection(BlockPos pos, String dimension) {
            this.pos = pos;
            this.dimension = dimension;
        }

        public BlockPos getPos() {
            return pos;
        }

        public String getDimension() {
            return dimension;
        }
    }

    private final Map<UUID, Selection> pos1Map = new ConcurrentHashMap<>();
    private final Map<UUID, Selection> pos2Map = new ConcurrentHashMap<>();

    public void setPos1(UUID playerId, BlockPos pos, String dimension) {
        if (pos == null) {
            pos1Map.remove(playerId);
        } else {
            pos1Map.put(playerId, new Selection(pos, dimension));
        }
    }

    public void setPos2(UUID playerId, BlockPos pos, String dimension) {
        if (pos == null) {
            pos2Map.remove(playerId);
        } else {
            pos2Map.put(playerId, new Selection(pos, dimension));
        }
    }

    public Selection getPos1(UUID playerId) {
        return pos1Map.get(playerId);
    }

    public Selection getPos2(UUID playerId) {
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
