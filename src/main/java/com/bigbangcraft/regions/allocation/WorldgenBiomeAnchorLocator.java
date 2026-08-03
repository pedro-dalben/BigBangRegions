package com.bigbangcraft.regions.allocation;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Set;

/**
 * Searches the biome source in small, resumable batches.
 *
 * <p>The vanilla {@code findBiomeHorizontal} helper is convenient, but it
 * performs the complete radius scan in one call. That makes the configured
 * per-tick deadline ineffective for sparse biomes. This locator uses the same
 * quart-cell resolution and persists its perimeter position in the allocation
 * cursor so a large search can be spread across scheduler ticks.</p>
 */
public class WorldgenBiomeAnchorLocator implements BiomeAnchorLocator {
    private static final int MAX_BIOME_SAMPLE_SPACING_BLOCKS = 16;

    @Override
    public BiomeAnchorSearchStepResult searchStep(
        WorldgenSearchContext context,
        BiomeOption biomeOption,
        AllocationSearchCursor cursor,
        SearchBudget budget
    ) {
        if (context == null || biomeOption == null) {
            return new BiomeAnchorSearchStepResult.Exhausted(cursor, new AnchorSearchProgress(0, 0, "missing_context"));
        }
        if (budget == null || !budget.hasLocateCallsRemaining() || !budget.hasSamplesRemaining()) {
            return new BiomeAnchorSearchStepResult.Continue(cursor, new AnchorSearchProgress(0, 0, "budget_exhausted"));
        }

        Set<ResourceKey<Biome>> accepted = biomeOption.getAcceptedBiomeKeys();
        if (accepted.isEmpty()) {
            return new BiomeAnchorSearchStepResult.Exhausted(cursor, new AnchorSearchProgress(0, 0, "empty_biome_option"));
        }

        int searchRadiusQuart = Math.max(1, BiomeCoordinateMath.blockToQuart(Math.max(4, cursor.getAnchorAttempt())));
        // maxSamples is the per-step work budget, not the spatial skip. The
        // previous implementation used blockCheckInterval (64 by default) as
        // both values, which skipped small cherry/mountain biome patches.
        // Keep the batch bounded while sampling at no more than 16-block
        // spacing; biome source cells themselves are four blocks wide.
        int intervalBlocks = Math.min(Math.max(4, budget.maxSamples()), MAX_BIOME_SAMPLE_SPACING_BLOCKS);
        int intervalQuart = Math.max(1, BiomeCoordinateMath.blockToQuart(intervalBlocks));
        int centerQuartX = BiomeCoordinateMath.blockToQuart(cursor.getSectorX());
        int centerQuartZ = BiomeCoordinateMath.blockToQuart(cursor.getSectorZ());
        List<Integer> searchYs = context.getEffectiveSampleBlockYs();
        normalizeScanState(cursor, searchYs.size(), searchRadiusQuart, intervalQuart);
        cursor.setLocateCallsUsed(cursor.getLocateCallsUsed() + 1);

        long lookupStartedAt = System.nanoTime();
        int samplesScanned = 0;
        while (samplesScanned < budget.maxSamples()) {
            if (cursor.getAnchorSearchYIndex() >= searchYs.size()) {
                recordProgress(cursor, samplesScanned);
                AllocationMetrics.add("bigbangregions_biome_source_lookup_nanos_total", System.nanoTime() - lookupStartedAt);
                AllocationMetrics.increment("bigbangregions_biome_source_lookup_total");
                AllocationMetrics.add("bigbangregions_biome_source_samples_total", samplesScanned);
                return new BiomeAnchorSearchStepResult.Exhausted(
                    cursor,
                    new AnchorSearchProgress(samplesScanned, 1, "anchor_not_found")
                );
            }

            int ringQuart = cursor.getAnchorSearchRingQuart();
            int pointIndex = cursor.getAnchorSearchPointIndex();
            int pointCount = perimeterPointCount(ringQuart, intervalQuart);
            if (pointIndex >= pointCount) {
                advanceRing(cursor, searchRadiusQuart, intervalQuart, searchYs.size());
                continue;
            }

            int[] offset = perimeterPoint(ringQuart, intervalQuart, pointIndex);
            int quartX = centerQuartX + offset[0];
            int quartZ = centerQuartZ + offset[1];
            int sampleY = searchYs.get(cursor.getAnchorSearchYIndex());
            Holder<Biome> holder = context.biomeSource().getNoiseBiome(
                quartX,
                BiomeCoordinateMath.blockToQuart(sampleY),
                quartZ,
                context.noiseSampler()
            );
            samplesScanned++;

            cursor.setAnchorSearchPointIndex(pointIndex + 1);
            if (holder.unwrapKey().map(accepted::contains).orElse(false)) {
                int blockX = BiomeCoordinateMath.quartToBlock(quartX);
                int blockZ = BiomeCoordinateMath.quartToBlock(quartZ);
                String biomeId = holder.unwrapKey()
                    .map(ResourceKey::location)
                    .map(ResourceLocation::toString)
                    .orElse("unknown");
                cursor.setCurrentAnchorX(blockX);
                cursor.setCurrentAnchorY(sampleY);
                cursor.setCurrentAnchorZ(blockZ);
                cursor.setCurrentAnchorBiomeId(biomeId);
                resetScanState(cursor);
                recordProgress(cursor, samplesScanned);
                AllocationMetrics.add("bigbangregions_biome_source_lookup_nanos_total", System.nanoTime() - lookupStartedAt);
                AllocationMetrics.increment("bigbangregions_biome_source_lookup_total");
                AllocationMetrics.add("bigbangregions_biome_source_samples_total", samplesScanned);
                return new BiomeAnchorSearchStepResult.Found(
                    new BiomeAnchor(blockX, sampleY, blockZ, biomeId),
                    cursor
                );
            }

            if (cursor.getAnchorSearchPointIndex() >= pointCount) {
                advanceRing(cursor, searchRadiusQuart, intervalQuart, searchYs.size());
            }
        }

        AllocationMetrics.add("bigbangregions_biome_source_lookup_nanos_total", System.nanoTime() - lookupStartedAt);
        AllocationMetrics.increment("bigbangregions_biome_source_lookup_total");
        recordProgress(cursor, samplesScanned);
        AllocationMetrics.add("bigbangregions_biome_source_samples_total", samplesScanned);
        return new BiomeAnchorSearchStepResult.Continue(
            cursor,
            new AnchorSearchProgress(samplesScanned, 1, "budget_exhausted")
        );
    }

