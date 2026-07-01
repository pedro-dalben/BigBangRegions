package com.bigbangcraft.regions.allocation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.UUID;

public interface RegionChunkTicketManager {
    TicketLease acquire(
        ServerLevel world,
        Set<ChunkPos> chunks,
        UUID requestId,
        long expiresAt
    );

    void release(TicketLease lease);
}
