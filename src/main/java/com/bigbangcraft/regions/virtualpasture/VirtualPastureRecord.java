package com.bigbangcraft.regions.virtualpasture;

import java.util.UUID;

public record VirtualPastureRecord(String dimensionKey, long blockPos, String regionId, UUID ownerUuid,
                                   int chunkX, int chunkZ, State state, Long pendingExpiresAt) {
    public enum State { PENDING, ACTIVE }
}
