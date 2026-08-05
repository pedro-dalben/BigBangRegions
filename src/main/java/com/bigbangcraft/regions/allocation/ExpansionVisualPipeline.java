package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.domain.RegionBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.UUID;

/** Server-thread state machine for expansion snapshots and border changes. */
final class ExpansionVisualPipeline {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-ExpansionVisualPipeline");
    private static final long LARGE_SNAPSHOT_BYTES = 16L * 1024L * 1024L;

    enum State { PENDING, CAPTURING, PERSISTING, WAITING_FOR_CHUNKS, APPLYING, COMPLETED, FAILED, CANCELLED }
    enum RequestStatus { STARTED, DUPLICATE, REJECTED }

    record Result(String regionId, String operationId, long generation, State state,
                  long captureCandidates, long capturedBlocks, long applicationCandidates, long appliedBlocks,
                  long captureNanos, long persistenceQueueNanos, long serializationNanos,
                  long compressionAndWriteNanos, long applicationNanos, int captureTicks, int applicationTicks,
                  long snapshotBytes, String failureStage, String failureDetail) {
        boolean succeeded() { return state == State.COMPLETED; }
    }

    record Plan(String regionId, String operationId, long generation, String dimension,
                RegionBounds oldBounds, RegionBounds targetBounds, List<Column> captureColumns,
                List<Column> removeColumns, List<Column> applyColumns, List<BlockPos> captureCeiling,
                List<BlockPos> applyCeiling, String borderMaterial, Path snapshotPath, String signature) {
    }

    private final ThreadPoolExecutor persistenceExecutor;
    private final RegionChunkTicketManager chunkTicketManager = new SimpleRegionChunkTicketManager();
    private final Map<String, Job> jobsByRegion = new LinkedHashMap<>();
    private boolean stopping;
    private int roundRobin;

