package com.bigbangcraft.regions.flag;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.domain.Region;

public class RegionFlagResolver extends FlagResolver {
    public EffectiveRegionPolicy resolve(Region region, String flagId, Config config) {
        return super.resolve(region, flagId, config);
    }
}
