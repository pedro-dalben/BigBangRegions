package com.bigbangcraft.regions.membership;

import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.region.RegionRoleResolver;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class RegionRoleResolverTest {

    @Test
    public void testRoleResolution() {
        RegionMembershipCache cache = new RegionMembershipCache();
        RegionRoleResolver resolver = new RegionRoleResolver(cache);

        UUID owner = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();

        Region region = new Region("reg1", "Player Claim", RegionType.PLAYER_REGION,
                new RegionBounds("overworld", 0, 0, 0, 10, 10, 10), 100, owner, UUID.randomUUID(), 0, 0, "ACTIVE");

        // Set up memberships
        region.setMember(new RegionMember(leader, RegionRole.LEADER, owner, 0, 0));
        region.setMember(new RegionMember(member, RegionRole.MEMBER, owner, 0, 0));

        // Load into cache
        cache.loadFromRegion(region);

        assertEquals(RegionRole.OWNER, resolver.resolveRole(region, owner));
        assertEquals(RegionRole.LEADER, resolver.resolveRole(region, leader));
        assertEquals(RegionRole.MEMBER, resolver.resolveRole(region, member));
        assertEquals(RegionRole.VISITOR, resolver.resolveRole(region, visitor));
        assertEquals(RegionRole.VISITOR, resolver.resolveRole(null, owner));
    }
}
