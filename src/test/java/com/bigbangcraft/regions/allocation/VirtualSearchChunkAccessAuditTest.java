package com.bigbangcraft.regions.allocation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class VirtualSearchChunkAccessAuditTest {
    private static final List<String> FORBIDDEN_TOKENS = List.of(
        "getChunk(",
        "getWorldChunk(",
        "getChunkFuture(",
        "CompletableFuture",
        ".join("
    );

    @Test
    void virtualSearchClassesDoNotReferenceChunkApis() throws Exception {
        List<Path> files = List.of(
            Path.of("src/main/java/com/bigbangcraft/regions/allocation/BiomeSearchService.java"),
            Path.of("src/main/java/com/bigbangcraft/regions/allocation/CachingBiomeVirtualSampler.java"),
            Path.of("src/main/java/com/bigbangcraft/regions/allocation/AdaptiveVirtualFootprintValidator.java")
        );

        for (Path file : files) {
            String content = Files.readString(file);
            for (String forbidden : FORBIDDEN_TOKENS) {
                assertFalse(content.contains(forbidden), () -> file + " contains forbidden token " + forbidden);
            }
        }
    }
}
