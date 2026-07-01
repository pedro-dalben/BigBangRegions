package com.bigbangcraft.regions.allocation;

import net.minecraft.world.level.ChunkPos;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

final class ChunkPreparationPlanCodec {
    private ChunkPreparationPlanCodec() {
    }

    static String encode(ChunkPreparationPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append(plan.purpose().name()).append('|').append(plan.timeout().toSeconds()).append('|');
        boolean first = true;
        for (ChunkPos chunk : plan.requiredChunks()) {
            if (!first) {
                builder.append(';');
            }
            builder.append(chunk.x).append(',').append(chunk.z);
            first = false;
        }
        return builder.toString();
    }

    static ChunkPreparationPlan decode(String raw) {
        String[] parts = raw.split("\\|", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid chunk preparation plan payload");
        }
        PreparationPurpose purpose = PreparationPurpose.valueOf(parts[0]);
        Duration timeout = Duration.ofSeconds(Long.parseLong(parts[1]));
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        if (!parts[2].isBlank()) {
            for (String token : parts[2].split(";")) {
                String[] pair = token.split(",", 2);
                chunks.add(new ChunkPos(Integer.parseInt(pair[0]), Integer.parseInt(pair[1])));
            }
        }
        return new ChunkPreparationPlan(chunks, timeout, purpose);
    }
}
