package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.domain.RegionBounds;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InitialBorderPipelinePlanTest {
    @Test
    void creationPlanDoesNotRecaptureOrRemoveTheAlreadySnapshottedTerrain() {
        var plan = ExpansionVisualPipeline.initialBorderPlan("r1", "op1", 1L,
            new RegionBounds("minecraft:overworld", 0, -64, 0, 9, 320, 9),
            new Config.BorderConfig(), Path.of("build", "test-snapshots"));

        assertTrue(plan.captureColumns().isEmpty());
        assertTrue(plan.removeColumns().isEmpty());
        assertFalse(plan.applyColumns().isEmpty());
    }
}
