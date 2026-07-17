package com.bigbangcraft.regions.protection;

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
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PlayerRegionProtectionTest {
    private RegionResolver regionResolver;
    private FlagResolver flagResolver;
    private PermissionManager permissionManager;
    private ConfigManager configManager;
    private RegionMembershipCache membershipCache;
    private ProtectionService protectionService;

    private Region region;
    private UUID owner;
    private UUID member;
    private UUID visitor;

    @BeforeAll
    public static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private ServerPlayer mockPlayer(UUID uuid, Level level) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(uuid);
        when(player.level()).thenReturn(level);
        return player;
    }

    @BeforeEach
    public void setUp() {
        regionResolver = mock(RegionResolver.class);
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
        member = UUID.randomUUID();
        visitor = UUID.randomUUID();

        Map<UUID, RegionMember> members = new HashMap<>();
        members.put(member, new RegionMember(member, RegionRole.MEMBER, owner, 0, 0));

        region = new Region("player_claim", "PlayerClaim", RegionType.PLAYER_REGION,
                new RegionBounds("minecraft:overworld", 0, 0, 0, 10, 10, 10), 100, owner, UUID.randomUUID(), 0, 0, "ACTIVE", members);
        membershipCache.loadFromRegion(region);

        when(regionResolver.resolveRegionAt("minecraft:overworld", 5, 5, 5)).thenReturn(Optional.of(region));
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
    public void testVisitorBlockedEvenWithFlagAllow() {
        Level level = mockLevel();
        ServerPlayer player = mockPlayer(visitor, level);
        BlockPos pos = new BlockPos(5, 5, 5);

        ProtectionContext context = new ProtectionContext.Builder(RegionAction.BLOCK_BREAK, level, pos)
                .player(player)
                .build();
        ProtectionResult result = protectionService.check(context);
        assertFalse(result.isAllowed());
        assertEquals("DENY_REASON_VISITOR_ROLE", result.getReason());
    }

    @Test
    public void testMemberAllowedWithFlagAllow() {
        Level level = mockLevel();
        ServerPlayer player = mockPlayer(member, level);
        BlockPos pos = new BlockPos(5, 5, 5);

        ProtectionContext context = new ProtectionContext.Builder(RegionAction.BLOCK_BREAK, level, pos)
                .player(player)
                .build();
        ProtectionResult result = protectionService.check(context);
        assertTrue(result.isAllowed());
        assertEquals("ALLOW_REASON_MEMBER", result.getReason());
    }

    @Test
    public void testMemberNotBlockedWithFlagDeny() {
        Level level = mockLevel();
        ServerPlayer player = mockPlayer(member, level);
        BlockPos pos = new BlockPos(5, 5, 5);

        region.setFlag("visitor-build", "DENY");

        ProtectionContext context = new ProtectionContext.Builder(RegionAction.BLOCK_BREAK, level, pos)
                .player(player)
                .build();
        ProtectionResult result = protectionService.check(context);
        assertTrue(result.isAllowed());
        assertEquals("ALLOW_REASON_MEMBER", result.getReason());
    }

    @Test
    public void testOwnerNotBlockedWithFlagDeny() {
        Level level = mockLevel();
        ServerPlayer player = mockPlayer(owner, level);
        BlockPos pos = new BlockPos(5, 5, 5);

        region.setFlag("visitor-build", "DENY");

        ProtectionContext context = new ProtectionContext.Builder(RegionAction.BLOCK_BREAK, level, pos)
                .player(player)
                .build();
        ProtectionResult result = protectionService.check(context);
        assertTrue(result.isAllowed());
        assertEquals("ALLOW_REASON_OWNER", result.getReason());
    }

    @Test
    public void testAdminBypassWins() {
        Level level = mockLevel();
        ServerPlayer player = mockPlayer(visitor, level);
        BlockPos pos = new BlockPos(5, 5, 5);

        when(permissionManager.hasBypass(player, "visitor-build")).thenReturn(true);

        ProtectionContext context = new ProtectionContext.Builder(RegionAction.BLOCK_BREAK, level, pos)
                .player(player)
                .build();
        ProtectionResult result = protectionService.check(context);
        assertTrue(result.isAllowed());
        assertEquals("ALLOW_REASON_BYPASS", result.getReason());
    }
}
