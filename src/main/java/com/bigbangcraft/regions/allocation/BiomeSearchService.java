package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BiomeSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-BiomeSearchService");
    private final ConfigManager configManager;

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

        for (int i = 0; i < sampleGridSize; i++) {
            int x = minX + i * stepX;
            for (int j = 0; j < sampleGridSize; j++) {
                int z = minZ + j * stepZ;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                Holder<Biome> holder = level.getBiome(new BlockPos(x, y, z));
                String biomeId = holder.unwrapKey()
                                       .map(k -> k.location().toString())
                                       .orElse("");

                if (acceptedBiomeIds.contains(biomeId)) {
                    matchCount++;
                }
            }
        }

        double matchPercentage = ((double) matchCount / totalCount) * 100.0;
        boolean matches = matchPercentage >= bsc.getMinimumMatchPercentage();
        LOGGER.debug("Checked bounds [{} to {}] x [{} to {}] for biome '{}': matches={} ({}%)",
                minX, maxX, minZ, maxZ, option.getKey(), matches, matchPercentage);
        return matches;
    }
}
