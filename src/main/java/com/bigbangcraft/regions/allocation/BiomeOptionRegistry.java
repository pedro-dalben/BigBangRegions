package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BiomeOptionRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-BiomeOptionRegistry");
    private final ConfigManager configManager;
    private final Map<String, BiomeOption> options = new LinkedHashMap<>();

    public BiomeOptionRegistry(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void load() {
        options.clear();
        Config config = configManager.getConfig();
        if (config == null || config.getBiomeOptions() == null) {
            return;
        }

        for (Map.Entry<String, Config.BiomeOptionConfig> entry : config.getBiomeOptions().entrySet()) {
            String key = entry.getKey().toLowerCase();
            Config.BiomeOptionConfig optionConfig = entry.getValue();

            if (optionConfig.getDisplayName() == null || optionConfig.getDisplayName().trim().isEmpty()) {
                LOGGER.warn("Biome option '{}' ignored: missing display name.", key);
                continue;
            }

            if (optionConfig.getAcceptedBiomeIds() == null || optionConfig.getAcceptedBiomeIds().isEmpty()) {
                LOGGER.warn("Biome option '{}' ignored: empty accepted biome list.", key);
                continue;
            }

            BiomeOption option = new BiomeOption(
                    key,
                    optionConfig.getDisplayName(),
                    optionConfig.getAliases() != null ? optionConfig.getAliases() : Collections.emptyList(),
                    optionConfig.getAcceptedBiomeIds()
            );
            options.put(key, option);
        }
        LOGGER.info("Registered {} biome options.", options.size());
    }

    public Collection<BiomeOption> getAll() {
        return options.values();
    }

    public Optional<BiomeOption> lookup(String query) {
        if (query == null) return Optional.empty();
        String q = query.toLowerCase().trim();

        // Exact key match first
        BiomeOption direct = options.get(q);
        if (direct != null) {
            return Optional.of(direct);
        }

        // Search in aliases
        for (BiomeOption option : options.values()) {
            if (option.matches(q)) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }
}
