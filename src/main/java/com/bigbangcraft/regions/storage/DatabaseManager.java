package com.bigbangcraft.regions.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.*;
import java.util.stream.Collectors;

public class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-DB");
    
    private final Path dbFile;
    private Connection connection;

    public DatabaseManager(Path dbFile) {
        this.dbFile = dbFile;
    }

    public synchronized void initialize() throws SQLException {
        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        LOGGER.info("Connecting to SQLite database: {}", url);
        
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }

        connection = DriverManager.getConnection(url);
        // Enable foreign keys
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        
        runMigrations();
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }
        }
        return connection;
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    LOGGER.info("Database connection closed.");
                }
            } catch (SQLException e) {
                LOGGER.error("Error closing database connection: ", e);
            }
        }
    }

    private void runMigrations() throws SQLException {
        int currentVersion = getCurrentSchemaVersion();
        LOGGER.info("Current schema version: {}", currentVersion);

        if (currentVersion < 1) {
            LOGGER.info("Applying migration V1...");
            executeMigrationResource("/storage/migrations/V001__initial_schema.sql");
            setSchemaVersion(1);
            LOGGER.info("Migration V1 applied successfully.");
            currentVersion = 1;
        }

        if (currentVersion < 2) {
            LOGGER.info("Applying migration V2...");
            executeMigrationResource("/storage/migrations/V002__player_region_membership.sql");
            setSchemaVersion(2);
            LOGGER.info("Migration V2 applied successfully.");
            currentVersion = 2;
        }

        if (currentVersion < 3) {
            LOGGER.info("Applying migration V3...");
            executeMigrationResource("/storage/migrations/V003__player_region_allocation.sql");
            setSchemaVersion(3);
            LOGGER.info("Migration V3 applied successfully.");
            currentVersion = 3;
        }

        if (currentVersion < 4) {
            LOGGER.info("Applying migration V4...");
            executeMigrationResource("/storage/migrations/V004__audit_log_index.sql");
            setSchemaVersion(4);
            LOGGER.info("Migration V4 applied successfully.");
            currentVersion = 4;
        }

        if (currentVersion < 5) {
            LOGGER.info("Applying migration V5...");
            executeMigrationResource("/storage/migrations/V005__allocation_plot_slot_id.sql");
            setSchemaVersion(5);
            LOGGER.info("Migration V5 applied successfully.");
        }
    }

    private int getCurrentSchemaVersion() {
        try (Statement stmt = getConnection().createStatement()) {
            // Check if table exists
            ResultSet rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version';"
            );
            if (!rs.next()) {
                return 0;
            }
            rs.close();

            ResultSet rsVer = stmt.executeQuery("SELECT MAX(version) FROM schema_version;");
            if (rsVer.next()) {
                int ver = rsVer.getInt(1);
                rsVer.close();
                return ver;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to query schema version: ", e);
        }
        return 0;
    }

    private void setSchemaVersion(int version) throws SQLException {
        String sql = "INSERT OR REPLACE INTO schema_version (version, appliedAt) VALUES (?, ?);";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, version);
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.executeUpdate();
        }
    }

    private void executeMigrationResource(String path) throws SQLException {
        InputStream is = DatabaseManager.class.getResourceAsStream(path);
        if (is == null) {
            throw new SQLException("Migration file not found: " + path);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            java.util.List<String> lines = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.startsWith("--") && !line.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            String sql = String.join(" ", lines);
            String[] statements = sql.split(";");
            
            try (Statement stmt = getConnection().createStatement()) {
                for (String statement : statements) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
            }
        } catch (Exception e) {
            throw new SQLException("Failed to execute migration script: " + path, e);
        }
    }
}
