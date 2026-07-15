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
        when(level.getChunkSource()).thenReturn(chunkSource);
        return level;
    }
}
