package com.bigbangcraft.regions.allocation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.UUID;

public record TicketLease(
    ServerLevel world,
    Set<ChunkPos> chunks,
    UUID requestId,
    long expiresAt
) {
}
