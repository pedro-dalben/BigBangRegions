package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class RegionRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-Repository");
    
    private final DatabaseManager dbManager;

    public RegionRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public List<Region> loadAll() {
        synchronized (dbManager) {
            List<Region> list = new ArrayList<>();
            Connection conn = null;
            try {
                conn = dbManager.getConnection();
                
                // 1. Load regions
                String regionSql = "SELECT * FROM regions;";
                Map<String, Region> regionMap = new HashMap<>();
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(regionSql)) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        String name = rs.getString("name");
                        RegionType type = RegionType.valueOf(rs.getString("type"));
                        String dimensionKey = rs.getString("dimensionKey");
                        int minX = rs.getInt("minX");
                        int minY = rs.getInt("minY");
                        int minZ = rs.getInt("minZ");
                        int maxX = rs.getInt("maxX");
                        int maxY = rs.getInt("maxY");
                        int maxZ = rs.getInt("maxZ");
                        int priority = rs.getInt("priority");
                        
                        String ownerStr = rs.getString("ownerUuid");
                        UUID ownerUuid = (ownerStr != null && !ownerStr.isEmpty()) ? UUID.fromString(ownerStr) : null;
                        
                        UUID createdByUuid = UUID.fromString(rs.getString("createdByUuid"));
                        long createdAt = rs.getLong("createdAt");
                        long updatedAt = rs.getLong("updatedAt");
                        String status = rs.getString("status");

                        RegionBounds bounds = new RegionBounds(dimensionKey, minX, minY, minZ, maxX, maxY, maxZ);
                        Region region = new Region(id, name, type, bounds, priority, ownerUuid, createdByUuid, createdAt, updatedAt, status);
                        
                        regionMap.put(id, region);
                        list.add(region);
                    }
                }

                // 2. Load members
                String memberSql = "SELECT * FROM region_members;";
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(memberSql)) {
                    while (rs.next()) {
                        String regionId = rs.getString("regionId");
                        Region region = regionMap.get(regionId);
                        if (region != null) {
                            UUID uuid = UUID.fromString(rs.getString("uuid"));
                            RegionRole role = RegionRole.valueOf(rs.getString("role"));
                            String addedByStr = rs.getString("addedByUuid");
                            UUID addedByUuid = (addedByStr != null && !addedByStr.isEmpty()) ? UUID.fromString(addedByStr) : null;
                            long createdAt = rs.getLong("createdAt");
                            long updatedAt = rs.getLong("updatedAt");
                            if (rs.wasNull()) {
                                updatedAt = createdAt;
                            }
                            RegionMember member = new RegionMember(uuid, role, addedByUuid, createdAt, updatedAt);
                            region.setMember(member);
                        }
                    }
                }

                // 3. Load flags
                String flagSql = "SELECT * FROM region_flags;";
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(flagSql)) {
                    while (rs.next()) {
                        String regionId = rs.getString("regionId");
                        Region region = regionMap.get(regionId);
                        if (region != null) {
                            String flag = rs.getString("flag");
                            String value = rs.getString("value");
                            region.setFlag(flag, value);
                        }
                    }
                }

            } catch (SQLException e) {
                LOGGER.error("Failed to load regions from database: ", e);
            }
            return list;
        }
    }

    public void save(Region region) {
        synchronized (dbManager) {
            Connection conn = null;
            try {
                conn = dbManager.getConnection();
                conn.setAutoCommit(false);

                // 1. Insert or replace region
                String regionSql = "INSERT OR REPLACE INTO regions (" +
                        "id, name, type, dimensionKey, minX, minY, minZ, maxX, maxY, maxZ, " +
                        "priority, ownerUuid, createdByUuid, createdAt, updatedAt, status" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
                
                try (PreparedStatement pstmt = conn.prepareStatement(regionSql)) {
                    pstmt.setString(1, region.getId());
                    pstmt.setString(2, region.getName());
                    pstmt.setString(3, region.getType().name());
                    pstmt.setString(4, region.getBounds().getDimension());
                    pstmt.setInt(5, region.getBounds().getMinX());
                    pstmt.setInt(6, region.getBounds().getMinY());
                    pstmt.setInt(7, region.getBounds().getMinZ());
                    pstmt.setInt(8, region.getBounds().getMaxX());
                    pstmt.setInt(9, region.getBounds().getMaxY());
                    pstmt.setInt(10, region.getBounds().getMaxZ());
                    pstmt.setInt(11, region.getPriority());
                    pstmt.setString(12, region.getOwnerUuid() != null ? region.getOwnerUuid().toString() : null);
                    pstmt.setString(13, region.getCreatedByUuid().toString());
                    pstmt.setLong(14, region.getCreatedAt());
                    pstmt.setLong(15, region.getUpdatedAt());
                    pstmt.setString(16, region.getStatus());
                    pstmt.executeUpdate();
                }

                // 2. Update members (delete all and re-insert)
                String deleteMembersSql = "DELETE FROM region_members WHERE regionId = ?;";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteMembersSql)) {
                    pstmt.setString(1, region.getId());
                    pstmt.executeUpdate();
                }

                String insertMemberSql = "INSERT INTO region_members (regionId, uuid, role, addedByUuid, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?);";
                try (PreparedStatement pstmt = conn.prepareStatement(insertMemberSql)) {
                    for (RegionMember member : region.getMembers().values()) {
                        pstmt.setString(1, region.getId());
                        pstmt.setString(2, member.getUuid().toString());
                        pstmt.setString(3, member.getRole().name());
                        pstmt.setString(4, member.getAddedByUuid() != null ? member.getAddedByUuid().toString() : null);
                        pstmt.setLong(5, member.getCreatedAt());
                        pstmt.setLong(6, member.getUpdatedAt());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                // 3. Update flags (delete all and re-insert)
                String deleteFlagsSql = "DELETE FROM region_flags WHERE regionId = ?;";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteFlagsSql)) {
                    pstmt.setString(1, region.getId());
                    pstmt.executeUpdate();
                }

                String insertFlagSql = "INSERT INTO region_flags (regionId, flag, value) VALUES (?, ?, ?);";
                try (PreparedStatement pstmt = conn.prepareStatement(insertFlagSql)) {
                    for (Map.Entry<String, String> entry : region.getFlags().entrySet()) {
                        pstmt.setString(1, region.getId());
                        pstmt.setString(2, entry.getKey());
                        pstmt.setString(3, entry.getValue());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                LOGGER.error("Failed to save region " + region.getId() + " to database: ", e);
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        LOGGER.error("Error rolling back transaction: ", ex);
                    }
                }
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException e) {
                        LOGGER.error("Failed to reset auto-commit: ", e);
                    }
                }
            }
        }
    }

    public void delete(String regionId) {
        synchronized (dbManager) {
            String sql = "DELETE FROM regions WHERE id = ?;";
            try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
                pstmt.setString(1, regionId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to delete region " + regionId + " from database: ", e);
            }
        }
    }

    public void updateFlags(Region region) {
        // Simple delegator to save for this implementation stage
        save(region);
    }

    public void updateMembers(Region region) {
        // Simple delegator to save for this implementation stage
        save(region);
    }
}
