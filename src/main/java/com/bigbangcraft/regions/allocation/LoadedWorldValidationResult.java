package com.bigbangcraft.regions.allocation;

import java.util.List;

public record LoadedWorldValidationResult(
    boolean accepted,
    LoadedWorldFailureReason failureReason,
    SafeSpawnLocation safeSpawn,
    List<String> diagnostics
) {
    public static LoadedWorldValidationResult accepted(SafeSpawnLocation safeSpawn, List<String> diagnostics) {
        return new LoadedWorldValidationResult(true, LoadedWorldFailureReason.NONE, safeSpawn, List.copyOf(diagnostics));
    }

    public static LoadedWorldValidationResult rejected(LoadedWorldFailureReason reason, List<String> diagnostics) {
        return new LoadedWorldValidationResult(false, reason, null, List.copyOf(diagnostics));
    }
}