    ExpansionVisualPipeline(Path snapshotDirectory, Config.RegionExpansionPerformanceConfig performance) {
        try {
            RegionTerrainSnapshot.recoverIncompleteFiles(snapshotDirectory);
        } catch (IOException error) {
            LOGGER.warn("Could not recover incomplete expansion snapshot files from {}", snapshotDirectory, error);
        }
        AtomicInteger sequence = new AtomicInteger();
        persistenceExecutor = new ThreadPoolExecutor(
            performance.getPersistenceWorkers(), performance.getPersistenceWorkers(),
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(performance.getPersistenceQueueCapacity()),
            runnable -> {
                Thread thread = new Thread(runnable, "BigBangRegions-ExpansionSnapshotIO-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    static Plan plan(String regionId, String operationId, long generation, RegionBounds oldBounds,
                     RegionBounds targetBounds, Config.BorderConfig border, Path snapshotDirectory) {
        List<Column> captureColumns = boundaryColumns(targetBounds);
        List<Column> removeColumns = boundaryColumns(oldBounds);
        List<BlockPos> ceiling = border.isCreateCeiling() ? ceilingPositions(targetBounds) : List.of();
        String signature = regionId + '|' + operationId + '|' + generation + '|' + oldBounds + '|'
            + targetBounds + '|' + border.getMaterial() + '|' + border.isCreateCeiling() + "|mutation_snapshot_v2";
        return new Plan(regionId, operationId, generation, targetBounds.getDimension(), oldBounds, targetBounds,
            captureColumns, removeColumns, captureColumns, ceiling, ceiling, border.getMaterial(),
            snapshotDirectory.resolve(regionId + ".nbt"), signature);
    }

    /** Initial creation already owns a complete mutation snapshot, so only the border is queued. */
    static Plan initialBorderPlan(String regionId, String operationId, long generation, RegionBounds bounds,
                                  Config.BorderConfig border, Path snapshotDirectory) {
        List<Column> applyColumns = boundaryColumns(bounds);
        List<BlockPos> ceiling = border.isCreateCeiling() ? ceilingPositions(bounds) : List.of();
        String signature = regionId + '|' + operationId + '|' + generation + "|initial_border|"
            + border.getMaterial() + '|' + border.isCreateCeiling();
        return new Plan(regionId, operationId, generation, bounds.getDimension(), bounds, bounds,
            List.of(), List.of(), applyColumns, List.of(), ceiling, border.getMaterial(),
            snapshotDirectory.resolve(regionId + ".nbt"), signature);
    }

    RequestStatus request(Plan plan, Consumer<Result> completion) {
        if (stopping) return RequestStatus.REJECTED;
        Job current = jobsByRegion.get(plan.regionId());
        if (current != null && current.plan.signature().equals(plan.signature())) {
            LOGGER.debug("Consolidated duplicate expansion visual request: region={}, op={}",
                plan.regionId(), plan.operationId());
            return RequestStatus.DUPLICATE;
        }
        if (current != null) {
            current.state = State.CANCELLED;
            releaseChunkTickets(current);
            LOGGER.warn("Discarded obsolete expansion visual job: region={}, op={}, generation={}",
                current.plan.regionId(), current.plan.operationId(), current.plan.generation());
        }

        Job job = new Job(plan, completion);
        jobsByRegion.put(plan.regionId(), job);
        LOGGER.info("Expansion visual job started: region={}, op={}, generation={}, captureCandidates={}, applyCandidates={}",
            plan.regionId(), plan.operationId(), plan.generation(), job.captureCandidates, job.applicationCandidates);
        return RequestStatus.STARTED;
    }

    void tick(MinecraftServer server, Config.RegionExpansionPerformanceConfig performance) {
        if (stopping || jobsByRegion.isEmpty()) return;
        List<Job> jobs = new ArrayList<>(jobsByRegion.values());
        for (int checked = 0; checked < jobs.size(); checked++) {
            int index = Math.floorMod(roundRobin++, jobs.size());
            Job job = jobs.get(index);
            if (jobsByRegion.get(job.plan.regionId()) != job) continue;
            if (job.state == State.WAITING_FOR_CHUNKS && !resumeWhenChunkLoads(server, job)) return;
            if (job.state == State.PENDING) job.state = State.CAPTURING;
            if (job.state == State.CAPTURING) {
                try {
                    advanceCapture(server, job, performance);
                } catch (RuntimeException error) {
                    fail(job, "CAPTURE", error);
                }
                return;
            }
            if (job.state == State.APPLYING) {
                try {
                    advanceApplication(server, job, performance);
                } catch (RuntimeException error) {
                    fail(job, "APPLICATION", error);
                }
                return;
            }
        }
    }

    void shutdown(int timeoutSeconds) {
        stopping = true;
        int cancelled = jobsByRegion.size();
        jobsByRegion.values().forEach(job -> {
            if (job.state != State.PERSISTING) job.state = State.CANCELLED;
            releaseChunkTickets(job);
        });
        jobsByRegion.clear();

        int queued = persistenceExecutor.getQueue().size();
        persistenceExecutor.getQueue().clear(); // Captured-only jobs are retried by reconcile after restart.
        persistenceExecutor.shutdown();
        try {
            if (!persistenceExecutor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                LOGGER.warn("Expansion snapshot I/O still running after {}s; final files remain atomic and incomplete operations will reconcile after restart.",
                    timeoutSeconds);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while waiting for expansion snapshot I/O shutdown.");
        }
        LOGGER.info("Expansion visual pipeline stopped: cancelledJobs={}, droppedQueuedPersistence={}", cancelled, queued);
    }

    private void advanceCapture(MinecraftServer server, Job job, Config.RegionExpansionPerformanceConfig performance) {
        ServerLevel level = level(server, job);
        if (level == null) return;
        long startedAt = System.nanoTime();
        long deadline = startedAt + TimeUnit.MILLISECONDS.toNanos(performance.getSnapshotCaptureBudgetMs());
        int processed = 0;
        job.captureTicks++;
        while (processed < performance.getSnapshotCaptureMaxBlocksPerTick() && System.nanoTime() < deadline) {
            BlockPos pos = nextCapturePosition(level, job);
            if (pos == null) {
                if (job.state != State.CAPTURING) return;
                startPersistence(server, job);
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isCollisionShapeFullBlock(level, pos)) {
                job.captured.add(RegionTerrainSnapshot.captureExpansionBlock(level, pos, state));
            }
            job.captureProcessed++;
            processed++;
        }
        warnOverBudget(job, "capture", System.nanoTime() - startedAt, performance.getSnapshotCaptureBudgetMs());
    }

    private void startPersistence(MinecraftServer server, Job job) {
        job.state = State.PERSISTING;
        job.persistenceQueuedAt = System.nanoTime();
        RegionTerrainSnapshot.ExpansionCapture capture = new RegionTerrainSnapshot.ExpansionCapture(
            job.plan.regionId(), job.plan.dimension(), job.plan.generation(), job.plan.targetBounds().volume(),
            job.plan.snapshotPath(), job.captured
        );
        try {
            CompletableFuture.supplyAsync(() -> {
                long startedAt = System.nanoTime();
                try {
                    return new PersistenceCompletion(RegionTerrainSnapshot.persistExpansion(capture), startedAt, null);
                } catch (Throwable error) {
                    return new PersistenceCompletion(null, startedAt, error);
                }
            }, persistenceExecutor).whenComplete((completion, ignored) -> server.execute(() -> finishPersistence(job, completion)));
        } catch (RejectedExecutionException rejected) {
            LOGGER.warn("Expansion snapshot persistence queue is full: region={}, op={}",
                job.plan.regionId(), job.plan.operationId());
            fail(job, "PERSISTENCE_QUEUE_FULL", rejected);
        }
    }

    private void finishPersistence(Job job, PersistenceCompletion completion) {
        if (stopping || jobsByRegion.get(job.plan.regionId()) != job || job.state != State.PERSISTING) {
            LOGGER.warn("Discarded obsolete expansion snapshot callback: region={}, op={}, generation={}",
                job.plan.regionId(), job.plan.operationId(), job.plan.generation());
            return;
        }
        if (completion == null || completion.error() != null) {
            fail(job, "PERSISTENCE", completion == null ? new IOException("Missing persistence result") : completion.error());
            return;
        }
        if (completion.result().discarded()) {
            fail(job, "STALE_SNAPSHOT", new IOException("A newer snapshot generation already exists."));
            return;
        }
        job.persistenceQueueNanos = completion.startedAt() - job.persistenceQueuedAt;
        job.serializationNanos = completion.result().serializationNanos();
        job.compressionAndWriteNanos = completion.result().compressionAndWriteNanos();
        job.snapshotBytes = completion.result().compressedBytes();
        if (job.snapshotBytes > LARGE_SNAPSHOT_BYTES) {
            LOGGER.warn("Large expansion snapshot: region={}, op={}, bytes={}",
                job.plan.regionId(), job.plan.operationId(), job.snapshotBytes);
        }
        job.applicationStartedAt = System.nanoTime();
        job.state = State.APPLYING;
    }

    private void advanceApplication(MinecraftServer server, Job job, Config.RegionExpansionPerformanceConfig performance) {
        ServerLevel level = level(server, job);
        if (level == null) return;
        BlockState borderState = borderState(job.plan.borderMaterial());
        long startedAt = System.nanoTime();
        long deadline = startedAt + TimeUnit.MILLISECONDS.toNanos(performance.getBorderApplicationBudgetMs());
        int processed = 0;
        job.applicationTicks++;
        while (processed < performance.getBorderApplicationMaxBlocksPerTick() && System.nanoTime() < deadline) {
            Change change = nextApplicationChange(level, job);
            if (change == null) {
                if (job.state != State.APPLYING) return;
                complete(job);
                return;
            }
            BlockState current = level.getBlockState(change.pos());
            if (change.remove()) {
                if (current.equals(borderState)) {
                    level.setBlock(change.pos(), Blocks.AIR.defaultBlockState(), 2);
                    job.appliedBlocks++;
                }
            } else if (!current.equals(borderState) && !current.isCollisionShapeFullBlock(level, change.pos())) {
                level.setBlock(change.pos(), borderState, 2);
                job.appliedBlocks++;
            }
            job.applicationProcessed++;
            processed++;
        }
        warnOverBudget(job, "application", System.nanoTime() - startedAt, performance.getBorderApplicationBudgetMs());
    }

    private ServerLevel level(MinecraftServer server, Job job) {
        ResourceLocation location;
        try {
            location = ResourceLocation.parse(job.plan.dimension());
        } catch (Exception invalid) {
            fail(job, "DIMENSION", invalid);
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, location));
        if (level == null) fail(job, "WORLD_UNAVAILABLE", new IllegalStateException(job.plan.dimension()));
        return level;
    }

    private BlockPos nextCapturePosition(ServerLevel level, Job job) {
        while (job.captureColumnIndex < job.plan.captureColumns().size()) {
            Column column = job.plan.captureColumns().get(job.captureColumnIndex);
            if (job.captureY == Integer.MIN_VALUE) {
                if (!ensureLoaded(level, column.x(), column.z(), job)) return null;
                job.captureY = TerrainAllocationCoordinator.borderStartY(level, job.plan.targetBounds(), column.x(), column.z());
                job.captureEndY = Math.min(job.plan.targetBounds().getMaxY(), level.getMaxBuildHeight() - 1);
            }
            if (job.captureY <= job.captureEndY) {
                return new BlockPos(column.x(), job.captureY++, column.z());
            }
            job.captureColumnIndex++;
            job.captureY = Integer.MIN_VALUE;
        }
        while (job.captureCeilingIndex < job.plan.captureCeiling().size()) {
            BlockPos pos = job.plan.captureCeiling().get(job.captureCeilingIndex);
            if (pos.getY() >= level.getMaxBuildHeight()) pos = pos.atY(level.getMaxBuildHeight() - 1);
            if (!ensureLoaded(level, pos.getX(), pos.getZ(), job)) return null;
            job.captureCeilingIndex++;
            return pos;
        }
        return null;
    }

    private Change nextApplicationChange(ServerLevel level, Job job) {
        while (job.removeColumnIndex < job.plan.removeColumns().size()) {
            Column column = job.plan.removeColumns().get(job.removeColumnIndex);
            if (job.removeY == Integer.MIN_VALUE) {
                if (!ensureLoaded(level, column.x(), column.z(), job)) return null;
                job.removeY = job.plan.oldBounds().getMinY();
                job.removeEndY = Math.min(job.plan.oldBounds().getMaxY(), level.getMaxBuildHeight() - 1);
            }
            if (job.removeY <= job.removeEndY) return new Change(new BlockPos(column.x(), job.removeY++, column.z()), true);
            job.removeColumnIndex++;
            job.removeY = Integer.MIN_VALUE;
        }
        while (job.applyColumnIndex < job.plan.applyColumns().size()) {
            Column column = job.plan.applyColumns().get(job.applyColumnIndex);
            if (job.applyY == Integer.MIN_VALUE) {
                if (!ensureLoaded(level, column.x(), column.z(), job)) return null;
                job.applyY = TerrainAllocationCoordinator.borderStartY(level, job.plan.targetBounds(), column.x(), column.z());
                job.applyEndY = Math.min(job.plan.targetBounds().getMaxY(), level.getMaxBuildHeight() - 1);
            }
            if (job.applyY <= job.applyEndY) return new Change(new BlockPos(column.x(), job.applyY++, column.z()), false);
            job.applyColumnIndex++;
            job.applyY = Integer.MIN_VALUE;
        }
        while (job.applyCeilingIndex < job.plan.applyCeiling().size()) {
            BlockPos pos = job.plan.applyCeiling().get(job.applyCeilingIndex);
            if (pos.getY() >= level.getMaxBuildHeight()) pos = pos.atY(level.getMaxBuildHeight() - 1);
            if (!ensureLoaded(level, pos.getX(), pos.getZ(), job)) return null;
            job.applyCeilingIndex++;
            return new Change(pos, false);
        }
        return null;
    }

    private boolean ensureLoaded(ServerLevel level, int x, int z, Job job) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) return true;

        ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
        if (job.ticketedChunks.add(chunk)) {
            job.chunkTickets.add(chunkTicketManager.acquire(level, Set.of(chunk), job.chunkTicketId, Long.MAX_VALUE));
        }
        job.waitingChunkX = chunkX;
        job.waitingChunkZ = chunkZ;
        job.resumeState = job.state;
        job.state = State.WAITING_FOR_CHUNKS;
        return false;
    }

    private boolean resumeWhenChunkLoads(MinecraftServer server, Job job) {
        ServerLevel level = level(server, job);
        if (level == null) return false;
        if (level.getChunkSource().getChunkNow(job.waitingChunkX, job.waitingChunkZ) == null) return false;

        job.state = job.resumeState;
        job.resumeState = null;
        job.waitingChunkX = Integer.MIN_VALUE;
        job.waitingChunkZ = Integer.MIN_VALUE;
        return true;
    }

    private void releaseChunkTickets(Job job) {
        for (TicketLease ticket : job.chunkTickets) {
            try {
                chunkTicketManager.release(ticket);
            } catch (RuntimeException error) {
                LOGGER.warn("Could not release expansion chunk ticket: region={}, op={}",
                    job.plan.regionId(), job.plan.operationId(), error);
            }
        }
        job.chunkTickets.clear();
        job.ticketedChunks.clear();
    }

    private BlockState borderState(String material) {
        try {
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(material));
            return block == null || block == Blocks.AIR ? Blocks.GLASS.defaultBlockState() : block.defaultBlockState();
        } catch (Exception ignored) {
            return Blocks.GLASS.defaultBlockState();
        }
    }

