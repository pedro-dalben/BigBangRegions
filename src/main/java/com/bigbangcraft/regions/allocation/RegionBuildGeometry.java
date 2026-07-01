package com.bigbangcraft.regions.allocation;

import java.util.List;

public record RegionBuildGeometry(List<PlotFootprint> includedFootprints) {
    public RegionBuildGeometry {
        includedFootprints = List.copyOf(includedFootprints);
    }
}
