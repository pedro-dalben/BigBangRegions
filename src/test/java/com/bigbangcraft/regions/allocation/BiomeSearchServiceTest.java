package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BiomeSearchServiceTest {
    @BeforeAll
    public static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @SuppressWarnings("unchecked")
    private static ChunkAccess chunkWith(ServerLevel level, java.util.function.BiFunction<Integer, Integer, Holder<Biome>> resolve) {
        ChunkAccess chunk = mock(ChunkAccess.class);
        when(chunk.getNoiseBiome(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            int bx = inv.getArgument(0);
            int bz = inv.getArgument(2);
            return resolve.apply(bx, bz);
        });
        when(level.getChunk(anyInt(), anyInt(), eq(ChunkStatus.BIOMES), anyBoolean())).thenReturn(chunk);
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
    public void returnsFalseWhenChunkMissing() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-biome-search-test");
        ConfigManager configManager = new ConfigManager(tempDir);
        BiomeSearchService service = new BiomeSearchService(configManager);

        ServerLevel level = mock(ServerLevel.class);
        when(level.getChunk(anyInt(), anyInt(), ArgumentMatchers.<ChunkStatus>any(), anyBoolean())).thenReturn(null);

        BiomeOption option = new BiomeOption(
            "planicies", "Planicies", List.of("plains"),
            List.of("minecraft:plains"), "minecraft:grass_block"
        );

        assertFalse(service.isBiomeOptionMatching(level, 0, 64, 0, 64, option));
    }

    private Holder<Biome> biomeHolder(String biomeId) {
        @SuppressWarnings("unchecked")
        Holder<Biome> holder = mock(Holder.class);
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId));
        when(holder.unwrapKey()).thenReturn(Optional.of(key));
        return holder;
    }
}