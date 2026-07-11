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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BiomeAllocationPipelineTest {

    @BeforeAll
    public static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private Config config;
    private ConfigManager configManager;

    @BeforeEach
    public void setUp() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-pipeline-test");
        configManager = new ConfigManager(tempDir);
        config = configManager.getConfig();
        config.getPlayerLandAllocation().getBiomeSearch().setMinimumMatchPercentage(60);
        config.getPlayerLandAllocation().getBiomeSearch().setMinimumBorderMatchPercentage(50);
        config.getPlayerLandAllocation().getBiomeSearch().setRequireFullBorderMatch(false);
    }

    @Test
    public void pureBiomeFootprintAccepted() {
        BiomeSource biomeSource = pureBiomeSource("minecraft:plains");
        WorldgenSearchContext context = testContext(biomeSource);
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        AdaptiveVirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            mockSampler(biomeSource), 5, 60, 50, false
        );

        VirtualBiomeValidationResult result = validator.validate(
            context, option,
            new PlotFootprint(0, 512, 0, 512),
            SearchBudget.unbounded()
        );

        assertTrue(result.accepted(), "Pure plains footprint should be accepted. Score: " + result.score());
        assertTrue(result.score() >= 60.0, "Score should be >= 60%, got " + result.score());
    }

    @Test
    public void mixedFootprintAbove60PercentAccepted() {
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenAnswer(inv -> {
                int quartX = inv.getArgument(0);
                int quartZ = inv.getArgument(2);
                int step = 4;
                int idxX = quartX / step;
                int idxZ = quartZ / step;
                boolean onBorder = quartX == 0 || quartZ == 0 || quartX >= 16 || quartZ >= 16;
                if (onBorder && (idxX + idxZ) % 3 == 0) {
                    return biomeHolder("minecraft:river");
                }
                if (!onBorder && idxX >= 3 && idxZ >= 3) {
                    return biomeHolder("minecraft:river");
                }
                return biomeHolder("minecraft:plains");
            });

        WorldgenSearchContext context = testContext(biomeSource);
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        AdaptiveVirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            mockSampler(biomeSource), 5, 60, 50, false
        );

        VirtualBiomeValidationResult result = validator.validate(
            context, option,
            new PlotFootprint(0, 64, 0, 64),
            SearchBudget.unbounded()
        );

        assertTrue(result.accepted(),
            "Mixed footprint with >60% plains should be accepted. Score: " + result.score());
    }

    @Test
    public void centerMismatchRejectedEvenIfOverallScorePasses() {
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenAnswer(inv -> {
                int quartX = inv.getArgument(0);
                int quartZ = inv.getArgument(2);
                int centerQuart = (0 + 64) / 2 / 4;
                if (Math.abs(quartX - centerQuart) <= 1 && Math.abs(quartZ - centerQuart) <= 1) {
                    return biomeHolder("minecraft:river");
                }
                return biomeHolder("minecraft:plains");
            });

        WorldgenSearchContext context = testContext(biomeSource);
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        AdaptiveVirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            mockSampler(biomeSource), 5, 60, 50, false
        );

        VirtualBiomeValidationResult result = validator.validate(
            context, option,
            new PlotFootprint(0, 64, 0, 64),
            SearchBudget.unbounded()
        );

        assertFalse(result.accepted(), "Center biome mismatch should reject footprint");
        assertEquals(ValidationFailureReason.BORDER_MISMATCH, result.failureReason());
    }

    @Test
    public void partialBorderAboveThresholdAccepted() {
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenAnswer(inv -> {
                int quartX = inv.getArgument(0);
                int quartZ = inv.getArgument(2);
                if ((quartX == 0 || quartZ == 0 || quartX >= 60 || quartZ >= 60)
                    && (quartX + quartZ) % 3 == 0) {
                    return biomeHolder("minecraft:river");
                }
                return biomeHolder("minecraft:plains");
            });

        WorldgenSearchContext context = testContext(biomeSource);
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        AdaptiveVirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            mockSampler(biomeSource), 5, 60, 50, false
        );

        VirtualBiomeValidationResult result = validator.validate(
            context, option,
            new PlotFootprint(0, 64, 0, 64),
            SearchBudget.unbounded()
        );

        assertTrue(result.accepted(),
            "Partial border with >50% match should be accepted. Score: " + result.score()
            + " edge=" + result.edgeMatches() + "/" + (result.edgeMatches() + result.edgeMismatches()));
    }

    @Test
    public void fullBorderMatchRequiredWhenConfigured() {
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenAnswer(inv -> {
                int quartX = inv.getArgument(0);
                int quartZ = inv.getArgument(2);
                if (quartX == 0 || quartZ == 0 || quartX >= 60 || quartZ >= 60) {
                    return biomeHolder("minecraft:river");
                }
                return biomeHolder("minecraft:plains");
            });

        WorldgenSearchContext context = testContext(biomeSource);
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        AdaptiveVirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            mockSampler(biomeSource), 5, 60, 50, true
        );

        VirtualBiomeValidationResult result = validator.validate(
            context, option,
            new PlotFootprint(0, 64, 0, 64),
            SearchBudget.unbounded()
        );

        assertFalse(result.accepted(),
            "requireFullBorderMatch=true should reject footprint with border mismatches");
        assertEquals(ValidationFailureReason.BORDER_MISMATCH, result.failureReason());
    }

    @Test
    public void budgetExhaustedReturnsFailure() {
        Holder<Biome> plainsHolder = biomeHolder("minecraft:plains");
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenReturn(plainsHolder);

        WorldgenSearchContext context = testContext(biomeSource);
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        AdaptiveVirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            mockSampler(biomeSource), 5, 60, 50, false
        );

        VirtualBiomeValidationResult result = validator.validate(
            context, option,
            new PlotFootprint(0, 1024, 0, 1024),
            new SearchBudget(2, 2)
        );

        assertEquals(ValidationFailureReason.BUDGET_EXHAUSTED, result.failureReason());
    }

    @Test
    public void noAcceptedBiomeIdsRejects() {
        BiomeSource biomeSource = pureBiomeSource("minecraft:plains");
        WorldgenSearchContext context = testContext(biomeSource);
        BiomeOption option = biomeOption("vazio", List.of());
        AdaptiveVirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            mockSampler(biomeSource), 5, 60, 50, false
        );

        VirtualBiomeValidationResult result = validator.validate(
            context, option,
            new PlotFootprint(0, 64, 0, 64),
            SearchBudget.unbounded()
        );

        assertFalse(result.accepted());
        assertEquals(ValidationFailureReason.EMPTY_ACCEPTED_BIOMES, result.failureReason());
    }

    @Test
    public void cherryGroveAcceptedBiomeIsCherryGrove() {
        BiomeSource biomeSource = pureBiomeSource("minecraft:cherry_grove");
        WorldgenSearchContext context = testContext(biomeSource);
        BiomeOption option = biomeOption("cerejeira", List.of("minecraft:cherry_grove"));
        AdaptiveVirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            mockSampler(biomeSource), 5, 60, 50, false
        );

        VirtualBiomeValidationResult result = validator.validate(
            context, option,
            new PlotFootprint(0, 64, 0, 64),
            SearchBudget.unbounded()
        );

        assertTrue(result.accepted(),
            "Cherry grove footprint should be accepted. Score: " + result.score());
    }

    @Test
    public void biomeOptionHasCorrectDefaultIds() {
        BiomeOptionRegistry registry = new BiomeOptionRegistry(configManager);
        registry.load();

        Optional<BiomeOption> cerejeira = registry.lookup("cerejeira");
        assertTrue(cerejeira.isPresent());
        assertTrue(cerejeira.get().getAcceptedBiomeIds().contains("minecraft:cherry_grove"),
            "Cerejeira must accept minecraft:cherry_grove");
        assertFalse(cerejeira.get().getAcceptedBiomeIds().contains("minecraft:meadow"),
            "Cerejeira must NOT include minecraft:meadow as if it were cherry");

        Optional<BiomeOption> planicies = registry.lookup("planicies");
        assertTrue(planicies.isPresent());
        assertTrue(planicies.get().getAcceptedBiomeIds().contains("minecraft:plains"));

        Optional<BiomeOption> floresta = registry.lookup("floresta");
        assertTrue(floresta.isPresent());
        assertTrue(floresta.get().getAcceptedBiomeIds().contains("minecraft:forest"));

        Optional<BiomeOption> deserto = registry.lookup("deserto");
        assertTrue(deserto.isPresent());
        assertTrue(deserto.get().getAcceptedBiomeIds().contains("minecraft:desert"));

        Optional<BiomeOption> taiga = registry.lookup("taiga");
        assertTrue(taiga.isPresent());
        assertTrue(taiga.get().getAcceptedBiomeIds().contains("minecraft:taiga"));

        Optional<BiomeOption> oceano = registry.lookup("oceano");
        assertFalse(oceano.isPresent(), "Ocean should be blocked by policy");
    }

    @Test
    public void biomeOptionInvalidIdsFiltered() {
        config.getBiomeOptions().put("test_invalid", new Config.BiomeOptionConfig(
            "Test Invalid",
            List.of("test"),
            List.of("invalid::id", "minecraft:plains"),
            "minecraft:map"
        ));
        BiomeOptionRegistry registry = new BiomeOptionRegistry(configManager);
        registry.load();

        Optional<BiomeOption> opt = registry.lookup("test_invalid");
        assertTrue(opt.isPresent());
        assertEquals(1, opt.get().getAcceptedBiomeIds().size());
        assertEquals("minecraft:plains", opt.get().getAcceptedBiomeIds().get(0));
    }

    @Test
    public void biomeOptionAllInvalidIdsDisabled() {
        config.getBiomeOptions().put("test_all_invalid", new Config.BiomeOptionConfig(
            "Test All Invalid",
            List.of("test"),
            List.of("invalid::a", "bad::b"),
            "minecraft:map"
        ));
        BiomeOptionRegistry registry = new BiomeOptionRegistry(configManager);
        registry.load();

        Optional<BiomeOption> opt = registry.lookup("test_all_invalid");
        assertFalse(opt.isPresent(), "Option with no valid biome IDs should be disabled");
    }

    @Test
    public void candidateSortedByDistanceToAnchor() {
        int slotSize = 512;
        int anchorX = 600;
        int anchorZ = 100;
        BiomeAnchor anchor = new BiomeAnchor(anchorX, 64, anchorZ, "minecraft:plains");

        Config.PlayerLandAllocationConfig lac = config.getPlayerLandAllocation();
        lac.setSlotSize(slotSize);
        lac.setInitialClaimSize(100);
        lac.setSlotInternalMargin(8);
        lac.getWorldgenSearch().setMaxCandidateSlotsPerAnchor(25);

        var candidates = localCandidatesForAnchor(anchor, lac);
        assertFalse(candidates.isEmpty());

        double prevDist = -1;
        for (var candidate : candidates) {
            int claimMinX = candidate.minX + lac.getSlotInternalMargin();
            int claimMinZ = candidate.minZ + lac.getSlotInternalMargin();
            int claimSize = Math.min(slotSize, lac.getInitialClaimSize());
            int claimCenterX = (claimMinX + claimMinX + claimSize - 1) / 2;
            int claimCenterZ = (claimMinZ + claimMinZ + claimSize - 1) / 2;
            double dist = Math.sqrt(Math.pow(anchorX - claimCenterX, 2) + Math.pow(anchorZ - claimCenterZ, 2));
            assertTrue(dist >= prevDist - 0.01,
                "Candidates must be sorted by distance ascending. Prev=" + prevDist + " Current=" + dist
                + " grid=(" + candidate.gridX + "," + candidate.gridZ + ")");
            prevDist = dist;
        }
    }

    private List<PlotSlotService.PlotSlotCandidate> localCandidatesForAnchor(BiomeAnchor anchor, Config.PlayerLandAllocationConfig lac) {
        int slotSize = lac.getSlotSize();
        int anchorGridX = Math.floorDiv(anchor.blockX(), slotSize);
        int anchorGridZ = Math.floorDiv(anchor.blockZ(), slotSize);
        int[][] deltas = new int[][]{
            {0, 0}, {0, -1}, {0, 1}, {1, 0}, {-1, 0},
            {1, -1}, {1, 1}, {-1, -1}, {-1, 1},
            {0, -2}, {0, 2}, {2, 0}, {-2, 0},
            {2, -1}, {2, 1}, {-2, -1}, {-2, 1},
            {1, -2}, {1, 2}, {-1, -2}, {-1, 2},
            {2, -2}, {2, 2}, {-2, -2}, {-2, 2}
        };
        java.util.List<double[]> withDist = new java.util.ArrayList<>();
        for (int[] delta : deltas) {
            int gridX = anchorGridX + delta[0];
            int gridZ = anchorGridZ + delta[1];
            int minX = gridX * slotSize;
            int minZ = gridZ * slotSize;
            int claimSize = Math.min(slotSize, lac.getInitialClaimSize());
            int claimMinX = minX + lac.getSlotInternalMargin();
            int claimMinZ = minZ + lac.getSlotInternalMargin();
            int claimCenterX = (claimMinX + claimMinX + claimSize - 1) / 2;
            int claimCenterZ = (claimMinZ + claimMinZ + claimSize - 1) / 2;
            double dist = Math.sqrt(Math.pow(anchor.blockX() - claimCenterX, 2) + Math.pow(anchor.blockZ() - claimCenterZ, 2));
            withDist.add(new double[]{gridX, gridZ, dist});
        }
        withDist.sort(java.util.Comparator.comparingDouble(a -> a[2]));
        java.util.List<PlotSlotService.PlotSlotCandidate> result = new java.util.ArrayList<>();
        for (double[] entry : withDist) {
            int gridX = (int) entry[0];
            int gridZ = (int) entry[1];
            result.add(new PlotSlotService.PlotSlotCandidate(gridX, gridZ, gridX * slotSize, gridZ * slotSize));
        }
        return result;
    }

    @Test
    public void biomeSearchServiceReturnsPendingOnBudgetExhausted() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-pending-test");
        ConfigManager cfgMgr = new ConfigManager(tempDir);
        BiomeSearchService service = new BiomeSearchService(cfgMgr);

        Holder<Biome> plainsHolder = biomeHolder("minecraft:plains");
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenReturn(plainsHolder);

        WorldgenSearchContext context = testContext(biomeSource);
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));

        CachingBiomeVirtualSampler sampler = new CachingBiomeVirtualSampler(16, 60_000L);
        AdaptiveVirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            sampler, 5, 60, 50, false
        );

        VirtualBiomeValidationResult result = validator.validate(
            context, option,
            new PlotFootprint(0, 2048, 0, 2048),
            new SearchBudget(8, 8)
        );

        assertEquals(ValidationFailureReason.BUDGET_EXHAUSTED, result.failureReason(),
            "Very limited budget should cause BUDGET_EXHAUSTED for a large footprint");
        assertFalse(result.accepted());
    }

    private static WorldgenSearchContext testContext(BiomeSource biomeSource) {
        @SuppressWarnings("unchecked")
        ChunkGenerator chunkGenerator = mock(ChunkGenerator.class);
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        WorldgenFingerprint fingerprint = new WorldgenFingerprint(
            "test-hash",
            "minecraft:overworld",
            1234L,
            "generator",
            "biomeSource",
            "datapacks",
            "biomeReplacer",
            Config.BIOME_SEARCH_VALIDATION_SCHEMA_VERSION
        );
        return new WorldgenSearchContext(dimensionKey, 1234L, chunkGenerator, biomeSource, Climate.empty(),
            fingerprint, 64, java.util.List.of(64));
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

    private static BiomeSource pureBiomeSource(String biomeId) {
        Holder<Biome> holder = biomeHolder(biomeId);
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenReturn(holder);
        return biomeSource;
    }

    private static BiomeVirtualSampler mockSampler(BiomeSource biomeSource) {
        return new BiomeVirtualSampler() {
            @Override
            public ResourceKey<Biome> sampleAtBlock(WorldgenSearchContext context, int blockX, int blockY, int blockZ) {
                int quartX = BiomeCoordinateMath.blockToQuart(blockX);
                int quartY = BiomeCoordinateMath.blockToQuart(blockY);
                int quartZ = BiomeCoordinateMath.blockToQuart(blockZ);
                Holder<Biome> holder = biomeSource.getNoiseBiome(quartX, quartY, quartZ, context.noiseSampler());
                return holder.unwrapKey().orElse(null);
            }
        };
    }
}
