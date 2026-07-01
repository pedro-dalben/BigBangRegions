package com.bigbangcraft.regions.allocation;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AdaptiveVirtualFootprintValidator implements VirtualFootprintValidator {
    private final BiomeVirtualSampler sampler;
    private final int sampleGridSize;
    private final int minimumMatchPercentage;

    public AdaptiveVirtualFootprintValidator(BiomeVirtualSampler sampler, int sampleGridSize, int minimumMatchPercentage) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.sampleGridSize = normalizeGridSize(sampleGridSize);
        this.minimumMatchPercentage = Math.max(1, Math.min(100, minimumMatchPercentage));
    }

    @Override
    public VirtualBiomeValidationResult validate(
        WorldgenSearchContext context,
        BiomeOption biomeOption,
        PlotFootprint footprint,
        SearchBudget budget
    ) {
        if (context == null) {
            return VirtualBiomeValidationResult.rejected(0.0, 0, 0, 0, 0, 0, ValidationFailureReason.NO_CONTEXT);
        }

        Set<ResourceKey<Biome>> acceptedKeys = resolveAcceptedKeys(biomeOption);
        if (acceptedKeys.isEmpty()) {
            return VirtualBiomeValidationResult.rejected(0.0, 0, 0, 0, 0, 0, ValidationFailureReason.EMPTY_ACCEPTED_BIOMES);
        }

        int[] sampleXs = buildSampleAxis(footprint.minX(), footprint.maxX());
        int[] sampleZs = buildSampleAxis(footprint.minZ(), footprint.maxZ());
        long remainingSamples = budget == null ? Integer.MAX_VALUE : Math.max(1, budget.maxSamples());
        Set<Long> seenQuartPositions = new HashSet<>();
        int uniqueQuartSamples = 0;
        int edgeMatches = 0;
        int edgeMismatches = 0;
        int interiorMatches = 0;
        int interiorSamples = 0;
        int totalMatches = 0;
        int totalSamples = 0;

        SampleOutcome corners = samplePhase(context, acceptedKeys, sampleXs, sampleZs, seenQuartPositions, remainingSamples, true, true, false, false);
        if (corners.failureReason() != ValidationFailureReason.NONE) {
            return corners.result(
                corners.uniqueSamples(),
                edgeMatches + corners.matches(),
                edgeMismatches + corners.mismatches(),
                interiorMatches,
                interiorSamples,
                totalSamples + corners.samples(),
                totalMatches + corners.matches()
            );
        }
        remainingSamples = corners.remainingSamples();
        uniqueQuartSamples = corners.uniqueSamples();
        edgeMatches += corners.matches();
        edgeMismatches += corners.mismatches();
        totalMatches += corners.matches();
        totalSamples += corners.samples();

        SampleOutcome edgeMidpoints = samplePhase(context, acceptedKeys, sampleXs, sampleZs, seenQuartPositions, remainingSamples, false, true, true, false);
        if (edgeMidpoints.failureReason() != ValidationFailureReason.NONE) {
            return edgeMidpoints.result(
                edgeMidpoints.uniqueSamples(),
                edgeMatches + edgeMidpoints.matches(),
                edgeMismatches + edgeMidpoints.mismatches(),
                interiorMatches,
                interiorSamples,
                totalSamples + edgeMidpoints.samples(),
                totalMatches + edgeMidpoints.matches()
            );
        }
        remainingSamples = edgeMidpoints.remainingSamples();
        uniqueQuartSamples = edgeMidpoints.uniqueSamples();
        edgeMatches += edgeMidpoints.matches();
        edgeMismatches += edgeMidpoints.mismatches();
        totalMatches += edgeMidpoints.matches();
        totalSamples += edgeMidpoints.samples();

        SampleOutcome border = samplePhase(context, acceptedKeys, sampleXs, sampleZs, seenQuartPositions, remainingSamples, false, true, false, false);
        if (border.failureReason() != ValidationFailureReason.NONE) {
            return border.result(
                border.uniqueSamples(),
                edgeMatches + border.matches(),
                edgeMismatches + border.mismatches(),
                interiorMatches,
                interiorSamples,
                totalSamples + border.samples(),
                totalMatches + border.matches()
            );
        }
        remainingSamples = border.remainingSamples();
        uniqueQuartSamples = border.uniqueSamples();
        edgeMatches += border.matches();
        edgeMismatches += border.mismatches();
        totalMatches += border.matches();
        totalSamples += border.samples();

        SampleOutcome center = samplePhase(context, acceptedKeys, sampleXs, sampleZs, seenQuartPositions, remainingSamples, false, false, false, true);
        if (center.failureReason() != ValidationFailureReason.NONE) {
            return center.result(
                center.uniqueSamples(),
                edgeMatches,
                edgeMismatches,
                interiorMatches + center.matches(),
                interiorSamples + center.samples(),
                totalSamples + center.samples(),
                totalMatches + center.matches()
            );
        }
        remainingSamples = center.remainingSamples();
        uniqueQuartSamples = center.uniqueSamples();
        interiorMatches += center.matches();
        interiorSamples += center.samples();
        totalMatches += center.matches();
        totalSamples += center.samples();

        SampleOutcome interior = samplePhase(context, acceptedKeys, sampleXs, sampleZs, seenQuartPositions, remainingSamples, false, false, false, false);
        if (interior.failureReason() != ValidationFailureReason.NONE) {
            return interior.result(
                interior.uniqueSamples(),
                edgeMatches,
                edgeMismatches,
                interiorMatches + interior.matches(),
                interiorSamples + interior.samples(),
                totalSamples + interior.samples(),
                totalMatches + interior.matches()
            );
        }
        uniqueQuartSamples = interior.uniqueSamples();
        interiorMatches += interior.matches();
        interiorSamples += interior.samples();
        totalMatches += interior.matches();
        totalSamples += interior.samples();

        double score = totalSamples == 0 ? 0.0 : ((double) totalMatches / (double) totalSamples) * 100.0;
        boolean accepted = score >= minimumMatchPercentage;
        if (!accepted) {
            return VirtualBiomeValidationResult.rejected(
                score,
                uniqueQuartSamples,
                edgeMatches,
                edgeMismatches,
                interiorMatches,
                interiorSamples,
                ValidationFailureReason.INTERIOR_THRESHOLD_NOT_MET
            );
        }

        return VirtualBiomeValidationResult.accepted(score, uniqueQuartSamples, edgeMatches, interiorMatches, interiorSamples);
    }

    private SampleOutcome samplePhase(
        WorldgenSearchContext context,
        Set<ResourceKey<Biome>> acceptedKeys,
        int[] sampleXs,
        int[] sampleZs,
        Set<Long> seenQuartPositions,
        long remainingSamples,
        boolean cornersOnly,
        boolean borderOnly,
        boolean midpointsOnly,
        boolean centerOnly
    ) {
        int samples = 0;
        int matches = 0;
        int mismatches = 0;

        List<Point> orderedPoints = orderedPoints(sampleXs, sampleZs, cornersOnly, borderOnly, midpointsOnly, centerOnly);
        for (Point point : orderedPoints) {
            long packedQuart = BiomeCoordinateMath.packQuart(
                BiomeCoordinateMath.blockToQuart(point.x()),
                BiomeCoordinateMath.blockToQuart(context.sampleBlockY()),
                BiomeCoordinateMath.blockToQuart(point.z())
            );
            if (!seenQuartPositions.add(packedQuart)) {
                continue;
            }
            if (remainingSamples <= 0L) {
                return SampleOutcome.exhausted(seenQuartPositions.size(), matches, mismatches, samples);
            }

            remainingSamples--;
            samples++;
            ResourceKey<Biome> sampled = sampler.sampleAtBlock(context, point.x(), context.sampleBlockY(), point.z());
            boolean match = sampled != null && acceptedKeys.contains(sampled);
            if (match) {
                matches++;
            } else if (borderOnly || cornersOnly || midpointsOnly) {
                mismatches++;
                return SampleOutcome.borderMismatch(seenQuartPositions.size(), matches, mismatches, samples, remainingSamples);
            }
        }

        return SampleOutcome.success(seenQuartPositions.size(), matches, mismatches, samples, remainingSamples);
    }

    private static List<Point> orderedPoints(int[] sampleXs, int[] sampleZs, boolean cornersOnly, boolean borderOnly, boolean midpointsOnly, boolean centerOnly) {
        int last = sampleXs.length - 1;
        int center = sampleXs.length / 2;
        List<Point> points = new ArrayList<>();

        if (cornersOnly) {
            points.add(new Point(sampleXs[0], sampleZs[0]));
            points.add(new Point(sampleXs[last], sampleZs[0]));
            points.add(new Point(sampleXs[0], sampleZs[last]));
            points.add(new Point(sampleXs[last], sampleZs[last]));
            return points;
        }

        if (midpointsOnly) {
            points.add(new Point(sampleXs[center], sampleZs[0]));
            points.add(new Point(sampleXs[center], sampleZs[last]));
            points.add(new Point(sampleXs[0], sampleZs[center]));
            points.add(new Point(sampleXs[last], sampleZs[center]));
            return points;
        }

        if (centerOnly) {
            points.add(new Point(sampleXs[center], sampleZs[center]));
            return points;
        }

        if (borderOnly) {
            for (int xIndex = 0; xIndex <= last; xIndex++) {
                for (int zIndex = 0; zIndex <= last; zIndex++) {
                    boolean onBorder = xIndex == 0 || zIndex == 0 || xIndex == last || zIndex == last;
                    if (onBorder) {
                        points.add(new Point(sampleXs[xIndex], sampleZs[zIndex]));
                    }
                }
            }
            return points;
        }

        for (int xIndex = 1; xIndex < last; xIndex++) {
            for (int zIndex = 1; zIndex < last; zIndex++) {
                if (xIndex == center && zIndex == center) {
                    continue;
                }
                points.add(new Point(sampleXs[xIndex], sampleZs[zIndex]));
            }
        }
        return points;
    }

    private int[] buildSampleAxis(int min, int max) {
        int normalizedSize = sampleGridSize;
        int[] axis = new int[normalizedSize];
        int span = max - min;
        int step = Math.max(1, span / (normalizedSize - 1));
        for (int i = 0; i < normalizedSize; i++) {
            axis[i] = (i == normalizedSize - 1) ? max : min + (i * step);
        }
        return axis;
    }

    private static int normalizeGridSize(int gridSize) {
        int normalized = Math.max(3, gridSize);
        if (normalized % 2 == 0) {
            normalized++;
        }
        return normalized;
    }

    private static Set<ResourceKey<Biome>> resolveAcceptedKeys(BiomeOption biomeOption) {
        Set<ResourceKey<Biome>> keys = new HashSet<>();
        for (String biomeId : biomeOption.getAcceptedBiomeIds()) {
            try {
                ResourceLocation location = ResourceLocation.parse(biomeId);
                keys.add(ResourceKey.create(Registries.BIOME, location));
            } catch (Exception ignored) {
                // Invalid biome ids are treated as non-matches.
            }
        }
        return keys;
    }

    private record Point(int x, int z) {
    }

    private record SampleOutcome(
        ValidationFailureReason failureReason,
        int uniqueSamples,
        int matches,
        int mismatches,
        int samples,
        long remainingSamples
    ) {
        private static SampleOutcome success(int uniqueSamples, int matches, int mismatches, int samples, long remainingSamples) {
            return new SampleOutcome(ValidationFailureReason.NONE, uniqueSamples, matches, mismatches, samples, remainingSamples);
        }

        private static SampleOutcome borderMismatch(int uniqueSamples, int matches, int mismatches, int samples, long remainingSamples) {
            return new SampleOutcome(ValidationFailureReason.BORDER_MISMATCH, uniqueSamples, matches, mismatches, samples, remainingSamples);
        }

        private static SampleOutcome exhausted(int uniqueSamples, int matches, int mismatches, int samples) {
            return new SampleOutcome(ValidationFailureReason.BUDGET_EXHAUSTED, uniqueSamples, matches, mismatches, samples, 0L);
        }

        private VirtualBiomeValidationResult result(
            int uniqueSamples,
            int edgeMatches,
            int edgeMismatches,
            int interiorMatches,
            int interiorSamples,
            int totalSamples,
            int totalMatches
        ) {
            double score = totalSamples == 0 ? 0.0 : ((double) totalMatches / (double) totalSamples) * 100.0;
            return VirtualBiomeValidationResult.rejected(score, uniqueSamples, edgeMatches, edgeMismatches, interiorMatches, interiorSamples, failureReason);
        }
    }
}
