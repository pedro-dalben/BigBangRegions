package com.bigbangcraft.regions.allocation;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

public class SimpleRegionChunkTicketManager implements RegionChunkTicketManager {
    private static final TicketType<UUID> PREPARATION_TICKET =
        TicketType.create("bigbangregions_preparation", Comparator.comparing(UUID::toString));
    private static final int PREPARATION_TICKET_LEVEL = 2;

    @Override
    public TicketLease acquire(net.minecraft.server.level.ServerLevel world, Set<ChunkPos> chunks, UUID requestId, long expiresAt) {
        ServerChunkCache chunkSource = world.getChunkSource();
        for (ChunkPos chunk : chunks) {
            chunkSource.addRegionTicket(PREPARATION_TICKET, chunk, PREPARATION_TICKET_LEVEL, requestId);
        }
        AllocationMetrics.increment("bigbangregions_ticket_acquired_total");
        return new TicketLease(world, Set.copyOf(chunks), requestId, expiresAt);
    }

    @Override
    public void release(TicketLease lease) {
        ServerChunkCache chunkSource = lease.world().getChunkSource();
        for (ChunkPos chunk : lease.chunks()) {
            chunkSource.removeRegionTicket(PREPARATION_TICKET, chunk, PREPARATION_TICKET_LEVEL, lease.requestId());
        }
        AllocationMetrics.increment("bigbangregions_ticket_released_total");
    }
}
