package com.bigbangcraft.regions.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
            } catch (Exception e) {
                LOGGER.error("Failed to parse configuration file. Using safe fallback defaults. Error details: ", e);
                // Safe fallback: keep the default config created in constructor
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read or create configuration directory/file: ", e);
        }
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
