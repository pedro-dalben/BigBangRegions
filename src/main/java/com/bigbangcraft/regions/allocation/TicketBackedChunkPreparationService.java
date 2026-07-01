package com.bigbangcraft.regions.allocation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TicketBackedChunkPreparationService implements ChunkPreparationService {
    private record ActivePreparation(
        PreparationHandle handle,
        ServerLevel world,
        ReservedPlotCandidate candidate,
        ChunkPreparationPlan plan,
        TicketLease lease,
        PreparationCompletionCallback callback,
        long timeoutAt,
        boolean readyNotified
    ) {
        ActivePreparation withReadyNotified() {
            return new ActivePreparation(handle, world, candidate, plan, lease, callback, timeoutAt, true);
        }
    }

    private final RegionChunkTicketManager ticketManager;
    private final Map<String, ActivePreparation> activePreparations = new ConcurrentHashMap<>();

    public TicketBackedChunkPreparationService(RegionChunkTicketManager ticketManager) {
        this.ticketManager = ticketManager;
    }

    @Override
    public PreparationHandle beginPreparation(ServerLevel world, ReservedPlotCandidate candidate, ChunkPreparationPlan plan, PreparationCompletionCallback callback) {
        String handleId = UUID.randomUUID().toString();
        PreparationHandle handle = new PreparationHandle(handleId, candidate.requestId());
        long timeoutAt = System.currentTimeMillis() + plan.timeout().toMillis();
        TicketLease lease = ticketManager.acquire(world, plan.requiredChunks(), UUID.fromString(candidate.requestId()), timeoutAt);
        activePreparations.put(handleId, new ActivePreparation(handle, world, candidate, plan, lease, callback, timeoutAt, false));
        AllocationMetrics.increment("bigbangregions_prepare_started_total");
        AllocationMetrics.setGauge("bigbangregions_prepare_chunk_count", plan.chunkCount());
        return handle;
    }

    @Override
    public void cancelPreparation(PreparationHandle handle, PreparationCancelReason reason) {
        ActivePreparation active = activePreparations.remove(handle.id());
        if (active != null) {
            ticketManager.release(active.lease());
            if (reason == PreparationCancelReason.CANCELLED) {
                AllocationMetrics.increment("bigbangregions_prepare_cancelled_total");
            }
        }
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ActivePreparation> entry : activePreparations.entrySet()) {
            ActivePreparation active = entry.getValue();
            if (now >= active.timeoutAt()) {
                if (activePreparations.remove(entry.getKey(), active)) {
                    ticketManager.release(active.lease());
                    AllocationMetrics.increment("bigbangregions_prepare_timeout_total");
                    active.callback().onCompleted(active.handle(), PreparationResult.failed(PreparationResultType.TIMEOUT, "Chunk preparation timed out"));
                }
                continue;
            }

            if (active.readyNotified()) {
                continue;
            }

            boolean ready = true;
            for (ChunkPos chunk : active.plan().requiredChunks()) {
                if (active.world().getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                    ready = false;
                    break;
                }
            }

            if (ready) {
                ActivePreparation updated = active.withReadyNotified();
                if (activePreparations.replace(entry.getKey(), active, updated)) {
                    active.callback().onCompleted(active.handle(), PreparationResult.ready());
                }
            }
        }
    }
}
