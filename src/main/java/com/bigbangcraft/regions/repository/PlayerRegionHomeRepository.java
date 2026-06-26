package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.allocation.PlayerRegionHome;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class PlayerRegionHomeRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-PlayerRegionHomeRepository");
    private final DatabaseManager dbManager;

    public PlayerRegionHomeRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void save(PlayerRegionHome home) {
        synchronized (dbManager) {
            String sql = "INSERT OR REPLACE INTO player_region_homes (" +
                    "region_id, dimension_key, x, y, z, yaw, pitch, created_at, updated_at" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, home.getRegionId());
                pstmt.setString(2, home.getDimensionKey());
                pstmt.setDouble(3, home.getX());
                pstmt.setDouble(4, home.getY());
                pstmt.setDouble(5, home.getZ());
                pstmt.setFloat(6, home.getYaw());
                pstmt.setFloat(7, home.getPitch());
                pstmt.setLong(8, home.getCreatedAt());
                pstmt.setLong(9, home.getUpdatedAt());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to save player region home: ", e);
            }
        }
    }

    public PlayerRegionHome get(String regionId) {
        synchronized (dbManager) {
            String sql = "SELECT * FROM player_region_homes WHERE region_id = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, regionId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSet(rs);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to get player region home: ", e);
            }
            return null;
        }
    }

    public void delete(String regionId) {
        synchronized (dbManager) {
            String sql = "DELETE FROM player_region_homes WHERE region_id = ?;";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, regionId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to delete player region home: ", e);
            }
        }
    }

    private PlayerRegionHome mapResultSet(ResultSet rs) throws SQLException {
        return new PlayerRegionHome(
                rs.getString("region_id"),
                rs.getString("dimension_key"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
