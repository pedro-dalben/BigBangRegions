package com.bigbangcraft.regions.region;

import com.bigbangcraft.regions.audit.AuditService;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.invite.InviteStatus;
import com.bigbangcraft.regions.invite.RegionInvite;
import com.bigbangcraft.regions.repository.RegionInviteRepository;
import com.bigbangcraft.regions.repository.RegionRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RegionInviteService {
    private final RegionInviteRepository repository;
    private final RegionRepository regionRepository;
    private final RegionCache regionCache;
    private final RegionMembershipCache membershipCache;
    private final RegionRoleResolver roleResolver;
    private final AuditService auditService;

    public RegionInviteService(RegionInviteRepository repository, RegionRepository regionRepository,
                               RegionCache regionCache, RegionMembershipCache membershipCache,
                               RegionRoleResolver roleResolver, AuditService auditService) {
        this.repository = repository;
        this.regionRepository = regionRepository;
        this.regionCache = regionCache;
        this.membershipCache = membershipCache;
        this.roleResolver = roleResolver;
        this.auditService = auditService;
    }

    public RegionInvite sendInvite(Region region, UUID actorUuid, UUID invitedUuid, RegionRole role, long expiresInMs) {
        validateInvite(region, actorUuid, invitedUuid, role);
        if (!repository.getPendingDuplicates(region.getId(), invitedUuid).isEmpty()) {
            throw new IllegalStateException("Ja existe um convite pendente para esse jogador nesta regiao");
        }
        long now = System.currentTimeMillis();
        RegionInvite invite = new RegionInvite(
            UUID.randomUUID().toString(),
            region.getId(),
            invitedUuid,
            actorUuid,
            role,
            InviteStatus.PENDING,
            now,
            now + expiresInMs,
            null,
            null
        );
        repository.save(invite);
        auditService.log(region.getId(), actorUuid, "MEMBER_INVITED", null, role.name(),
            "{\"invitedUuid\":\"" + invitedUuid + "\"}");
        return invite;
    }

    public List<RegionInvite> getPendingInvitesForPlayer(UUID invitedUuid) {
        return repository.getPendingForPlayer(invitedUuid);
    }

    public List<RegionInvite> getPendingInvitesForRegion(String regionId) {
        return repository.getPendingForRegion(regionId);
    }

    public List<RegionInvite> getPendingInvitesSentBy(UUID actorUuid, String regionId) {
        return repository.getPendingForRegion(regionId).stream()
            .filter(invite -> invite.getInvitedByUuid().equals(actorUuid))
            .toList();
    }

    public RegionInvite cancelInvite(String inviteId, UUID actorUuid) {
        RegionInvite invite = repository.get(inviteId);
        if (invite == null) {
            throw new IllegalArgumentException("Convite nao encontrado");
        }
        if (!invite.getInvitedByUuid().equals(actorUuid)) {
            throw new IllegalArgumentException("Voce nao pode cancelar este convite");
        }
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new IllegalStateException("Convite nao esta pendente");
        }
        long now = System.currentTimeMillis();
        repository.updateStatus(inviteId, InviteStatus.CANCELLED, null, now);
        auditService.log(invite.getRegionId(), actorUuid, "INVITE_CANCELLED", null, invite.getRole().name(),
            "{\"inviteId\":\"" + inviteId + "\"}");
        return invite;
    }

    public RegionInvite acceptInvite(String inviteId, UUID invitedUuid) {
        RegionInvite invite = requirePlayerInvite(inviteId, invitedUuid);
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new IllegalStateException("Convite nao esta pendente");
        }
        if (invite.isExpired(System.currentTimeMillis())) {
            repository.updateStatus(inviteId, InviteStatus.EXPIRED, null, System.currentTimeMillis());
            throw new IllegalStateException("Convite expirado");
        }

        Region region = regionCache.get(invite.getRegionId());
        if (region == null) {
            throw new IllegalStateException("Regiao nao encontrada para o convite");
        }
        if (region.getType() != RegionType.PLAYER_REGION || !"ACTIVE".equals(region.getStatus())) {
            throw new IllegalStateException("Convite indisponivel para esta regiao");
        }

        long now = System.currentTimeMillis();
        if (invite.getRole() == RegionRole.OWNER) {
            Region updatedRegion = transferOwnership(region, invite.getInvitedByUuid(), invitedUuid);
            regionRepository.saveMembers(updatedRegion.getId(), updatedRegion.getMembers());
        } else {
            RegionMember member = new RegionMember(invitedUuid, invite.getRole(), invite.getInvitedByUuid(),
                now, now);
            Map<UUID, RegionMember> updatedMembers = new HashMap<>(region.getMembers());
            updatedMembers.put(invitedUuid, member);
            regionRepository.saveMembers(region.getId(), updatedMembers);

            Region updatedRegion = rebuildRegion(region, updatedMembers);
            regionCache.remove(region.getId());
            regionCache.add(updatedRegion);
            membershipCache.loadFromRegion(updatedRegion);
        }

        repository.updateStatus(inviteId, InviteStatus.ACCEPTED, now, now);
        auditService.log(region.getId(), invitedUuid, "INVITE_ACCEPTED", null, invite.getRole().name(),
            "{\"inviteId\":\"" + inviteId + "\"}");
        return invite;
    }

    public void declineInvite(String inviteId, UUID invitedUuid) {
        RegionInvite invite = requirePlayerInvite(inviteId, invitedUuid);
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new IllegalStateException("Convite nao esta pendente");
        }
        long now = System.currentTimeMillis();
        repository.updateStatus(inviteId, InviteStatus.DECLINED, null, now);
        auditService.log(invite.getRegionId(), invitedUuid, "INVITE_DECLINED", null, invite.getRole().name(),
            "{\"inviteId\":\"" + inviteId + "\"}");
    }

    public Region transferOwnership(Region region, UUID actorUuid, UUID newOwnerUuid) {
        if (region == null) throw new IllegalArgumentException("Region cannot be null");
        if (actorUuid == null || newOwnerUuid == null) throw new IllegalArgumentException("UUIDs cannot be null");
        if (!actorUuid.equals(region.getOwnerUuid())) {
            throw new IllegalArgumentException("Only the owner can transfer ownership");
        }
        if (actorUuid.equals(newOwnerUuid)) {
            throw new IllegalArgumentException("You already own this region");
        }

        RegionRole newOwnerRole = roleResolver.resolveRole(region, newOwnerUuid);
        if (newOwnerRole == RegionRole.VISITOR) {
            throw new IllegalArgumentException("New owner must already be a member");
        }

        RegionMember currentOwnerMember = new RegionMember(actorUuid, RegionRole.LEADER, actorUuid,
            System.currentTimeMillis(), System.currentTimeMillis());
        RegionMember promotedOwner = new RegionMember(newOwnerUuid, RegionRole.OWNER, actorUuid,
            System.currentTimeMillis(), System.currentTimeMillis());

        Map<UUID, RegionMember> updatedMembers = new HashMap<>(region.getMembers());
        updatedMembers.put(actorUuid, currentOwnerMember);
        updatedMembers.put(newOwnerUuid, promotedOwner);

        Region updated = new Region(region.getId(), region.getName(), region.getType(), region.getBounds(),
            region.getPriority(), newOwnerUuid, region.getCreatedByUuid(), region.getCreatedAt(),
            System.currentTimeMillis(), region.getStatus(), updatedMembers);
        for (Map.Entry<String, String> entry : region.getFlags().entrySet()) {
            updated.setFlag(entry.getKey(), entry.getValue());
        }
        regionRepository.save(updated);
        regionCache.remove(region.getId());
        regionCache.add(updated);
        membershipCache.loadFromRegion(updated);
        auditService.log(region.getId(), actorUuid, "TRANSFER_OWNERSHIP", region.getOwnerUuid().toString(),
            newOwnerUuid.toString(), "{\"newOwnerUuid\":\"" + newOwnerUuid + "\"}");
        return updated;
    }

    public void expireInvites() {
        repository.expirePending(System.currentTimeMillis());
    }

    private void validateInvite(Region region, UUID actorUuid, UUID invitedUuid, RegionRole role) {
        if (region == null) throw new IllegalArgumentException("Region cannot be null");
        if (actorUuid == null) throw new IllegalArgumentException("Actor UUID cannot be null");
        if (invitedUuid == null) throw new IllegalArgumentException("Invited UUID cannot be null");
        if (actorUuid.equals(invitedUuid)) throw new IllegalArgumentException("Nao e possivel convidar a si mesmo");
        if (role == null || role == RegionRole.VISITOR) {
            throw new IllegalArgumentException("Cargo de convite invalido");
        }
        if (region.getOwnerUuid() != null && region.getOwnerUuid().equals(invitedUuid)) {
            throw new IllegalArgumentException("O dono ja possui acesso total");
        }

        RegionRole actorRole = roleResolver.resolveRole(region, actorUuid);
        if (actorRole == RegionRole.VISITOR) {
            throw new IllegalArgumentException("Voce nao pode convidar nesta regiao");
        }
        if (actorRole == RegionRole.MANAGER && role != RegionRole.MEMBER) {
            throw new IllegalArgumentException("Managers podem convidar apenas MEMBER");
        }
        if (actorRole == RegionRole.MEMBER) {
            throw new IllegalArgumentException("Members nao podem convidar");
        }
        if (role == RegionRole.OWNER) {
            if (actorRole != RegionRole.OWNER) {
                throw new IllegalArgumentException("Only the owner can invite a new owner");
            }
            if (region.getRole(invitedUuid) == RegionRole.VISITOR) {
                throw new IllegalArgumentException("New owner must already be a member before transfer");
            }
            return;
        }
        if (region.getRole(invitedUuid) != RegionRole.VISITOR) {
            throw new IllegalStateException("Jogador ja e membro da regiao");
        }
    }

    private RegionInvite requirePlayerInvite(String inviteId, UUID invitedUuid) {
        RegionInvite invite = repository.get(inviteId);
        if (invite == null) {
            throw new IllegalArgumentException("Convite nao encontrado");
        }
        if (!invite.getInvitedUuid().equals(invitedUuid)) {
            throw new IllegalArgumentException("Convite nao pertence a este jogador");
        }
        return invite;
    }

    private Region rebuildRegion(Region region, Map<UUID, RegionMember> updatedMembers) {
        Region rebuilt = new Region(region.getId(), region.getName(), region.getType(), region.getBounds(),
            region.getPriority(), region.getOwnerUuid(), region.getCreatedByUuid(), region.getCreatedAt(),
            System.currentTimeMillis(), region.getStatus(), updatedMembers);
        for (Map.Entry<String, String> entry : region.getFlags().entrySet()) {
            rebuilt.setFlag(entry.getKey(), entry.getValue());
        }
        return rebuilt;
    }
}
