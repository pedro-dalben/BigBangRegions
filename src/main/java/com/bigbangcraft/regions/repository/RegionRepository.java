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

                // 1. Load region rows
                String regionSql = "SELECT * FROM regions;";
                Map<String, Object[]> regionRows = new LinkedHashMap<>();

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(regionSql)) {
                    while (rs.next()) {
                        regionRows.put(rs.getString("id"), new Object[]{
                            rs.getString("name"),
                            rs.getString("type"),
                            rs.getString("dimensionKey"),
                            rs.getInt("minX"), rs.getInt("minY"), rs.getInt("minZ"),
                            rs.getInt("maxX"), rs.getInt("maxY"), rs.getInt("maxZ"),
                            rs.getInt("priority"),
                            rs.getString("ownerUuid"),
                            rs.getString("createdByUuid"),
                            rs.getLong("createdAt"),
                            rs.getLong("updatedAt"),
                            rs.getString("status")
                        });
                    }
                }

                // 2. Load members grouped by region
                Map<String, Map<UUID, RegionMember>> membersByRegion = new HashMap<>();
                String memberSql = "SELECT * FROM region_members;";
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(memberSql)) {
                    while (rs.next()) {
                        String regionId = rs.getString("regionId");
                        if (!regionRows.containsKey(regionId)) continue;
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        RegionRole role = RegionRole.valueOf(rs.getString("role"));
                        String addedByStr = rs.getString("addedByUuid");
                        UUID addedByUuid = (addedByStr != null && !addedByStr.isEmpty()) ? UUID.fromString(addedByStr) : null;
                        long createdAt = rs.getLong("createdAt");
                        long updatedAt = rs.getLong("updatedAt");
                        if (rs.wasNull()) {
                            updatedAt = createdAt;
                        }
                        membersByRegion.computeIfAbsent(regionId, k -> new HashMap<>())
                            .put(uuid, new RegionMember(uuid, role, addedByUuid, createdAt, updatedAt));
                    }
                }

                // 3. Load flags grouped by region
                Map<String, Map<String, String>> flagsByRegion = new HashMap<>();
                String flagSql = "SELECT * FROM region_flags;";
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(flagSql)) {
                    while (rs.next()) {
                        String regionId = rs.getString("regionId");
                        if (!regionRows.containsKey(regionId)) continue;
                        flagsByRegion.computeIfAbsent(regionId, k -> new HashMap<>())
                            .put(rs.getString("flag"), rs.getString("value"));
                    }
                }

                // 4. Build region objects with immutable members and flags
                // Array index: [0]=name, [1]=type, [2]=dimensionKey,
                // [3]=minX, [4]=minY, [5]=minZ, [6]=maxX, [7]=maxY, [8]=maxZ,
                // [9]=priority, [10]=ownerUuid, [11]=createdByUuid, [12]=createdAt, [13]=updatedAt, [14]=status
                for (Map.Entry<String, Object[]> entry : regionRows.entrySet()) {
                    String id = entry.getKey();
                    Object[] row = entry.getValue();
                    RegionType type = RegionType.valueOf((String) row[1]);
                    RegionBounds bounds = new RegionBounds(
                        (String) row[2], (int) row[3], (int) row[4], (int) row[5],
                        (int) row[6], (int) row[7], (int) row[8]
                    );
                    String ownerStr = (String) row[10];
                    UUID ownerUuid = (ownerStr != null && !ownerStr.isEmpty()) ? UUID.fromString(ownerStr) : null;
                    Map<UUID, RegionMember> regionMembers = membersByRegion.getOrDefault(id, Collections.emptyMap());
                    Region region = new Region(
                        id, (String) row[0], type, bounds, (int) row[9],
                        ownerUuid, UUID.fromString((String) row[11]),
                        (long) row[12], (long) row[13], (String) row[14],
                        regionMembers
                    );
                    Map<String, String> regionFlags = flagsByRegion.getOrDefault(id, Collections.emptyMap());
                    for (Map.Entry<String, String> fe : regionFlags.entrySet()) {
                        region.setFlag(fe.getKey(), fe.getValue());
                    }
                    list.add(region);
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

                String sql = "INSERT OR REPLACE INTO regions (" +
                        "id, name, type, dimensionKey, minX, minY, minZ, maxX, maxY, maxZ, " +
                        "priority, ownerUuid, createdByUuid, createdAt, updatedAt, status" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

                saveFlags(conn, region.getId(), region.getFlags());

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

    public void saveMembers(String regionId, Map<UUID, RegionMember> members) {
        synchronized (dbManager) {
            Connection conn = null;
            try {
                conn = dbManager.getConnection();
                conn.setAutoCommit(false);

                String deleteSql = "DELETE FROM region_members WHERE regionId = ?;";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                    pstmt.setString(1, regionId);
                    pstmt.executeUpdate();
                }

                String insertSql = "INSERT INTO region_members (regionId, uuid, role, addedByUuid, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?);";
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    for (RegionMember member : members.values()) {
                        pstmt.setString(1, regionId);
                        pstmt.setString(2, member.getUuid().toString());
                        pstmt.setString(3, member.getRole().name());
                        pstmt.setString(4, member.getAddedByUuid() != null ? member.getAddedByUuid().toString() : null);
                        pstmt.setLong(5, member.getCreatedAt());
                        pstmt.setLong(6, member.getUpdatedAt());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                LOGGER.error("Failed to save members for region " + regionId + ": ", e);
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

    private void saveFlags(Connection conn, String regionId, Map<String, String> flags) throws SQLException {
        String deleteSql = "DELETE FROM region_flags WHERE regionId = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
            pstmt.setString(1, regionId);
            pstmt.executeUpdate();
        }

        String insertSql = "INSERT INTO region_flags (regionId, flag, value) VALUES (?, ?, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (Map.Entry<String, String> entry : flags.entrySet()) {
                pstmt.setString(1, regionId);
                pstmt.setString(2, entry.getKey());
                pstmt.setString(3, entry.getValue());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
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
        save(region);
    }

    public void updateMembers(Region region) {
        saveMembers(region.getId(), region.getMembers());
    }
}
