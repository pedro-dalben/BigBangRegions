package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

public class BiomeSearchServiceTest {
    @BeforeAll
    public static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @SuppressWarnings("unchecked")
    private static LevelChunk chunkWith(ServerLevel level, java.util.function.BiFunction<Integer, Integer, Holder<Biome>> resolve) {
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld")));
        LevelChunk chunk = mock(LevelChunk.class);
        when(chunk.getNoiseBiome(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            int bx = inv.getArgument(0);
            int bz = inv.getArgument(2);
            return resolve.apply(bx, bz);
        });
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(level.getChunkSource()).thenReturn(chunkSource);
        when(chunkSource.getChunkNow(anyInt(), anyInt())).thenReturn(chunk);
        return chunk;
    }

    @Test
    public void rejectsMixedBiomeEdges() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-biome-search-test");
        ConfigManager configManager = new ConfigManager(tempDir);
        BiomeSearchService service = new BiomeSearchService(configManager);

        ServerLevel level = mock(ServerLevel.class);
        Holder<Biome> plains = biomeHolder("minecraft:plains");
        Holder<Biome> river = biomeHolder("minecraft:river");
        // Biome coordinates are block>>2. Claim [0,64] 5x5 grid -> biome coords 0..16 step 4.
        // Edge samples have biome==0 or biome==16 -> river; interior -> plains.
        chunkWith(level, (bx, bz) -> {
            if (bx == 0 || bz == 0 || bx == 16 || bz == 16) return river;
            return plains;
        });

        BiomeOption option = new BiomeOption(
            "planicies",
            "Planicies",
            List.of("plains"),
            List.of("minecraft:plains"),
            "minecraft:grass_block"
        );

        assertFalse(service.isBiomeOptionMatching(level, 0, 64, 0, 64, option));
    }

    @Test
    public void acceptsPureMatchingBiome() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-biome-search-test");
        ConfigManager configManager = new ConfigManager(tempDir);
        BiomeSearchService service = new BiomeSearchService(configManager);

        ServerLevel level = mock(ServerLevel.class);
        Holder<Biome> plains = biomeHolder("minecraft:plains");
        chunkWith(level, (bx, bz) -> plains);

        BiomeOption option = new BiomeOption(
            "planicies",
            "Planicies",
            List.of("plains"),
            List.of("minecraft:plains"),
            "minecraft:grass_block"
        );

        assertTrue(service.isBiomeOptionMatching(level, 0, 64, 0, 64, option));
    }

    @Test
    public void returnsFalseWhileChunkFutureIsPending() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-biome-search-test");
        ConfigManager configManager = new ConfigManager(tempDir);
        BiomeSearchService service = new BiomeSearchService(configManager);

        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld")));
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(level.getChunkSource()).thenReturn(chunkSource);
        when(chunkSource.getChunkNow(anyInt(), anyInt())).thenReturn(null);
        when(chunkSource.getChunkFuture(anyInt(), anyInt(), eq(ChunkStatus.BIOMES), eq(true)))
            .thenReturn(new CompletableFuture<>());

        BiomeOption option = new BiomeOption(
            "planicies", "Planicies", List.of("plains"),
            List.of("minecraft:plains"), "minecraft:grass_block"
        );

        assertFalse(service.isBiomeOptionMatching(level, 128, 192, 128, 192, option));
    }

    @Test
    public void reportsPendingWhenChunkFutureIsPending() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-biome-search-test");
        ConfigManager configManager = new ConfigManager(tempDir);
        BiomeSearchService service = new BiomeSearchService(configManager);

        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld")));
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(level.getChunkSource()).thenReturn(chunkSource);
        when(chunkSource.getChunkNow(anyInt(), anyInt())).thenReturn(null);
        when(chunkSource.getChunkFuture(anyInt(), anyInt(), eq(ChunkStatus.BIOMES), eq(true)))
            .thenReturn(new CompletableFuture<>());

        BiomeOption option = new BiomeOption(
            "planicies", "Planicies", List.of("plains"),
            List.of("minecraft:plains"), "minecraft:grass_block"
        );

        assertEquals(
            BiomeSearchService.MatchResult.PENDING,
            service.evaluateBiomeOptionMatching(level, 128, 192, 128, 192, option)
        );
    }

    @Test
    public void usesChunkFutureWhenChunkIsNotLoaded() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-biome-search-test");
        ConfigManager configManager = new ConfigManager(tempDir);
        BiomeSearchService service = new BiomeSearchService(configManager);

        ServerLevel level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld")));
        Holder<Biome> plains = biomeHolder("minecraft:plains");
        LevelChunk chunk = mock(LevelChunk.class);
        when(chunk.getNoiseBiome(anyInt(), anyInt(), anyInt())).thenReturn(plains);

        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(level.getChunkSource()).thenReturn(chunkSource);
        when(chunkSource.getChunkNow(anyInt(), anyInt())).thenReturn(null);
        when(chunkSource.getChunkFuture(anyInt(), anyInt(), eq(ChunkStatus.BIOMES), eq(true)))
            .thenReturn(CompletableFuture.completedFuture(ChunkResult.of(chunk)));

        BiomeOption option = new BiomeOption(
            "planicies",
            "Planicies",
            List.of("plains"),
            List.of("minecraft:plains"),
            "minecraft:grass_block"
        );

        assertTrue(service.isBiomeOptionMatching(level, 256, 320, 256, 320, option));
    }

    private Holder<Biome> biomeHolder(String biomeId) {
        @SuppressWarnings("unchecked")
        Holder<Biome> holder = mock(Holder.class);
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId));
        when(holder.unwrapKey()).thenReturn(Optional.of(key));
        return holder;
    }
}
