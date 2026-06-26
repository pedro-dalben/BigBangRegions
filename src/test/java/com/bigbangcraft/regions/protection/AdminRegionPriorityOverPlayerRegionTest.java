package com.bigbangcraft.regions.protection;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.cache.RegionMembershipCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.flag.FlagResolver;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.region.RegionResolver;
import com.bigbangcraft.regions.region.RegionRoleResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AdminRegionPriorityOverPlayerRegionTest {
    private RegionCache cache;
    private RegionResolver regionResolver;
    private FlagResolver flagResolver;
    private PermissionManager permissionManager;
    private ConfigManager configManager;
    private RegionMembershipCache membershipCache;
    private ProtectionService protectionService;

    private Region adminRegion;
    private Region playerRegion;
    private UUID owner;

    private ServerPlayer mockPlayer(UUID uuid, Level level) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(uuid);
        when(player.level()).thenReturn(level);
        return player;
    }

    @BeforeEach
    public void setUp() {
        cache = new RegionCache();
        regionResolver = new RegionResolver(cache);
        flagResolver = new FlagResolver();
        permissionManager = mock(PermissionManager.class);
        configManager = mock(ConfigManager.class);
        Config config = new Config();
        when(configManager.getConfig()).thenReturn(config);

        membershipCache = new RegionMembershipCache();
        RegionRoleResolver roleResolver = new RegionRoleResolver(membershipCache);
        RegionAccessService accessService = new RegionAccessService(roleResolver, flagResolver, configManager);
        protectionService = new ProtectionService(regionResolver, permissionManager, accessService);

        owner = UUID.randomUUID();

        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 10, 10, 10);
        playerRegion = new Region("player_claim", "PlayerClaim", RegionType.PLAYER_REGION, bounds, 100, owner, UUID.randomUUID(), 0, 0, "ACTIVE");
        adminRegion = new Region("spawn", "Spawn", RegionType.ADMIN_REGION, bounds, 1000, null, UUID.randomUUID(), 0, 0, "ACTIVE");

        cache.add(playerRegion);
        cache.add(adminRegion);

        membershipCache.loadFromRegion(playerRegion);
        membershipCache.loadFromRegion(adminRegion);
    }

    private Level mockLevel() {
        Level level = mock(Level.class);
        ResourceKey<Level> resourceKey = mock(ResourceKey.class);
        ResourceLocation location = mock(ResourceLocation.class);
        when(location.toString()).thenReturn("minecraft:overworld");
        when(resourceKey.location()).thenReturn(location);
        when(level.dimension()).thenReturn(resourceKey);
        return level;
    }

    @Test
    public void testAdminRegionPriorityOverlapping() {
        Level level = mockLevel();
        ServerPlayer player = mockPlayer(owner, level);
        BlockPos pos = new BlockPos(5, 5, 5);

        var resolved = regionResolver.resolveRegionAt("minecraft:overworld", 5, 5, 5);
        assertTrue(resolved.isPresent());
        assertEquals("spawn", resolved.get().getId());

        ProtectionContext context = new ProtectionContext.Builder(RegionAction.BLOCK_BREAK, level, pos)
                .player(player)
                .build();
        
        ProtectionResult result = protectionService.check(context);
        assertFalse(result.isAllowed());
        assertEquals("DENY_REASON_ADMIN_REGION", result.getReason());
    }
}
