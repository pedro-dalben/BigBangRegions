package com.bigbangcraft.regions.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    public void testConfigInitializationAndFallback() throws IOException {
        Path configDir = tempDir.resolve("config");
        ConfigManager manager = new ConfigManager(configDir);

        // 1. First load: file does not exist -> should write defaults
        manager.load();
        
        Path configFile = configDir.resolve("config.json");
        assertTrue(Files.exists(configFile));

        Config config = manager.getConfig();
        assertNotNull(config);
        assertEquals(1, config.getSchemaVersion());
        assertEquals(1000, config.getDefaultPriorities().getAdminRegion());
        assertEquals("ALLOW", config.getDefaults().getGlobal().get("visitor-build"));
        assertEquals("DENY", config.getDefaults().getAdminRegion().get("visitor-build"));
        assertEquals(5, config.getPlayerLandAllocation().getBiomeSearch().getSampleGridSize());
        assertEquals(1, config.getPlayerLandAllocation().getScheduler().getMaxActiveRequests());

        // 2. Corrupt file with invalid JSON
        Files.writeString(configFile, "{ invalid json garbage }");

        ConfigManager brokenManager = new ConfigManager(configDir);
        // Load should handle exception, keep using fallback default Config, and NOT overwrite the invalid file
        brokenManager.load();

        Config fallbackConfig = brokenManager.getConfig();
        assertNotNull(fallbackConfig);
        assertEquals(1000, fallbackConfig.getDefaultPriorities().getAdminRegion());
        assertEquals(5, fallbackConfig.getPlayerLandAllocation().getBiomeSearch().getSampleGridSize());
        
        // Confirm the file was not overwritten (original bad content is still there for user to fix)
        assertEquals("{ invalid json garbage }", Files.readString(configFile).trim());
    }
}
