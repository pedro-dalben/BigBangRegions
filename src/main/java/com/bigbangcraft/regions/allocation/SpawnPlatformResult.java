package com.bigbangcraft.regions.allocation;

import net.minecraft.core.BlockPos;

import java.util.List;

public record SpawnPlatformResult(
    BlockPos finalStandPosition,
    boolean success,
    List<String> diagnostics
) {
    public static SpawnPlatformResult success(BlockPos pos) {
        return new SpawnPlatformResult(pos, true, List.of());
    }

    public static SpawnPlatformResult failure(BlockPos pos, String... reasons) {
        return new SpawnPlatformResult(pos, false, List.of(reasons));
    }
}
