package com.bigbangcraft.regions.allocation;

public interface PlotChunkPlanResolver {
    ChunkPreparationPlan resolve(
        PlotFootprint footprint,
        RegionBuildGeometry geometry,
        PreparationPurpose purpose
    );
}
