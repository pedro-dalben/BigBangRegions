package com.bigbangcraft.regions.flag;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class FlagResolverTest {
    private FlagResolver flagResolver;
    private Config config;
    private Region region;
    private UUID creator;

    @BeforeEach
    public void setUp() {
        flagResolver = new FlagResolver();
        config = new Config(); // Defaults loaded: build=ALLOW in global, DENY in adminRegion
        creator = UUID.randomUUID();
        region = new Region("testReg", "Test Region", RegionType.ADMIN_REGION,
                new RegionBounds("overworld", 0, 0, 0, 10, 10, 10), 1000, null, creator, 0, 0, "ACTIVE");
    }

    @Test
    public void testExplicitRegionFlagOverrides() {
        // Explicit DENY
        region.setFlag("visitor-build", "DENY");
        EffectiveRegionPolicy policy = flagResolver.resolve(region, "visitor-build", config);
        assertEquals(FlagPolicy.DENY, policy.policy());
        assertEquals("region_explicit", policy.source());

        // Explicit ALLOW
        region.setFlag("visitor-build", "ALLOW");
        policy = flagResolver.resolve(region, "visitor-build", config);
        assertEquals(FlagPolicy.ALLOW, policy.policy());
        assertEquals("region_explicit", policy.source());
    }

    @Test
    public void testInheritanceToRegionTypeDefaults() {
        // Region has INHERIT (or unset) -> Should fallback to type adminRegion's default (DENY for visitor-build)
        region.setFlag("visitor-build", "INHERIT");
        EffectiveRegionPolicy policy = flagResolver.resolve(region, "visitor-build", config);
        assertEquals(FlagPolicy.DENY, policy.policy());
        assertEquals("region_type_default", policy.source());
    }

    @Test
    public void testInheritanceToGlobalDefaults() {
        Region playerRegion = new Region("testReg", "Test Region", RegionType.PLAYER_REGION,
                new RegionBounds("overworld", 0, 0, 0, 10, 10, 10), 100, UUID.randomUUID(), creator, 0, 0, "ACTIVE");
        EffectiveRegionPolicy policy = flagResolver.resolve(playerRegion, "visitor-pickup-items", config);
        assertEquals(FlagPolicy.ALLOW, policy.policy());
        assertEquals("region_type_default", policy.source());
    }

    @Test
    public void testInheritanceWithNoRegion() {
        // Null region (Global coordinate check)
        EffectiveRegionPolicy policy = flagResolver.resolve(null, "visitor-build", config);
        assertEquals(FlagPolicy.ALLOW, policy.policy()); // global config is ALLOW
        assertEquals("global_default", policy.source());
    }

    @Test
    public void testExplosionDefaults() {
        EffectiveRegionPolicy globalPolicy = flagResolver.resolve(null, "explosion-block-damage", config);
        assertEquals(FlagPolicy.ALLOW, globalPolicy.policy());
        assertEquals("global_default", globalPolicy.source());

        Region playerRegion = new Region("playerReg", "Player Region", RegionType.PLAYER_REGION,
                new RegionBounds("overworld", 0, 0, 0, 10, 10, 10), 100, UUID.randomUUID(), creator, 0, 0, "ACTIVE");
        EffectiveRegionPolicy playerPolicy = flagResolver.resolve(playerRegion, "explosion-block-damage", config);
        assertEquals(FlagPolicy.DENY, playerPolicy.policy());
        assertEquals("region_type_default", playerPolicy.source());

        EffectiveRegionPolicy adminPolicy = flagResolver.resolve(region, "explosion-block-damage", config);
        assertEquals(FlagPolicy.DENY, adminPolicy.policy());
        assertEquals("region_type_default", adminPolicy.source());
    }

    @Test
    public void testModFallback() {
        // Query an unregistered / fake flag
        EffectiveRegionPolicy policy = flagResolver.resolve(null, "non-existent-flag-id", config);
        assertEquals(FlagPolicy.ALLOW, policy.policy()); // unregistered flags fallback to ALLOW in FlagResolver
        assertEquals("mod_default", policy.source());
    }
}
