package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
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

        BlockPos origin = new BlockPos(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ());
        Vec3i size = new Vec3i(
            bounds.getMaxX() - bounds.getMinX() + 1,
            bounds.getMaxY() - bounds.getMinY() + 1,
            bounds.getMaxZ() - bounds.getMinZ() + 1
        );

        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin, size, false, Blocks.STRUCTURE_VOID);

        CompoundTag root = new CompoundTag();
        root.putString("regionId", regionId);
        root.putString("dimension", bounds.getDimension());
        root.putString("format", "structure_template_v1");
        root.putInt("originX", origin.getX());
        root.putInt("originY", origin.getY());
        root.putInt("originZ", origin.getZ());
        root.put("template", template.save(new CompoundTag()));

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

        if (root.contains("template", Tag.TAG_COMPOUND)) {
            boolean restored = restoreFromStructureTemplate(level, region, root);
            if (restored) {
                Files.deleteIfExists(file);
            }
            return restored;
        }

        if (root.contains("blocks", Tag.TAG_LIST)) {
            boolean restored = restoreLegacySnapshot(level, region, root);
            if (restored) {
                Files.deleteIfExists(file);
            }
            return restored;
        }

        LOGGER.warn("Skipping restore for {} because snapshot format is not recognized", region.getId());
        return false;
    }

    private static boolean restoreFromStructureTemplate(ServerLevel level, Region region, CompoundTag root) {
        BlockPos origin = new BlockPos(root.getInt("originX"), root.getInt("originY"), root.getInt("originZ"));
        StructureTemplate template = new StructureTemplate();
        HolderGetter<Block> blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        template.load(blockRegistry, root.getCompound("template"));

        Vec3i size = template.getSize();
        RegionBounds restoreBounds = new RegionBounds(
            region.getBounds().getDimension(),
            origin.getX(),
            origin.getY(),
            origin.getZ(),
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
