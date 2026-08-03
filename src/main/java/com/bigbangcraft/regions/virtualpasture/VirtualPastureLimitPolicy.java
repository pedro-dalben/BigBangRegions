package com.bigbangcraft.regions.virtualpasture;

/** Pure placement decision, kept independent from Minecraft and SQLite for exhaustive tests. */
public final class VirtualPastureLimitPolicy {
    public record Counts(int region, int owner, int chunk) { }
    public record Decision(boolean allowed, int current, int maximum, String scope) { }

    private VirtualPastureLimitPolicy() { }

    public static Decision check(Counts counts, int maxRegion, int maxOwner, int maxChunk, boolean hasOwner, boolean bypass) {
        if (bypass) return new Decision(true, 0, 0, null);
        if (reached(counts.region(), maxRegion)) return new Decision(false, counts.region(), maxRegion, "região");
        if (hasOwner && reached(counts.owner(), maxOwner)) return new Decision(false, counts.owner(), maxOwner, "proprietário");
        if (reached(counts.chunk(), maxChunk)) return new Decision(false, counts.chunk(), maxChunk, "chunk");
        return new Decision(true, 0, 0, null);
    }

    private static boolean reached(int current, int maximum) { return maximum > 0 && current >= maximum; }
}
