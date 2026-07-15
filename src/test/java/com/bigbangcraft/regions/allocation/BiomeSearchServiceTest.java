package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BiomeSearchServiceTest {
    @BeforeAll
    public static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void rejectsMixedBiomeEdgesWithoutChunkAccess() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-biome-search-test");
        ConfigManager configManager = new ConfigManager(tempDir);
        BiomeSearchService service = new BiomeSearchService(configManager);

        Holder<Biome> plains = biomeHolder("minecraft:plains");
        Holder<Biome> river = biomeHolder("minecraft:river");
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            int quartX = inv.getArgument(0);
            int quartZ = inv.getArgument(2);
            if (quartX == 0 || quartZ == 0 || quartX == 16 || quartZ == 16) {
                return river;
            }
            return plains;
        });

        WorldgenSearchContext context = worldgenContext(biomeSource, "mixed-edges");
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));

        assertEquals(
            BiomeSearchService.MatchResult.MISMATCH,
            service.evaluateBiomeOptionMatching(context, 0, 64, 0, 64, option)
        );
    }

    @Test
    public void relaxedFallbackAcceptsBiomeAtCenterWhenStrictBorderRuleRejects() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-biome-search-test");
        ConfigManager configManager = new ConfigManager(tempDir);
        configManager.getConfig().getPlayerLandAllocation().getBiomeSearch().setMinimumMatchPercentage(60);
        configManager.getConfig().getPlayerLandAllocation().getBiomeSearch().setMinimumBorderMatchPercentage(60);
        configManager.getConfig().getPlayerLandAllocation().getBiomeSearch().setRelaxedFallbackEnabled(true);
        configManager.getConfig().getPlayerLandAllocation().getBiomeSearch().setRelaxedMinimumMatchPercentage(30);
        configManager.getConfig().getPlayerLandAllocation().getBiomeSearch().setRelaxedMinimumBorderMatchPercentage(0);
        BiomeSearchService service = new BiomeSearchService(configManager);

        Holder<Biome> plains = biomeHolder("minecraft:plains");
        Holder<Biome> river = biomeHolder("minecraft:river");
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            int quartX = inv.getArgument(0);
            int quartZ = inv.getArgument(2);
            return quartX > 0 && quartX < 16 && quartZ > 0 && quartZ < 16 ? plains : river;
        });

        WorldgenSearchContext context = worldgenContext(biomeSource, "relaxed-center-plains");
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));

        assertEquals(BiomeSearchService.MatchResult.MISMATCH,
            service.evaluateBiomeOptionMatching(context, 0, 64, 0, 64, option));
        assertEquals(BiomeSearchService.MatchResult.MATCH,
            service.evaluateRelaxedBiomeOptionMatching(context, 0, 64, 0, 64, option));
    }

    @Test
    public void acceptsPureMatchingBiomeWithoutChunkAccess() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-biome-search-test");
        ConfigManager configManager = new ConfigManager(tempDir);
        BiomeSearchService service = new BiomeSearchService(configManager);

        Holder<Biome> plains = biomeHolder("minecraft:plains");
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any())).thenReturn(plains);

        WorldgenSearchContext context = worldgenContext(biomeSource, "pure-plains");
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));

        assertEquals(
            BiomeSearchService.MatchResult.MATCH,
            service.evaluateBiomeOptionMatching(context, 0, 64, 0, 64, option)
        );
    }

    @Test
    public void rejectsWhenAcceptedBiomeIsNotVisibleInArea() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-biome-search-test");
        ConfigManager configManager = new ConfigManager(tempDir);
        BiomeSearchService service = new BiomeSearchService(configManager);

        Holder<Biome> river = biomeHolder("minecraft:river");
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any())).thenReturn(river);

        WorldgenSearchContext context = worldgenContext(biomeSource, "river-only");
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));

        assertEquals(
            BiomeSearchService.MatchResult.MISMATCH,
            service.evaluateBiomeOptionMatching(context, 0, 64, 0, 64, option)
        );
    }

    @Test
    public void footprintValidationDoesNotUseGlobalBiomeAreaScan() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-biome-search-test");
        ConfigManager configManager = new ConfigManager(tempDir);
        BiomeSearchService service = new BiomeSearchService(configManager);

        Holder<Biome> plains = biomeHolder("minecraft:plains");
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any())).thenReturn(plains);

        WorldgenSearchContext context = worldgenContext(biomeSource, "no-area-scan");
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));

        assertEquals(
            BiomeSearchService.MatchResult.MATCH,
            service.evaluateBiomeOptionMatching(context, 0, 64, 0, 64, option)
        );
        verify(biomeSource, never()).getBiomesWithin(anyInt(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void cachesVirtualSamplesByFingerprint() {
        Holder<Biome> plains = biomeHolder("minecraft:plains");
        AtomicInteger calls = new AtomicInteger();
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            calls.incrementAndGet();
            return plains;
        });

        CachingBiomeVirtualSampler sampler = new CachingBiomeVirtualSampler(16, 60_000L);
        WorldgenSearchContext firstContext = worldgenContext(biomeSource, "fingerprint-a");
        WorldgenSearchContext secondContext = worldgenContext(biomeSource, "fingerprint-b");

        ResourceKey<Biome> first = sampler.sampleAtBlock(firstContext, 0, 64, 0);
        ResourceKey<Biome> second = sampler.sampleAtBlock(firstContext, 0, 64, 0);
        ResourceKey<Biome> third = sampler.sampleAtBlock(secondContext, 0, 64, 0);

        assertEquals(ResourceKey.create(Registries.BIOME, ResourceLocation.parse("minecraft:plains")), first);
        assertEquals(first, second);
        assertEquals(first, third);
        assertEquals(2, calls.get());
    }

    private static WorldgenSearchContext worldgenContext(BiomeSource biomeSource, String fingerprintHash) {
        @SuppressWarnings("unchecked")
        ChunkGenerator chunkGenerator = mock(ChunkGenerator.class);
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        WorldgenFingerprint fingerprint = new WorldgenFingerprint(
            fingerprintHash,
            "minecraft:overworld",
            1234L,
            "generator",
            "biomeSource",
            "datapacks",
            "biomeReplacer",
            Config.BIOME_SEARCH_VALIDATION_SCHEMA_VERSION
        );
        return new WorldgenSearchContext(dimensionKey, 1234L, chunkGenerator, biomeSource, Climate.empty(), fingerprint, 64, java.util.List.of(64));
    }

    private static BiomeOption biomeOption(String key, List<String> acceptedBiomeIds) {
        return new BiomeOption(key, key, List.of(key), acceptedBiomeIds, "minecraft:map");
    }

    private static Holder<Biome> biomeHolder(String biomeId) {
        @SuppressWarnings("unchecked")
        Holder<Biome> holder = mock(Holder.class);
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId));
        when(holder.unwrapKey()).thenReturn(Optional.of(key));
        return holder;
    }
}
