package com.bigbangcraft.regions.journeymap;

import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.region.RegionRoleResolver;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionVisibilityResolverTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void restrictsChunkLoadersToOwnerOrViewAllStaff() {
        PermissionManager permissions = mock(PermissionManager.class);
        RegionRoleResolver roles = mock(RegionRoleResolver.class);
        RegionVisibilityResolver resolver = new RegionVisibilityResolver(
            new ConfigManager(Path.of("build", "test-config")), roles,
            mock(RegionMembershipCache.class), permissions
        );
        UUID owner = UUID.randomUUID();
        Region region = new Region("region", "Region", RegionType.PLAYER_REGION,
            new RegionBounds("minecraft:overworld", 0, 0, 0, 15, 255, 15),
            0, owner, owner, 0L, 0L, "ACTIVE", Map.of());

        ServerPlayer ownerPlayer = mock(ServerPlayer.class);
        when(ownerPlayer.getUUID()).thenReturn(owner);
        when(roles.resolveRole(region, owner)).thenReturn(RegionRole.OWNER);
        assertTrue(resolver.canSeeChunkLoaders(ownerPlayer, region));

        ServerPlayer member = mock(ServerPlayer.class);
        when(member.getUUID()).thenReturn(UUID.randomUUID());
        assertFalse(resolver.canSeeChunkLoaders(member, region));

        when(permissions.hasPermission(member, "bigbangregions.journeymap.view-all")).thenReturn(true);
        assertTrue(resolver.canSeeChunkLoaders(member, region));
    }

    @Test
    void choosesPaletteByRelationship() {
        PermissionManager permissions = mock(PermissionManager.class);
        RegionRoleResolver roles = mock(RegionRoleResolver.class);
        ConfigManager configManager = new ConfigManager(Path.of("build", "test-config"));
        RegionVisibilityResolver resolver = new RegionVisibilityResolver(
            configManager, roles, mock(RegionMembershipCache.class), permissions
        );
        UUID owner = UUID.randomUUID();
        Region region = new Region("region", "Region", RegionType.PLAYER_REGION,
            new RegionBounds("minecraft:overworld", 0, 0, 0, 15, 255, 15),
            0, owner, owner, 0L, 0L, "ACTIVE");
        region.setFlag("map-visibility", "public");
        ServerPlayer ownerPlayer = mock(ServerPlayer.class);
        ServerPlayer member = mock(ServerPlayer.class);
        ServerPlayer publicPlayer = mock(ServerPlayer.class);
        ServerPlayer staff = mock(ServerPlayer.class);
        when(ownerPlayer.getUUID()).thenReturn(owner);
        when(member.getUUID()).thenReturn(UUID.randomUUID());
        when(publicPlayer.getUUID()).thenReturn(UUID.randomUUID());
        when(staff.getUUID()).thenReturn(UUID.randomUUID());
        when(roles.resolveRole(region, owner)).thenReturn(RegionRole.OWNER);
        when(roles.resolveRole(region, member.getUUID())).thenReturn(RegionRole.MEMBER);
        when(roles.resolveRole(region, publicPlayer.getUUID())).thenReturn(RegionRole.VISITOR);
        when(roles.resolveRole(region, staff.getUUID())).thenReturn(RegionRole.VISITOR);
        when(permissions.hasPermission(publicPlayer, "bigbangregions.journeymap.view-public")).thenReturn(true);
        when(permissions.hasPermission(staff, "bigbangregions.journeymap.view-all")).thenReturn(true);

        Config.JourneyMapConfig palette = configManager.getConfig().getJourneyMap();
        assertEquals(palette.getPlayerRegion().getFillColor(), resolver.styleFor(ownerPlayer, region).getFillColor());
        assertEquals(palette.getMemberRegion().getFillColor(), resolver.styleFor(member, region).getFillColor());
        assertEquals(palette.getPublicRegion().getFillColor(), resolver.styleFor(publicPlayer, region).getFillColor());
        assertEquals(palette.getStaffRegion().getFillColor(), resolver.styleFor(staff, region).getFillColor());
    }
}