    private void complete(Job job) {
        if (jobsByRegion.get(job.plan.regionId()) != job) return;
        job.state = State.COMPLETED;
        jobsByRegion.remove(job.plan.regionId());
        releaseChunkTickets(job);
        AllocationMetrics.add("bigbangregions_expansion_visual_capture_blocks_total", job.captured.size());
        AllocationMetrics.add("bigbangregions_expansion_visual_application_blocks_total", job.appliedBlocks);
        AllocationMetrics.add("bigbangregions_expansion_visual_snapshot_bytes_total", job.snapshotBytes);
        AllocationMetrics.add("bigbangregions_expansion_visual_capture_nanos_total", job.captureNanos());
        AllocationMetrics.add("bigbangregions_expansion_visual_application_nanos_total", job.applicationNanos());
        Result result = job.result(null, null);
        LOGGER.info("Expansion visual job completed: region={}, op={}, generation={}, captured={}/{}, applied={}/{}, captureMs={}, queueMs={}, serializationMs={}, compressionWriteMs={}, applicationMs={}, captureTicks={}, applicationTicks={}, snapshotBytes={}",
            result.regionId(), result.operationId(), result.generation(), result.capturedBlocks(), result.captureCandidates(),
            result.appliedBlocks(), result.applicationCandidates(), millis(result.captureNanos()), millis(result.persistenceQueueNanos()),
            millis(result.serializationNanos()), millis(result.compressionAndWriteNanos()), millis(result.applicationNanos()),
            result.captureTicks(), result.applicationTicks(), result.snapshotBytes());
        job.completion.accept(result);
    }

