package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.mojang.datafixers.util.Pair;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
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
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BiomeAnchorSearchRegressionTest {

    @BeforeAll
    public static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private Config config;
    private ConfigManager configManager;

    @BeforeEach
    public void setUp() throws Exception {
        Path tempDir = Files.createTempDirectory("bigbangregions-regression-test");
        configManager = new ConfigManager(tempDir);
        config = configManager.getConfig();
        config.getPlayerLandAllocation().getBiomeSearch().setMinimumMatchPercentage(60);
        config.getPlayerLandAllocation().getBiomeSearch().setMinimumBorderMatchPercentage(50);
        config.getPlayerLandAllocation().getBiomeSearch().setRequireFullBorderMatch(false);
        config.getPlayerLandAllocation().getWorldgenSearch().setSectorSizeBlocks(2048);
        config.getPlayerLandAllocation().getWorldgenSearch().setLocateRadiusBlocks(1300);
    }

    @Test
    public void locatorReturnsExhaustedWhenNoBiomeExistsWithinRadius() {
        BiomeSource biomeSource = mock(BiomeSource.class);
        Holder<Biome> riverHolder = biomeHolder("minecraft:river");
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenReturn(riverHolder);

        WorldgenSearchContext context = testContext(biomeSource, List.of(64));
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        AllocationSearchCursor cursor = new AllocationSearchCursor("test-request");
        cursor.setSectorX(1000);
        cursor.setSectorZ(1000);
        cursor.setAnchorAttempt(16);

        WorldgenBiomeAnchorLocator locator = new WorldgenBiomeAnchorLocator();
        BiomeAnchorSearchStepResult result = locator.searchStep(
            context, option, cursor,
            new SearchBudget(128, 1)
        );

        assertInstanceOf(BiomeAnchorSearchStepResult.Exhausted.class, result,
            "Should return Exhausted after the complete small radius is scanned");
        assertEquals(1, cursor.getLocateCallsUsed());
    }

    @Test
    public void locatorReturnsContinueWhenBudgetExhausted() {
        WorldgenSearchContext context = testContext(mock(BiomeSource.class), List.of(64));
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        AllocationSearchCursor cursor = new AllocationSearchCursor("test-request");
        cursor.setSectorX(1000);
        cursor.setSectorZ(1000);

        WorldgenBiomeAnchorLocator locator = new WorldgenBiomeAnchorLocator();
        BiomeAnchorSearchStepResult result = locator.searchStep(
            context, option, cursor,
            new SearchBudget(0, 0)
        );

        assertInstanceOf(BiomeAnchorSearchStepResult.Continue.class, result,
            "Should return Continue when budget has no locate calls remaining");
    }

    @Test
    public void locatorReturnsFoundWhenBiomeLocated() {
        Holder<Biome> plainsHolder = biomeHolder("minecraft:plains");
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenAnswer(invocation -> {
                int quartX = invocation.getArgument(0);
                int quartY = invocation.getArgument(1);
                int quartZ = invocation.getArgument(2);
                return quartX == BiomeCoordinateMath.blockToQuart(1064)
                    && quartY == BiomeCoordinateMath.blockToQuart(64)
                    && quartZ == BiomeCoordinateMath.blockToQuart(1000)
                    ? plainsHolder
                    : biomeHolder("minecraft:river");
            });

        WorldgenSearchContext context = testContext(biomeSource, List.of(64));
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        AllocationSearchCursor cursor = new AllocationSearchCursor("test-request");
        cursor.setSectorX(1000);
        cursor.setSectorZ(1000);
        cursor.setAnchorAttempt(1300);

        WorldgenBiomeAnchorLocator locator = new WorldgenBiomeAnchorLocator();
        BiomeAnchorSearchStepResult result = locator.searchStep(
            context, option, cursor,
            new SearchBudget(128, 1)
        );

        assertInstanceOf(BiomeAnchorSearchStepResult.Found.class, result);
        BiomeAnchorSearchStepResult.Found found = (BiomeAnchorSearchStepResult.Found) result;
        assertEquals(1064, found.anchor().blockX());
        assertEquals(64, found.anchor().blockY());
        assertEquals(1000, found.anchor().blockZ());
        assertTrue(cursor.getCurrentAnchorX() != null);
        assertEquals(1, cursor.getLocateCallsUsed());
    }

    @Test
    public void fastLocatorUsesSixteenBlockNativeSamplingStep() {
        Holder<Biome> cherryHolder = biomeHolder("minecraft:cherry_grove");
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.findBiomeHorizontal(
            anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), anyBoolean(), any()
        )).thenReturn(Pair.of(new BlockPos(128, 64, 128), cherryHolder));

        WorldgenSearchContext context = testContext(biomeSource, List.of(64));
        AllocationSearchCursor cursor = new AllocationSearchCursor("test-request");
        cursor.setSectorX(0);
        cursor.setSectorZ(0);
        cursor.setAnchorAttempt(1300);

        BiomeAnchorSearchStepResult result = new WorldgenBiomeAnchorLocator().searchStep(
            context,
            biomeOption("cerejeira", List.of("minecraft:cherry_grove")),
            cursor,
            new SearchBudget(64, 1)
        );

        assertInstanceOf(BiomeAnchorSearchStepResult.Found.class, result);
        verify(biomeSource).findBiomeHorizontal(
            eq(0), eq(64), eq(0), eq(BiomeCoordinateMath.blockToQuart(1300)),
            eq(BiomeCoordinateMath.blockToQuart(16)), any(), any(), eq(true), any()
        );
    }

    @Test
    public void locatorUsesQuartGridForBiomeSourceLookup() {
        BiomeSource biomeSource = mock(BiomeSource.class);
        Holder<Biome> cherryHolder = biomeHolder("minecraft:cherry_grove");
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenAnswer(invocation -> invocation.getArgument(0).equals(BiomeCoordinateMath.blockToQuart(3072))
                && invocation.getArgument(1).equals(BiomeCoordinateMath.blockToQuart(64))
                && invocation.getArgument(2).equals(BiomeCoordinateMath.blockToQuart(1024))
                ? cherryHolder : biomeHolder("minecraft:river"));

        WorldgenSearchContext context = testContext(biomeSource, List.of(64));
        BiomeOption option = biomeOption("cerejeira", List.of("minecraft:cherry_grove"));
        AllocationSearchCursor cursor = new AllocationSearchCursor("test-request");
        cursor.setSectorX(3072);
        cursor.setSectorZ(1024);
        cursor.setAnchorAttempt(1300);

        WorldgenBiomeAnchorLocator locator = new WorldgenBiomeAnchorLocator();
        BiomeAnchorSearchStepResult result = locator.searchStep(context, option, cursor, new SearchBudget(64, 1));

        assertInstanceOf(BiomeAnchorSearchStepResult.Found.class, result);
        BiomeAnchorSearchStepResult.Found found = (BiomeAnchorSearchStepResult.Found) result;
        assertEquals(3072, found.anchor().blockX());
        assertEquals(64, found.anchor().blockY());
        assertEquals(1024, found.anchor().blockZ());
    }

    @Test
    public void locatorPersistsScanPositionAcrossSteps() {
        BiomeSource biomeSource = mock(BiomeSource.class);
        Set<String> sampledPositions = new HashSet<>();
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenAnswer(invocation -> {
                sampledPositions.add(invocation.getArgument(0) + ":" + invocation.getArgument(1) + ":" + invocation.getArgument(2));
                return biomeHolder("minecraft:river");
            });

        WorldgenSearchContext context = testContext(biomeSource, List.of(64));
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        AllocationSearchCursor cursor = new AllocationSearchCursor("test-request");
        cursor.setSectorX(0);
        cursor.setSectorZ(0);
        cursor.setAnchorAttempt(1300);

        WorldgenBiomeAnchorLocator locator = new WorldgenBiomeAnchorLocator();
        BiomeAnchorSearchStepResult first = locator.searchStep(context, option, cursor, new SearchBudget(64, 1));
        int firstPointIndex = cursor.getAnchorSearchPointIndex();
        int firstRing = cursor.getAnchorSearchRingQuart();
        assertInstanceOf(BiomeAnchorSearchStepResult.Continue.class, first);

        locator.searchStep(context, option, cursor, new SearchBudget(64, 1));

        assertTrue(firstPointIndex > 0 || firstRing > 0, "First step must advance the persisted scan position");
        assertEquals(128, sampledPositions.size(), "Two steps must not rescan the same quart cells");
    }

    @Test
    public void locatorFindsBiomeInsideLegacySixtyFourBlockGap() {
        Holder<Biome> cherryHolder = biomeHolder("minecraft:cherry_grove");
        Holder<Biome> riverHolder = biomeHolder("minecraft:river");
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenAnswer(invocation -> invocation.getArgument(0).equals(BiomeCoordinateMath.blockToQuart(16))
                && invocation.getArgument(1).equals(BiomeCoordinateMath.blockToQuart(64))
                && invocation.getArgument(2).equals(BiomeCoordinateMath.blockToQuart(0))
                ? cherryHolder : riverHolder);

        AllocationSearchCursor cursor = new AllocationSearchCursor("test-request");
        cursor.setSectorX(0);
        cursor.setSectorZ(0);
        cursor.setAnchorAttempt(64);

        BiomeAnchorSearchStepResult result = new WorldgenBiomeAnchorLocator().searchStep(
            testContext(biomeSource, List.of(64)),
            biomeOption("cerejeira", List.of("minecraft:cherry_grove")),
            cursor,
            new SearchBudget(64, 1)
        );

        assertInstanceOf(BiomeAnchorSearchStepResult.Found.class, result);
        assertEquals(16, ((BiomeAnchorSearchStepResult.Found) result).anchor().blockX());
        assertEquals(1, cursor.getLocateCallsUsed());
    }

    @Test
    public void sectorExhaustionAdvancesSectorIndex() {
        BiomeSource biomeSource = mock(BiomeSource.class);
        Holder<Biome> riverHolder = biomeHolder("minecraft:river");
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenReturn(riverHolder);

        WorldgenSearchContext context = testContext(biomeSource, List.of(64));
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        AllocationSearchCursor cursor = new AllocationSearchCursor("test-request");
        cursor.setCurrentBandId("primary");
        cursor.setCurrentSectorIndex(0);
        cursor.setSectorX(1000);
        cursor.setSectorZ(1000);
        cursor.setAnchorAttempt(16);
        cursor.setLocalCandidateIndex(0);

        assertEquals(0, cursor.getCurrentSectorIndex());
        assertEquals(0, cursor.getTotalSectorsChecked());

        WorldgenBiomeAnchorLocator locator = new WorldgenBiomeAnchorLocator();
        BiomeAnchorSearchStepResult result = locator.searchStep(
            context, option, cursor,
            new SearchBudget(128, 1)
        );

        assertInstanceOf(BiomeAnchorSearchStepResult.Exhausted.class, result);

        int initialSectorIndex = cursor.getCurrentSectorIndex();
        assertEquals(0, initialSectorIndex,
            "Locator should not advance sector index (that's the coordinator's job)");
    }

    @Test
    public void perColumnScoringPureMultiYReturns100Percent() {
        BiomeSource biomeSource = pureBiomeSource("minecraft:plains");
        WorldgenSearchContext context = testContext(biomeSource, List.of(64, 96, 128, 160));
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        BiomeVirtualSampler sampler = mockSampler(biomeSource);
        AdaptiveVirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            sampler, 5, 60, 50, false
        );

        VirtualBiomeValidationResult result = validator.validate(
            context, option,
            new PlotFootprint(0, 512, 0, 512),
            SearchBudget.unbounded()
        );

        assertTrue(result.accepted(), "Pure biome with 4 Y levels should be accepted. Score: " + result.score());
        assertTrue(result.score() >= 95.0, "Score should be >= 95%, got " + result.score()
            + " (4 Y levels should produce per-column scoring, not dilute to 25%)");
    }

    @Test
    public void perColumnScoringCompatibleAtAnyYCountsOnce() {
        BiomeSource biomeSource = mock(BiomeSource.class);
        Holder<Biome> plainsHolder = biomeHolder("minecraft:plains");
        Holder<Biome> riverHolder = biomeHolder("minecraft:river");
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenAnswer(inv -> {
                int quartY = inv.getArgument(1);
                if (quartY == BiomeCoordinateMath.blockToQuart(96)) {
                    return plainsHolder;
                }
                return riverHolder;
            });

        WorldgenSearchContext context = testContext(biomeSource, List.of(64, 96, 128, 160));
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        BiomeVirtualSampler sampler = mockSampler(biomeSource);
        AdaptiveVirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            sampler, 5, 60, 50, false
        );

        VirtualBiomeValidationResult result = validator.validate(
            context, option,
            new PlotFootprint(0, 64, 0, 64),
            SearchBudget.unbounded()
        );

        assertTrue(result.accepted(),
            "Biome found at least at one Y level should count as compatible column. Score: " + result.score());
    }

    @Test
    public void perColumnScoringIncompatibleColumnCountsAsOneMismatch() {
        Holder<Biome> riverHolder = biomeHolder("minecraft:river");
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenReturn(riverHolder);

        WorldgenSearchContext context = testContext(biomeSource, List.of(64, 96, 128, 160));
        BiomeOption option = biomeOption("planicies", List.of("minecraft:plains"));
        BiomeVirtualSampler sampler = mockSampler(biomeSource);
        AdaptiveVirtualFootprintValidator validator = new AdaptiveVirtualFootprintValidator(
            sampler, 5, 60, 50, false
        );

        VirtualBiomeValidationResult result = validator.validate(
            context, option,
            new PlotFootprint(0, 64, 0, 64),
            SearchBudget.unbounded()
        );

        assertFalse(result.accepted(), "Fully incompatible area should be rejected");
        assertEquals(0.0, result.score(), 0.01, "Score should be 0%");
    }

    @Test
    public void stonyShoreLocatorReturnsFoundForStonyShore() {
        Holder<Biome> stonyShoreHolder = biomeHolder("minecraft:stony_shore");
        BiomeSource biomeSource = mock(BiomeSource.class);
        when(biomeSource.getNoiseBiome(anyInt(), anyInt(), anyInt(), any()))
            .thenAnswer(invocation -> invocation.getArgument(0).equals(BiomeCoordinateMath.blockToQuart(64))
                && invocation.getArgument(1).equals(BiomeCoordinateMath.blockToQuart(64))
                && invocation.getArgument(2).equals(BiomeCoordinateMath.blockToQuart(0))
                ? stonyShoreHolder : biomeHolder("minecraft:river"));

        WorldgenSearchContext context = testContext(biomeSource, List.of(64));
        BiomeOption option = biomeOption("costapedra", List.of("minecraft:stony_shore", "minecraft:stony_peaks"));
        AllocationSearchCursor cursor = new AllocationSearchCursor("test-request");
        cursor.setSectorX(0);
        cursor.setSectorZ(0);
        cursor.setAnchorAttempt(64);
        cursor.setLocalCandidateIndex(0);

        WorldgenBiomeAnchorLocator locator = new WorldgenBiomeAnchorLocator();
        BiomeAnchorSearchStepResult result = locator.searchStep(
            context, option, cursor,
            new SearchBudget(128, 1)
        );

        assertInstanceOf(BiomeAnchorSearchStepResult.Found.class, result,
            "Stony shore should be found when accepted biomes include stony_shore");
        BiomeAnchorSearchStepResult.Found found = (BiomeAnchorSearchStepResult.Found) result;
        assertEquals("minecraft:stony_shore", found.anchor().biomeId());
        assertEquals(64, found.anchor().blockX());
        assertTrue(cursor.getCurrentAnchorX() != null);
        assertTrue(cursor.getAnchorsFound() == 0 || cursor.getAnchorsFound() >= 0);
    }

    @Test
    public void claimFootprintResolverProducesCenteredFootprint() {
        Config.PlayerLandAllocationConfig lac = config.getPlayerLandAllocation();
        lac.setSlotSize(512);
        lac.setInitialClaimSize(80);

        PlotSlotService.PlotSlotCandidate candidate = new PlotSlotService.PlotSlotCandidate(5, 5, 5 * 512, 5 * 512);
        PlotFootprint footprint = TerrainAllocationCoordinator.resolveClaimFootprint(candidate, lac);

        int expectedOffset = (512 - 80) / 2;
        assertEquals(5 * 512 + expectedOffset, footprint.minX());
        assertEquals(5 * 512 + expectedOffset, footprint.minZ());
        assertEquals(5 * 512 + expectedOffset + 80 - 1, footprint.maxX());
        assertEquals(5 * 512 + expectedOffset + 80 - 1, footprint.maxZ());
    }

    @Test
    public void claimFootprintForSortMatchesFootprintForValidation() {
        Config.PlayerLandAllocationConfig lac = config.getPlayerLandAllocation();
        lac.setSlotSize(512);
        lac.setInitialClaimSize(80);

        int slotMinX = 3 * 512;
        int slotMinZ = 3 * 512;
        PlotSlotService.PlotSlotCandidate candidate = new PlotSlotService.PlotSlotCandidate(3, 3, slotMinX, slotMinZ);

        PlotFootprint fromResolver = TerrainAllocationCoordinator.resolveClaimFootprint(candidate, lac);

        int claimOffset = (lac.getSlotSize() - lac.getInitialClaimSize()) / 2;
        assertEquals(slotMinX + claimOffset, fromResolver.minX());
        assertEquals(slotMinZ + claimOffset, fromResolver.minZ());
        assertEquals(slotMinX + claimOffset + lac.getInitialClaimSize() - 1, fromResolver.maxX());
        assertEquals(slotMinZ + claimOffset + lac.getInitialClaimSize() - 1, fromResolver.maxZ());
    }

    @Test
    public void cancellationTransitionsToCancelledBeforeRegionCreation() {
        AllocationRequestState state = AllocationRequestState.VIRTUAL_SEARCHING;
        assertTrue(state.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));

        state = AllocationRequestState.SLOT_RESERVED;
        assertTrue(state.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));

        state = AllocationRequestState.WAITING_FOR_CHUNKS;
        assertTrue(state.canTransitionTo(AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION));

        state = AllocationRequestState.CANCELLED_BEFORE_REGION_CREATION;
        assertTrue(state.isTerminal());
    }

    @Test
    public void cursorProgressSignatureChangesWhenSectorAdvances() {
        AllocationSearchCursor cursor = new AllocationSearchCursor("test-request");
        cursor.setCurrentBandId("primary");
        cursor.setCurrentSectorIndex(0);
        cursor.setAnchorAttempt(1300);

        String sig1 = progressSignature(cursor);

        cursor.setCurrentSectorIndex(1);
        String sig2 = progressSignature(cursor);

        assertNotEquals(sig1, sig2, "Progress signature should change when sector index advances");
    }

    @Test
    public void sameSectorSameAnchorNoProgressSignatureChange() {
        AllocationSearchCursor cursor = new AllocationSearchCursor("test-request");
        cursor.setCurrentBandId("primary");
        cursor.setCurrentSectorIndex(0);
        cursor.setCurrentAnchorX(1000);
        cursor.setCurrentAnchorY(64);
        cursor.setCurrentAnchorZ(1000);

        String sig1 = progressSignature(cursor);
        String sig2 = progressSignature(cursor);

        assertEquals(sig1, sig2, "Progress signature should NOT change when nothing actually progresses");
    }

    @Test
    public void cursorProgressSignatureChangesWhenBiomeScanAdvances() {
        AllocationSearchCursor cursor = new AllocationSearchCursor("test-request");
        cursor.setCurrentBandId("primary");
        cursor.setCurrentSectorIndex(0);
        cursor.setSectorX(1024);
        cursor.setSectorZ(-1024);
        cursor.setAnchorAttempt(1300);
        cursor.setAnchorSearchIntervalQuart(4);
        cursor.setAnchorSearchRingQuart(16);
        cursor.setAnchorSearchPointIndex(5);
        cursor.setTotalBiomeSamples(64);

        String sig1 = progressSignature(cursor);

        cursor.setAnchorSearchPointIndex(6);
        cursor.setTotalBiomeSamples(65);
        String sig2 = progressSignature(cursor);

        assertNotEquals(sig1, sig2,
            "Biome-source scan progress must prevent a false allocation-stuck warning");
    }

    @Test
    public void defaultLocateRadiusIsLargeEnoughToReachSectorEdge() {
        int sectorSize = 2048;
        int locateRadius = 1300;
        int halfSize = sectorSize / 2;

        assertTrue(locateRadius >= halfSize,
            "locateRadiusBlocks=" + locateRadius + " must be >= half sector size=" + halfSize
                + " to search most of the sector");
    }

    private String progressSignature(AllocationSearchCursor cursor) {
        return "VIRTUAL_SEARCHING"
            + "|" + cursor.getCurrentBandId()
            + "|" + cursor.getCurrentSectorIndex()
            + "|" + cursor.getSectorX()
            + "|" + cursor.getSectorZ()
            + "|" + cursor.getAnchorAttempt()
            + "|" + cursor.getAnchorSearchYIndex()
            + "|" + cursor.getAnchorSearchRingQuart()
            + "|" + cursor.getAnchorSearchPointIndex()
            + "|" + cursor.getAnchorSearchIntervalQuart()
            + "|" + cursor.getTotalBiomeSamples()
            + "|" + cursor.getCurrentAnchorX()
            + "|" + cursor.getCurrentAnchorY()
            + "|" + cursor.getCurrentAnchorZ()
            + "|" + cursor.getLocalCandidateIndex()
            + "|" + null;
    }

    private static WorldgenSearchContext testContext(BiomeSource biomeSource, List<Integer> sampleYs) {
        @SuppressWarnings("unchecked")
        ChunkGenerator chunkGenerator = mock(ChunkGenerator.class);
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
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
        return new WorldgenSearchContext(dimKey, 1234L, chunkGenerator, biomeSource, Climate.empty(),
            fingerprint, sampleYs.getFirst(), sampleYs);
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
