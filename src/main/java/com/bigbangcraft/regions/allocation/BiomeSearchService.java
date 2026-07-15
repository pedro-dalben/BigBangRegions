package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BiomeSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-BiomeSearchService");

    private final ConfigManager configManager;
    private final WorldgenSearchContextFactory contextFactory;
    private final BiomeVirtualSampler virtualSampler;
    private final ConcurrentMap<ValidatorKey, AdaptiveVirtualFootprintValidator> validators = new ConcurrentHashMap<>();

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
        return evaluateBiomeOptionMatching(
            context, minX, maxX, minZ, maxZ, option,
            biomeSearch.getMinimumMatchPercentage(),
            biomeSearch.getMinimumBorderMatchPercentage(),
            biomeSearch.isRequireFullBorderMatch()
        );
    }

    /**
     * A bounded fallback for irregular biomes. The claim center remains a
     * mandatory match, but the outer footprint may transition into nearby
     * biomes when no strict candidate exists.
     */
    public MatchResult evaluateRelaxedBiomeOptionMatching(
        WorldgenSearchContext context, int minX, int maxX, int minZ, int maxZ, BiomeOption option
    ) {
        if (context == null || option == null) {
            return MatchResult.MISMATCH;
        }

        Config.BiomeSearchConfig biomeSearch = configManager.getConfig().getPlayerLandAllocation().getBiomeSearch();
        if (!biomeSearch.isRelaxedFallbackEnabled()) {
            return MatchResult.MISMATCH;
        }
        return evaluateBiomeOptionMatching(
            context, minX, maxX, minZ, maxZ, option,
            biomeSearch.getRelaxedMinimumMatchPercentage(),
            biomeSearch.getRelaxedMinimumBorderMatchPercentage(),
            false
        );
    }

    private MatchResult evaluateBiomeOptionMatching(
        WorldgenSearchContext context, int minX, int maxX, int minZ, int maxZ, BiomeOption option,
        int minimumMatchPercentage, int minimumBorderMatchPercentage, boolean requireFullBorderMatch
    ) {
        int sampleGridSize = normalizeGridSize(configManager.getConfig().getPlayerLandAllocation().getBiomeSearch().getSampleGridSize());

        VirtualFootprintValidator validator = validators.computeIfAbsent(
            new ValidatorKey(sampleGridSize, minimumMatchPercentage, minimumBorderMatchPercentage, requireFullBorderMatch),
            key -> new AdaptiveVirtualFootprintValidator(
                virtualSampler,
                key.sampleGridSize(),
                key.minimumMatchPercentage(),
                key.minimumBorderMatchPercentage(),
                key.requireFullBorderMatch()
            )
        );

        long startedAt = System.nanoTime();
        VirtualBiomeValidationResult result = validator.validate(
            context,
            option,
            new PlotFootprint(minX, maxX, minZ, maxZ),
            SearchBudget.unbounded()
        );
        AllocationMetrics.add("bigbangregions_virtual_validation_nanos_total", System.nanoTime() - startedAt);
        AllocationMetrics.increment("bigbangregions_virtual_validation_total");

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

    private record ValidatorKey(
        int sampleGridSize,
        int minimumMatchPercentage,
        int minimumBorderMatchPercentage,
        boolean requireFullBorderMatch
    ) {
    }
}
