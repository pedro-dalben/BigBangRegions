package com.bigbangcraft.regions.command;

import com.bigbangcraft.regions.audit.AuditService;
import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.permission.PermissionManager;
import com.bigbangcraft.regions.region.RegionResolver;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.util.SelectionManager;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RegionSelectionDimensionTest {

    @BeforeAll
    public static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private PermissionManager permissionManager;
    private SelectionManager selectionManager;
    private RegionCache regionCache;
    private RegionRepository regionRepository;
    private RegionResolver regionResolver;
    private AuditService auditService;
    private ConfigManager configManager;

    private CommandContext<CommandSourceStack> context;
    private CommandSourceStack source;
    private ServerPlayer player;
    private UUID playerUuid;

    @BeforeEach
    public void setUp() {
        permissionManager = mock(PermissionManager.class);
        selectionManager = new SelectionManager(); // Use real SelectionManager
        regionCache = mock(RegionCache.class);
        regionRepository = mock(RegionRepository.class);
        regionResolver = mock(RegionResolver.class);
        auditService = mock(AuditService.class);
        configManager = mock(ConfigManager.class);

        // Initialize Command
        RegionsCommand.initialize(permissionManager, selectionManager, regionCache, regionRepository, regionResolver, auditService, configManager);

        context = mock(CommandContext.class);
        source = mock(CommandSourceStack.class);
        player = mock(ServerPlayer.class);
        playerUuid = UUID.randomUUID();

        when(context.getSource()).thenReturn(source);
        when(source.getPlayer()).thenReturn(player);
        when(player.getUUID()).thenReturn(playerUuid);

        // Allow creation permission
        when(permissionManager.hasPermission(player, "bigbangregions.admin.create")).thenReturn(true);
        // Set mock command argument "id" -> "testReg"
        when(context.getArgument("id", String.class)).thenReturn("testReg");
        // Mock regionCache returns null (region does not exist)
        when(regionCache.get("testReg")).thenReturn(null);
        // Mock config for overlap check
        Config config = new Config();
        when(configManager.getConfig()).thenReturn(config);
        // Mock regionCache.getAll() returns empty (no overlap)
        when(regionCache.getAll()).thenReturn(java.util.Collections.emptyList());
    }

    @Test
    public void testScenario1_BothOverworld() {
        selectionManager.setPos1(playerUuid, new BlockPos(0, 0, 0), "minecraft:overworld");
        selectionManager.setPos2(playerUuid, new BlockPos(10, 10, 10), "minecraft:overworld");

        int result = RegionsCommand.createAdmin(context, 100);
        assertEquals(1, result);

        ArgumentCaptor<Region> regionCaptor = ArgumentCaptor.forClass(Region.class);
        verify(regionRepository).save(regionCaptor.capture());
        verify(regionCache).add(any());
        verify(auditService).log(eq("testReg"), eq(playerUuid), eq("CREATE_REGION"), any(), any(), any());

        Region saved = regionCaptor.getValue();
        assertEquals("minecraft:overworld", saved.getBounds().getDimension());
    }

    @Test
    public void testScenario2_BothNether() {
        selectionManager.setPos1(playerUuid, new BlockPos(0, 0, 0), "minecraft:the_nether");
        selectionManager.setPos2(playerUuid, new BlockPos(10, 10, 10), "minecraft:the_nether");

        int result = RegionsCommand.createAdmin(context, 100);
        assertEquals(1, result);

        ArgumentCaptor<Region> regionCaptor = ArgumentCaptor.forClass(Region.class);
        verify(regionRepository).save(regionCaptor.capture());

        Region saved = regionCaptor.getValue();
        assertEquals("minecraft:the_nether", saved.getBounds().getDimension());
    }

    @Test
    public void testScenario3_OverworldAndNetherFails() {
        selectionManager.setPos1(playerUuid, new BlockPos(0, 0, 0), "minecraft:overworld");
        selectionManager.setPos2(playerUuid, new BlockPos(10, 10, 10), "minecraft:the_nether");

        int result = RegionsCommand.createAdmin(context, 100);
        assertEquals(0, result);

        verify(source).sendFailure(any());
        verify(regionRepository, never()).save(any());
        verify(regionCache, never()).add(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testScenario4_NetherAndEndFails() {
        selectionManager.setPos1(playerUuid, new BlockPos(0, 0, 0), "minecraft:the_nether");
        selectionManager.setPos2(playerUuid, new BlockPos(10, 10, 10), "minecraft:the_end");

        int result = RegionsCommand.createAdmin(context, 100);
        assertEquals(0, result);

        verify(source).sendFailure(any());
        verify(regionRepository, never()).save(any());
        verify(regionCache, never()).add(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testScenario5_SelectOverworldExecuteNether() {
        // Player current dimension is nether, but selection is Overworld
        net.minecraft.world.level.Level level = mock(net.minecraft.world.level.Level.class);
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> resourceKey = mock(net.minecraft.resources.ResourceKey.class);
        net.minecraft.resources.ResourceLocation location = mock(net.minecraft.resources.ResourceLocation.class);
        when(location.toString()).thenReturn("minecraft:the_nether");
        when(resourceKey.location()).thenReturn(location);
        when(level.dimension()).thenReturn(resourceKey);
        when(player.level()).thenReturn(level);

        selectionManager.setPos1(playerUuid, new BlockPos(0, 0, 0), "minecraft:overworld");
        selectionManager.setPos2(playerUuid, new BlockPos(10, 10, 10), "minecraft:overworld");

        int result = RegionsCommand.createAdmin(context, 100);
        assertEquals(1, result);

        ArgumentCaptor<Region> regionCaptor = ArgumentCaptor.forClass(Region.class);
        verify(regionRepository).save(regionCaptor.capture());

        Region saved = regionCaptor.getValue();
        // Dimension key must be Overworld, not player's current Nether!
        assertEquals("minecraft:overworld", saved.getBounds().getDimension());
    }
}
