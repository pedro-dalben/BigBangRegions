package com.bigbangcraft.regions.allocation;

public interface VirtualFootprintValidator {
    VirtualBiomeValidationResult validate(
        WorldgenSearchContext context,
        BiomeOption biomeOption,
        PlotFootprint footprint,
        SearchBudget budget
    );
}
