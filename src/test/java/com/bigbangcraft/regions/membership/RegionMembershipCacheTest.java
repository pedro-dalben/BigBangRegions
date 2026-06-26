package com.bigbangcraft.regions.membership;

import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class RegionMembershipCacheTest {

    @Test
    public void testCacheOperations() {
        RegionMembershipCache cache = new RegionMembershipCache();

        UUID owner = UUID.randomUUID();
        UUID user = UUID.randomUUID();

        Map<UUID, RegionMember> members = new HashMap<>();
        members.put(user, new RegionMember(user, RegionRole.MEMBER, owner, 0, 0));

        Region region = new Region("reg1", "Claim", RegionType.PLAYER_REGION,
                new RegionBounds("overworld", 0, 0, 0, 5, 5, 5), 100, owner, UUID.randomUUID(), 0, 0, "ACTIVE", members);

        assertEquals(RegionRole.VISITOR, cache.getRole("reg1", user, owner));

        cache.loadFromRegion(region);
        assertEquals(RegionRole.MEMBER, cache.getRole("reg1", user, owner));

        cache.updateMember("reg1", user, RegionRole.LEADER);
        assertEquals(RegionRole.LEADER, cache.getRole("reg1", user, owner));

        assertEquals(RegionRole.OWNER, cache.getRole("reg1", owner, owner));

        cache.updateMember("reg1", user, null);
        assertEquals(RegionRole.VISITOR, cache.getRole("reg1", user, owner));

        cache.updateMember("reg1", user, RegionRole.MEMBER);
        cache.clear();
        assertEquals(RegionRole.VISITOR, cache.getRole("reg1", user, owner));
    }
}
