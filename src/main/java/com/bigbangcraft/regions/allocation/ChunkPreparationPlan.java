package com.bigbangcraft.regions.allocation;

import net.minecraft.world.level.ChunkPos;

import java.time.Duration;
import java.util.Set;

public record ChunkPreparationPlan(
    Set<ChunkPos> requiredChunks,
    Duration timeout,
    PreparationPurpose purpose
) {
    public ChunkPreparationPlan {
        requiredChunks = Set.copyOf(requiredChunks);
        if (requiredChunks.isEmpty()) {
            throw new IllegalArgumentException("requiredChunks cannot be empty");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public int chunkCount() {
        return requiredChunks.size();
    }
}
