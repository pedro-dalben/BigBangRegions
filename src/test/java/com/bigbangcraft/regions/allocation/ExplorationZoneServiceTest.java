package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ExplorationZoneServiceTest {
    private ConfigManager configManager;
    private Config config;
    private ExplorationZoneService service;

    @BeforeEach
    public void setUp() {
        configManager = mock(ConfigManager.class);
        config = new Config();
        when(configManager.getConfig()).thenReturn(config);
        service = new ExplorationZoneService(configManager);
    }

    @Test
    public void testInsideExplorationZone() {
        assertTrue(service.isInsideExplorationZone("minecraft:overworld", 0, 0));
        assertTrue(service.isInsideExplorationZone("minecraft:overworld", -20000, 20000));
        assertTrue(service.isInsideExplorationZone("minecraft:overworld", 20000, -20000));
    }

    @Test
    public void testOutsideExplorationZone() {
        assertFalse(service.isInsideExplorationZone("minecraft:overworld", 30000, 0));
        assertFalse(service.isInsideExplorationZone("minecraft:overworld", 0, 30000));
        assertFalse(service.isInsideExplorationZone("minecraft:overworld", -30000, -30000));
    }

    @Test
    public void testWrongDimension() {
        assertFalse(service.isInsideExplorationZone("minecraft:nether", 0, 0));
        assertFalse(service.isInsideExplorationZone("minecraft:the_end", 0, 0));
    }
}
