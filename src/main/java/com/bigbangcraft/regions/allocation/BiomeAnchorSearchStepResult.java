package com.bigbangcraft.regions.allocation;

public sealed interface BiomeAnchorSearchStepResult {
    record Found(BiomeAnchor anchor, AllocationSearchCursor nextCursor) implements BiomeAnchorSearchStepResult {}
    record Continue(AllocationSearchCursor nextCursor, AnchorSearchProgress progress) implements BiomeAnchorSearchStepResult {}
    record Exhausted(AllocationSearchCursor nextCursor, AnchorSearchProgress progress) implements BiomeAnchorSearchStepResult {}
}
