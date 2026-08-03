package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.core.Vec3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class RegionTerrainSnapshot {
    private static final String MUTATION_FORMAT = "mutation_snapshot_v2";
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-RegionTerrainSnapshot");
    private static final Map<Path, Object> EXPANSION_WRITE_LOCKS = new ConcurrentHashMap<>();

    private RegionTerrainSnapshot() {
    }

    /**
     * Captures only blocks that region creation will mutate. This method must
     * run before the spawn platform and border are generated.
     */
    static void capture(
        ServerLevel level,
        RegionBounds bounds,
        BlockPos homePos,
        String regionId,
        Path directory,
        boolean createCeiling
    ) throws IOException {
        ChunkAccessGuard.assertAllowed(AllocationPhase.REGION_CREATING);
        CreationCapture capture = captureForCreation(level, bounds, homePos, regionId, directory, createCeiling);
        persistCreation(capture);
    }

    /** Captures immutable NBT on the server thread; the caller may persist it on bounded I/O. */
    static CreationCapture captureForCreation(
        ServerLevel level, RegionBounds bounds, BlockPos homePos, String regionId, Path directory, boolean createCeiling
    ) {
        ChunkAccessGuard.assertAllowed(AllocationPhase.REGION_CREATING);
        ListTag blocks = captureMutationBlocks(level, bounds, homePos, createCeiling);
        CompoundTag root = new CompoundTag();
        root.putString("regionId", regionId);
        root.putString("dimension", bounds.getDimension());
        root.putString("format", MUTATION_FORMAT);
        root.putLong("regionVolume", bounds.volume());
        root.putInt("blockCount", blocks.size());
        root.put("blocks", blocks);

        return new CreationCapture(root, snapshotPath(directory, regionId), blocks.size());
    }

    /**
     * Resumable creation capture. Every {@link CreationCaptureCursor#advance}
     * performs at most one small world read, so callers can enforce a tick budget.
     */
    static CreationCaptureCursor beginCreationCapture(
        RegionBounds bounds, BlockPos homePos, String regionId, Path directory, boolean createCeiling
    ) {
        return new CreationCaptureCursor(bounds, homePos, regionId, directory, createCeiling);
    }

    /** Does not touch a world. Safe for the allocation snapshot writer. */
    static void persistCreation(CreationCapture capture) throws IOException {
        Files.createDirectories(capture.target().getParent());
        writeAtomically(capture.root(), capture.target());
        AllocationMetrics.add("bigbangregions_snapshot_capture_blocks_total", capture.blockCount());
        AllocationMetrics.add("bigbangregions_snapshot_capture_bytes_total", Files.size(capture.target()));
    }

    static final class CreationCaptureCursor {
        private enum Stage { BORDER, CEILING, PLATFORM_HEIGHT, PLATFORM_BLOCKS, COMPLETED }

        private static final int UNSET_Y = Integer.MIN_VALUE;
        private final RegionBounds bounds;
        private final BlockPos homePos;
        private final String regionId;
        private final Path directory;
        private final boolean createCeiling;
        private final List<BlockPos> borderColumns = new ArrayList<>();
        private final ListTag blocks = new ListTag();
        private final Set<Long> seen = new HashSet<>();
        private final int[] platformSurface = new int[25];
        private Stage stage = Stage.BORDER;
        private int borderColumnIndex;
        private int borderY = UNSET_Y;
        private int ceilingX;
        private int ceilingZ;
        private int platformIndex;
        private int platformFloor = UNSET_Y;
        private int platformY = UNSET_Y;

        private CreationCaptureCursor(RegionBounds bounds, BlockPos homePos, String regionId, Path directory, boolean createCeiling) {
            this.bounds = bounds;
            this.homePos = homePos;
            this.regionId = regionId;
            this.directory = directory;
            this.createCeiling = createCeiling;
            for (int z = bounds.getMinZ(); z <= bounds.getMaxZ(); z++) {
                borderColumns.add(new BlockPos(bounds.getMinX(), 0, z));
                borderColumns.add(new BlockPos(bounds.getMaxX(), 0, z));
            }
            for (int x = bounds.getMinX(); x <= bounds.getMaxX(); x++) {
                borderColumns.add(new BlockPos(x, 0, bounds.getMinZ()));
                borderColumns.add(new BlockPos(x, 0, bounds.getMaxZ()));
            }
            ceilingX = bounds.getMinX();
            ceilingZ = bounds.getMinZ();
        }

        boolean isComplete() {
            return stage == Stage.COMPLETED;
        }

        /** Executes one bounded capture step on the server thread. */
        void advance(ServerLevel level) {
            ChunkAccessGuard.assertAllowed(AllocationPhase.REGION_CREATING);
            switch (stage) {
                case BORDER -> advanceBorder(level);
                case CEILING -> advanceCeiling(level);
                case PLATFORM_HEIGHT -> advancePlatformHeight(level);
                case PLATFORM_BLOCKS -> advancePlatformBlocks(level);
                case COMPLETED -> { }
            }
        }

        CreationCapture finish() {
            if (!isComplete()) throw new IllegalStateException("Creation capture is incomplete");
            CompoundTag root = new CompoundTag();
            root.putString("regionId", regionId);
            root.putString("dimension", bounds.getDimension());
            root.putString("format", MUTATION_FORMAT);
            root.putLong("regionVolume", bounds.volume());
            root.putInt("blockCount", blocks.size());
            root.put("blocks", blocks);
            return new CreationCapture(root, snapshotPath(directory, regionId), blocks.size());
        }

        private void advanceBorder(ServerLevel level) {
            if (borderColumnIndex >= borderColumns.size()) {
                stage = createCeiling ? Stage.CEILING : Stage.PLATFORM_HEIGHT;
                return;
            }
            BlockPos column = borderColumns.get(borderColumnIndex);
            int endY = Math.min(bounds.getMaxY(), level.getMaxBuildHeight() - 1);
            if (borderY == UNSET_Y) {
                borderY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                return;
            }
            if (borderY > bounds.getMinY()
                && TerrainAllocationCoordinator.isReplaceableBorderBlock(level, new BlockPos(column.getX(), borderY - 1, column.getZ()))) {
                borderY--;
                return;
            }
            if (borderY >= bounds.getMinY() && borderY <= endY) {
                captureIfReplaceable(level, new BlockPos(column.getX(), borderY++, column.getZ()));
                return;
            }
            borderColumnIndex++;
            borderY = UNSET_Y;
        }

        private void advanceCeiling(ServerLevel level) {
            if (ceilingX > bounds.getMaxX()) {
                stage = Stage.PLATFORM_HEIGHT;
                return;
            }
            int y = Math.min(bounds.getMaxY(), level.getMaxBuildHeight() - 1);
            captureIfReplaceable(level, new BlockPos(ceilingX, y, ceilingZ));
            if (++ceilingZ > bounds.getMaxZ()) {
                ceilingZ = bounds.getMinZ();
                ceilingX++;
            }
        }

        private void advancePlatformHeight(ServerLevel level) {
            if (platformIndex < platformSurface.length) {
                int x = homePos.getX() - 2 + platformIndex / 5;
                int z = homePos.getZ() - 2 + platformIndex % 5;
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                platformSurface[platformIndex++] = surface;
                platformFloor = Math.max(platformFloor == UNSET_Y ? homePos.getY() - 1 : platformFloor, surface - 1);
                return;
            }
            stage = Stage.PLATFORM_BLOCKS;
            platformIndex = 0;
            platformY = platformSurface[0] - 1;
        }

        private void advancePlatformBlocks(ServerLevel level) {
            if (platformIndex >= platformSurface.length) {
                stage = Stage.COMPLETED;
                return;
            }
            int x = homePos.getX() - 2 + platformIndex / 5;
            int z = homePos.getZ() - 2 + platformIndex % 5;
            capture(level, new BlockPos(x, platformY++, z));
            if (platformY > platformFloor + 2) {
                platformIndex++;
                if (platformIndex < platformSurface.length) platformY = platformSurface[platformIndex] - 1;
            }
        }

        private void captureIfReplaceable(ServerLevel level, BlockPos pos) {
            if (TerrainAllocationCoordinator.isReplaceableBorderBlock(level, pos)) capture(level, pos);
        }

        private void capture(ServerLevel level, BlockPos pos) {
            addSnapshot(level, pos, blocks, seen);
        }
    }

    /** Bounded counterpart of the spawn-platform mutation captured above. */
    static SpawnPlatformCursor beginSpawnPlatform(BlockPos homePos) {
        return new SpawnPlatformCursor(homePos);
    }

    static final class SpawnPlatformCursor {
        private enum Stage { HEIGHTS, BLOCKS, GLOWSTONE, COMPLETED, FAILED }

        private final BlockPos homePos;
        private final int[] surfaces = new int[25];
        private Stage stage = Stage.HEIGHTS;
        private int index;
        private int floor;
        private int y;
        private String failure;

        private SpawnPlatformCursor(BlockPos homePos) {
            this.homePos = homePos;
            this.floor = homePos.getY() - 1;
        }

        boolean isComplete() { return stage == Stage.COMPLETED; }
        boolean isFailed() { return stage == Stage.FAILED; }
        String failure() { return failure; }
        SpawnPlatformResult result() {
            return isComplete()
                ? SpawnPlatformResult.success(new BlockPos(homePos.getX(), floor + 1, homePos.getZ()))
                : SpawnPlatformResult.failure(homePos, failure == null ? "Platform generation incomplete" : failure);
        }

        /** Performs exactly one height read or block write. */
        void advance(ServerLevel level) {
            try {
                if (stage == Stage.HEIGHTS) {
                    if (index == surfaces.length) {
                        stage = Stage.BLOCKS;
                        index = 0;
                        y = surfaces[0] - 1;
                        return;
                    }
                    int x = homePos.getX() - 2 + index / 5;
                    int z = homePos.getZ() - 2 + index % 5;
                    int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    surfaces[index++] = surface;
                    floor = Math.max(floor, surface - 1);
                    return;
                }
                if (stage == Stage.BLOCKS) {
                    if (index == surfaces.length) {
                        stage = Stage.GLOWSTONE;
                        return;
                    }
                    int x = homePos.getX() - 2 + index / 5;
                    int z = homePos.getZ() - 2 + index % 5;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (y <= floor) {
                        level.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                    } else {
                        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    }
                    if (++y > floor + 2) {
                        index++;
                        if (index < surfaces.length) y = surfaces[index] - 1;
                    }
                    return;
                }
                if (stage == Stage.GLOWSTONE) {
                    level.setBlock(new BlockPos(homePos.getX(), floor, homePos.getZ()),
                        net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState(), 2);
                    stage = Stage.COMPLETED;
                }
            } catch (RuntimeException error) {
                stage = Stage.FAILED;
                failure = error.getMessage();
            }
        }
    }

    static void discard(String regionId, Path directory) throws IOException {
        Files.deleteIfExists(snapshotPath(directory, regionId));
    }

    /**
     * Captures one world block into data that has no live world/chunk/block-entity
     * reference. This is intentionally called only from the server-thread job.
     */
    static CapturedExpansionBlock captureExpansionBlock(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag blockEntityData = blockEntity == null
            ? null : blockEntity.saveWithFullMetadata(level.registryAccess()).copy();
        return new CapturedExpansionBlock(pos.asLong(), stateData(state), blockEntityData);
    }

    /**
     * Runs on the bounded snapshot I/O executor. It receives only copied DTO
     * data, merges it into the durable restoration snapshot and never touches a
     * Minecraft world.
     */
    static PersistenceResult persistExpansion(ExpansionCapture capture) throws IOException {
        Path target = capture.target();
        Object lock = EXPANSION_WRITE_LOCKS.computeIfAbsent(target.toAbsolutePath().normalize(), ignored -> new Object());
        synchronized (lock) {
            long startedAt = System.nanoTime();
            Files.createDirectories(target.getParent());
            CompoundTag root = Files.exists(target)
                ? NbtIo.readCompressed(target, NbtAccounter.unlimitedHeap())
                : new CompoundTag();
            long persistedGeneration = root.contains("expansionGeneration", Tag.TAG_LONG)
                ? root.getLong("expansionGeneration") : 0L;
            if (persistedGeneration > capture.generation()) {
                return PersistenceResult.discarded(System.nanoTime() - startedAt);
            }

            ListTag blocks = root.contains("blocks", Tag.TAG_LIST)
                ? root.getList("blocks", Tag.TAG_COMPOUND) : new ListTag();
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < blocks.size(); i++) {
                Tag tag = blocks.get(i);
                if (tag instanceof CompoundTag entry && entry.contains("pos", Tag.TAG_LONG)) {
                    seen.add(entry.getLong("pos"));
                }
            }

            long serializationStartedAt = System.nanoTime();
            for (CapturedExpansionBlock block : capture.blocks()) {
                if (!seen.add(block.pos())) continue;
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", block.pos());
                entry.put("state", block.state().toNbt());
                if (block.blockEntityData() != null) {
                    entry.put("blockEntity", block.blockEntityData().copy());
                }
                blocks.add(entry);
            }
            root.putString("regionId", capture.regionId());
            root.putString("dimension", capture.dimension());
            root.putString("format", MUTATION_FORMAT);
            root.putLong("regionVolume", capture.regionVolume());
            root.putLong("expansionGeneration", capture.generation());
            root.putInt("blockCount", blocks.size());
            root.put("blocks", blocks);
            long serializationNanos = System.nanoTime() - serializationStartedAt;

            long writeStartedAt = System.nanoTime();
            writeAtomically(root, target);
            long writeNanos = System.nanoTime() - writeStartedAt;
            return new PersistenceResult(false, blocks.size(), Files.size(target), serializationNanos,
                writeNanos, System.nanoTime() - startedAt);
        }
    }

    static void recoverIncompleteFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.contains(".tmp-")) {
                    Files.deleteIfExists(file);
                    continue;
                }
                int backup = name.indexOf(".bak-");
                if (backup < 0) continue;
                Path target = file.resolveSibling(name.substring(0, backup));
                if (Files.exists(target)) Files.deleteIfExists(file);
                else Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static boolean restore(ServerLevel level, Region region, Path directory) throws IOException {
        ChunkAccessGuard.assertAllowed(AllocationPhase.REGION_CREATING);
        long startedAt = System.nanoTime();
        try {
            Path file = snapshotPath(directory, region.getId());
            if (!Files.exists(file)) {
                return false;
            }

            long fileSize = Files.size(file);
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            String snapshotDimension = root.getString("dimension");
            if (!snapshotDimension.isEmpty() && !snapshotDimension.equals(region.getBounds().getDimension())) {
                LOGGER.warn("Skipping restore for {} because snapshot dimension {} differs from region dimension {}",
                    region.getId(), snapshotDimension, region.getBounds().getDimension());
                return false;
            }

            boolean restored;
            if (root.contains("template", Tag.TAG_COMPOUND)) {
                restored = restoreFromStructureTemplate(level, region, root);
            } else if (MUTATION_FORMAT.equals(root.getString("format"))) {
                restored = restoreMutationSnapshot(level, region, root);
            } else if (root.contains("blocks", Tag.TAG_LIST)) {
                // Compatibility with snapshots created before mutation_snapshot_v2.
                restored = restoreLegacySnapshot(level, region, root);
            } else {
                LOGGER.warn("Skipping restore for {} because snapshot format is not recognized", region.getId());
                return false;
            }

            if (restored) {
                Files.deleteIfExists(file);
                AllocationMetrics.add("bigbangregions_snapshot_restore_bytes_total", fileSize);
            }
            return restored;
        } finally {
            AllocationMetrics.add("bigbangregions_snapshot_restore_nanos_total", System.nanoTime() - startedAt);
        }
    }

    /** Reads immutable snapshot data off-thread; block registry/world access stays in {@link RestoreCursor}. */
    static RestoreData readMutationSnapshot(Region region, Path directory) throws IOException {
        Path file = snapshotPath(directory, region.getId());
        if (!Files.exists(file)) return RestoreData.missing();

        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        String snapshotDimension = root.getString("dimension");
        if (!snapshotDimension.isEmpty() && !snapshotDimension.equals(region.getBounds().getDimension())) {
            throw new IOException("Snapshot dimension differs from region dimension");
        }
        if (!MUTATION_FORMAT.equals(root.getString("format"))) {
            throw new IOException("Snapshot is not in resumable mutation format");
        }

        List<SerializedSnapshotBlock> blocks = new ArrayList<>();
        Set<Long> chunks = new LinkedHashSet<>();
        ListTag tags = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) {
            Tag tag = tags.get(i);
            if (!(tag instanceof CompoundTag entry) || !entry.contains("state", Tag.TAG_COMPOUND)) {
                throw new IOException("Snapshot contains an invalid block entry");
            }
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            if (!region.getBounds().contains(region.getBounds().getDimension(), pos.getX(), pos.getY(), pos.getZ())) {
                throw new IOException("Snapshot block lies outside region bounds");
            }
            blocks.add(new SerializedSnapshotBlock(pos.asLong(), entry.getCompound("state").copy(),
                entry.contains("blockEntity", Tag.TAG_COMPOUND) ? entry.getCompound("blockEntity").copy() : null));
            chunks.add(ChunkKey.pack(pos.getX() >> 4, pos.getZ() >> 4));
        }
        return new RestoreData(file, Files.size(file), List.copyOf(blocks), List.copyOf(chunks));
    }

    private static ListTag captureMutationBlocks(
        ServerLevel level,
        RegionBounds bounds,
        BlockPos homePos,
        boolean createCeiling
    ) {
        ListTag blocks = new ListTag();
        Set<Long> seen = new HashSet<>();

        addBorderShell(level, bounds, blocks, seen);
        if (createCeiling) {
            addCeiling(level, bounds, blocks, seen);
        }
        addSpawnPlatform(level, homePos, blocks, seen);
        return blocks;
    }

    private static void addBorderShell(ServerLevel level, RegionBounds bounds, ListTag blocks, Set<Long> seen) {
        int minX = bounds.getMinX();
        int maxX = bounds.getMaxX();
        int minZ = bounds.getMinZ();
        int maxZ = bounds.getMaxZ();
        for (int z = minZ; z <= maxZ; z++) {
            addSurfaceBorderSnapshot(level, bounds, minX, z, blocks, seen);
            addSurfaceBorderSnapshot(level, bounds, maxX, z, blocks, seen);
        }
        for (int x = minX; x <= maxX; x++) {
            addSurfaceBorderSnapshot(level, bounds, x, minZ, blocks, seen);
            addSurfaceBorderSnapshot(level, bounds, x, maxZ, blocks, seen);
        }
    }

    private static void addSurfaceBorderSnapshot(ServerLevel level, RegionBounds bounds, int x, int z,
                                                 ListTag blocks, Set<Long> seen) {
        int startY = TerrainAllocationCoordinator.borderStartY(level, bounds, x, z);
        int endY = Math.min(bounds.getMaxY(), level.getMaxBuildHeight() - 1);
        if (startY < bounds.getMinY() || startY > endY) {
            return;
        }

        for (int y = startY; y <= endY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (TerrainAllocationCoordinator.isReplaceableBorderBlock(level, pos)) {
                addSnapshot(level, pos, blocks, seen);
            }
        }
    }

    private static void addCeiling(ServerLevel level, RegionBounds bounds, ListTag blocks, Set<Long> seen) {
        int ceilingY = Math.min(bounds.getMaxY(), level.getMaxBuildHeight() - 1);
        for (int x = bounds.getMinX(); x <= bounds.getMaxX(); x++) {
            for (int z = bounds.getMinZ(); z <= bounds.getMaxZ(); z++) {
                BlockPos pos = new BlockPos(x, ceilingY, z);
                if (TerrainAllocationCoordinator.isReplaceableBorderBlock(level, pos)) {
                    addSnapshot(level, pos, blocks, seen);
                }
            }
        }
    }

    private static void addSpawnPlatform(ServerLevel level, BlockPos homePos, ListTag blocks, Set<Long> seen) {
        int minX = homePos.getX() - 2;
        int maxX = homePos.getX() + 2;
        int minZ = homePos.getZ() - 2;
        int maxZ = homePos.getZ() + 2;
        int yFloor = homePos.getY() - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                yFloor = Math.max(yFloor, surface - 1);
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = surface - 1; y <= yFloor + 2; y++) {
                    addSnapshot(level, new BlockPos(x, y, z), blocks, seen);
                }
            }
        }
    }

    private static void addSnapshot(ServerLevel level, BlockPos pos, ListTag blocks, Set<Long> seen) {
        if (!seen.add(pos.asLong())) {
            return;
        }

        CompoundTag entry = new CompoundTag();
        entry.putLong("pos", pos.asLong());
        entry.put("state", NbtUtils.writeBlockState(level.getBlockState(pos)));
        blocks.add(entry);
    }

    private static boolean restoreMutationSnapshot(ServerLevel level, Region region, CompoundTag root) {
        List<SnapshotBlock> blocks = decodeBlocks(level, region.getBounds(), root.getList("blocks", Tag.TAG_COMPOUND));
        if (blocks == null || blocks.isEmpty()) {
            LOGGER.warn("Skipping restore for {} because mutation snapshot has no valid blocks", region.getId());
            return false;
        }
        if (!areSnapshotChunksLoaded(level, blocks)) {
            LOGGER.warn("Skipping restore for {} because required mutation chunks are not already loaded", region.getId());
            return false;
        }

        for (SnapshotBlock block : blocks) {
            level.setBlock(block.pos(), block.state(), 2);
            restoreBlockEntity(level, block.pos(), block.blockEntityData());
        }
        AllocationMetrics.add("bigbangregions_snapshot_restore_blocks_total", blocks.size());
        return true;
    }

    /** Server-thread cursor with a preflight phase so a missing chunk cannot cause a partial restore. */
    static final class RestoreCursor {
        private enum Stage { PREFLIGHT, APPLYING, COMPLETED, FAILED }

        private final RestoreData data;
        private final HolderGetter<net.minecraft.world.level.block.Block> blockRegistry;
        private int chunkIndex;
        private int blockIndex;
        private Stage stage = Stage.PREFLIGHT;
        private String failure;

        RestoreCursor(ServerLevel level, RestoreData data) {
            this.data = data;
            this.blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        }

        boolean isComplete() { return stage == Stage.COMPLETED; }
        boolean isFailed() { return stage == Stage.FAILED; }
        String failure() { return failure; }
        int totalBlocks() { return data.blocks().size(); }
        int restoredBlocks() { return blockIndex; }
        long fileBytes() { return data.fileBytes(); }

        /** Executes one small read/check/set operation on the server thread. */
        void advance(ServerLevel level) {
            if (stage == Stage.PREFLIGHT) {
                if (chunkIndex >= data.chunks().size()) {
                    stage = Stage.APPLYING;
                    return;
                }
                long packed = data.chunks().get(chunkIndex++);
                if (level.getChunkSource().getChunkNow(ChunkKey.x(packed), ChunkKey.z(packed)) == null) {
                    fail("Required snapshot chunk is not loaded");
                }
                return;
            }
            if (stage != Stage.APPLYING) return;
            if (blockIndex >= data.blocks().size()) {
                stage = Stage.COMPLETED;
                return;
            }
            try {
                SerializedSnapshotBlock block = data.blocks().get(blockIndex++);
                BlockPos pos = BlockPos.of(block.pos());
                level.setBlock(pos, NbtUtils.readBlockState(blockRegistry, block.state()), 2);
                restoreBlockEntity(level, pos, block.blockEntityData());
            } catch (RuntimeException error) {
                fail(error.getMessage());
            }
        }

        private void fail(String detail) {
            stage = Stage.FAILED;
            failure = detail == null ? "Unknown restore error" : detail;
        }
    }

    private static List<SnapshotBlock> decodeBlocks(ServerLevel level, RegionBounds bounds, ListTag tags) {
        HolderGetter<net.minecraft.world.level.block.Block> blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        List<SnapshotBlock> blocks = new ArrayList<>(tags.size());
        for (int i = 0; i < tags.size(); i++) {
            Tag tag = tags.get(i);
            if (!(tag instanceof CompoundTag entry) || !entry.contains("state", Tag.TAG_COMPOUND)) {
                return null;
            }
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            if (!bounds.contains(bounds.getDimension(), pos.getX(), pos.getY(), pos.getZ())) {
                LOGGER.warn("Skipping snapshot because block {} lies outside region bounds {}", pos, bounds);
                return null;
            }
            CompoundTag blockEntityData = entry.contains("blockEntity", Tag.TAG_COMPOUND)
                ? entry.getCompound("blockEntity").copy() : null;
            blocks.add(new SnapshotBlock(pos, NbtUtils.readBlockState(blockRegistry, entry.getCompound("state")), blockEntityData));
        }
        return blocks;
    }

    private static void restoreBlockEntity(ServerLevel level, BlockPos pos, CompoundTag data) {
        if (data == null) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return;
        blockEntity.loadWithComponents(data.copy(), level.registryAccess());
        blockEntity.setChanged();
    }

    private static boolean areSnapshotChunksLoaded(ServerLevel level, List<SnapshotBlock> blocks) {
        Set<Long> chunkPositions = new HashSet<>();
        for (SnapshotBlock block : blocks) {
            chunkPositions.add(ChunkKey.pack(block.pos().getX() >> 4, block.pos().getZ() >> 4));
        }
        for (long packed : chunkPositions) {
            int chunkX = ChunkKey.x(packed);
            int chunkZ = ChunkKey.z(packed);
            if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                return false;
            }
        }
        return true;
    }

    private static boolean restoreFromStructureTemplate(ServerLevel level, Region region, CompoundTag root) {
        BlockPos origin = new BlockPos(root.getInt("originX"), root.getInt("originY"), root.getInt("originZ"));
        StructureTemplate template = new StructureTemplate();
        HolderGetter<net.minecraft.world.level.block.Block> blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        template.load(blockRegistry, root.getCompound("template"));

        Vec3i size = template.getSize();
        RegionBounds restoreBounds = new RegionBounds(
            region.getBounds().getDimension(),
            origin.getX(), origin.getY(), origin.getZ(),
            origin.getX() + size.getX() - 1,
            origin.getY() + size.getY() - 1,
            origin.getZ() + size.getZ() - 1
        );
        if (!areChunksLoaded(level, restoreBounds)) {
            LOGGER.warn("Skipping restore for {} because required chunks are not already loaded", region.getId());
            return false;
        }

        boolean restored = template.placeInWorld(
            level,
            origin,
            origin,
            new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true),
            level.getRandom(),
            2
        );
        if (!restored) {
            LOGGER.warn("Failed to restore structure template for region {}", region.getId());
        }
        return restored;
    }

    private static boolean restoreLegacySnapshot(ServerLevel level, Region region, CompoundTag root) {
        if (!areChunksLoaded(level, region.getBounds())) {
            LOGGER.warn("Skipping restore for {} because required chunks are not already loaded", region.getId());
            return false;
        }

        HolderGetter<net.minecraft.world.level.block.Block> blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            Tag tag = blocks.get(i);
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            BlockState state = NbtUtils.readBlockState(blockRegistry, entry.getCompound("state"));
            level.setBlock(pos, state, 2);
        }
        return true;
    }

    private static boolean areChunksLoaded(ServerLevel level, RegionBounds bounds) {
        int minChunkX = bounds.getMinX() >> 4;
        int maxChunkX = bounds.getMaxX() >> 4;
        int minChunkZ = bounds.getMinZ() >> 4;
        int maxChunkZ = bounds.getMaxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    static void writeAtomically(CompoundTag root, Path target) throws IOException {
        writeAtomically(root, target, NbtIo::writeCompressed, RegionTerrainSnapshot::move);
    }

    static void writeAtomically(CompoundTag root, Path target, SnapshotWriter writer, SnapshotMover mover) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        Path backup = null;
        try {
            writer.write(root, temporary);
            try {
                mover.move(temporary, target, true);
            } catch (AtomicMoveNotSupportedException ignored) {
                backup = target.resolveSibling(target.getFileName() + ".bak-" + UUID.randomUUID());
                if (Files.exists(target)) mover.move(target, backup, false);
                try {
                    mover.move(temporary, target, false);
                    Files.deleteIfExists(backup);
                } catch (IOException failure) {
                    if (Files.exists(backup) && !Files.exists(target)) mover.move(backup, target, false);
                    throw failure;
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void move(Path source, Path target, boolean atomic) throws IOException {
        if (atomic) {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path snapshotPath(Path directory, String regionId) {
        return directory.resolve(regionId + ".nbt");
    }

    private static StateData stateData(BlockState state) {
        String id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        Map<String, String> properties = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            addProperty(state, property, properties);
        }
        return new StateData(id, properties);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addProperty(BlockState state, Property property, Map<String, String> properties) {
        properties.put(property.getName(), property.getName((Comparable) state.getValue(property)));
    }

    record ExpansionCapture(String regionId, String dimension, long generation, long regionVolume,
                            Path target, List<CapturedExpansionBlock> blocks) {
        ExpansionCapture {
            blocks = List.copyOf(blocks);
        }
    }

    record CreationCapture(CompoundTag root, Path target, int blockCount) {
        CreationCapture {
            root = root.copy();
        }
    }

    record RestoreData(Path file, long fileBytes, List<SerializedSnapshotBlock> blocks, List<Long> chunks) {
        static RestoreData missing() { return new RestoreData(null, 0L, List.of(), List.of()); }
        boolean exists() { return file != null; }
        int blockCount() { return blocks.size(); }
    }

    private record SerializedSnapshotBlock(long pos, CompoundTag state, CompoundTag blockEntityData) {
        private SerializedSnapshotBlock {
            state = state.copy();
            blockEntityData = blockEntityData == null ? null : blockEntityData.copy();
        }
    }

    record CapturedExpansionBlock(long pos, StateData state, CompoundTag blockEntityData) {
        CapturedExpansionBlock {
            blockEntityData = blockEntityData == null ? null : blockEntityData.copy();
        }
    }

    record StateData(String blockId, Map<String, String> properties) {
        StateData {
            properties = Map.copyOf(properties);
        }

        CompoundTag toNbt() {
            CompoundTag state = new CompoundTag();
            state.putString("Name", blockId);
            if (!properties.isEmpty()) {
                CompoundTag values = new CompoundTag();
                properties.forEach(values::putString);
                state.put("Properties", values);
            }
            return state;
        }
    }

    record PersistenceResult(boolean discarded, int blockCount, long compressedBytes,
                             long serializationNanos, long compressionAndWriteNanos, long totalNanos) {
        static PersistenceResult discarded(long totalNanos) {
            return new PersistenceResult(true, 0, 0, 0, 0, totalNanos);
        }
    }

    @FunctionalInterface
    interface SnapshotWriter {
        void write(CompoundTag root, Path target) throws IOException;
    }

    @FunctionalInterface
    interface SnapshotMover {
        void move(Path source, Path target, boolean atomic) throws IOException;
    }

    private record SnapshotBlock(BlockPos pos, BlockState state, CompoundTag blockEntityData) {
    }

    private static final class ChunkKey {
        private ChunkKey() {
        }

        private static long pack(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }

        private static int x(long packed) {
            return (int) (packed >> 32);
        }

        private static int z(long packed) {
            return (int) packed;
        }
    }
}
