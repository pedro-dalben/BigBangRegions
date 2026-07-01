package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.world.level.ChunkPos;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultPlotChunkPlanResolver implements PlotChunkPlanResolver {
    private final ConfigManager configManager;

    public DefaultPlotChunkPlanResolver(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public ChunkPreparationPlan resolve(PlotFootprint footprint, RegionBuildGeometry geometry, PreparationPurpose purpose) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (PlotFootprint included : geometry.includedFootprints()) {
            int minChunkX = included.minX() >> 4;
            int maxChunkX = included.maxX() >> 4;
            int minChunkZ = included.minZ() >> 4;
            int maxChunkZ = included.maxZ() >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    chunks.add(new ChunkPos(chunkX, chunkZ));
                }
            }
        }

        int maxChunks = configManager.getConfig().getPlayerLandAllocation().getRegionPreparation().getMaxChunksPerPreparation();
        if (chunks.size() > maxChunks) {
            throw new IllegalStateException("Chunk preparation plan exceeds configured limit: " + chunks.size() + " > " + maxChunks);
        }

        int timeoutSeconds = configManager.getConfig().getPlayerLandAllocation().getRegionPreparation().getTimeoutSeconds();
        return new ChunkPreparationPlan(chunks, Duration.ofSeconds(timeoutSeconds), purpose);
    }
}
