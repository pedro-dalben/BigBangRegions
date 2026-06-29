package com.bigbangcraft.regions.invite;

import com.bigbangcraft.regions.domain.RegionRole;

import java.util.Objects;
import java.util.UUID;

public class RegionInvite {
    private final String id;
    private final String regionId;
    private final UUID invitedUuid;
    private final UUID invitedByUuid;
    private final RegionRole role;
    private InviteStatus status;
    private final long createdAt;
    private final long expiresAt;
    private Long acceptedAt;
    private Long respondedAt;

    public RegionInvite(String id, String regionId, UUID invitedUuid, UUID invitedByUuid, RegionRole role,
                        InviteStatus status, long createdAt, long expiresAt, Long acceptedAt, Long respondedAt) {
        this.id = Objects.requireNonNull(id, "Id cannot be null");
        this.regionId = Objects.requireNonNull(regionId, "RegionId cannot be null");
        this.invitedUuid = Objects.requireNonNull(invitedUuid, "InvitedUuid cannot be null");
        this.invitedByUuid = Objects.requireNonNull(invitedByUuid, "InvitedByUuid cannot be null");
        this.role = Objects.requireNonNull(role, "Role cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.acceptedAt = acceptedAt;
        this.respondedAt = respondedAt;
    }

    public String getId() { return id; }
    public String getRegionId() { return regionId; }
    public UUID getInvitedUuid() { return invitedUuid; }
    public UUID getInvitedByUuid() { return invitedByUuid; }
    public RegionRole getRole() { return role; }
    public InviteStatus getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public Long getAcceptedAt() { return acceptedAt; }
    public Long getRespondedAt() { return respondedAt; }

    public boolean isExpired(long now) {
        return now >= expiresAt;
    }

    public void accept(long now) {
        this.status = InviteStatus.ACCEPTED;
        this.acceptedAt = now;
        this.respondedAt = now;
    }

    public void decline(long now) {
        this.status = InviteStatus.DECLINED;
        this.respondedAt = now;
    }

    public void expire(long now) {
        this.status = InviteStatus.EXPIRED;
        this.respondedAt = now;
    }
}
