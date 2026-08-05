package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.domain.RegionBounds;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class ExpansionVisualPipelineTest {
    @BeforeAll
    static void bootMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void unloadedChunkWaitsForATicketThenResumes(@TempDir Path directory) throws Exception {
        Config.RegionExpansionPerformanceConfig performance = performance(1);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(null);
        ServerLevel level = level(chunks);
        MinecraftServer server = server(level, null);
        AtomicReference<ExpansionVisualPipeline.Result> result = new AtomicReference<>();
        ExpansionVisualPipeline pipeline = new ExpansionVisualPipeline(directory, performance);

        try {
            pipeline.request(plan(directory, "op-1", 1), result::set);
            pipeline.tick(server, performance);

            assertNull(result.get());
            verify(chunks).addRegionTicket(any(), eq(new ChunkPos(0, 0)), eq(2), any(UUID.class));

            when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
            driveUntilResult(pipeline, server, performance, result);

            assertEquals(ExpansionVisualPipeline.State.COMPLETED, result.get().state());
            verify(chunks).removeRegionTicket(any(), eq(new ChunkPos(0, 0)), eq(2), any(UUID.class));
        } finally {
            pipeline.shutdown(1);
        }
    }

    @Test
    void blockBudgetLimitsCaptureAndApplicationPerTick(@TempDir Path directory) throws Exception {
        Config.RegionExpansionPerformanceConfig performance = performance(1);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        ServerLevel level = level(chunks);
        CountDownLatch persisted = new CountDownLatch(1);
        MinecraftServer server = server(level, persisted);
        AtomicReference<ExpansionVisualPipeline.Result> result = new AtomicReference<>();
        ExpansionVisualPipeline pipeline = new ExpansionVisualPipeline(directory, performance);

        try {
            pipeline.request(plan(directory, "op-budget", 2), result::set);
            pipeline.tick(server, performance);
            verify(level, times(1)).getBlockState(any());

            driveUntilPersisted(pipeline, server, performance, persisted);
            clearInvocations(level);
            pipeline.tick(server, performance);
            verify(level, times(1)).getBlockState(any());
            assertNull(result.get());

            driveUntilResult(pipeline, server, performance, result);
            assertTrue(result.get().captureTicks() > 1);
            assertTrue(result.get().applicationTicks() > 1);
            verify(level, never()).getBlockEntity(any());
        } finally {
            pipeline.shutdown(1);
        }
    }

    @Test
    void duplicateAndCancelledJobsNeverRunObsoleteCallbacks(@TempDir Path directory) throws Exception {
        Config.RegionExpansionPerformanceConfig performance = performance(50);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        ServerLevel level = level(chunks);
        MinecraftServer server = server(level, null);
        AtomicInteger obsoleteCallbacks = new AtomicInteger();
        AtomicReference<ExpansionVisualPipeline.Result> current = new AtomicReference<>();
        ExpansionVisualPipeline pipeline = new ExpansionVisualPipeline(directory, performance);
        ExpansionVisualPipeline.Plan first = initialPlan(directory, "op-old", 3);
        ExpansionVisualPipeline.Plan replacement = initialPlan(directory, "op-new", 4);

        try {
            assertEquals(ExpansionVisualPipeline.RequestStatus.STARTED,
                pipeline.request(first, ignored -> obsoleteCallbacks.incrementAndGet()));
            assertEquals(ExpansionVisualPipeline.RequestStatus.DUPLICATE,
                pipeline.request(first, ignored -> obsoleteCallbacks.incrementAndGet()));
            assertEquals(ExpansionVisualPipeline.RequestStatus.STARTED, pipeline.request(replacement, current::set));

            driveUntilResult(pipeline, server, performance, current);
            assertEquals(0, obsoleteCallbacks.get());
            assertEquals("op-new", current.get().operationId());
        } finally {
            pipeline.shutdown(1);
        }
    }

    @Test
    void applicationErrorKeepsPersistedSnapshotForRetry(@TempDir Path directory) throws Exception {
        Config.RegionExpansionPerformanceConfig performance = performance(50);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        ServerLevel level = level(chunks);
        CountDownLatch persisted = new CountDownLatch(1);
        MinecraftServer server = server(level, persisted);
        AtomicReference<ExpansionVisualPipeline.Result> result = new AtomicReference<>();
        ExpansionVisualPipeline pipeline = new ExpansionVisualPipeline(directory, performance);

        try {
            pipeline.request(initialPlan(directory, "op-error", 5), result::set);
            pipeline.tick(server, performance);
            assertTrue(persisted.await(2, TimeUnit.SECONDS));
            when(level.getBlockState(any())).thenThrow(new IllegalStateException("world read failed"));

            pipeline.tick(server, performance);

            assertEquals("APPLICATION", result.get().failureStage());
            assertSnapshotReadable(directory.resolve("region.nbt"));
        } finally {
            pipeline.shutdown(1);
        }
    }

    @Test
    void shutdownLeavesCompletedSnapshotRecoverable(@TempDir Path directory) throws Exception {
        Config.RegionExpansionPerformanceConfig performance = performance(50);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(chunks.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        ServerLevel level = level(chunks);
        CountDownLatch persisted = new CountDownLatch(1);
        MinecraftServer server = server(level, persisted);
        ExpansionVisualPipeline pipeline = new ExpansionVisualPipeline(directory, performance);

        pipeline.request(initialPlan(directory, "op-shutdown", 6), ignored -> fail("shutdown callback must be discarded"));
        pipeline.tick(server, performance);
        assertTrue(persisted.await(2, TimeUnit.SECONDS));
        pipeline.shutdown(1);

        assertSnapshotReadable(directory.resolve("region.nbt"));
    }

    private static Config.RegionExpansionPerformanceConfig performance(int maxBlocks) {
        Config.RegionExpansionPerformanceConfig performance = new Config.RegionExpansionPerformanceConfig();
        performance.setSnapshotCaptureBudgetMs(20);
        performance.setSnapshotCaptureMaxBlocksPerTick(maxBlocks);
        performance.setBorderApplicationBudgetMs(20);
        performance.setBorderApplicationMaxBlocksPerTick(maxBlocks);
        return performance;
    }

    private static ExpansionVisualPipeline.Plan plan(Path directory, String operation, long generation) {
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 1, 0, 1);
        return ExpansionVisualPipeline.plan("region", operation, generation, bounds, bounds,
            new Config.BorderConfig(), directory);
    }

    private static ExpansionVisualPipeline.Plan initialPlan(Path directory, String operation, long generation) {
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 1, 0, 1);
        return ExpansionVisualPipeline.initialBorderPlan("region", operation, generation, bounds,
            new Config.BorderConfig(), directory);
    }

    private static ServerLevel level(ServerChunkCache chunks) {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getChunkSource()).thenReturn(chunks);
        when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(0);
        when(level.getMaxBuildHeight()).thenReturn(1);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.setBlock(any(), any(), anyInt())).thenReturn(true);
        return level;
    }

    private static MinecraftServer server(ServerLevel level, CountDownLatch executed) {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getLevel(any())).thenReturn(level);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            if (executed != null) executed.countDown();
            return null;
        }).when(server).execute(any(Runnable.class));
        return server;
    }

    private static void driveUntilPersisted(ExpansionVisualPipeline pipeline, MinecraftServer server,
                                             Config.RegionExpansionPerformanceConfig performance,
                                             CountDownLatch persisted) throws Exception {
        for (int tick = 0; tick < 100 && persisted.getCount() != 0; tick++) {
            pipeline.tick(server, performance);
            Thread.sleep(5);
        }
        assertTrue(persisted.await(1, TimeUnit.SECONDS), "snapshot persistence did not complete");
    }

    private static void driveUntilResult(ExpansionVisualPipeline pipeline, MinecraftServer server,
                                         Config.RegionExpansionPerformanceConfig performance,
                                         AtomicReference<ExpansionVisualPipeline.Result> result) throws Exception {
        for (int tick = 0; tick < 200 && result.get() == null; tick++) {
            pipeline.tick(server, performance);
            Thread.sleep(5);
        }
        assertNotNull(result.get(), "pipeline did not complete");
    }

    private static void assertSnapshotReadable(Path snapshot) throws Exception {
        assertTrue(Files.exists(snapshot));
        assertEquals("mutation_snapshot_v2",
            NbtIo.readCompressed(snapshot, NbtAccounter.unlimitedHeap()).getString("format"));
    }
}
