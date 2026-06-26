package com.bigbangcraft.regions.membership;

import com.bigbangcraft.regions.audit.AuditService;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.region.RegionMembershipService;
import com.bigbangcraft.regions.region.RegionRoleResolver;
import com.bigbangcraft.regions.repository.RegionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RegionMembershipServiceTest {
    private RegionRepository repository;
    private RegionMembershipCache cache;
    private AuditService auditService;
    private RegionRoleResolver roleResolver;
    private RegionMembershipService service;

    private Region region;
    private UUID owner;
    private UUID leader;
    private UUID member;
    private UUID visitor;

    @BeforeEach
    public void setUp() {
        repository = mock(RegionRepository.class);
        cache = new RegionMembershipCache();
        auditService = mock(AuditService.class);
        roleResolver = new RegionRoleResolver(cache);
        service = new RegionMembershipService(repository, cache, auditService, roleResolver);

        owner = UUID.randomUUID();
        leader = UUID.randomUUID();
        member = UUID.randomUUID();
        visitor = UUID.randomUUID();

        region = new Region("reg1", "PlayerClaim", RegionType.PLAYER_REGION,
                new RegionBounds("overworld", 0, 0, 0, 10, 10, 10), 100, owner, UUID.randomUUID(), 0, 0, "ACTIVE");
        cache.loadFromRegion(region);
    }

    @Test
    public void testOwnerCannotBeAddedAsMember() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.addMember(region, owner, owner, RegionRole.MEMBER, false);
        });
        verifyNoInteractions(auditService);
    }

    @Test
    public void testCannotAddMemberTwice() {
        service.addMember(region, owner, member, RegionRole.MEMBER, false);
        assertThrows(IllegalArgumentException.class, () -> {
            service.addMember(region, owner, member, RegionRole.MEMBER, false);
        });
    }

    @Test
    public void testPromotionAndDemotion() {
        service.addMember(region, owner, member, RegionRole.MEMBER, false);
        assertEquals(RegionRole.MEMBER, roleResolver.resolveRole(region, member));

        service.setRole(region, owner, member, RegionRole.LEADER, false);
        assertEquals(RegionRole.LEADER, roleResolver.resolveRole(region, member));
        verify(auditService).log(eq("reg1"), eq(owner), eq("PROMOTE_MEMBER"), anyString(), anyString(), anyString());

        service.setRole(region, owner, member, RegionRole.MEMBER, false);
        assertEquals(RegionRole.MEMBER, roleResolver.resolveRole(region, member));
        verify(auditService).log(eq("reg1"), eq(owner), eq("DEMOTE_LEADER"), anyString(), anyString(), anyString());
    }

    @Test
    public void testRemoveMember() {
        service.addMember(region, owner, member, RegionRole.MEMBER, false);
        assertEquals(RegionRole.MEMBER, roleResolver.resolveRole(region, member));

        service.removeMember(region, owner, member, false);
        assertEquals(RegionRole.VISITOR, roleResolver.resolveRole(region, member));
        verify(auditService).log(eq("reg1"), eq(owner), eq("REMOVE_MEMBER"), anyString(), isNull(), anyString());
    }

    @Test
    public void testMemberLeave() {
        service.addMember(region, owner, member, RegionRole.MEMBER, false);
        assertEquals(RegionRole.MEMBER, roleResolver.resolveRole(region, member));

        service.leaveRegion(region, member);
        assertEquals(RegionRole.VISITOR, roleResolver.resolveRole(region, member));
        verify(auditService).log(eq("reg1"), eq(member), eq("MEMBER_LEAVE"), anyString(), isNull(), anyString());
    }

    @Test
    public void testOwnerCannotLeave() {
        assertThrows(IllegalArgumentException.class, () -> {
            service.leaveRegion(region, owner);
        });
    }

    @Test
    public void testLeaderHierarchyRestrictions() {
        service.addMember(region, owner, leader, RegionRole.LEADER, false);

        assertThrows(IllegalArgumentException.class, () -> {
            service.addMember(region, leader, visitor, RegionRole.LEADER, false);
        });

        service.addMember(region, leader, visitor, RegionRole.MEMBER, false);
        assertEquals(RegionRole.MEMBER, roleResolver.resolveRole(region, visitor));

        assertThrows(IllegalArgumentException.class, () -> {
            service.setRole(region, leader, visitor, RegionRole.LEADER, false);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            service.removeMember(region, leader, owner, false);
        });
    }
}
