package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultPlotChunkPlanResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void physicalValidationPlanStaysWithinConfiguredChunkBudget() {
        ConfigManager configManager = new ConfigManager(tempDir);
        configManager.load();
        DefaultPlotChunkPlanResolver resolver = new DefaultPlotChunkPlanResolver(configManager);

        PlotFootprint footprint = new PlotFootprint(0, 127, 0, 127);
        RegionBuildGeometry geometry = new RegionBuildGeometry(List.of(footprint));

        ChunkPreparationPlan plan = resolver.resolve(footprint, geometry, PreparationPurpose.PHYSICAL_VALIDATION);

        assertTrue(plan.chunkCount() <= configManager.getConfig().getPlayerLandAllocation().getRegionPreparation().getMaxChunksPerPreparation());
        assertTrue(plan.requiredChunks().contains(new ChunkPos(3, 3)) || plan.requiredChunks().contains(new ChunkPos(4, 4)));
    }
}
