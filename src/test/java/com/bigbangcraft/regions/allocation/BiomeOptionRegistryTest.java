package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BiomeOptionRegistryTest {
    private ConfigManager configManager;
    private Config config;
    private BiomeOptionRegistry registry;

    @BeforeEach
    public void setUp() {
        configManager = mock(ConfigManager.class);
        config = new Config();
        when(configManager.getConfig()).thenReturn(config);
        registry = new BiomeOptionRegistry(configManager);
    }

    @Test
    public void testRegistryLoadAndLookup() {
        registry.load();
        assertTrue(registry.getAll().size() > 0);

        assertTrue(registry.lookup("planicies").isPresent());
        assertTrue(registry.lookup("plains").isPresent());
        assertTrue(registry.lookup("FLORESTA").isPresent());
        assertFalse(registry.lookup("invalid_biome").isPresent());
    }

    @Test
    public void testRegistryInvalidBiomeOptionsIgnored() {
        config.getBiomeOptions().put("invalid_v1", new Config.BiomeOptionConfig("", Arrays.asList("alias1"), Arrays.asList("minecraft:plains")));
        config.getBiomeOptions().put("invalid_v2", new Config.BiomeOptionConfig("Display", Arrays.asList("alias2"), Collections.emptyList()));

        registry.load();
        assertFalse(registry.lookup("invalid_v1").isPresent());
        assertFalse(registry.lookup("invalid_v2").isPresent());
    }

    @Test
    public void testOceanBiomeIsBlockedByPolicy() {
        registry.load();

        assertFalse(registry.lookup("oceano").isPresent());
        assertFalse(registry.lookup("ocean").isPresent());
        assertFalse(registry.lookup("mar").isPresent());
    }

    @Test
    public void regionsUnexploredIsOptionalAndDoesNotCreateCategories() {
        registry = new BiomeOptionRegistry(configManager, () -> false);
        registry.load();

        assertTrue(registry.getAll().stream()
            .flatMap(option -> option.getAcceptedBiomeIds().stream())
            .noneMatch(id -> id.startsWith("regions_unexplored:")));
        assertFalse(registry.lookup("maple_forest").isPresent());
    }

    @Test
    public void regionsUnexploredIdsAreCuratedMergedAndNotPersisted() {
        Config.BiomeOptionConfig forestConfig = config.getBiomeOptions().get("floresta");
        List<String> originalIds = new ArrayList<>(forestConfig.getAcceptedBiomeIds());
        originalIds.add("minecraft:forest");
        originalIds.add("example:custom_biome");
        forestConfig.setAcceptedBiomeIds(originalIds);

        registry = new BiomeOptionRegistry(configManager, () -> true);
        registry.load();

        List<String> ids = registry.lookup("floresta").orElseThrow().getAcceptedBiomeIds();
        assertTrue(ids.contains("regions_unexplored:maple_forest"));
        assertTrue(ids.contains("example:custom_biome"));
        assertEquals(ids.size(), new java.util.HashSet<>(ids).size());
        assertEquals(originalIds, forestConfig.getAcceptedBiomeIds());
        assertFalse(ids.contains("regions_unexplored:inferno"));
        assertFalse(ids.contains("regions_unexplored:infernal_holt"));
        assertFalse(ids.stream().anyMatch(id -> id.startsWith("regions_unexplored:") && id.contains("nether")));
        assertFalse(registry.lookup("maple_forest").isPresent());
    }

    @Test
    public void regionsUnexploredBiomeMatchesItsExistingOption() {
        registry = new BiomeOptionRegistry(configManager, () -> true);
        registry.load();

        BiomeOption forest = registry.lookup("floresta").orElseThrow();
        assertTrue(forest.getAcceptedBiomeKeys().stream()
            .anyMatch(key -> key.location().toString().equals("regions_unexplored:maple_forest")));
    }
}