    private void fail(Job job, String stage, Throwable error) {
        if (job.state == State.FAILED || job.state == State.CANCELLED) return;
        job.state = State.FAILED;
        if (jobsByRegion.get(job.plan.regionId()) == job) jobsByRegion.remove(job.plan.regionId());
        releaseChunkTickets(job);
        Result result = job.result(stage, error.getMessage());
        LOGGER.warn("Expansion visual job failed: region={}, op={}, generation={}, stage={}, captured={}, applied={}",
            result.regionId(), result.operationId(), result.generation(), stage, result.capturedBlocks(), result.appliedBlocks(), error);
        job.completion.accept(result);
    }

    private void warnOverBudget(Job job, String phase, long elapsedNanos, int budgetMillis) {
        long now = System.nanoTime();
        if (elapsedNanos <= TimeUnit.MILLISECONDS.toNanos(budgetMillis) || now - job.lastBudgetWarningNanos < TimeUnit.SECONDS.toNanos(1)) return;
        job.lastBudgetWarningNanos = now;
        LOGGER.warn("Expansion visual {} batch exceeded budget: region={}, op={}, elapsedMs={}, budgetMs={}",
            phase, job.plan.regionId(), job.plan.operationId(), millis(elapsedNanos), budgetMillis);
    }

    private static List<Column> boundaryColumns(RegionBounds bounds) {
        Set<Column> columns = new LinkedHashSet<>();
        for (int z = bounds.getMinZ(); z <= bounds.getMaxZ(); z++) {
            columns.add(new Column(bounds.getMinX(), z));
            columns.add(new Column(bounds.getMaxX(), z));
        }
        for (int x = bounds.getMinX(); x <= bounds.getMaxX(); x++) {
            columns.add(new Column(x, bounds.getMinZ()));
            columns.add(new Column(x, bounds.getMaxZ()));
        }
        List<Column> ordered = new ArrayList<>(columns);
        ordered.sort(Comparator.comparingLong(Column::chunkKey).thenComparingInt(Column::x).thenComparingInt(Column::z));
        return List.copyOf(ordered);
    }

