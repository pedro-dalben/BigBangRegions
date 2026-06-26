package com.bigbangcraft.regions.command;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PlayerRegionCreationValidationTest {
    private RegionCache cache;
    private ConfigManager configManager;
    private Config config;

    @BeforeEach
    public void setUp() {
        cache = new RegionCache();
        configManager = mock(ConfigManager.class);
        config = new Config();
        when(configManager.getConfig()).thenReturn(config);
    }

    @Test
    public void testPlayerRegionRequiresOwner() {
        UUID creator = UUID.randomUUID();
        RegionBounds bounds = new RegionBounds("overworld", 0, 0, 0, 10, 10, 10);

        assertThrows(IllegalArgumentException.class, () -> {
            new Region("player_claim", "PlayerClaim", RegionType.PLAYER_REGION, bounds, 100, null, creator, 0, 0, "ACTIVE");
        });

        assertDoesNotThrow(() -> {
            new Region("admin_claim", "AdminClaim", RegionType.ADMIN_REGION, bounds, 1000, null, creator, 0, 0, "ACTIVE");
        });
    }
}
