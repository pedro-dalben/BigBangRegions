package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BiomeSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-BiomeSearchService");
    private final ConfigManager configManager;

    private static final int SAMPLE_Y = 64;

    public BiomeSearchService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public boolean isBiomeOptionMatching(ServerLevel level, int minX, int maxX, int minZ, int maxZ, BiomeOption option) {
        Config config = configManager.getConfig();
        Config.BiomeSearchConfig bsc = config.getPlayerLandAllocation().getBiomeSearch();

        int sampleGridSize = bsc.getSampleGridSize();
        if (sampleGridSize < 3) sampleGridSize = 3;
        if (sampleGridSize % 2 == 0) sampleGridSize += 1;

        List<String> acceptedBiomeIds = option.getAcceptedBiomeIds();

        int stepX = (sampleGridSize > 1) ? (maxX - minX) / (sampleGridSize - 1) : 0;
        int stepZ = (sampleGridSize > 1) ? (maxZ - minZ) / (sampleGridSize - 1) : 0;

        int matchCount = 0;
        int totalCount = sampleGridSize * sampleGridSize;
        int edgeMismatchCount = 0;
        int biomeY = SAMPLE_Y >> 2;

        for (int i = 0; i < sampleGridSize; i++) {
            int x = minX + i * stepX;
            for (int j = 0; j < sampleGridSize; j++) {
                int z = minZ + j * stepZ;

                String biomeId = readBiomeId(level, x, z, biomeY);

                if (acceptedBiomeIds.contains(biomeId)) {
                    matchCount++;
                } else if (isEdgeSample(i, j, sampleGridSize)) {
                    edgeMismatchCount++;
                }
            }
        }

        double matchPercentage = ((double) matchCount / totalCount) * 100.0;
        boolean matches = matchPercentage >= bsc.getMinimumMatchPercentage() && edgeMismatchCount == 0;
        LOGGER.debug("Checked bounds [{} to {}] x [{} to {}] for biome '{}': matches={} ({}%)",
                minX, maxX, minZ, maxZ, option.getKey(), matches, matchPercentage);
        return matches;
    }

    private String readBiomeId(ServerLevel level, int blockX, int blockZ, int biomeY) {
        try {
            int cx = blockX >> 4;
            int cz = blockZ >> 4;

            // BIOMES status skips terrain/noise/features/light generation. With Chunky
            // pre-generated worlds this simply deserializes the saved biome palettes.
            // create=true so unloaded pre-gen chunks still resolve; this never forces FULL.
            ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.BIOMES, true);
            if (chunk == null) return "";

            Holder<Biome> holder = chunk.getNoiseBiome(blockX >> 2, biomeY, blockZ >> 2);
            return holder.unwrapKey()
                    .map(k -> k.location().toString())
                    .orElse("");
        } catch (Exception e) {
            LOGGER.debug("Failed to read biome at ({},{},{}): {}", blockX, SAMPLE_Y, blockZ, e.getMessage());
            return "";
        }
    }

    private boolean isEdgeSample(int xIndex, int zIndex, int sampleGridSize) {
        return xIndex == 0 || zIndex == 0 || xIndex == sampleGridSize - 1 || zIndex == sampleGridSize - 1;
    }
}