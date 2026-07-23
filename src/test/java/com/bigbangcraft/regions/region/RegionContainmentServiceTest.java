package com.bigbangcraft.regions.region;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionContainmentServiceTest {
    @Test
    void pwarpsRequireThePlayerRegionOwnerAndTemporaryStayExpires() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        String dimension = "minecraft:overworld";

        Region region = new Region("claim", "Claim", RegionType.PLAYER_REGION,
            new RegionBounds(dimension, 0, 0, 0, 10, 10, 10), 100,
            owner, owner, 0, 0, "ACTIVE");
        RegionMembershipCache memberships = new RegionMembershipCache();
        memberships.loadFromRegion(region);
        RegionContainmentService service = new RegionContainmentService(
            new ConfigManager(Files.createTempDirectory("regions-config")),
            cache(region), new RegionRoleResolver(memberships));

        assertTrue(service.canCreatePlayerWarp(owner, dimension, 5, 5, 5));
        assertFalse(service.canCreatePlayerWarp(member, dimension, 5, 5, 5));
        assertFalse(service.canCreatePlayerWarp(visitor, dimension, 5, 5, 5));
        assertTrue(service.canUsePlayerWarp(owner, dimension, 5, 5, 5));
        assertFalse(service.canUsePlayerWarp(member, dimension, 5, 5, 5));
        assertTrue(service.canCreatePlayerWarp(visitor, dimension, 50, 5, 50));

        service.recordPlayerWarpArrival(visitor, owner, dimension, 5, 5, 5);
        assertTrue(service.isAuthorizedPwarpStay(visitor, dimension, 5, 5, 5));
        assertFalse(service.isAuthorizedPwarpStay(visitor, dimension, 50, 5, 50));

        service.recordPlayerWarpArrival(visitor, owner, dimension, 5, 5, 5);
        service.removePlayer(visitor);
        assertFalse(service.isAuthorizedPwarpStay(visitor, dimension, 5, 5, 5));
    }

    private static RegionCache cache(Region region) {
        RegionCache cache = new RegionCache();
        cache.add(region);
        return cache;
    }
}
