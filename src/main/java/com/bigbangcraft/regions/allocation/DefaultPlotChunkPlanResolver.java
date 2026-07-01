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
        int maxChunks = configManager.getConfig().getPlayerLandAllocation().getRegionPreparation().getMaxChunksPerPreparation();
        Set<ChunkPos> chunks = purpose == PreparationPurpose.PHYSICAL_VALIDATION
            ? resolveValidationChunks(footprint, maxChunks)
            : resolveAllChunks(geometry);

        if (chunks.size() > maxChunks) {
            throw new IllegalStateException("Chunk preparation plan exceeds configured limit: " + chunks.size() + " > " + maxChunks);
        }

        int timeoutSeconds = configManager.getConfig().getPlayerLandAllocation().getRegionPreparation().getTimeoutSeconds();
        return new ChunkPreparationPlan(chunks, Duration.ofSeconds(timeoutSeconds), purpose);
    }

    private Set<ChunkPos> resolveAllChunks(RegionBuildGeometry geometry) {
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
        return chunks;
    }

    private Set<ChunkPos> resolveValidationChunks(PlotFootprint footprint, int maxChunks) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        int minChunkX = footprint.minX() >> 4;
        int maxChunkX = footprint.maxX() >> 4;
        int minChunkZ = footprint.minZ() >> 4;
        int maxChunkZ = footprint.maxZ() >> 4;
        int centerChunkX = footprint.centerX() >> 4;
        int centerChunkZ = footprint.centerZ() >> 4;

        chunks.add(new ChunkPos(centerChunkX, centerChunkZ));
        for (int radius = 1; chunks.size() < maxChunks; radius++) {
            boolean addedAny = false;
            for (int dz = -radius; dz <= radius && chunks.size() < maxChunks; dz++) {
                for (int dx = -radius; dx <= radius && chunks.size() < maxChunks; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int chunkX = centerChunkX + dx;
                    int chunkZ = centerChunkZ + dz;
                    if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
                        continue;
                    }
                    chunks.add(new ChunkPos(chunkX, chunkZ));
                    addedAny = true;
                }
            }
            if (!addedAny) {
                break;
            }
        }
        return chunks;
    }
}
