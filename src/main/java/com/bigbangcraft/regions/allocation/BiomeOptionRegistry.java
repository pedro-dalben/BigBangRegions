package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BooleanSupplier;

public class BiomeOptionRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-BiomeOptionRegistry");
    private static final Set<String> BLOCKED_OPTION_KEYS = Set.of("oceano");
    private static final String REGIONS_UNEXPLORED_MOD_ID = "regions_unexplored";
    private static final Map<String, List<String>> REGIONS_UNEXPLORED_BIOMES = Map.ofEntries(
        Map.entry("floresta", List.of(
            "alpha_grove", "ashen_woodland", "autumnal_maple_forest", "eucalyptus_forest",
            "magnolia_woodland", "maple_forest", "old_growth_forest", "orchard", "redwoods",
            "silver_birch_forest", "sparse_redwoods", "willow_forest", "windswept_maple_forest",
            "wisteria_grove"
        )),
        Map.entry("taiga", List.of(
            "blackwood_taiga", "boreal_taiga", "cold_boreal_taiga", "frozen_pine_taiga",
            "old_growth_boreal_taiga", "old_growth_golden_boreal_taiga", "pine_slopes", "pine_taiga"
        )),
        Map.entry("deserto", List.of("dry_bushland", "joshua_desert", "outback", "saguaro_desert", "shrubland")),
        Map.entry("savana", List.of("baobab_savanna")),
        Map.entry("selva", List.of("bamboo_forest", "rainforest", "sparse_rainforest", "tropics")),
        Map.entry("planicies", List.of("clover_plains", "flower_fields", "grassland", "poppy_fields", "prairie")),
        Map.entry("praia", List.of("grassy_beach", "gravel_beach")),
        Map.entry("montanha", List.of("chalk_cliffs", "highland_fields", "icy_heights", "spires", "towering_cliffs")),
        Map.entry("pantano", List.of("bayou", "fen", "fungal_fen", "marsh", "old_growth_bayou")),
        Map.entry("neve", List.of("tundra")),
        Map.entry("rio", List.of("cold_river", "muddy_river", "tropical_river"))
    );
    private final ConfigManager configManager;
    private final BooleanSupplier regionsUnexploredLoaded;
    private final Map<String, BiomeOption> options = new LinkedHashMap<>();

    public BiomeOptionRegistry(ConfigManager configManager) {
        this(configManager, () -> FabricLoader.getInstance().isModLoaded(REGIONS_UNEXPLORED_MOD_ID));
    }

    BiomeOptionRegistry(ConfigManager configManager, BooleanSupplier regionsUnexploredLoaded) {
        this.configManager = configManager;
        this.regionsUnexploredLoaded = regionsUnexploredLoaded;
    }

    public void load() {
        options.clear();
        Config config = configManager.getConfig();
        if (config == null || config.getBiomeOptions() == null) {
            return;
        }
        boolean regionsUnexploredLoaded = this.regionsUnexploredLoaded.getAsBoolean();

        for (Map.Entry<String, Config.BiomeOptionConfig> entry : config.getBiomeOptions().entrySet()) {
            String key = entry.getKey().toLowerCase();
            Config.BiomeOptionConfig optionConfig = entry.getValue();

            if (BLOCKED_OPTION_KEYS.contains(key)) {
                LOGGER.info("Biome option '{}' ignored: blocked by server policy.", key);
                continue;
            }

            if (optionConfig.getDisplayName() == null || optionConfig.getDisplayName().trim().isEmpty()) {
                LOGGER.warn("Biome option '{}' ignored: missing display name.", key);
                continue;
            }

            if (optionConfig.getAcceptedBiomeIds() == null || optionConfig.getAcceptedBiomeIds().isEmpty()) {
                LOGGER.warn("Biome option '{}' ignored: empty accepted biome list.", key);
                continue;
            }

            Set<String> configuredIds = new LinkedHashSet<>(optionConfig.getAcceptedBiomeIds());
            if (regionsUnexploredLoaded) {
                REGIONS_UNEXPLORED_BIOMES.getOrDefault(key, List.of()).stream()
                    .map(id -> REGIONS_UNEXPLORED_MOD_ID + ":" + id)
                    .forEach(configuredIds::add);
            }

            List<String> validIds = new ArrayList<>();
            List<String> invalidIds = new ArrayList<>();
            for (String biomeId : configuredIds) {
                try {
                    ResourceLocation.parse(biomeId);
                    validIds.add(biomeId);
                } catch (Exception e) {
                    invalidIds.add(biomeId);
                    LOGGER.warn("Biome option '{}': invalid biome ID '{}' discarded: {}", key, biomeId, e.getMessage());
                }
            }
            if (validIds.isEmpty()) {
                LOGGER.error("Biome option '{}' disabled: no valid biome IDs. configured={} resolved=[] invalid={}",
                    key, configuredIds, invalidIds);
                continue;
            }
            if (!invalidIds.isEmpty()) {
                LOGGER.info("Biome option '{}': resolved={} invalid={}", key, validIds, invalidIds);
            } else {
                LOGGER.info("Biome option '{}': resolved={}", key, validIds);
            }

            BiomeOption option = new BiomeOption(
                    key,
                    optionConfig.getDisplayName(),
                    optionConfig.getAliases() != null ? optionConfig.getAliases() : Collections.emptyList(),
                    validIds,
                    optionConfig.getIcon()
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
