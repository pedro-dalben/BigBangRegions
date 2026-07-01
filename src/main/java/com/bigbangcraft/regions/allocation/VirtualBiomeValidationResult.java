package com.bigbangcraft.regions.allocation;

public record VirtualBiomeValidationResult(
    boolean accepted,
    double score,
    int uniqueQuartSamples,
    int edgeMatches,
    int edgeMismatches,
    int interiorMatches,
    int interiorSamples,
    ValidationFailureReason failureReason
) {
    public static VirtualBiomeValidationResult accepted(double score, int uniqueQuartSamples, int edgeMatches, int interiorMatches, int interiorSamples) {
        return new VirtualBiomeValidationResult(true, score, uniqueQuartSamples, edgeMatches, 0, interiorMatches, interiorSamples, ValidationFailureReason.NONE);
    }

    public static VirtualBiomeValidationResult rejected(
        double score,
        int uniqueQuartSamples,
        int edgeMatches,
        int edgeMismatches,
        int interiorMatches,
        int interiorSamples,
        ValidationFailureReason reason
    ) {
        return new VirtualBiomeValidationResult(false, score, uniqueQuartSamples, edgeMatches, edgeMismatches, interiorMatches, interiorSamples, reason);
    }
}
