package com.bigbangcraft.regions.invite;

import com.bigbangcraft.regions.audit.AuditService;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.region.RegionInviteService;
import com.bigbangcraft.regions.region.RegionRoleResolver;
import com.bigbangcraft.regions.repository.RegionInviteRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class RegionInviteServiceTest {
    private RegionInviteRepository inviteRepository;
    private RegionRepository regionRepository;
    private RegionCache regionCache;
    private RegionMembershipCache membershipCache;
    private AuditService auditService;
    private RegionRoleResolver roleResolver;
    private RegionInviteService service;

    private Region region;
    private UUID owner;
    private UUID invited;

    @BeforeEach
    public void setUp() {
        inviteRepository = mock(RegionInviteRepository.class);
        regionRepository = mock(RegionRepository.class);
        regionCache = mock(RegionCache.class);
        membershipCache = new RegionMembershipCache();
        auditService = mock(AuditService.class);
        roleResolver = new RegionRoleResolver(membershipCache);
        service = new RegionInviteService(inviteRepository, regionRepository, regionCache, membershipCache, roleResolver, auditService);

        owner = UUID.randomUUID();
        invited = UUID.randomUUID();
        region = new Region("reg1", "PlayerClaim", RegionType.PLAYER_REGION,
            new RegionBounds("overworld", 0, 0, 0, 10, 10, 10), 100, owner, UUID.randomUUID(), 0, 0, "ACTIVE");
        membershipCache.loadFromRegion(region);
    }

    @Test
    public void sendsInviteAndRejectsDuplicates() {
        when(inviteRepository.getPendingDuplicates("reg1", invited)).thenReturn(Collections.emptyList());

        service.sendInvite(region, owner, invited, RegionRole.MEMBER, 1000L);
        verify(inviteRepository).save(any());

        when(inviteRepository.getPendingDuplicates("reg1", invited)).thenReturn(List.of(mock(RegionInvite.class)));
        assertThrows(IllegalStateException.class, () -> service.sendInvite(region, owner, invited, RegionRole.MEMBER, 1000L));
    }

    @Test
    public void acceptsInviteIntoRegion() {
        RegionInvite invite = new RegionInvite("inv1", "reg1", invited, owner, RegionRole.MEMBER,
            InviteStatus.PENDING, System.currentTimeMillis(), System.currentTimeMillis() + 1000L, null, null);
        when(inviteRepository.get("inv1")).thenReturn(invite);
        when(regionCache.get("reg1")).thenReturn(region);
        when(inviteRepository.getPendingDuplicates("reg1", invited)).thenReturn(Collections.emptyList());

        service.acceptInvite("inv1", invited);

        verify(regionRepository).saveMembers(eq("reg1"), anyMap());
        verify(inviteRepository).updateStatus(eq("inv1"), eq(InviteStatus.ACCEPTED), anyLong(), anyLong());
        verify(auditService).log(eq("reg1"), eq(invited), eq("INVITE_ACCEPTED"), isNull(), eq("MEMBER"), anyString());
    }

    @Test
    public void acceptsOwnerTransferInvite() {
        RegionMember invitedMember = new RegionMember(invited, RegionRole.MEMBER, owner, System.currentTimeMillis(), System.currentTimeMillis());
        Region withMember = new Region("reg1", "PlayerClaim", RegionType.PLAYER_REGION,
            new RegionBounds("overworld", 0, 0, 0, 10, 10, 10), 100, owner, UUID.randomUUID(), 0, 0, "ACTIVE",
            Collections.singletonMap(invited, invitedMember));
        membershipCache.loadFromRegion(withMember);

        RegionInvite invite = new RegionInvite("inv-owner", "reg1", invited, owner, RegionRole.OWNER,
            InviteStatus.PENDING, System.currentTimeMillis(), System.currentTimeMillis() + 1000L, null, null);
        when(inviteRepository.get("inv-owner")).thenReturn(invite);
        when(regionCache.get("reg1")).thenReturn(withMember);

        service.acceptInvite("inv-owner", invited);

        verify(regionRepository, atLeastOnce()).save(any(Region.class));
        verify(inviteRepository).updateStatus(eq("inv-owner"), eq(InviteStatus.ACCEPTED), anyLong(), anyLong());
        verify(auditService).log(eq("reg1"), eq(invited), eq("INVITE_ACCEPTED"), isNull(), eq("OWNER"), anyString());
    }

    @Test
    public void cancelsSentInvite() {
        RegionInvite invite = new RegionInvite("inv2", "reg1", invited, owner, RegionRole.MEMBER,
            InviteStatus.PENDING, System.currentTimeMillis(), System.currentTimeMillis() + 1000L, null, null);
        when(inviteRepository.get("inv2")).thenReturn(invite);

        service.cancelInvite("inv2", owner);

        verify(inviteRepository).updateStatus(eq("inv2"), eq(InviteStatus.CANCELLED), isNull(), anyLong());
        verify(auditService).log(eq("reg1"), eq(owner), eq("INVITE_CANCELLED"), isNull(), eq("MEMBER"), anyString());
    }
}
