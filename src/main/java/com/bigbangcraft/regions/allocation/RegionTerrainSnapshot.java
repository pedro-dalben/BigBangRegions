package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class RegionTerrainSnapshot {
    private static final Logger LOGGER = LoggerFactory.getLogger("BigBangRegions-RegionTerrainSnapshot");

    private RegionTerrainSnapshot() {
    }

    static void capture(ServerLevel level, RegionBounds bounds, BlockPos homePos, String regionId, Path directory) throws IOException {
        ChunkAccessGuard.assertAllowed(AllocationPhase.REGION_CREATING);
        Files.createDirectories(directory);

        CompoundTag root = new CompoundTag();
        root.putString("regionId", regionId);
        root.putString("dimension", bounds.getDimension());
        root.put("blocks", captureBlocks(level, bounds, homePos));

        NbtIo.writeCompressed(root, snapshotPath(directory, regionId));
    }

    static boolean restore(ServerLevel level, Region region, Path directory) throws IOException {
        ChunkAccessGuard.assertAllowed(AllocationPhase.REGION_CREATING);
        Path file = snapshotPath(directory, region.getId());
        if (!Files.exists(file)) {
            return false;
        }

        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        String snapshotDimension = root.getString("dimension");
        if (!snapshotDimension.isEmpty() && !snapshotDimension.equals(region.getBounds().getDimension())) {
            LOGGER.warn("Skipping restore for {} because snapshot dimension {} differs from region dimension {}", region.getId(), snapshotDimension, region.getBounds().getDimension());
            return false;
        }

        if (!areChunksLoaded(level, region.getBounds())) {
            LOGGER.warn("Skipping restore for {} because required chunks are not already loaded", region.getId());
            return false;
        }

        HolderGetter<Block> blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        ListTag blocks = root.getList("blocks", 10);
        for (int i = 0; i < blocks.size(); i++) {
            Tag tag = blocks.get(i);
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            BlockState state = NbtUtils.readBlockState(blockRegistry, entry.getCompound("state"));
            level.setBlock(pos, state, 2);
        }

        Files.deleteIfExists(file);
        return true;
    }

    private static ListTag captureBlocks(ServerLevel level, RegionBounds bounds, BlockPos homePos) {
        HolderGetter<Block> blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        ListTag blocks = new ListTag();
        LongSet seen = new LongOpenHashSet();

        addBorderShell(level, bounds, blockRegistry, blocks, seen);
        addSpawnPlatform(level, homePos, blockRegistry, blocks, seen);

        return blocks;
    }

    private static void addBorderShell(ServerLevel level, RegionBounds bounds, HolderGetter<Block> blockRegistry,
                                       ListTag blocks, LongSet seen) {
        int minX = bounds.getMinX();
        int maxX = bounds.getMaxX();
        int minZ = bounds.getMinZ();
        int maxZ = bounds.getMaxZ();
        int minY = bounds.getMinY();
        int maxY = bounds.getMaxY();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                addSnapshot(level, new BlockPos(minX, y, z), blockRegistry, blocks, seen);
                addSnapshot(level, new BlockPos(maxX, y, z), blockRegistry, blocks, seen);
            }
            for (int x = minX; x <= maxX; x++) {
                addSnapshot(level, new BlockPos(x, y, minZ), blockRegistry, blocks, seen);
                addSnapshot(level, new BlockPos(x, y, maxZ), blockRegistry, blocks, seen);
            }
        }
    }

    private static void addSpawnPlatform(ServerLevel level, BlockPos homePos, HolderGetter<Block> blockRegistry,
                                         ListTag blocks, LongSet seen) {
        int minX = homePos.getX() - 1;
        int maxX = homePos.getX() + 2;
        int minZ = homePos.getZ() - 1;
        int maxZ = homePos.getZ() + 2;
        int yFloor = homePos.getY() - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                addSnapshot(level, new BlockPos(x, yFloor, z), blockRegistry, blocks, seen);
                addSnapshot(level, new BlockPos(x, yFloor + 1, z), blockRegistry, blocks, seen);
                addSnapshot(level, new BlockPos(x, yFloor + 2, z), blockRegistry, blocks, seen);
            }
        }
    }

    private static void addSnapshot(ServerLevel level, BlockPos pos, HolderGetter<Block> blockRegistry,
                                    ListTag blocks, LongSet seen) {
        long packed = pos.asLong();
        if (!seen.add(packed)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        CompoundTag entry = new CompoundTag();
        entry.putLong("pos", packed);
        entry.put("state", NbtUtils.writeBlockState(state));
        blocks.add(entry);
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

    private static Path snapshotPath(Path directory, String regionId) {
        return directory.resolve(regionId + ".nbt");
    }
}
