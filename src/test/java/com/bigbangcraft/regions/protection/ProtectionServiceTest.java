package com.bigbangcraft.regions.protection;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.flag.EffectiveRegionPolicy;
import com.bigbangcraft.regions.flag.FlagPolicy;
import com.bigbangcraft.regions.flag.FlagResolver;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.region.RegionResolver;
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

public class ProtectionServiceTest {
    @BeforeAll
    public static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private RegionResolver regionResolver;
    private FlagResolver flagResolver;
    private PermissionManager permissionManager;
    private ConfigManager configManager;
    private ProtectionService protectionService;
    private com.bigbangcraft.regions.cache.RegionMembershipCache membershipCache;

    private Level level;
    private ServerPlayer player;
    private BlockPos pos;
    private RegionBounds bounds;
    private Region region;
    private UUID playerUuid;

    @BeforeEach
    public void setUp() {
        regionResolver = mock(RegionResolver.class);
        flagResolver = mock(FlagResolver.class);
        permissionManager = mock(PermissionManager.class);
        configManager = mock(ConfigManager.class);
        
        membershipCache = new com.bigbangcraft.regions.cache.RegionMembershipCache();
        com.bigbangcraft.regions.region.RegionRoleResolver roleResolver = new com.bigbangcraft.regions.region.RegionRoleResolver(membershipCache);
        RegionAccessService accessService = new RegionAccessService(roleResolver, flagResolver, configManager);
        protectionService = new ProtectionService(regionResolver, permissionManager, accessService);

        // Mock Level dimension structure
        level = mock(Level.class);
        ResourceKey<Level> resourceKey = mock(ResourceKey.class);
        ResourceLocation location = mock(ResourceLocation.class);
        when(location.toString()).thenReturn("minecraft:overworld");
        when(resourceKey.location()).thenReturn(location);
        when(level.dimension()).thenReturn(resourceKey);

        // Mock ServerPlayer
        player = mock(ServerPlayer.class);
        playerUuid = UUID.randomUUID();
        when(player.getUUID()).thenReturn(playerUuid);
        when(player.level()).thenReturn(level);

        pos = mock(BlockPos.class);
        when(pos.getX()).thenReturn(10);
        when(pos.getY()).thenReturn(10);
        when(pos.getZ()).thenReturn(10);

        // Mock Region
        bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 20, 20, 20);
        region = new Region("regA", "Region A", RegionType.ADMIN_REGION, bounds, 1000, null, UUID.randomUUID(), 0, 0, "ACTIVE");
    }

    @Test
    public void testPlayerBypass() {
        // Player has bypass permission
        when(regionResolver.resolveRegionAt("minecraft:overworld", 10, 10, 10)).thenReturn(Optional.of(region));
        when(permissionManager.hasBypass(player, "player-build")).thenReturn(true);

        ProtectionContext context = new ProtectionContext.Builder(RegionAction.BLOCK_BREAK, level, pos)
                .player(player)
                .build();

        ProtectionResult result = protectionService.check(context);
        assertEquals(ProtectionDecision.BYPASS, result.getDecision());
        assertTrue(result.isAllowed());
    }

    @Test
    public void testNoRegion() {
        // No region found at position
        when(regionResolver.resolveRegionAt("minecraft:overworld", 10, 10, 10)).thenReturn(Optional.empty());

        ProtectionContext context = new ProtectionContext.Builder(RegionAction.BLOCK_BREAK, level, pos)
                .player(player)
                .build();

        ProtectionResult result = protectionService.check(context);
        assertEquals(ProtectionDecision.NO_REGION, result.getDecision());
        assertTrue(result.isAllowed());
    }

    @Test
    public void testUnknownActorBlocksDestructiveAction() {
        // Region covers position
        when(regionResolver.resolveRegionAt("minecraft:overworld", 10, 10, 10)).thenReturn(Optional.of(region));

        // Unknown actor trying to break a block
        ProtectionContext context = new ProtectionContext.Builder(RegionAction.BLOCK_BREAK, level, pos)
                .actor(ActorType.UNKNOWN)
                .build();

        ProtectionResult result = protectionService.check(context);
        assertEquals(ProtectionDecision.DENY, result.getDecision());
        assertEquals("Unknown actor performing destructive action", result.getReason());
    }

    @Test
    public void testRegionMemberIsAllowed() {
        // Create region with member
        Map<UUID, RegionMember> members = new HashMap<>();
        members.put(playerUuid, new RegionMember(playerUuid, RegionRole.MEMBER, null, 0, 0));
        region = new Region("regA", "Region A", RegionType.ADMIN_REGION, bounds, 1000, null, UUID.randomUUID(), 0, 0, "ACTIVE", members);

        // Region covers position
        when(regionResolver.resolveRegionAt("minecraft:overworld", 10, 10, 10)).thenReturn(Optional.of(region));

        membershipCache.loadFromRegion(region);

        ProtectionContext context = new ProtectionContext.Builder(RegionAction.BLOCK_BREAK, level, pos)
                .player(player)
                .build();

        ProtectionResult result = protectionService.check(context);
        assertEquals(ProtectionDecision.ALLOW, result.getDecision());
        assertEquals("ALLOW_REASON_MEMBER", result.getReason());
    }

    @Test
    public void testRegionVisitorEvaluatesFlags() {
        // Region covers position
        when(regionResolver.resolveRegionAt("minecraft:overworld", 10, 10, 10)).thenReturn(Optional.of(region));

        // Player is not a member (VISITOR)
        // Set flag resolver to DENY
        Config config = new Config();
        when(configManager.getConfig()).thenReturn(config);
        when(flagResolver.resolve(region, "player-build", config))
                .thenReturn(new EffectiveRegionPolicy(FlagPolicy.DENY, "region_explicit", region));

        ProtectionContext context = new ProtectionContext.Builder(RegionAction.BLOCK_BREAK, level, pos)
                .player(player)
                .build();

        ProtectionResult result = protectionService.check(context);
        assertEquals(ProtectionDecision.DENY, result.getDecision());
        assertFalse(result.isAllowed());
    }
}
