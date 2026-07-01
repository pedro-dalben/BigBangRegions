package com.bigbangcraft.regions.allocation;

public record AllocationStatusSnapshot(
    AllocationRequest request,
    AllocationSearchCursor cursor,
    String biomeDisplayName,
    long elapsedMillis,
    long timeoutRemainingMillis,
    int totalKnownSectors,
    String statusLine
) {
}
