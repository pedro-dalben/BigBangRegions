package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.domain.*;
import com.bigbangcraft.regions.audit.AuditService;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.region.RegionMembershipService;
import com.bigbangcraft.regions.region.RegionRoleResolver;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class RegionMemberRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    public void testSaveAndLoadRegionMembers() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("test_repo.db"));
        dbManager.initialize();
        RegionRepository repository = new RegionRepository(dbManager);

        UUID owner = UUID.randomUUID();
        UUID creator = UUID.randomUUID();
        UUID member1 = UUID.randomUUID();
        UUID leader1 = UUID.randomUUID();

        Map<UUID, RegionMember> members = new HashMap<>();
        members.put(member1, new RegionMember(member1, RegionRole.MEMBER, owner, 3000L, 4000L));
        members.put(leader1, new RegionMember(leader1, RegionRole.LEADER, owner, 5000L, 6000L));

        Region region = new Region("player_claim", "PlayerClaim", RegionType.PLAYER_REGION,
                new RegionBounds("minecraft:overworld", 0, 0, 0, 10, 10, 10), 100, owner, creator, 1000L, 2000L, "ACTIVE", members);

        repository.save(region);
        repository.saveMembers(region.getId(), region.getMembers());

        List<Region> loaded = repository.loadAll();
        assertEquals(1, loaded.size());
        Region loadedRegion = loaded.get(0);

        assertEquals("player_claim", loadedRegion.getId());
        assertEquals(owner, loadedRegion.getOwnerUuid());
        assertEquals(2, loadedRegion.getMembers().size());

        RegionMember loadedM1 = loadedRegion.getMembers().get(member1);
        assertNotNull(loadedM1);
        assertEquals(RegionRole.MEMBER, loadedM1.getRole());
        assertEquals(owner, loadedM1.getAddedByUuid());
        assertEquals(3000L, loadedM1.getCreatedAt());
        assertEquals(4000L, loadedM1.getUpdatedAt());

        RegionMember loadedL1 = loadedRegion.getMembers().get(leader1);
        assertNotNull(loadedL1);
        assertEquals(RegionRole.LEADER, loadedL1.getRole());
        assertEquals(owner, loadedL1.getAddedByUuid());
        assertEquals(5000L, loadedL1.getCreatedAt());
        assertEquals(6000L, loadedL1.getUpdatedAt());

        dbManager.close();
    }

    @Test
    public void memberKeepsPermissionAfterCacheReload() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(tempDir.resolve("reload_members.db"));
        dbManager.initialize();
        try {
            RegionRepository repository = new RegionRepository(dbManager);
            RegionCache regionCache = new RegionCache();
            RegionMembershipCache membershipCache = new RegionMembershipCache();
            RegionRoleResolver roleResolver = new RegionRoleResolver(membershipCache);
            RegionMembershipService service = new RegionMembershipService(
                repository, membershipCache, regionCache, mock(AuditService.class), roleResolver);

            UUID owner = UUID.randomUUID();
            UUID member = UUID.randomUUID();
            Region region = new Region("reload_claim", "ReloadClaim", RegionType.PLAYER_REGION,
                new RegionBounds("minecraft:overworld", 0, 0, 0, 10, 10, 10), 100,
                owner, owner, 1L, 1L, "ACTIVE");
            repository.save(region);
            regionCache.add(region);
            membershipCache.loadFromRegion(region);

            service.addMember(region, owner, member, RegionRole.MEMBER, false);
            regionCache.clear();
            membershipCache.clear();
            repository.reloadCaches(regionCache, membershipCache);

            Region reloaded = regionCache.get("reload_claim");
            assertNotNull(reloaded);
            assertEquals(RegionRole.MEMBER, roleResolver.resolveRole(reloaded, member));
            assertEquals(RegionRole.MEMBER, reloaded.getRole(member));
        } finally {
            dbManager.close();
        }
    }
}
