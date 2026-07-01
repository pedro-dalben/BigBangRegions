package com.bigbangcraft.regions.allocation;

public record ReservedPlotCandidate(
    String requestId,
    String slotId,
    String biomeOptionKey,
    String dimensionKey,
    PlotFootprint footprint
) {
}