    private static List<BlockPos> ceilingPositions(RegionBounds bounds) {
        int ceilingY = bounds.getMaxY();
        List<BlockPos> positions = new ArrayList<>();
        for (int x = bounds.getMinX(); x <= bounds.getMaxX(); x++) {
            for (int z = bounds.getMinZ(); z <= bounds.getMaxZ(); z++) {
                positions.add(new BlockPos(x, ceilingY, z));
            }
        }
        positions.sort(Comparator.<BlockPos>comparingLong(pos -> chunkKey(pos.getX(), pos.getZ()))
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return List.copyOf(positions);
    }

    private static long candidates(RegionBounds bounds, List<Column> columns, List<BlockPos> ceiling) {
        return (long) columns.size() * Math.max(0, bounds.getMaxY() - bounds.getMinY() + 1) + ceiling.size();
    }

    private static long chunkKey(int x, int z) {
        return ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL);
    }

    private static long millis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private record Column(int x, int z) {
        long chunkKey() { return ExpansionVisualPipeline.chunkKey(x, z); }
    }

    private record Change(BlockPos pos, boolean remove) {
    }

    private record PersistenceCompletion(RegionTerrainSnapshot.PersistenceResult result, long startedAt, Throwable error) {
    }

    private static final class Job {
        private final Plan plan;
        private final Consumer<Result> completion;
        private final List<RegionTerrainSnapshot.CapturedExpansionBlock> captured = new ArrayList<>();
        private final List<TicketLease> chunkTickets = new ArrayList<>();
        private final Set<ChunkPos> ticketedChunks = new LinkedHashSet<>();
        private final UUID chunkTicketId = UUID.randomUUID();
        private final long captureCandidates;
        private final long applicationCandidates;
        private final long startedAt = System.nanoTime();
        private State state = State.PENDING;
        private State resumeState;
        private int waitingChunkX = Integer.MIN_VALUE;
        private int waitingChunkZ = Integer.MIN_VALUE;
        private int captureColumnIndex;
        private int captureCeilingIndex;
        private int captureY = Integer.MIN_VALUE;
        private int captureEndY;
        private int removeColumnIndex;
        private int removeY = Integer.MIN_VALUE;
        private int removeEndY;
        private int applyColumnIndex;
        private int applyCeilingIndex;
        private int applyY = Integer.MIN_VALUE;
        private int applyEndY;
        private int captureTicks;
        private int applicationTicks;
        private long captureProcessed;
        private long applicationProcessed;
        private long appliedBlocks;
        private long persistenceQueuedAt;
        private long applicationStartedAt;
        private long persistenceQueueNanos;
        private long serializationNanos;
        private long compressionAndWriteNanos;
        private long snapshotBytes;
        private long lastBudgetWarningNanos;

        private Job(Plan plan, Consumer<Result> completion) {
            this.plan = plan;
            this.completion = completion;
            this.captureCandidates = candidates(plan.targetBounds(), plan.captureColumns(), plan.captureCeiling());
            this.applicationCandidates = candidates(plan.oldBounds(), plan.removeColumns(), List.of())
                + candidates(plan.targetBounds(), plan.applyColumns(), plan.applyCeiling());
        }

        private long captureNanos() {
            return state == State.PENDING ? 0L : Math.max(0L, persistenceQueuedAt - startedAt);
        }

        private long applicationNanos() {
            if (applicationStartedAt == 0L || (state != State.COMPLETED && state != State.FAILED)) return 0L;
            return Math.max(0L, System.nanoTime() - applicationStartedAt);
        }

        private Result result(String failureStage, String failureDetail) {
            return new Result(plan.regionId(), plan.operationId(), plan.generation(), state,
                captureCandidates, captured.size(), applicationCandidates, appliedBlocks,
                captureNanos(), persistenceQueueNanos, serializationNanos, compressionAndWriteNanos,
                applicationNanos(), captureTicks, applicationTicks, snapshotBytes, failureStage, failureDetail);
        }
    }
}
