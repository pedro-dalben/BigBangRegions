package com.bigbangcraft.regions.allocation;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class WorldgenBiomeAnchorLocator implements BiomeAnchorLocator {
    @Override
    public BiomeAnchorSearchStepResult searchStep(
        WorldgenSearchContext context,
        BiomeOption biomeOption,
        AllocationSearchCursor cursor,
        SearchBudget budget
    ) {
        if (context == null || biomeOption == null) {
            return new BiomeAnchorSearchStepResult.Exhausted(cursor, new AnchorSearchProgress(0, 0, "missing_context"));
        }
        if (budget == null || !budget.hasLocateCallsRemaining()) {
            return new BiomeAnchorSearchStepResult.Continue(cursor, new AnchorSearchProgress(0, 0, "budget_exhausted"));
        }

        Set<ResourceKey<Biome>> accepted = acceptedKeys(biomeOption);
        if (accepted.isEmpty()) {
            return new BiomeAnchorSearchStepResult.Exhausted(cursor, new AnchorSearchProgress(0, 0, "empty_biome_option"));
        }

        int centerX = cursor.getSectorX();
        int centerZ = cursor.getSectorZ();
        // BiomeSource converts the block centre to quart coordinates internally,
        // but its search radius and skip values are already expressed in quart
        // cells. Passing the configured block radius directly makes the search
        // four times larger than intended and frequently returns an anchor from
        // another sector (especially for sparse biomes such as cherry groves).
        int searchRadius = Math.max(1, BiomeCoordinateMath.blockToQuart(Math.max(4, cursor.getAnchorAttempt())));
        int interval = Math.max(1, BiomeCoordinateMath.blockToQuart(Math.max(4, budget.maxSamples())));
        Climate.Sampler sampler = context.noiseSampler();
        Predicate<Holder<Biome>> predicate = holder -> holder.unwrapKey().map(accepted::contains).orElse(false);
        RandomSource random = RandomSource.create(deterministicSeed(context, biomeOption, cursor));

        cursor.setLocateCallsUsed(cursor.getLocateCallsUsed() + 1);

        List<Integer> searchYs = context.getEffectiveSampleBlockYs();
        Pair<BlockPos, Holder<Biome>> bestFound = null;
        int foundY = 64;

        for (int searchY : searchYs) {
            Pair<BlockPos, Holder<Biome>> found = context.biomeSource().findBiomeHorizontal(
                centerX,
                searchY,
                centerZ,
                searchRadius,
                interval,
                predicate,
                random,
                true,
                sampler
            );
            if (found != null) {
                bestFound = found;
                foundY = searchY;
                break;
            }
        }

        if (bestFound == null) {
            return new BiomeAnchorSearchStepResult.Exhausted(cursor, new AnchorSearchProgress(0, 1, "anchor_not_found"));
        }

        BlockPos pos = bestFound.getFirst();
        String biomeId = bestFound.getSecond().unwrapKey()
            .map(ResourceKey::location)
            .map(ResourceLocation::toString)
            .orElse("unknown");
        cursor.setCurrentAnchorX(pos.getX());
        cursor.setCurrentAnchorY(foundY);
        cursor.setCurrentAnchorZ(pos.getZ());
        cursor.setCurrentAnchorBiomeId(biomeId);
        return new BiomeAnchorSearchStepResult.Found(new BiomeAnchor(pos.getX(), foundY, pos.getZ(), biomeId), cursor);
    }

    private static long deterministicSeed(WorldgenSearchContext context, BiomeOption biomeOption, AllocationSearchCursor cursor) {
        long seed = context.worldSeed();
        seed = 31L * seed + biomeOption.getKey().hashCode();
        seed = 31L * seed + cursor.getRequestId().hashCode();
        seed = 31L * seed + cursor.getSectorX();
        seed = 31L * seed + cursor.getSectorZ();
        return seed;
    }

    private static Set<ResourceKey<Biome>> acceptedKeys(BiomeOption option) {
        Set<ResourceKey<Biome>> keys = new HashSet<>();
        for (String biomeId : option.getAcceptedBiomeIds()) {
            try {
                keys.add(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId)));
            } catch (Exception ignored) {
            }
        }
        return keys;
    }
}
