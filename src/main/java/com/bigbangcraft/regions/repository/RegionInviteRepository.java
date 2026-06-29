package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.invite.InviteStatus;
import com.bigbangcraft.regions.invite.RegionInvite;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RegionInviteRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-RegionInviteRepository");

    private final DatabaseManager dbManager;

    public RegionInviteRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void save(RegionInvite invite) {
        synchronized (dbManager) {
            String sql = "INSERT INTO region_invites " +
                "(id, regionId, invitedUuid, invitedByUuid, role, status, createdAt, expiresAt, acceptedAt, respondedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                bind(pstmt, invite);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to save invite: ", e);
                throw new IllegalStateException("Nao foi possivel salvar o convite", e);
            }
        }
    }

    public RegionInvite get(String id) {
        synchronized (dbManager) {
            String sql = "SELECT * FROM region_invites WHERE id = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return map(rs);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to load invite {}", id, e);
            }
            return null;
        }
    }

    public List<RegionInvite> getPendingForRegion(String regionId) {
        synchronized (dbManager) {
            return query("SELECT * FROM region_invites WHERE regionId = ? AND status = 'PENDING' ORDER BY createdAt ASC;", regionId);
        }
    }

    public List<RegionInvite> getPendingForPlayer(UUID invitedUuid) {
        synchronized (dbManager) {
            return query("SELECT * FROM region_invites WHERE invitedUuid = ? AND status = 'PENDING' ORDER BY createdAt ASC;", invitedUuid.toString());
        }
    }

    public List<RegionInvite> getPendingDuplicates(String regionId, UUID invitedUuid) {
        synchronized (dbManager) {
            return query("SELECT * FROM region_invites WHERE regionId = ? AND invitedUuid = ? AND status = 'PENDING';",
                regionId, invitedUuid.toString());
        }
    }

    public void updateStatus(String id, InviteStatus status, Long acceptedAt, Long respondedAt) {
        synchronized (dbManager) {
            String sql = "UPDATE region_invites SET status = ?, acceptedAt = ?, respondedAt = ? WHERE id = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, status.name());
                pstmt.setObject(2, acceptedAt);
                pstmt.setObject(3, respondedAt);
                pstmt.setString(4, id);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to update invite {} status", id, e);
            }
        }
    }

    public List<RegionInvite> expirePending(long now) {
        synchronized (dbManager) {
            List<RegionInvite> expired = new ArrayList<>();
            String sql = "SELECT * FROM region_invites WHERE status = 'PENDING' AND expiresAt <= ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, now);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        expired.add(map(rs));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to expire invites", e);
            }
            for (RegionInvite invite : expired) {
                updateStatus(invite.getId(), InviteStatus.EXPIRED, null, now);
            }
            return expired;
        }
    }

    private List<RegionInvite> query(String sql, String... params) {
        List<RegionInvite> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setString(i + 1, params[i]);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to query invites", e);
        }
        return list;
    }

    private void bind(PreparedStatement pstmt, RegionInvite invite) throws SQLException {
        pstmt.setString(1, invite.getId());
        pstmt.setString(2, invite.getRegionId());
        pstmt.setString(3, invite.getInvitedUuid().toString());
        pstmt.setString(4, invite.getInvitedByUuid().toString());
        pstmt.setString(5, invite.getRole().name());
        pstmt.setString(6, invite.getStatus().name());
        pstmt.setLong(7, invite.getCreatedAt());
        pstmt.setLong(8, invite.getExpiresAt());
        pstmt.setObject(9, invite.getAcceptedAt());
        pstmt.setObject(10, invite.getRespondedAt());
    }

    private RegionInvite map(ResultSet rs) throws SQLException {
        return new RegionInvite(
            rs.getString("id"),
            rs.getString("regionId"),
            UUID.fromString(rs.getString("invitedUuid")),
            UUID.fromString(rs.getString("invitedByUuid")),
            RegionRole.valueOf(rs.getString("role")),
            InviteStatus.valueOf(rs.getString("status")),
            rs.getLong("createdAt"),
            rs.getLong("expiresAt"),
            rs.getObject("acceptedAt") != null ? rs.getLong("acceptedAt") : null,
            rs.getObject("respondedAt") != null ? rs.getLong("respondedAt") : null
        );
    }
}
