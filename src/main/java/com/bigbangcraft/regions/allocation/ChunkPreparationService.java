package com.bigbangcraft.regions.allocation;

import net.minecraft.server.level.ServerLevel;

public interface ChunkPreparationService {
    PreparationHandle beginPreparation(
        ServerLevel world,
        ReservedPlotCandidate candidate,
        ChunkPreparationPlan plan,
        PreparationCompletionCallback callback
    );

    void cancelPreparation(
        PreparationHandle handle,
        PreparationCancelReason reason
    );

    void tick();
}
