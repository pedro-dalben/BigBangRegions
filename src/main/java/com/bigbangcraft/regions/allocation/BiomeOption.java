package com.bigbangcraft.regions.allocation;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BiomeOption {
    private final String key;
    private final String displayName;
    private final List<String> aliases;
    private final List<String> acceptedBiomeIds;
    private final Set<ResourceKey<Biome>> acceptedBiomeKeys;

    private final String icon;

    public BiomeOption(String key, String displayName, List<String> aliases, List<String> acceptedBiomeIds, String icon) {
        this.key = key;
        this.displayName = displayName;
        this.aliases = aliases;
        this.acceptedBiomeIds = acceptedBiomeIds;
        this.acceptedBiomeKeys = resolveAcceptedBiomeKeys(acceptedBiomeIds);
        this.icon = icon;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public List<String> getAliases() { return aliases; }
    public List<String> getAcceptedBiomeIds() { return acceptedBiomeIds; }
    public Set<ResourceKey<Biome>> getAcceptedBiomeKeys() { return acceptedBiomeKeys; }
    public String getIcon() { return icon; }

    public boolean matches(String query) {
        if (query == null) return false;
        String q = query.toLowerCase();
        if (key.equalsIgnoreCase(q)) return true;
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(q)) return true;
        }
        return false;
    }

    private static Set<ResourceKey<Biome>> resolveAcceptedBiomeKeys(List<String> biomeIds) {
        if (biomeIds == null || biomeIds.isEmpty()) {
            return Set.of();
        }

        Set<ResourceKey<Biome>> keys = new HashSet<>();
        for (String biomeId : biomeIds) {
            try {
                keys.add(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId)));
            } catch (RuntimeException ignored) {
                // Invalid IDs are reported by BiomeOptionRegistry when loaded.
            }
        }
        return Collections.unmodifiableSet(keys);
    }
}
