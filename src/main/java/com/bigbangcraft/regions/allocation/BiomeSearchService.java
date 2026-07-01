package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BiomeSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-BiomeSearchService");

    private final ConfigManager configManager;
    private final WorldgenSearchContextFactory contextFactory;
    private final BiomeVirtualSampler virtualSampler;

    public enum MatchResult {
        MATCH,
        MISMATCH,
        PENDING
    }

    public BiomeSearchService(ConfigManager configManager) {
        this(configManager, new WorldgenSearchContextFactory(), createSampler(configManager));
    }

    public BiomeSearchService(ConfigManager configManager, WorldgenSearchContextFactory contextFactory, BiomeVirtualSampler virtualSampler) {
        this.configManager = configManager;
        this.contextFactory = contextFactory;
        this.virtualSampler = virtualSampler;
    }

    public boolean isBiomeOptionMatching(ServerLevel level, int minX, int maxX, int minZ, int maxZ, BiomeOption option) {
        return evaluateBiomeOptionMatching(level, minX, maxX, minZ, maxZ, option) == MatchResult.MATCH;
    }

    public MatchResult evaluateBiomeOptionMatching(ServerLevel level, int minX, int maxX, int minZ, int maxZ, BiomeOption option) {
        if (level == null) {
            return MatchResult.PENDING;
        }
        Config config = configManager.getConfig();
        WorldgenSearchContext context = contextFactory.getOrCreate(level, config);
        return evaluateBiomeOptionMatching(context, minX, maxX, minZ, maxZ, option);
    }

    public MatchResult evaluateBiomeOptionMatching(WorldgenSearchContext context, int minX, int maxX, int minZ, int maxZ, BiomeOption option) {
        if (context == null || option == null) {
            return MatchResult.MISMATCH;
        }

        Config config = configManager.getConfig();
        Config.BiomeSearchConfig biomeSearch = config.getPlayerLandAllocation().getBiomeSearch();
        int sampleGridSize = normalizeGridSize(biomeSearch.getSampleGridSize());
        int minimumMatchPercentage = biomeSearch.getMinimumMatchPercentage();

        VirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            virtualSampler,
            sampleGridSize,
            minimumMatchPercentage
        );

        VirtualBiomeValidationResult result = validator.validate(
            context,
            option,
            new PlotFootprint(minX, maxX, minZ, maxZ),
            SearchBudget.unbounded()
        );

        if (result.failureReason() == ValidationFailureReason.BUDGET_EXHAUSTED) {
            LOGGER.debug("Virtual biome validation budget exhausted for '{}' in {}", option.getKey(), context.dimensionKey());
            return MatchResult.PENDING;
        }

        return result.accepted() ? MatchResult.MATCH : MatchResult.MISMATCH;
    }

    public WorldgenSearchContextFactory getContextFactory() {
        return contextFactory;
    }

    public BiomeVirtualSampler getVirtualSampler() {
        return virtualSampler;
    }

    private static BiomeVirtualSampler createSampler(ConfigManager configManager) {
        Config config = configManager.getConfig();
        Config.WorldgenSearchConfig worldgenSearch = config.getPlayerLandAllocation().getWorldgenSearch();
        return new CachingBiomeVirtualSampler(
            worldgenSearch.getVirtualBiomeCacheMaxEntries(),
            worldgenSearch.getVirtualBiomeCacheTtlSeconds() * 1000L
        );
    }

    private static int normalizeGridSize(int gridSize) {
        int normalized = Math.max(3, gridSize);
        if (normalized % 2 == 0) {
            normalized++;
        }
        return normalized;
    }
}