    private static void normalizeScanState(
        AllocationSearchCursor cursor,
        int sampleYCount,
        int searchRadiusQuart,
        int intervalQuart
    ) {
        int ring = cursor.getAnchorSearchRingQuart();
        if (cursor.getAnchorSearchIntervalQuart() != intervalQuart
            || cursor.getAnchorSearchYIndex() < 0 || cursor.getAnchorSearchYIndex() >= sampleYCount
            || ring < 0 || ring > searchRadiusQuart || ring % intervalQuart != 0) {
            resetScanState(cursor);
            cursor.setAnchorSearchIntervalQuart(intervalQuart);
            return;
        }
        int pointCount = perimeterPointCount(ring, intervalQuart);
        if (cursor.getAnchorSearchPointIndex() < 0 || cursor.getAnchorSearchPointIndex() > pointCount) {
            cursor.setAnchorSearchPointIndex(0);
        }
    }

    private static void advanceRing(
        AllocationSearchCursor cursor,
        int searchRadiusQuart,
        int intervalQuart,
        int sampleYCount
    ) {
        int nextRing = cursor.getAnchorSearchRingQuart() + intervalQuart;
        if (nextRing <= searchRadiusQuart) {
            cursor.setAnchorSearchRingQuart(nextRing);
            cursor.setAnchorSearchPointIndex(0);
            return;
        }

        int nextY = cursor.getAnchorSearchYIndex() + 1;
        if (nextY < sampleYCount) {
            cursor.setAnchorSearchYIndex(nextY);
            cursor.setAnchorSearchRingQuart(0);
            cursor.setAnchorSearchPointIndex(0);
        } else {
            cursor.setAnchorSearchYIndex(sampleYCount);
            cursor.setAnchorSearchRingQuart(0);
            cursor.setAnchorSearchPointIndex(0);
        }
    }

    private static int perimeterPointCount(int ringQuart, int intervalQuart) {
        if (ringQuart == 0) {
            return 1;
        }
        int steps = ringQuart / intervalQuart;
        return 8 * steps;
    }

    /** Returns points clockwise from the top edge, matching the perimeter scan order. */
    private static int[] perimeterPoint(int ringQuart, int intervalQuart, int pointIndex) {
        if (ringQuart == 0) {
            return new int[]{0, 0};
        }

        int steps = ringQuart / intervalQuart;
        int topRowCount = 2 * steps + 1;
        if (pointIndex < topRowCount) {
            return new int[]{-ringQuart + pointIndex * intervalQuart, -ringQuart};
        }

        int remaining = pointIndex - topRowCount;
        int middleRowCount = 2 * (2 * steps - 1);
        if (remaining < middleRowCount) {
            int row = remaining / 2 + 1;
            int side = remaining % 2;
            return new int[]{side == 0 ? -ringQuart : ringQuart, -ringQuart + row * intervalQuart};
        }

        int bottomIndex = remaining - middleRowCount;
        return new int[]{-ringQuart + bottomIndex * intervalQuart, ringQuart};
    }

    private static void resetScanState(AllocationSearchCursor cursor) {
        cursor.setAnchorSearchYIndex(0);
        cursor.setAnchorSearchRingQuart(0);
        cursor.setAnchorSearchPointIndex(0);
    }

    private static void recordProgress(AllocationSearchCursor cursor, int samplesScanned) {
        if (samplesScanned > 0) {
            cursor.setTotalBiomeSamples(cursor.getTotalBiomeSamples() + samplesScanned);
        }
    }
}
