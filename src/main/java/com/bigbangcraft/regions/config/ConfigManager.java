package com.bigbangcraft.regions.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
                JsonElement root = JsonParser.parseReader(reader);
                boolean normalized = normalizeVirtualPastureJson(root);
                Config parsed = GSON.fromJson(root, Config.class);
                if (parsed == null) {
                    throw new IOException("Parsed configuration was null");
                }
                this.config = parsed;
                LOGGER.info("Configuration loaded successfully from {}", configFile);
                applyMigrations(this.config);
                if (normalized) {
                    repairVirtualPastureConfig(this.config.getVirtualPasture());
                    if (this.config.getSchemaVersion() >= 4) save();
                }
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

        if (config.getSchemaVersion() < 4) {
            repairVirtualPastureConfig(config.getVirtualPasture());
            config.setSchemaVersion(4);
            changed = true;
        }

        if (changed) {
            save();
            LOGGER.info("Config migrated from schema v{} to v{} and saved.", originalVersion, config.getSchemaVersion());
        }
    }

    private static boolean normalizeVirtualPastureJson(JsonElement root) {
        if (!root.isJsonObject()) return false;
        JsonObject config = root.getAsJsonObject();
        JsonObject pasture = config.has("virtualPasture") && config.get("virtualPasture").isJsonObject()
            ? config.getAsJsonObject("virtualPasture") : null;
        if (pasture == null) return false;
        boolean changed = false;

        if (!pasture.has("blockIds") && pasture.has("blockId")) {
            String blockId = pasture.get("blockId").isJsonPrimitive() ? pasture.get("blockId").getAsString() : "";
            pasture.add("blockIds", GSON.toJsonTree(List.of(blockId)));
            changed = true;
        }

        if (pasture.has("limits") && pasture.get("limits").isJsonObject()) {
            JsonObject limits = pasture.getAsJsonObject("limits");
            int legacyRegion = pasture.has("maxPerRegion") ? pasture.get("maxPerRegion").getAsInt() : 2;
            boolean legacyLimits = pasture.has("maxPerRegion") || pasture.has("maxPerPlayer");
            for (Map.Entry<String, JsonElement> entry : List.copyOf(limits.entrySet())) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) continue;
                legacyLimits = true;
                JsonObject replacement = new JsonObject();
                replacement.addProperty("perPlayer", entry.getValue().getAsInt());
                replacement.addProperty("perRegion", legacyRegion);
                limits.add(entry.getKey(), replacement);
                changed = true;
            }
            if (legacyLimits && !limits.has("default")) {
                int perPlayer = pasture.has("maxPerPlayer") ? pasture.get("maxPerPlayer").getAsInt() : 2;
                int perRegion = pasture.has("maxPerRegion") ? pasture.get("maxPerRegion").getAsInt() : 2;
                limits.add("default", limitJson(perPlayer, perRegion));
                changed = true;
            }
        } else if (!pasture.has("limits")) {
            JsonObject limits = new JsonObject();
            int perPlayer = pasture.has("maxPerPlayer") ? pasture.get("maxPerPlayer").getAsInt() : 20;
            int perRegion = pasture.has("maxPerRegion") ? pasture.get("maxPerRegion").getAsInt() : 30;
            limits.add("default", limitJson(perPlayer, perRegion));
            pasture.add("limits", limits);
            changed = true;
        }
        return changed;
    }

    private static JsonObject limitJson(int perPlayer, int perRegion) {
        JsonObject limit = new JsonObject();
        limit.addProperty("perPlayer", perPlayer);
        limit.addProperty("perRegion", perRegion);
        return limit;
    }

    /** Keeps existing choices, but writes every v4 field so operators can tune it explicitly. */
    private static void repairVirtualPastureConfig(Config.VirtualPastureConfig pasture) {
        List<String> blockIds = pasture.getBlockIds().stream()
            .filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (blockIds.isEmpty()) {
            blockIds = List.of("virtualloot:virtual_pasture", "cobblemon:pasture");
        }
        pasture.setBlockIds(blockIds);
        pasture.setMaxPerChunk(pasture.getMaxPerChunk());
        if (pasture.getAdminBypassPermission() == null || pasture.getAdminBypassPermission().isBlank()) {
            pasture.setAdminBypassPermission("bigbangregions.virtualpasture.bypass");
        }

        Map<String, Config.PastureLimit> limits = new HashMap<>();
        pasture.getLimits().forEach((tier, limit) -> {
            if (tier != null && !tier.isBlank() && limit != null) {
                limits.put(tier, new Config.PastureLimit(limit.getPerPlayer(), limit.getPerRegion()));
            }
        });
        limits.putIfAbsent("default", new Config.PastureLimit(20, 30));
        limits.putIfAbsent("vip", new Config.PastureLimit(30, 40));
        limits.putIfAbsent("elite", new Config.PastureLimit(50, 60));
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
