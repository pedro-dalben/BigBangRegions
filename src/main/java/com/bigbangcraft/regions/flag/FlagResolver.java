package com.bigbangcraft.regions.flag;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionType;

import java.util.Map;
import java.util.Optional;

public class FlagResolver {

    public EffectiveRegionPolicy resolve(Region region, String flagId, Config config) {
        Optional<RegionFlag> optFlag = FlagRegistry.get(flagId);
        FlagPolicy fallbackDefault = optFlag.map(RegionFlag::getDefaultValue).orElse(FlagPolicy.ALLOW);

        if (region != null) {
            // 1. Explicit flag value on the region
            String explicitValue = region.getFlagValue(flagId);
            if (explicitValue != null && !explicitValue.equalsIgnoreCase("INHERIT")) {
                FlagPolicy policy = FlagPolicy.parse(explicitValue);
                if (policy != FlagPolicy.INHERIT) {
                    return new EffectiveRegionPolicy(policy, "region_explicit", region);
                }
            }

            // 2. Policy default for region type
            Map<String, String> typeDefaults = getTypeDefaults(region.getType(), config);
            if (typeDefaults != null && typeDefaults.containsKey(flagId)) {
                FlagPolicy policy = FlagPolicy.parse(typeDefaults.get(flagId));
                if (policy != FlagPolicy.INHERIT) {
                    return new EffectiveRegionPolicy(policy, "region_type_default", region);
                }
            }

            // 3. Global policy default
            Map<String, String> globalDefaults = config.getDefaults().getGlobal();
            if (globalDefaults.containsKey(flagId)) {
                FlagPolicy policy = FlagPolicy.parse(globalDefaults.get(flagId));
                if (policy != FlagPolicy.INHERIT) {
                    return new EffectiveRegionPolicy(policy, "global_default", region);
                }
            }

            // 4. Mod fallback default
            return new EffectiveRegionPolicy(fallbackDefault, "mod_default", region);
        } else {
            // No region: check global defaults in config
            Map<String, String> globalDefaults = config.getDefaults().getGlobal();
            if (globalDefaults.containsKey(flagId)) {
                FlagPolicy policy = FlagPolicy.parse(globalDefaults.get(flagId));
                if (policy != FlagPolicy.INHERIT) {
                    return new EffectiveRegionPolicy(policy, "global_default", null);
                }
            }
            return new EffectiveRegionPolicy(fallbackDefault, "mod_default", null);
        }
    }

    private Map<String, String> getTypeDefaults(RegionType type, Config config) {
        if (type == RegionType.ADMIN_REGION) {
            return config.getDefaults().getAdminRegion();
        }
        if (type == RegionType.PLAYER_REGION) {
            return config.getDefaults().getPlayerRegion();
        }
        return null;
    }
}
