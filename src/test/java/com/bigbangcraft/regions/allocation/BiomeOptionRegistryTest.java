package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
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
}
