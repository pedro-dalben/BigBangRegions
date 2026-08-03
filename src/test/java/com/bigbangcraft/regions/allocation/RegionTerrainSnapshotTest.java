package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RegionTerrainSnapshotTest {
    @BeforeAll
    static void bootMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void capturesOnlyCreationMutationFootprint(@TempDir Path tempDir) throws Exception {
        ServerLevel level = mockLevel();
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 4, 70, 4);

        RegionTerrainSnapshot.capture(level, bounds, new net.minecraft.core.BlockPos(2, 65, 2), "region-1", tempDir, false);

        CompoundTag root = NbtIo.readCompressed(tempDir.resolve("region-1.nbt"), NbtAccounter.unlimitedHeap());
        assertEquals("mutation_snapshot_v2", root.getString("format"));
        assertTrue(root.contains("blocks", Tag.TAG_LIST));
        assertEquals(root.getInt("blockCount"), root.getList("blocks", Tag.TAG_COMPOUND).size());
        assertTrue(root.getInt("blockCount") < bounds.volume(), "Mutation snapshot must be smaller than full region volume");
        try (var files = Files.list(tempDir)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().contains(".tmp-")));
        }
    }

    @Test
    void capturesAirBlocksAlongVerticalSurfaceBorder(@TempDir Path tempDir) throws Exception {
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 10, 70, 10);
        Map<Long, net.minecraft.world.level.block.state.BlockState> states = new HashMap<>();
        for (int x = bounds.getMinX(); x <= bounds.getMaxX(); x++) {
            for (int z = bounds.getMinZ(); z <= bounds.getMaxZ(); z++) {
                if (x == bounds.getMinX() || x == bounds.getMaxX() || z == bounds.getMinZ() || z == bounds.getMaxZ()) {
                    for (int y = 64; y <= 70; y++) {
                        states.put(new net.minecraft.core.BlockPos(x, y, z).asLong(), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        net.minecraft.core.BlockPos solidSurface = new net.minecraft.core.BlockPos(0, 64, 0);
        states.put(solidSurface.asLong(), Blocks.DIRT.defaultBlockState());

        RegionTerrainSnapshot.capture(levelWithStates(states), bounds,
            new net.minecraft.core.BlockPos(5, 65, 5), "region-surface", tempDir, false);

        CompoundTag root = NbtIo.readCompressed(tempDir.resolve("region-surface.nbt"), NbtAccounter.unlimitedHeap());
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        assertTrue(blocks.stream().map(tag -> net.minecraft.core.BlockPos.of(((CompoundTag) tag).getLong("pos")))
            .anyMatch(pos -> pos.equals(new net.minecraft.core.BlockPos(0, 64, 1))));
        assertTrue(blocks.stream().map(tag -> net.minecraft.core.BlockPos.of(((CompoundTag) tag).getLong("pos")))
            .anyMatch(pos -> pos.getY() == 70));
        assertFalse(blocks.stream().map(tag -> net.minecraft.core.BlockPos.of(((CompoundTag) tag).getLong("pos")))
            .anyMatch(solidSurface::equals));
    }

    @Test
    void restoresNonFullBorderBlocksReplacedByTheWall(@TempDir Path tempDir) throws Exception {
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 4, 70, 4);
        Map<Long, BlockState> states = new HashMap<>();
        for (int x = bounds.getMinX(); x <= bounds.getMaxX(); x++) {
            for (int z = bounds.getMinZ(); z <= bounds.getMaxZ(); z++) {
                if (x == bounds.getMinX() || x == bounds.getMaxX() || z == bounds.getMinZ() || z == bounds.getMaxZ()) {
                    for (int y = 64; y <= 70; y++) {
                        states.put(new net.minecraft.core.BlockPos(x, y, z).asLong(), Blocks.AIR.defaultBlockState());
                    }
                    for (int y = 60; y <= 63; y++) {
                        states.put(new net.minecraft.core.BlockPos(x, y, z).asLong(), Blocks.WATER.defaultBlockState());
                    }
                }
            }
        }
        net.minecraft.core.BlockPos flower = new net.minecraft.core.BlockPos(0, 63, 1);
        net.minecraft.core.BlockPos water = new net.minecraft.core.BlockPos(0, 60, 2);
        states.put(flower.asLong(), Blocks.DANDELION.defaultBlockState());
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(chunkSource.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        ServerLevel level = mockLevel(chunkSource, states);

        RegionTerrainSnapshot.capture(level, bounds,
            new net.minecraft.core.BlockPos(2, 65, 2), "region-non-full", tempDir, false);

        CompoundTag root = NbtIo.readCompressed(tempDir.resolve("region-non-full.nbt"), NbtAccounter.unlimitedHeap());
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        assertTrue(blocks.stream().map(tag -> net.minecraft.core.BlockPos.of(((CompoundTag) tag).getLong("pos")))
            .anyMatch(flower::equals));
        assertTrue(blocks.stream().map(tag -> net.minecraft.core.BlockPos.of(((CompoundTag) tag).getLong("pos")))
            .anyMatch(water::equals));

        states.put(flower.asLong(), Blocks.GLASS.defaultBlockState());
        states.put(water.asLong(), Blocks.GLASS.defaultBlockState());
        Region region = new Region(
            "region-non-full", "Player Region", RegionType.PLAYER_REGION, bounds, 100,
            UUID.randomUUID(), UUID.randomUUID(), 0L, 0L, "ACTIVE"
        );
        assertTrue(RegionTerrainSnapshot.restore(level, region, tempDir));
        assertEquals(Blocks.DANDELION.defaultBlockState(), states.get(flower.asLong()));
        assertEquals(Blocks.WATER.defaultBlockState(), states.get(water.asLong()));
    }

    @Test
    void restoresMutationSnapshotOnlyWhenAffectedChunksAreLoaded(@TempDir Path tempDir) throws Exception {
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(chunkSource.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        Map<Long, net.minecraft.world.level.block.state.BlockState> states = new HashMap<>();
        ServerLevel level = mockLevel(chunkSource, states);
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 4, 70, 4);
        Region region = new Region(
            "region-2", "Player Region", RegionType.PLAYER_REGION, bounds, 100,
            UUID.randomUUID(), UUID.randomUUID(), 0L, 0L, "ACTIVE"
        );

        RegionTerrainSnapshot.capture(level, bounds, new net.minecraft.core.BlockPos(2, 65, 2), "region-2", tempDir, true);
        CompoundTag root = NbtIo.readCompressed(tempDir.resolve("region-2.nbt"), NbtAccounter.unlimitedHeap());
        for (int i = 0; i < root.getList("blocks", Tag.TAG_COMPOUND).size(); i++) {
            CompoundTag entry = root.getList("blocks", Tag.TAG_COMPOUND).getCompound(i);
            states.put(entry.getLong("pos"), Blocks.GLASS.defaultBlockState());
        }

        assertTrue(RegionTerrainSnapshot.restore(level, region, tempDir));
        assertFalse(Files.exists(tempDir.resolve("region-2.nbt")));
        verify(level, atLeastOnce()).setBlock(any(net.minecraft.core.BlockPos.class), any(), anyInt());
        assertTrue(states.values().stream().allMatch(state -> state.equals(Blocks.DIRT.defaultBlockState())));
    }

    @Test
    void keepsSnapshotWhenRequiredChunkIsNotLoaded(@TempDir Path tempDir) throws Exception {
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(chunkSource.getChunkNow(anyInt(), anyInt())).thenReturn(null);
        ServerLevel level = mockLevel(chunkSource);
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 4, 70, 4);
        Region region = new Region(
            "region-3", "Player Region", RegionType.PLAYER_REGION, bounds, 100,
            UUID.randomUUID(), UUID.randomUUID(), 0L, 0L, "ACTIVE"
        );

        RegionTerrainSnapshot.capture(level, bounds, new net.minecraft.core.BlockPos(2, 65, 2), "region-3", tempDir, false);

        assertFalse(RegionTerrainSnapshot.restore(level, region, tempDir));
        assertTrue(Files.exists(tempDir.resolve("region-3.nbt")), "Failed restore must keep the snapshot for retry");
    }

    @Test
    void restoresMutationSnapshotIncrementallyOnlyAfterChunkPreflight(@TempDir Path tempDir) throws Exception {
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(chunkSource.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        Map<Long, BlockState> states = new HashMap<>();
        ServerLevel level = mockLevel(chunkSource, states);
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 4, 70, 4);
        Region region = new Region("region-cursor", "Player Region", RegionType.PLAYER_REGION, bounds, 100,
            UUID.randomUUID(), UUID.randomUUID(), 0L, 0L, "ACTIVE");

        RegionTerrainSnapshot.capture(level, bounds, new net.minecraft.core.BlockPos(2, 65, 2), "region-cursor", tempDir, false);
        RegionTerrainSnapshot.RestoreData data = RegionTerrainSnapshot.readMutationSnapshot(region, tempDir);
        RegionTerrainSnapshot.RestoreCursor cursor = new RegionTerrainSnapshot.RestoreCursor(level, data);
        for (int step = 0; step < 10_000 && !cursor.isComplete() && !cursor.isFailed(); step++) cursor.advance(level);

        assertTrue(cursor.isComplete());
        assertFalse(cursor.isFailed());
        assertEquals(data.blockCount(), cursor.restoredBlocks());
        verify(level, atLeastOnce()).setBlock(any(net.minecraft.core.BlockPos.class), any(), anyInt());
    }

    @Test
    void incrementalPreflightFailsBeforeChangingAnyBlock(@TempDir Path tempDir) throws Exception {
        ServerChunkCache loadedChunks = mock(ServerChunkCache.class);
        when(loadedChunks.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 4, 70, 4);
        Region region = new Region("region-preflight", "Player Region", RegionType.PLAYER_REGION, bounds, 100,
            UUID.randomUUID(), UUID.randomUUID(), 0L, 0L, "ACTIVE");
        RegionTerrainSnapshot.capture(mockLevel(loadedChunks), bounds, new net.minecraft.core.BlockPos(2, 65, 2),
            "region-preflight", tempDir, false);

        ServerChunkCache missingChunks = mock(ServerChunkCache.class);
        when(missingChunks.getChunkNow(anyInt(), anyInt())).thenReturn(null);
        ServerLevel restoreLevel = mockLevel(missingChunks, new HashMap<>());
        RegionTerrainSnapshot.RestoreCursor cursor = new RegionTerrainSnapshot.RestoreCursor(restoreLevel,
            RegionTerrainSnapshot.readMutationSnapshot(region, tempDir));
        cursor.advance(restoreLevel);

        assertTrue(cursor.isFailed());
        verify(restoreLevel, org.mockito.Mockito.never()).setBlock(any(net.minecraft.core.BlockPos.class), any(), anyInt());
    }

    @Test
    void keepsLegacyBlockSnapshotsReadable(@TempDir Path tempDir) throws Exception {
        ServerChunkCache chunkSource = mock(ServerChunkCache.class);
        when(chunkSource.getChunkNow(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        ServerLevel level = mockLevel(chunkSource);
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 4, 70, 4);
        Region region = new Region(
            "region-4", "Player Region", RegionType.PLAYER_REGION, bounds, 100,
            UUID.randomUUID(), UUID.randomUUID(), 0L, 0L, "ACTIVE"
        );
        ListTag blocks = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putLong("pos", new net.minecraft.core.BlockPos(2, 64, 2).asLong());
        entry.put("state", NbtUtils.writeBlockState(Blocks.DIRT.defaultBlockState()));
        blocks.add(entry);
        CompoundTag root = new CompoundTag();
        root.putString("regionId", "region-4");
        root.putString("dimension", "minecraft:overworld");
        root.put("blocks", blocks);
        NbtIo.writeCompressed(root, tempDir.resolve("region-4.nbt"));

        assertTrue(RegionTerrainSnapshot.restore(level, region, tempDir));
        assertFalse(Files.exists(tempDir.resolve("region-4.nbt")));
    }

    private static ServerLevel mockLevel() {
        return mockLevel(mock(ServerChunkCache.class));
    }

    private static ServerLevel mockLevel(ServerChunkCache chunkSource) {
        return mockLevel(chunkSource, null);
    }

    private static ServerLevel levelWithStates(Map<Long, net.minecraft.world.level.block.state.BlockState> states) {
        return mockLevel(mock(ServerChunkCache.class), states);
    }

    private static ServerLevel mockLevel(
        ServerChunkCache chunkSource,
        Map<Long, net.minecraft.world.level.block.state.BlockState> states
    ) {
        ServerLevel level = mock(ServerLevel.class);
        RegistryAccess registryAccess = mock(RegistryAccess.class);
        @SuppressWarnings("unchecked")
        HolderLookup.RegistryLookup<net.minecraft.world.level.block.Block> blockLookup = BuiltInRegistries.BLOCK.asLookup();
        when(registryAccess.lookupOrThrow(Registries.BLOCK)).thenReturn(blockLookup);
        when(level.registryAccess()).thenReturn(registryAccess);
        when(level.getBlockState(any(net.minecraft.core.BlockPos.class))).thenAnswer(invocation -> {
            net.minecraft.core.BlockPos pos = invocation.getArgument(0);
            return states == null
                ? Blocks.DIRT.defaultBlockState()
                : states.getOrDefault(pos.asLong(), Blocks.DIRT.defaultBlockState());
        });
        if (states != null) {
            when(level.setBlock(any(net.minecraft.core.BlockPos.class), any(), anyInt())).thenAnswer(invocation -> {
                net.minecraft.core.BlockPos pos = invocation.getArgument(0);
                net.minecraft.world.level.block.state.BlockState state = invocation.getArgument(1);
                states.put(pos.asLong(), state);
                return true;
            });
        }
        when(level.getHeight(any(), anyInt(), anyInt())).thenReturn(64);
        when(level.getMaxBuildHeight()).thenReturn(71);
        when(level.getChunkSource()).thenReturn(chunkSource);
        return level;
    }
}
