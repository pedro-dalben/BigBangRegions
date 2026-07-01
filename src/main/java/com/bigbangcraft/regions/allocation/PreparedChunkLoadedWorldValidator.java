package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PreparedChunkLoadedWorldValidator implements LoadedWorldValidator {
    private final ConfigManager configManager;
    private final BiomeOptionRegistry biomeOptionRegistry;

    public PreparedChunkLoadedWorldValidator(ConfigManager configManager, BiomeOptionRegistry biomeOptionRegistry) {
        this.configManager = configManager;
        this.biomeOptionRegistry = biomeOptionRegistry;
    }

    @Override
    public LoadedWorldValidationResult validate(ServerLevel world, ReservedPlotCandidate candidate, ChunkPreparationPlan preparedPlan) {
        ChunkAccessGuard.assertAllowed(AllocationPhase.VALIDATING_LOADED_WORLD);
        List<String> diagnostics = new ArrayList<>();
        Set<ChunkPos> preparedChunks = preparedPlan.requiredChunks();
        for (ChunkPos chunk : preparedChunks) {
            if (world.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                diagnostics.add("Chunk not ready: " + chunk.x + "," + chunk.z);
                return LoadedWorldValidationResult.rejected(LoadedWorldFailureReason.CHUNK_NOT_READY, diagnostics);
            }
        }

        Optional<BiomeOption> biomeOption = biomeOptionRegistry.lookup(candidate.biomeOptionKey());
        if (biomeOption.isEmpty()) {
            diagnostics.add("Biome option not found: " + candidate.biomeOptionKey());
            return LoadedWorldValidationResult.rejected(LoadedWorldFailureReason.VIRTUAL_PHYSICAL_BIOME_MISMATCH, diagnostics);
        }

        Set<String> acceptedBiomes = Set.copyOf(biomeOption.get().getAcceptedBiomeIds());
        int sampleY = configManager.getConfig().getPlayerLandAllocation().getWorldgenSearch().getSampleBlockY();
        List<int[]> samplePoints = buildPreparedChunkSamples(candidate.footprint(), preparedChunks);
        for (int index = 0; index < samplePoints.size(); index++) {
            int[] sample = samplePoints.get(index);
            BlockPos pos = new BlockPos(sample[0], sampleY, sample[1]);
            ResourceKey<Biome> biomeKey = world.getBiome(pos).unwrapKey().orElse(null);
            String biomeId = biomeKey == null ? null : biomeKey.location().toString();
            if (biomeId == null || !acceptedBiomes.contains(biomeId)) {
                diagnostics.add("Biome mismatch at " + sample[0] + "," + sample[1] + " expected one of " + acceptedBiomes + " but found " + biomeId);
                return LoadedWorldValidationResult.rejected(
                    index == 0 ? LoadedWorldFailureReason.INTERIOR_BIOME_MISMATCH : LoadedWorldFailureReason.EDGE_BIOME_MISMATCH,
                    diagnostics
                );
            }
        }

        Optional<BlockPos> safeSpawn = SafeSpawnFinder.findSafeSpawn(
            world,
            candidate.footprint().minX(),
            candidate.footprint().maxX(),
            candidate.footprint().minZ(),
            candidate.footprint().maxZ(),
            preparedChunks
        );
        if (safeSpawn.isEmpty()) {
            diagnostics.add("No safe spawn found in prepared footprint");
            return LoadedWorldValidationResult.rejected(LoadedWorldFailureReason.SAFE_SPAWN_NOT_FOUND, diagnostics);
        }

        return LoadedWorldValidationResult.accepted(new SafeSpawnLocation(safeSpawn.get()), diagnostics);
    }

    private List<int[]> buildPreparedChunkSamples(PlotFootprint footprint, Set<ChunkPos> preparedChunks) {
        List<int[]> samples = new ArrayList<>();
        samples.add(new int[]{footprint.centerX(), footprint.centerZ()});
        for (ChunkPos chunk : preparedChunks) {
            int x = clamp((chunk.x << 4) + 8, footprint.minX(), footprint.maxX());
            int z = clamp((chunk.z << 4) + 8, footprint.minZ(), footprint.maxZ());
            if (x == footprint.centerX() && z == footprint.centerZ()) {
                continue;
            }
            samples.add(new int[]{x, z});
        }
        return samples;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
