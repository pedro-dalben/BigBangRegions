package com.bigbangcraft.regions.allocation;

import com.bigbangcraft.regions.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AllocationConfigValidationTest {
    private Config config;

    @BeforeEach
    public void setUp() {
        config = new Config();
    }

    @Test
    public void testDefaultConfigIsValid() {
        assertTrue(AllocationConfigValidator.isValid(config));
    }

    @Test
    public void testInvalidInitialClaimSize() {
        config.getPlayerLandAllocation().setInitialClaimSize(0);
        assertFalse(AllocationConfigValidator.isValid(config));
    }

    @Test
    public void testInvalidSlotSize() {
        config.getPlayerLandAllocation().setInitialClaimSize(50);
        config.getPlayerLandAllocation().setSlotSize(50);
        assertFalse(AllocationConfigValidator.isValid(config));
    }

    @Test
    public void testInvalidMargins() {
        config.getPlayerLandAllocation().setSlotSize(256);
        config.getPlayerLandAllocation().setFutureMaximumClaimSize(240);
        config.getPlayerLandAllocation().setSlotInternalMargin(10); // 240 + 20 = 260 > 256
        assertFalse(AllocationConfigValidator.isValid(config));
    }

    @Test
    public void testInvalidMatchPercentage() {
        config.getPlayerLandAllocation().getBiomeSearch().setMinimumMatchPercentage(101);
        assertFalse(AllocationConfigValidator.isValid(config));
        config.getPlayerLandAllocation().getBiomeSearch().setMinimumMatchPercentage(0);
        assertFalse(AllocationConfigValidator.isValid(config));
    }

    @Test
    public void testInvalidGridSize() {
        config.getPlayerLandAllocation().getBiomeSearch().setSampleGridSize(4); // Even is invalid
        assertFalse(AllocationConfigValidator.isValid(config));
        config.getPlayerLandAllocation().getBiomeSearch().setSampleGridSize(2); // Even and < 3
        assertFalse(AllocationConfigValidator.isValid(config));
    }

    @Test
    public void testInvalidRequestTimeout() {
        config.getPlayerLandAllocation().getScheduler().setRequestTimeoutSeconds(0);
        assertFalse(AllocationConfigValidator.isValid(config));
    }
}
