package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.storage.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class AuditRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-AuditRepository");

    private final DatabaseManager dbManager;

    public AuditRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void log(String regionId, UUID actorUuid, String action, String beforeValue, String afterValue, String metadataJson) {
        String sql = "INSERT INTO region_audit_logs (regionId, actorUuid, action, beforeValue, afterValue, createdAt, metadataJson) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, regionId);
            pstmt.setString(2, actorUuid != null ? actorUuid.toString() : null);
            pstmt.setString(3, action);
            pstmt.setString(4, beforeValue);
            pstmt.setString(5, afterValue);
            pstmt.setLong(6, System.currentTimeMillis());
            pstmt.setString(7, metadataJson);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to write audit log to database: ", e);
        }
    }
}
