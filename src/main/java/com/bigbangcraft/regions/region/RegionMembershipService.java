package com.bigbangcraft.regions.region;

import com.bigbangcraft.regions.audit.AuditService;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.repository.RegionRepository;

import java.util.HashMap;
import java.util.Map;
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
        if (role != RegionRole.MEMBER && role != RegionRole.MANAGER && role != RegionRole.LEADER) {
            throw new IllegalArgumentException("Invalid role: Only MEMBER, MANAGER or LEADER can be added");
        }

        // owner cannot be added as member
        if (memberUuid.equals(region.getOwnerUuid())) {
            throw new IllegalArgumentException("Cannot add the owner as a member");
        }

        // if member already exists with the same role (use cache since region is immutable)
        RegionRole existingRole = cache.getRole(region.getId(), memberUuid, region.getOwnerUuid());
        if (existingRole != RegionRole.VISITOR && existingRole != RegionRole.OWNER) {
            throw new IllegalArgumentException("Player is already a member of this region");
        }

        // hierarchy validation
        if (!isAdmin) {
            if (actorUuid == null) {
                throw new IllegalArgumentException("Actor UUID cannot be null for non-admin requests");
            }
            RegionRole actorRole = roleResolver.resolveRole(region, actorUuid);
            if (!actorRole.isAtLeast(RegionRole.LEADER)) {
                throw new IllegalArgumentException("Only owners, leaders and managers can add members");
            }
            if (actorRole == RegionRole.MANAGER) {
                if (role != RegionRole.MEMBER) {
                    throw new IllegalArgumentException("Managers can only invite or add MEMBERS");
                }
            }
            if (actorRole == RegionRole.LEADER) {
                if (role == RegionRole.OWNER) {
                    throw new IllegalArgumentException("Leaders cannot assign owner role");
                }
            }
            if (actorUuid.equals(memberUuid)) {
                throw new IllegalArgumentException("You cannot manage yourself");
            }
        }

        // Perform additions — build new members map (immutable region)
        long now = System.currentTimeMillis();
        RegionMember member = new RegionMember(memberUuid, role, actorUuid, now, now);
        Map<UUID, RegionMember> updatedMembers = new HashMap<>(region.getMembers());
        updatedMembers.put(memberUuid, member);
        
        // Save to DB and update cache
        repository.saveMembers(region.getId(), updatedMembers);
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

        RegionRole existingRole = cache.getRole(region.getId(), memberUuid, region.getOwnerUuid());
        if (existingRole == RegionRole.VISITOR || existingRole == RegionRole.OWNER) {
            throw new IllegalArgumentException("Player is not a member of this region");
        }

        // hierarchy validation
        if (!isAdmin) {
            if (actorUuid == null) {
                throw new IllegalArgumentException("Actor UUID cannot be null for non-admin requests");
            }
            RegionRole actorRole = roleResolver.resolveRole(region, actorUuid);
            if (!actorRole.isAtLeast(RegionRole.MANAGER)) {
                throw new IllegalArgumentException("Only owners, leaders and managers can remove members");
            }
            if (actorRole == RegionRole.MANAGER) {
                if (existingRole != RegionRole.MEMBER) {
                    throw new IllegalArgumentException("Managers can only remove MEMBERS");
                }
            }
            if (actorRole == RegionRole.LEADER && existingRole == RegionRole.OWNER) {
                throw new IllegalArgumentException("Leaders cannot remove owners");
            }
            if (actorUuid.equals(memberUuid)) {
                throw new IllegalArgumentException("You cannot remove yourself this way, use leave instead");
            }
        }

        // Perform removal — build new members map (immutable region)
        Map<UUID, RegionMember> updatedMembers = new HashMap<>(region.getMembers());
        updatedMembers.remove(memberUuid);
        repository.saveMembers(region.getId(), updatedMembers);
        cache.updateMember(region.getId(), memberUuid, null);

        // Audit log
        String metadata = "{\"removedByUuid\":\"" + (actorUuid != null ? actorUuid.toString() : "admin") + "\"}";
        auditService.log(region.getId(), actorUuid, "REMOVE_MEMBER", existingRole.name(), null, metadata);
    }

    public void setRole(Region region, UUID actorUuid, UUID memberUuid, RegionRole role, boolean isAdmin) {
        if (region == null) throw new IllegalArgumentException("Region cannot be null");
        if (memberUuid == null) throw new IllegalArgumentException("Member UUID cannot be null");
        if (role != RegionRole.MEMBER && role != RegionRole.MANAGER && role != RegionRole.LEADER) {
            throw new IllegalArgumentException("Invalid role: must be LEADER, MANAGER or MEMBER");
        }

        if (memberUuid.equals(region.getOwnerUuid())) {
            throw new IllegalArgumentException("Cannot set member role for the owner");
        }

        RegionRole existingRole = cache.getRole(region.getId(), memberUuid, region.getOwnerUuid());
        if (existingRole == RegionRole.VISITOR || existingRole == RegionRole.OWNER) {
            throw new IllegalArgumentException("Player is not a member of this region");
        }

        if (existingRole == role) {
            throw new IllegalArgumentException("Player already has this role");
        }

        // hierarchy validation
        if (!isAdmin) {
            if (actorUuid == null) {
                throw new IllegalArgumentException("Actor UUID cannot be null for non-admin requests");
            }
            RegionRole actorRole = roleResolver.resolveRole(region, actorUuid);
            if (actorRole != RegionRole.OWNER && actorRole != RegionRole.LEADER) {
                throw new IllegalArgumentException("Only owners and leaders can change roles");
            }
            if (actorRole == RegionRole.LEADER && role == RegionRole.OWNER) {
                throw new IllegalArgumentException("Leaders cannot assign owner role");
            }
            if (actorRole == RegionRole.LEADER && existingRole == RegionRole.OWNER) {
                throw new IllegalArgumentException("Leaders cannot modify owner role");
            }
            if (actorUuid.equals(memberUuid)) {
                throw new IllegalArgumentException("You cannot promote or demote yourself");
            }
        }

        RegionRole oldRole = existingRole;
        RegionMember updatedMember = new RegionMember(memberUuid, role, actorUuid,
            System.currentTimeMillis(), System.currentTimeMillis());
        Map<UUID, RegionMember> updatedMembers = new HashMap<>(region.getMembers());
        updatedMembers.put(memberUuid, updatedMember);
        repository.saveMembers(region.getId(), updatedMembers);
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

        RegionRole existingRole = cache.getRole(region.getId(), memberUuid, region.getOwnerUuid());
        if (existingRole == RegionRole.VISITOR || existingRole == RegionRole.OWNER) {
            throw new IllegalArgumentException("You are not a member of this region");
        }

        Map<UUID, RegionMember> updatedMembers = new HashMap<>(region.getMembers());
        updatedMembers.remove(memberUuid);
        repository.saveMembers(region.getId(), updatedMembers);
        cache.updateMember(region.getId(), memberUuid, null);

        // Audit log
        String metadata = "{\"source\":\"player-leave\"}";
        auditService.log(region.getId(), memberUuid, "MEMBER_LEAVE", existingRole.name(), null, metadata);
    }
}
