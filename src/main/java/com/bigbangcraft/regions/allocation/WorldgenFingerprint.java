package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public record WorldgenFingerprint(
    String hash,
    String dimensionId,
    long worldSeed,
    String generatorCodecId,
    String biomeSourceCodecId,
    String datapackFingerprint,
    String biomeReplacerFingerprint,
    int validationSchemaVersion
) {
    public static WorldgenFingerprint capture(ServerLevel level, Config config, int sampleBlockY) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(config, "config");

        ResourceKey<Level> dimensionKey = level.dimension();
        long worldSeed = level.getSeed();
        ChunkGenerator chunkGenerator = level.getChunkSource().getGenerator();
        BiomeSource biomeSource = chunkGenerator.getBiomeSource();

        String dimensionId = dimensionKey.location().toString();
        String generatorCodecId = chunkGenerator.getTypeNameForDataFixer()
            .map(key -> key.location().toString())
            .orElse(chunkGenerator.getClass().getName());
        String biomeSourceCodecId = biomeSource.getClass().getName() + "|" + canonicalPossibleBiomes(biomeSource);
        String datapackFingerprint = digest(joinSelectedPackIds(level));
        String biomeReplacerFingerprint = digest(canonicalBiomeOptions(config));

        Config.WorldgenSearchConfig wg = config.getPlayerLandAllocation().getWorldgenSearch();
        Config.BiomeSearchConfig bs = config.getPlayerLandAllocation().getBiomeSearch();
        String sampleBlockYsStr = wg.getSampleBlockYs().stream()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(","));
        String stablePayload = new StringJoiner("\n")
            .add("dimension=" + dimensionId)
            .add("seed=" + worldSeed)
            .add("generator=" + generatorCodecId)
            .add("biomeSource=" + biomeSourceCodecId)
            .add("datapacks=" + datapackFingerprint)
            .add("biomeReplacer=" + biomeReplacerFingerprint)
            .add("sampleBlockY=" + sampleBlockY)
            .add("sampleBlockYs=" + sampleBlockYsStr)
            .add("minSampleY=" + (wg.getMinSampleY() == null ? "" : wg.getMinSampleY()))
            .add("maxSampleY=" + (wg.getMaxSampleY() == null ? "" : wg.getMaxSampleY()))
            .add("verticalCheckInterval=" + wg.getVerticalCheckInterval())
            .add("initialClaimSize=" + config.getPlayerLandAllocation().getInitialClaimSize())
            .add("slotSize=" + config.getPlayerLandAllocation().getSlotSize())
            .add("futureMaximumClaimSize=" + config.getPlayerLandAllocation().getFutureMaximumClaimSize())
            .add("slotInternalMargin=" + config.getPlayerLandAllocation().getSlotInternalMargin())
            .add("minimumMatchPercentage=" + bs.getMinimumMatchPercentage())
            .add("sampleGridSize=" + bs.getSampleGridSize())
            .add("maximumCandidateSlots=" + bs.getMaximumCandidateSlots())
            .add("maximumSearchRadiusBlocks=" + bs.getMaximumSearchRadiusBlocks())
            .add("requireFullBorderMatch=" + bs.isRequireFullBorderMatch())
            .add("minimumBorderMatchPercentage=" + bs.getMinimumBorderMatchPercentage())
            .add("relaxedFallbackEnabled=" + bs.isRelaxedFallbackEnabled())
            .add("relaxedMinimumMatchPercentage=" + bs.getRelaxedMinimumMatchPercentage())
            .add("relaxedMinimumBorderMatchPercentage=" + bs.getRelaxedMinimumBorderMatchPercentage())
            .add("worldgenSampleBlockY=" + wg.getSampleBlockY())
            .add("virtualBiomeCacheMaxEntries=" + wg.getVirtualBiomeCacheMaxEntries())
            .add("virtualBiomeCacheTtlSeconds=" + wg.getVirtualBiomeCacheTtlSeconds())
            .add("locateRadiusBlocks=" + wg.getLocateRadiusBlocks())
            .add("blockCheckInterval=" + wg.getBlockCheckInterval())
            .add("maxLocateCallsPerSearchStep=" + wg.getMaxLocateCallsPerSearchStep())
            .add("maxLocateCallsPerTick=" + wg.getMaxLocateCallsPerTick())
            .add("validationSchemaVersion=" + Config.BIOME_SEARCH_VALIDATION_SCHEMA_VERSION)
            .toString();

        String hash = digest(stablePayload);
        return new WorldgenFingerprint(
            hash,
            dimensionId,
            worldSeed,
            generatorCodecId,
            biomeSourceCodecId,
            datapackFingerprint,
            biomeReplacerFingerprint,
            Config.BIOME_SEARCH_VALIDATION_SCHEMA_VERSION
        );
    }

    private static String joinSelectedPackIds(ServerLevel level) {
        if (level.getServer() == null || level.getServer().getPackRepository() == null) {
            return "";
        }
        Collection<String> selectedIds = level.getServer().getPackRepository().getSelectedIds();
        return selectedIds.stream().sorted().collect(Collectors.joining(","));
    }

    private static String canonicalBiomeOptions(Config config) {
        Map<String, Config.BiomeOptionConfig> biomeOptions = config.getBiomeOptions();
        return biomeOptions.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                Config.BiomeOptionConfig value = entry.getValue();
                String aliases = value.getAliases().stream().sorted().collect(Collectors.joining(","));
                String acceptedBiomes = value.getAcceptedBiomeIds().stream().sorted().collect(Collectors.joining(","));
                return entry.getKey()
                    + "|display=" + value.getDisplayName()
                    + "|aliases=" + aliases
                    + "|accepted=" + acceptedBiomes
                    + "|icon=" + value.getIcon();
            })
            .collect(Collectors.joining("||"));
    }

    private static String canonicalPossibleBiomes(BiomeSource biomeSource) {
        List<String> entries = new ArrayList<>();
        for (Holder<Biome> holder : biomeSource.possibleBiomes()) {
            String biomeId = holder.unwrapKey()
                .map(ResourceKey::location)
                .map(ResourceLocation::toString)
                .orElse(holder.toString());
            entries.add(biomeId);
        }
        entries.sort(Comparator.naturalOrder());
        return String.join(",", entries);
    }

    private static String digest(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }
}
