package com.bigbangcraft.regions.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Path configDir;
    private final Path configFile;
    private Config config;

    public ConfigManager(Path configDir) {
        this.configDir = configDir;
        this.configFile = configDir.resolve("config.json");
        this.config = new Config(); // Default fallback
    }

    public void load() {
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            if (!Files.exists(configFile)) {
                LOGGER.info("Configuration file not found. Creating default at: {}", configFile);
                saveDefault();
                return;
            }

            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                Config parsed = GSON.fromJson(reader, Config.class);
                if (parsed == null) {
                    throw new IOException("Parsed configuration was null");
                }
                this.config = parsed;
                LOGGER.info("Configuration loaded successfully from {}", configFile);
                applyMigrations(this.config);
            } catch (Exception e) {
                LOGGER.error("Failed to parse configuration file. Using safe fallback defaults. Error details: ", e);
                // Safe fallback: keep the default config created in constructor
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read or create configuration directory/file: ", e);
        }
    }

    private static final List<String> VISITOR_FLAGS = Arrays.asList(
        "visitor-build", "visitor-usage", "visitor-item-frames", "visitor-armor-stands"
    );

    private void applyMigrations(Config config) {
        int originalVersion = config.getSchemaVersion();
        boolean changed = false;

        if (config.getSchemaVersion() < 2) {
            for (Map<String, String> section : Arrays.asList(
                config.getDefaults().getGlobal(),
                config.getDefaults().getAdminRegion(),
                config.getDefaults().getPlayerRegion()
            )) {
                for (String flag : VISITOR_FLAGS) {
                    section.put(flag, "DENY");
                }
            }
            config.setSchemaVersion(2);
            changed = true;
        }

        if (config.getSchemaVersion() < 3) {
            repairVirtualPastureConfig(config.getVirtualPasture());
            repairRegionPerformanceConfig(config.getRegionExpansionPerformance());
            config.setSchemaVersion(3);
            changed = true;
        }

        if (changed) {
            save();
            LOGGER.info("Config migrated from schema v{} to v{} and saved.", originalVersion, config.getSchemaVersion());
        }
    }

    /** Keeps existing choices, but writes every v3 field so operators can tune it explicitly. */
    private static void repairVirtualPastureConfig(Config.VirtualPastureConfig pasture) {
        if (pasture.getBlockId() == null || pasture.getBlockId().isBlank()) {
            pasture.setBlockId("virtualloot:virtual_pasture");
        }
        pasture.setMaxPerRegion(pasture.getMaxPerRegion());
        pasture.setMaxPerPlayer(pasture.getMaxPerPlayer());
        pasture.setMaxPerChunk(pasture.getMaxPerChunk());
        if (pasture.getAdminBypassPermission() == null || pasture.getAdminBypassPermission().isBlank()) {
            pasture.setAdminBypassPermission("bigbangregions.virtualpasture.bypass");
        }

        Map<String, Integer> limits = new HashMap<>();
        pasture.getLimits().forEach((tier, maximum) -> {
            if (tier != null && !tier.isBlank() && maximum != null) limits.put(tier, Math.max(0, maximum));
        });
        limits.putIfAbsent("default", pasture.getMaxPerPlayer());
        limits.putIfAbsent("vip", 3);
        pasture.setLimits(limits);
    }

    private static void repairRegionPerformanceConfig(Config.RegionExpansionPerformanceConfig performance) {
        performance.setSnapshotCaptureBudgetMs(performance.getSnapshotCaptureBudgetMs());
        performance.setSnapshotCaptureMaxBlocksPerTick(performance.getSnapshotCaptureMaxBlocksPerTick());
        performance.setBorderApplicationBudgetMs(performance.getBorderApplicationBudgetMs());
        performance.setBorderApplicationMaxBlocksPerTick(performance.getBorderApplicationMaxBlocksPerTick());
        performance.setPersistenceWorkers(performance.getPersistenceWorkers());
        performance.setPersistenceQueueCapacity(performance.getPersistenceQueueCapacity());
        performance.setShutdownTimeoutSeconds(performance.getShutdownTimeoutSeconds());
        performance.setDeletionRestoreTimeoutSeconds(performance.getDeletionRestoreTimeoutSeconds());
    }

    public void save() {
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration: ", e);
        }
    }

    private void saveDefault() {
        Config newConfig = new Config();
        try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            GSON.toJson(newConfig, writer);
            this.config = newConfig;
        } catch (IOException e) {
            LOGGER.error("Failed to save default configuration: ", e);
        }
    }

    public Config getConfig() {
        return config;
    }

    public Path getConfigDir() {
        return configDir;
    }

    public Path getConfigFile() {
        return configFile;
    }
}
