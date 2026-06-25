package com.bigbangcraft.regions.flag;

import com.bigbangcraft.regions.domain.Region;

public record EffectiveRegionPolicy(
    FlagPolicy policy,
    String source,
    Region region
) {
    public boolean isAllowed() {
        return policy == FlagPolicy.ALLOW;
    }
}
