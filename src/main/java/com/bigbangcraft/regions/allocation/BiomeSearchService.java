package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BiomeSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-BiomeSearchService");
    private final ConfigManager configManager;
    private final BiomeSampleCache sampleCache;

    public enum MatchResult {
        MATCH,
        MISMATCH,
        PENDING
    }

    public BiomeSearchService(ConfigManager configManager) {
        this(configManager, new BiomeSampleCache(2048, 120_000L));
    }

    public BiomeSearchService(ConfigManager configManager, BiomeSampleCache sampleCache) {
        this.configManager = configManager;
        this.sampleCache = sampleCache;
    }

    public BiomeSampleCache getSampleCache() {
        return sampleCache;
    }

    public boolean isBiomeOptionMatching(ServerLevel level, int minX, int maxX, int minZ, int maxZ, BiomeOption option) {
        return evaluateBiomeOptionMatching(level, minX, maxX, minZ, maxZ, option) == MatchResult.MATCH;
    }

    public MatchResult evaluateBiomeOptionMatching(ServerLevel level, int minX, int maxX, int minZ, int maxZ, BiomeOption option) {
        Config config = configManager.getConfig();
        Config.BiomeSearchConfig bsc = config.getPlayerLandAllocation().getBiomeSearch();

        int sampleGridSize = bsc.getSampleGridSize();
        if (sampleGridSize < 3) sampleGridSize = 3;
        if (sampleGridSize % 2 == 0) sampleGridSize += 1;

        Set<ResourceKey<Biome>> acceptedKeys = resolveAcceptedKeys(option.getAcceptedBiomeIds());
        if (acceptedKeys.isEmpty()) return MatchResult.MISMATCH;

        // Phase 1: corners first (fastest rejection)
        int[][] corners = {
            {minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}
        };
        for (int[] c : corners) {
            BiomeSampler.SampleRead sample = readBiome(level, c[0], c[1]);
            if (sample.isPending()) {
                return MatchResult.PENDING;
            }
            ResourceKey<Biome> bk = sample.getBiomeKey();
            if (bk == null || !acceptedKeys.contains(bk)) {
                return MatchResult.MISMATCH;
            }
        }

        // Phase 2: edges (excluding corners already checked)
        int stepX = (sampleGridSize > 1) ? (maxX - minX) / (sampleGridSize - 1) : 0;
        int stepZ = (sampleGridSize > 1) ? (maxZ - minZ) / (sampleGridSize - 1) : 0;

        for (int i = 1; i < sampleGridSize - 1; i++) {
            int x = minX + i * stepX;
            int zTop = minZ;
            int zBottom = maxZ;

            BiomeSampler.SampleRead bkTop = readBiome(level, x, zTop);
            if (bkTop.isPending()) {
                return MatchResult.PENDING;
            }
            if (bkTop.getBiomeKey() == null || !acceptedKeys.contains(bkTop.getBiomeKey())) {
                return MatchResult.MISMATCH;
            }

            BiomeSampler.SampleRead bkBottom = readBiome(level, x, zBottom);
            if (bkBottom.isPending()) {
                return MatchResult.PENDING;
            }
            if (bkBottom.getBiomeKey() == null || !acceptedKeys.contains(bkBottom.getBiomeKey())) {
                return MatchResult.MISMATCH;
            }
        }
        for (int j = 1; j < sampleGridSize - 1; j++) {
            int z = minZ + j * stepZ;
            int xLeft = minX;
            int xRight = maxX;

            BiomeSampler.SampleRead bkLeft = readBiome(level, xLeft, z);
            if (bkLeft.isPending()) {
                return MatchResult.PENDING;
            }
            if (bkLeft.getBiomeKey() == null || !acceptedKeys.contains(bkLeft.getBiomeKey())) {
                return MatchResult.MISMATCH;
            }

            BiomeSampler.SampleRead bkRight = readBiome(level, xRight, z);
            if (bkRight.isPending()) {
                return MatchResult.PENDING;
            }
            if (bkRight.getBiomeKey() == null || !acceptedKeys.contains(bkRight.getBiomeKey())) {
                return MatchResult.MISMATCH;
            }
        }

        // Phase 3: interior (only if edges all passed)
        int totalCount = sampleGridSize * sampleGridSize;
        int matchCount = (sampleGridSize - 1) * 4; // corners + edges already counted

        for (int i = 1; i < sampleGridSize - 1; i++) {
            int x = minX + i * stepX;
            for (int j = 1; j < sampleGridSize - 1; j++) {
                int z = minZ + j * stepZ;
                BiomeSampler.SampleRead sample = readBiome(level, x, z);
                if (sample.isPending()) {
                    return MatchResult.PENDING;
                }
                ResourceKey<Biome> bk = sample.getBiomeKey();
                if (bk != null && acceptedKeys.contains(bk)) {
                    matchCount++;
                }
            }
        }

        double matchPercentage = ((double) matchCount / totalCount) * 100.0;
        boolean pass = matchPercentage >= bsc.getMinimumMatchPercentage();
        LOGGER.debug("Biome check [{}x{}]-[{}x{}] for '{}': match={}/{} ({}%), pass={}",
                minX, maxX, minZ, maxZ, option.getKey(), matchCount, totalCount, matchPercentage, pass);
        return pass ? MatchResult.MATCH : MatchResult.MISMATCH;
    }

    private BiomeSampler.SampleRead readBiome(ServerLevel level, int blockX, int blockZ) {
        return BiomeSampler.readBiomeSample(level, blockX, blockZ,
                Config.BIOME_SEARCH_VALIDATION_SCHEMA_VERSION, sampleCache);
    }

    private Set<ResourceKey<Biome>> resolveAcceptedKeys(List<String> biomeIds) {
        Set<ResourceKey<Biome>> keys = new LinkedHashSet<>();
        for (String id : biomeIds) {
            try {
                ResourceLocation loc = ResourceLocation.parse(id);
                keys.add(ResourceKey.create(Registries.BIOME, loc));
            } catch (Exception e) {
                LOGGER.warn("Invalid biome ID in accepted list: {}", id);
            }
        }
        return keys;
    }
}
