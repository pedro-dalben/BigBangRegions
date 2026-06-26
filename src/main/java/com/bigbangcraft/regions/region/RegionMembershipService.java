package com.bigbangcraft.regions.region;

import com.bigbangcraft.regions.audit.AuditService;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.repository.RegionRepository;

import java.util.UUID;

public class RegionMembershipService {
    private final RegionRepository repository;
    private final RegionMembershipCache cache;
    private final AuditService auditService;
    private final RegionRoleResolver roleResolver;

    public RegionMembershipService(RegionRepository repository, RegionMembershipCache cache,
                                   AuditService auditService, RegionRoleResolver roleResolver) {
        this.repository = repository;
        this.cache = cache;
        this.auditService = auditService;
        this.roleResolver = roleResolver;
    }

    public void addMember(Region region, UUID actorUuid, UUID memberUuid, RegionRole role, boolean isAdmin) {
        if (region == null) throw new IllegalArgumentException("Region cannot be null");
        if (memberUuid == null) throw new IllegalArgumentException("Member UUID cannot be null");
        if (role != RegionRole.MEMBER && role != RegionRole.LEADER) {
            throw new IllegalArgumentException("Invalid role: Only MEMBER or LEADER can be added");
        }

        // owner cannot be added as member
        if (memberUuid.equals(region.getOwnerUuid())) {
            throw new IllegalArgumentException("Cannot add the owner as a member");
        }

        // if member already exists with the same role
        RegionMember existing = region.getMembers().get(memberUuid);
        if (existing != null) {
            if (existing.getRole() == role) {
                throw new IllegalArgumentException("Player is already a " + role.name() + " in this region");
            }
        }

        // hierarchy validation
        if (!isAdmin) {
            if (actorUuid == null) {
                throw new IllegalArgumentException("Actor UUID cannot be null for non-admin requests");
            }
            RegionRole actorRole = roleResolver.resolveRole(region, actorUuid);
            if (!actorRole.isAtLeast(RegionRole.LEADER)) {
                throw new IllegalArgumentException("Only owners and leaders can add members");
            }
            if (actorRole == RegionRole.LEADER) {
                if (role == RegionRole.LEADER) {
                    throw new IllegalArgumentException("Leaders cannot add other leaders");
                }
            }
            if (actorUuid.equals(memberUuid)) {
                throw new IllegalArgumentException("You cannot manage yourself");
            }
        }

        // Perform additions
        long now = System.currentTimeMillis();
        RegionMember member = new RegionMember(memberUuid, role, actorUuid, now, now);
        region.setMember(member);
        
        // Save to DB and update cache
        repository.save(region);
        cache.updateMember(region.getId(), memberUuid, role);

        // Audit log
        String metadata = "{\"addedByUuid\":\"" + (actorUuid != null ? actorUuid.toString() : "admin") + "\",\"role\":\"" + role.name() + "\"}";
        auditService.log(region.getId(), actorUuid, "ADD_MEMBER", null, role.name(), metadata);
    }

    public void removeMember(Region region, UUID actorUuid, UUID memberUuid, boolean isAdmin) {
        if (region == null) throw new IllegalArgumentException("Region cannot be null");
        if (memberUuid == null) throw new IllegalArgumentException("Member UUID cannot be null");

        if (memberUuid.equals(region.getOwnerUuid())) {
            throw new IllegalArgumentException("Cannot remove the owner of the region");
        }

        RegionMember existing = region.getMembers().get(memberUuid);
        if (existing == null) {
            throw new IllegalArgumentException("Player is not a member of this region");
        }

        // hierarchy validation
        if (!isAdmin) {
            if (actorUuid == null) {
                throw new IllegalArgumentException("Actor UUID cannot be null for non-admin requests");
            }
            RegionRole actorRole = roleResolver.resolveRole(region, actorUuid);
            if (!actorRole.isAtLeast(RegionRole.LEADER)) {
                throw new IllegalArgumentException("Only owners and leaders can remove members");
            }
            if (actorRole == RegionRole.LEADER) {
                if (existing.getRole() == RegionRole.LEADER) {
                    throw new IllegalArgumentException("Leaders cannot remove other leaders");
                }
            }
            if (actorUuid.equals(memberUuid)) {
                throw new IllegalArgumentException("You cannot remove yourself this way, use leave instead");
            }
        }

        // Perform removal
        region.removeMember(memberUuid);
        repository.save(region);
        cache.updateMember(region.getId(), memberUuid, null);

        // Audit log
        String metadata = "{\"removedByUuid\":\"" + (actorUuid != null ? actorUuid.toString() : "admin") + "\"}";
        auditService.log(region.getId(), actorUuid, "REMOVE_MEMBER", existing.getRole().name(), null, metadata);
    }

    public void setRole(Region region, UUID actorUuid, UUID memberUuid, RegionRole role, boolean isAdmin) {
        if (region == null) throw new IllegalArgumentException("Region cannot be null");
        if (memberUuid == null) throw new IllegalArgumentException("Member UUID cannot be null");
        if (role != RegionRole.MEMBER && role != RegionRole.LEADER) {
            throw new IllegalArgumentException("Invalid role: must be LEADER or MEMBER");
        }

        if (memberUuid.equals(region.getOwnerUuid())) {
            throw new IllegalArgumentException("Cannot set member role for the owner");
        }

        RegionMember existing = region.getMembers().get(memberUuid);
        if (existing == null) {
            throw new IllegalArgumentException("Player is not a member of this region");
        }

        if (existing.getRole() == role) {
            throw new IllegalArgumentException("Player already has this role");
        }

        // hierarchy validation
        if (!isAdmin) {
            if (actorUuid == null) {
                throw new IllegalArgumentException("Actor UUID cannot be null for non-admin requests");
            }
            RegionRole actorRole = roleResolver.resolveRole(region, actorUuid);
            if (actorRole != RegionRole.OWNER) {
                throw new IllegalArgumentException("Only owners can promote or demote members");
            }
            if (actorUuid.equals(memberUuid)) {
                throw new IllegalArgumentException("You cannot promote or demote yourself");
            }
        }

        RegionRole oldRole = existing.getRole();
        existing.setRole(role);
        existing.setUpdatedAt(System.currentTimeMillis());
        // To persist properly we must call save on repo
        repository.save(region);
        cache.updateMember(region.getId(), memberUuid, role);

        // Audit log
        String action = role == RegionRole.LEADER ? "PROMOTE_MEMBER" : "DEMOTE_LEADER";
        String metadata = "{\"roleBefore\":\"" + oldRole.name() + "\",\"roleAfter\":\"" + role.name() + "\"}";
        auditService.log(region.getId(), actorUuid, action, oldRole.name(), role.name(), metadata);
    }

    public void leaveRegion(Region region, UUID memberUuid) {
        if (region == null) throw new IllegalArgumentException("Region cannot be null");
        if (memberUuid == null) throw new IllegalArgumentException("Member UUID cannot be null");

        if (memberUuid.equals(region.getOwnerUuid())) {
            throw new IllegalArgumentException("The owner cannot leave the region");
        }

        RegionMember existing = region.getMembers().get(memberUuid);
        if (existing == null) {
            throw new IllegalArgumentException("You are not a member of this region");
        }

        region.removeMember(memberUuid);
        repository.save(region);
        cache.updateMember(region.getId(), memberUuid, null);

        // Audit log
        String metadata = "{\"source\":\"player-leave\"}";
        auditService.log(region.getId(), memberUuid, "MEMBER_LEAVE", existing.getRole().name(), null, metadata);
    }
}
