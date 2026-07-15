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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class RegionTerrainSnapshot {
    private static final String MUTATION_FORMAT = "mutation_snapshot_v2";
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-RegionTerrainSnapshot");

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
        Files.createDirectories(directory);

        ListTag blocks = captureMutationBlocks(level, bounds, homePos, createCeiling);
        CompoundTag root = new CompoundTag();
        root.putString("regionId", regionId);
        root.putString("dimension", bounds.getDimension());
        root.putString("format", MUTATION_FORMAT);
        root.putLong("regionVolume", bounds.volume());
        root.putInt("blockCount", blocks.size());
        root.put("blocks", blocks);

        Path target = snapshotPath(directory, regionId);
        writeAtomically(root, target);
        AllocationMetrics.add("bigbangregions_snapshot_capture_blocks_total", blocks.size());
        AllocationMetrics.add("bigbangregions_snapshot_capture_bytes_total", Files.size(target));
    }

    static void discard(String regionId, Path directory) throws IOException {
        Files.deleteIfExists(snapshotPath(directory, regionId));
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
        for (int y = bounds.getMinY(); y <= bounds.getMaxY(); y++) {
            for (int z = minZ; z <= maxZ; z++) {
                addSnapshot(level, new BlockPos(minX, y, z), blocks, seen);
                addSnapshot(level, new BlockPos(maxX, y, z), blocks, seen);
            }
            for (int x = minX; x <= maxX; x++) {
                addSnapshot(level, new BlockPos(x, y, minZ), blocks, seen);
                addSnapshot(level, new BlockPos(x, y, maxZ), blocks, seen);
            }
        }
    }

    private static void addCeiling(ServerLevel level, RegionBounds bounds, ListTag blocks, Set<Long> seen) {
        for (int x = bounds.getMinX(); x <= bounds.getMaxX(); x++) {
            for (int z = bounds.getMinZ(); z <= bounds.getMaxZ(); z++) {
                addSnapshot(level, new BlockPos(x, bounds.getMaxY(), z), blocks, seen);
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
        }
        AllocationMetrics.add("bigbangregions_snapshot_restore_blocks_total", blocks.size());
        return true;
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
            blocks.add(new SnapshotBlock(pos, NbtUtils.readBlockState(blockRegistry, entry.getCompound("state"))));
        }
        return blocks;
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

    private static void writeAtomically(CompoundTag root, Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            NbtIo.writeCompressed(root, temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path snapshotPath(Path directory, String regionId) {
        return directory.resolve(regionId + ".nbt");
    }

    private record SnapshotBlock(BlockPos pos, BlockState state) {
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
