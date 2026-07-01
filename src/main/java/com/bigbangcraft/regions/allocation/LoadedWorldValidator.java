package com.bigbangcraft.regions.allocation;

import net.minecraft.server.level.ServerLevel;

public interface LoadedWorldValidator {
    LoadedWorldValidationResult validate(
        ServerLevel world,
        ReservedPlotCandidate candidate,
        ChunkPreparationPlan preparedPlan
    );
}
