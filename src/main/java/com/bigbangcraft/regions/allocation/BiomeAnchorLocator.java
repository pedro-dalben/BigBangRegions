package com.bigbangcraft.regions.allocation;

public interface BiomeAnchorLocator {
    BiomeAnchorSearchStepResult searchStep(
        WorldgenSearchContext context,
        BiomeOption biomeOption,
        AllocationSearchCursor cursor,
        SearchBudget budget
    );
}
