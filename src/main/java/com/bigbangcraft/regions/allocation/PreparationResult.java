package com.bigbangcraft.regions.allocation;

import java.util.List;

public record PreparationResult(
    PreparationResultType type,
    List<String> diagnostics
) {
    public static PreparationResult ready() {
        return new PreparationResult(PreparationResultType.READY, List.of());
    }

    public static PreparationResult failed(PreparationResultType type, String diagnostic) {
        return new PreparationResult(type, diagnostic == null ? List.of() : List.of(diagnostic));
    }
}
