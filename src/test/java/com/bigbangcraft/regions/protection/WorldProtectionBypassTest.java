package com.bigbangcraft.regions.protection;

import com.bigbangcraft.regions.BigBangRegions;
import com.bigbangcraft.regions.permission.PermissionManager;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WorldProtectionBypassTest {

    @BeforeAll
    public static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testHasBypassWithPermissionManager() {
        ServerPlayer player = mock(ServerPlayer.class);

        // When permissionManager is null, falls back to hasPermissions(2)
        when(player.hasPermissions(2)).thenReturn(true);
        assertTrue(BigBangRegions.hasBypass(player, "visitor-build"));

        when(player.hasPermissions(2)).thenReturn(false);
        assertFalse(BigBangRegions.hasBypass(player, "visitor-build"));
    }

    @Test
    public void testHasBypassNullPlayer() {
        assertFalse(BigBangRegions.hasBypass(null, "visitor-build"));
    }
}
