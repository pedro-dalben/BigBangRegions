package com.bigbangcraft.regions.protection;

import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.flag.FlagPolicy;
import com.bigbangcraft.regions.flag.FlagResolver;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.region.RegionResolver;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class InteractionFlagPrecedenceTest {

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

    private Level level;
    private ServerPlayer player;
    private BlockPos pos;
    private Region region;
    private Config config;

    @BeforeEach
    public void setUp() {
        regionResolver = mock(RegionResolver.class);
        flagResolver = new FlagResolver(); // Use real resolver to test exact hierarchy
        permissionManager = mock(PermissionManager.class);
        configManager = mock(ConfigManager.class);
        
        com.bigbangcraft.regions.cache.RegionMembershipCache membershipCache = new com.bigbangcraft.regions.cache.RegionMembershipCache();
        com.bigbangcraft.regions.region.RegionRoleResolver roleResolver = new com.bigbangcraft.regions.region.RegionRoleResolver(membershipCache);
        RegionAccessService accessService = new RegionAccessService(roleResolver, flagResolver, configManager);
        protectionService = new ProtectionService(regionResolver, permissionManager, accessService);

        config = new Config();
        when(configManager.getConfig()).thenReturn(config);

        level = mock(Level.class);
        ResourceKey<Level> resourceKey = mock(ResourceKey.class);
        ResourceLocation location = mock(ResourceLocation.class);
        when(location.toString()).thenReturn("minecraft:overworld");
        when(resourceKey.location()).thenReturn(location);
        when(level.dimension()).thenReturn(resourceKey);

        player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.level()).thenReturn(level);

        pos = new BlockPos(10, 10, 10);

        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 20, 20, 20);
        region = new Region("regA", "Region A", RegionType.ADMIN_REGION, bounds, 1000, null, UUID.randomUUID(), 0, 0, "ACTIVE");
        
        when(regionResolver.resolveRegionAt("minecraft:overworld", 10, 10, 10)).thenReturn(Optional.of(region));
        when(regionResolver.resolveRegionAt("minecraft:overworld", 10, 11, 10)).thenReturn(Optional.of(region));
        when(permissionManager.hasBypass(any(), any())).thenReturn(false);
    }

    private boolean checkInteraction(BlockState state, BlockEntity be, ItemStack heldItem) {
        return checkInteraction(state, be, heldItem, Fluids.EMPTY.defaultFluidState());
    }

    private boolean checkInteraction(BlockState state, BlockEntity be, ItemStack heldItem, FluidState fluidState) {
        when(level.getBlockState(pos)).thenReturn(state);
        when(level.getBlockEntity(pos)).thenReturn(be);
        when(level.getFluidState(pos)).thenReturn(fluidState);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(heldItem);

        BlockHitResult hitResult = new BlockHitResult(new Vec3(10, 10, 10), Direction.UP, pos, false);

        BlockInteractionClassifier.ClassifiedInteraction classified = 
                BlockInteractionClassifier.classify(level, pos, state, player, InteractionHand.MAIN_HAND, hitResult);

        ProtectionContext context = new ProtectionContext.Builder(classified.getAction(), level, classified.getTargetPos())
                .player(player)
                .build();

        return protectionService.check(context).isAllowed();
    }

    @Test
    public void testVisitorUsageAllow_Container() {
        BlockState state = Blocks.CHEST.defaultBlockState();
        BlockEntity chestEntity = mock(BlockEntity.class, withSettings().extraInterfaces(net.minecraft.world.Container.class));
        region.setFlag("visitor-usage", "ALLOW");
        assertTrue(checkInteraction(state, chestEntity, ItemStack.EMPTY));
    }

    @Test
    public void testVisitorUsageAllow_Door() {
        BlockState state = Blocks.OAK_DOOR.defaultBlockState();
        region.setFlag("visitor-usage", "ALLOW");
        assertTrue(checkInteraction(state, null, ItemStack.EMPTY));
    }

    @Test
    public void testVisitorUsageAllow_Redstone() {
        BlockState state = Blocks.LEVER.defaultBlockState();
        region.setFlag("visitor-usage", "ALLOW");
        assertTrue(checkInteraction(state, null, ItemStack.EMPTY));
    }

    @Test
    public void testVisitorUsageAllow_GenericInteract() {
        BlockState state = Blocks.CRAFTING_TABLE.defaultBlockState();
        region.setFlag("visitor-usage", "ALLOW");
        assertTrue(checkInteraction(state, null, ItemStack.EMPTY));
    }

    @Test
    public void testVisitorUsageDeny_Container() {
        BlockState state = Blocks.CHEST.defaultBlockState();
        BlockEntity chestEntity = mock(BlockEntity.class, withSettings().extraInterfaces(net.minecraft.world.Container.class));
        region.setFlag("visitor-usage", "DENY");
        assertFalse(checkInteraction(state, chestEntity, ItemStack.EMPTY));
    }

    @Test
    public void testVisitorUsageDeny_Door() {
        BlockState state = Blocks.OAK_DOOR.defaultBlockState();
        region.setFlag("visitor-usage", "DENY");
        assertFalse(checkInteraction(state, null, ItemStack.EMPTY));
    }

    @Test
    public void testVisitorUsageDeny_Redstone() {
        BlockState state = Blocks.LEVER.defaultBlockState();
        region.setFlag("visitor-usage", "DENY");
        assertFalse(checkInteraction(state, null, ItemStack.EMPTY));
    }

    @Test
    public void testVisitorUsageDeny_GenericInteract() {
        BlockState state = Blocks.CRAFTING_TABLE.defaultBlockState();
        region.setFlag("visitor-usage", "DENY");
        assertFalse(checkInteraction(state, null, ItemStack.EMPTY));
    }

    @Test
    public void testVisitorUsageInherit_FallsBackToTypeDefault() {
        BlockState state = Blocks.CHEST.defaultBlockState();
        BlockEntity chestEntity = mock(BlockEntity.class, withSettings().extraInterfaces(net.minecraft.world.Container.class));
        region.setFlag("visitor-usage", "INHERIT");
        config.getDefaults().getAdminRegion().put("visitor-usage", "DENY");
        assertFalse(checkInteraction(state, chestEntity, ItemStack.EMPTY));
    }

    @Test
    public void testWaterBucketUsesWaterFlow() {
        BlockState state = Blocks.CRAFTING_TABLE.defaultBlockState();
        region.setFlag("water-flow", "ALLOW");
        region.setFlag("visitor-usage", "DENY");
        assertTrue(checkInteraction(state, null, new ItemStack(Items.WATER_BUCKET)));
    }

    @Test
    public void testLavaBucketUsesLavaFlow() {
        BlockState state = Blocks.CRAFTING_TABLE.defaultBlockState();
        region.setFlag("lava-flow", "ALLOW");
        region.setFlag("visitor-usage", "DENY");
        assertTrue(checkInteraction(state, null, new ItemStack(Items.LAVA_BUCKET)));
    }

    @Test
    public void testEmptyBucketOnWaterUsesWaterFlow() {
        BlockState state = Blocks.CRAFTING_TABLE.defaultBlockState();
        region.setFlag("water-flow", "ALLOW");
        region.setFlag("visitor-usage", "DENY");
        assertTrue(checkInteraction(state, null, new ItemStack(Items.BUCKET), Fluids.WATER.defaultFluidState()));
    }

    @Test
    public void testEmptyBucketOnLavaUsesLavaFlow() {
        BlockState state = Blocks.CRAFTING_TABLE.defaultBlockState();
        region.setFlag("lava-flow", "ALLOW");
        region.setFlag("visitor-usage", "DENY");
        assertTrue(checkInteraction(state, null, new ItemStack(Items.BUCKET), Fluids.LAVA.defaultFluidState()));
    }

    @Test
    public void testFlintAndSteelUsesFireSpread() {
        BlockState state = Blocks.CRAFTING_TABLE.defaultBlockState();
        region.setFlag("fire-spread", "ALLOW");
        region.setFlag("visitor-usage", "DENY");
        assertTrue(checkInteraction(state, null, new ItemStack(Items.FLINT_AND_STEEL)));
    }

    @Test
    public void testFireChargeUsesFireSpread() {
        BlockState state = Blocks.CRAFTING_TABLE.defaultBlockState();
        region.setFlag("fire-spread", "ALLOW");
        region.setFlag("visitor-usage", "DENY");
        assertTrue(checkInteraction(state, null, new ItemStack(Items.FIRE_CHARGE)));
    }
}
