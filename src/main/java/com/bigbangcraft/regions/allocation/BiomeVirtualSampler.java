package com.bigbangcraft.regions.allocation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public interface BiomeVirtualSampler {
    ResourceKey<Biome> sampleAtBlock(WorldgenSearchContext context, int blockX, int blockY, int blockZ);
}
