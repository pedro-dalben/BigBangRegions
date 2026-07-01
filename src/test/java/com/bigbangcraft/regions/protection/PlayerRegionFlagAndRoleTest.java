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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PlayerRegionFlagAndRoleTest {
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
    public void testAllActionsForMemberAndVisitor() {
        Level level = mockLevel();
        BlockPos pos = new BlockPos(5, 5, 5);

        RegionAction[] actions = {
            RegionAction.CONTAINER,
            RegionAction.DOOR,
            RegionAction.REDSTONE,
            RegionAction.ENTITY_INTERACT,
            RegionAction.ITEM_PICKUP,
            RegionAction.ITEM_DROP
        };

        for (RegionAction action : actions) {
            ServerPlayer visitorPlayer = mockPlayer(visitor, level);
            ProtectionContext visitorContext = new ProtectionContext.Builder(action, level, pos).player(visitorPlayer).build();
            assertFalse(protectionService.check(visitorContext).isAllowed(), "Visitor should be blocked on " + action);

            ServerPlayer memberPlayer = mockPlayer(member, level);
            ProtectionContext memberContext = new ProtectionContext.Builder(action, level, pos).player(memberPlayer).build();
            assertTrue(protectionService.check(memberContext).isAllowed(), "Member should be allowed on " + action);

            region.setFlag(action.getFlagId(), "DENY");
            assertFalse(protectionService.check(memberContext).isAllowed(), "Member should be blocked on " + action + " when flag is DENY");

            region.setFlag(action.getFlagId(), "INHERIT");
        }
    }

    @Test
    public void testExplosionDamageUsesRegionFlag() {
        Level level = mockLevel();
        BlockPos pos = new BlockPos(5, 5, 5);

        region.setFlag("explosion-block-damage", "ALLOW");

        ServerPlayer visitorPlayer = mockPlayer(visitor, level);
        ProtectionContext visitorContext = new ProtectionContext.Builder(RegionAction.EXPLOSION_BLOCK_DAMAGE, level, pos)
                .player(visitorPlayer)
                .build();
        assertTrue(protectionService.check(visitorContext).isAllowed());

        ServerPlayer memberPlayer = mockPlayer(member, level);
        ProtectionContext memberContext = new ProtectionContext.Builder(RegionAction.EXPLOSION_BLOCK_DAMAGE, level, pos)
                .player(memberPlayer)
                .build();
        assertTrue(protectionService.check(memberContext).isAllowed());

        region.setFlag("explosion-block-damage", "DENY");
        assertFalse(protectionService.check(memberContext).isAllowed());
    }
}
