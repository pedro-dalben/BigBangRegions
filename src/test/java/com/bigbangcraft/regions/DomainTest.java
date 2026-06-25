package com.bigbangcraft.regions;

import com.bigbangcraft.regions.cache.RegionCache;
import com.bigbangcraft.regions.config.Config;
import com.bigbangcraft.regions.config.ConfigManager;
import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.flag.EffectiveRegionPolicy;
import com.bigbangcraft.regions.flag.FlagPolicy;
import com.bigbangcraft.regions.flag.FlagResolver;
import com.bigbangcraft.regions.region.RegionResolver;
import com.bigbangcraft.regions.repository.AuditRepository;
import com.bigbangcraft.regions.repository.RegionRepository;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DomainTest {

    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;
    private RegionRepository regionRepository;
    private AuditRepository auditRepository;

    @BeforeEach
    public void setUp() throws Exception {
        // Use an in-memory SQLite database for unit tests to verify repository & SQLite integration
        dbManager = new DatabaseManager(tempDir.resolve("test.db"));
        dbManager.initialize();
        regionRepository = new RegionRepository(dbManager);
        auditRepository = new AuditRepository(dbManager);
    }

    @AfterEach
    public void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    public void testRegionBoundsContainsAndInclusive() {
        RegionBounds bounds = new RegionBounds("minecraft:overworld", 0, 0, 0, 10, 10, 10);
        
        // Exact corners
        assertTrue(bounds.contains("minecraft:overworld", 0, 0, 0));
        assertTrue(bounds.contains("minecraft:overworld", 10, 10, 10));
        
        // Inside
        assertTrue(bounds.contains("minecraft:overworld", 5, 5, 5));
        
        // Outside
        assertFalse(bounds.contains("minecraft:overworld", -1, 5, 5));
        assertFalse(bounds.contains("minecraft:overworld", 5, 11, 5));
        assertFalse(bounds.contains("minecraft:overworld", 5, 5, 15));
        
        // Wrong dimension
        assertFalse(bounds.contains("minecraft:the_nether", 5, 5, 5));
    }

    @Test
    public void testRegionResolverPriorityAndTieBreaking() {
        RegionCache cache = new RegionCache();
        RegionResolver resolver = new RegionResolver(cache);

        UUID creator = UUID.randomUUID();

        // Region A: admin region, priority 1000, larger
        RegionBounds boundsA = new RegionBounds("overworld", 0, 0, 0, 10, 10, 10);
        Region regA = new Region("regA", "Region A", RegionType.ADMIN_REGION, boundsA, 1000, null, creator, 0, 0, "ACTIVE");

        // Region B: player region, priority 100, smaller, inside A
        RegionBounds boundsB = new RegionBounds("overworld", 2, 2, 2, 5, 5, 5);
        Region regB = new Region("regB", "Region B", RegionType.PLAYER_REGION, boundsB, 100, null, creator, 0, 0, "ACTIVE");

        cache.add(regA);
        cache.add(regB);

        // Resolve at (3,3,3) where A and B overlap. A should win because priority 1000 > 100.
        Optional<Region> resolved = resolver.resolveRegionAt("overworld", 3, 3, 3);
        assertTrue(resolved.isPresent());
        assertEquals("regA", resolved.get().getId());

        // Now test tie-breaker: same priority, different size
        cache.clear();
        
        // Reg C: priority 500, larger (volume 1000)
        RegionBounds boundsC = new RegionBounds("overworld", 0, 0, 0, 9, 9, 9);
        Region regC = new Region("regC", "Reg C", RegionType.ADMIN_REGION, boundsC, 500, null, creator, 0, 0, "ACTIVE");

        // Reg D: priority 500, smaller (volume 8)
        RegionBounds boundsD = new RegionBounds("overworld", 1, 1, 1, 2, 2, 2);
        Region regD = new Region("regD", "Reg D", RegionType.ADMIN_REGION, boundsD, 500, null, creator, 0, 0, "ACTIVE");

        cache.add(regC);
        cache.add(regD);

        // Resolve at (2,2,2) where C and D overlap. D should win because it is smaller in volume.
        Optional<Region> resolvedTie = resolver.resolveRegionAt("overworld", 2, 2, 2);
        assertTrue(resolvedTie.isPresent());
        assertEquals("regD", resolvedTie.get().getId());

        // Now test tie-breaker: same priority, same size, different ID
        cache.clear();

        // Reg E: ID 'regE', priority 500, volume 8
        RegionBounds boundsE = new RegionBounds("overworld", 1, 1, 1, 2, 2, 2);
        Region regE = new Region("regE", "Reg E", RegionType.ADMIN_REGION, boundsE, 500, null, creator, 0, 0, "ACTIVE");

        // Reg F: ID 'regF', priority 500, volume 8
        RegionBounds boundsF = new RegionBounds("overworld", 1, 1, 1, 2, 2, 2);
        Region regF = new Region("regF", "Reg F", RegionType.ADMIN_REGION, boundsF, 500, null, creator, 0, 0, "ACTIVE");

        cache.add(regE);
        cache.add(regF);

        // Alphabetically regE < regF, so regE wins.
        Optional<Region> resolvedAlphabetical = resolver.resolveRegionAt("overworld", 2, 2, 2);
        assertTrue(resolvedAlphabetical.isPresent());
        assertEquals("regE", resolvedAlphabetical.get().getId());
    }

    @Test
    public void testOverlapRejection() {
        RegionCache cache = new RegionCache();
        RegionResolver resolver = new RegionResolver(cache);

        UUID creator = UUID.randomUUID();
        RegionBounds boundsA = new RegionBounds("overworld", 0, 0, 0, 10, 10, 10);
        Region regA = new Region("regA", "Region A", RegionType.PLAYER_REGION, boundsA, 100, null, creator, 0, 0, "ACTIVE");
        cache.add(regA);

        // Overlapping bounds
        RegionBounds boundsOverlapping = new RegionBounds("overworld", 5, 5, 5, 15, 15, 15);
        assertTrue(resolver.checkOverlap(boundsOverlapping, "regB"));

        // Non-overlapping bounds
        RegionBounds boundsNonOverlapping = new RegionBounds("overworld", 20, 20, 20, 30, 30, 30);
        assertFalse(resolver.checkOverlap(boundsNonOverlapping, "regB"));
    }

    @Test
    public void testFlagResolverCascade() {
        FlagResolver resolver = new FlagResolver();
        Config config = new Config(); // Defaults loaded: build=ALLOW for global, DENY for adminRegion

        UUID creator = UUID.randomUUID();
        RegionBounds bounds = new RegionBounds("overworld", 0, 0, 0, 10, 10, 10);
        Region region = new Region("regA", "Region A", RegionType.ADMIN_REGION, bounds, 1000, null, creator, 0, 0, "ACTIVE");

        // 1. Unset flag on region (INHERIT) -> falls back to adminRegion type default (DENY)
        EffectiveRegionPolicy p1 = resolver.resolve(region, "player-build", config);
        assertEquals(FlagPolicy.DENY, p1.policy());
        assertEquals("region_type_default", p1.source());

        // 2. Explicit ALLOW on region
        region.setFlag("player-build", "ALLOW");
        EffectiveRegionPolicy p2 = resolver.resolve(region, "player-build", config);
        assertEquals(FlagPolicy.ALLOW, p2.policy());
        assertEquals("region_explicit", p2.source());

        // 3. Explicit DENY on region
        region.setFlag("player-build", "DENY");
        EffectiveRegionPolicy p3 = resolver.resolve(region, "player-build", config);
        assertEquals(FlagPolicy.DENY, p3.policy());
        assertEquals("region_explicit", p3.source());

        // 4. Global default for a flag that has no type-specific default
        // Let's check a non-region resolution
        EffectiveRegionPolicy pGlobal = resolver.resolve(null, "player-build", config);
        assertEquals(FlagPolicy.ALLOW, pGlobal.policy());
        assertEquals("global_default", pGlobal.source());
    }

    @Test
    public void testCacheAndSpatialIndex() {
        RegionCache cache = new RegionCache();
        UUID creator = UUID.randomUUID();

        // Region crossing chunk boundaries: (0,0,0) to (20,0,20)
        // covers chunk (0,0), (1,0), (0,1), (1,1)
        RegionBounds bounds = new RegionBounds("overworld", 0, 0, 0, 20, 0, 20);
        Region region = new Region("regA", "Region A", RegionType.ADMIN_REGION, bounds, 1000, null, creator, 0, 0, "ACTIVE");
        
        cache.add(region);

        // Retrieve at chunk (0,0)
        List<Region> list = cache.getRegionsAt("overworld", 5, 0, 5);
        assertEquals(1, list.size());
        assertEquals("regA", list.get(0).getId());

        // Retrieve at chunk (1,1)
        List<Region> list2 = cache.getRegionsAt("overworld", 18, 0, 18);
        assertEquals(1, list2.size());

        // Retrieve at chunk (2,2) - outside bounds
        List<Region> listOutside = cache.getRegionsAt("overworld", 35, 0, 35);
        assertTrue(listOutside.isEmpty());

        // Invalidation: remove from cache
        cache.remove("regA");
        assertTrue(cache.getRegionsAt("overworld", 5, 0, 5).isEmpty());
    }

    @Test
    public void testSQLitePersistenceAndAudit() throws Exception {
        UUID creator = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        
        RegionBounds bounds = new RegionBounds("overworld", 0, 0, 0, 10, 10, 10);
        Region region = new Region("regA", "Region A", RegionType.ADMIN_REGION, bounds, 1000, null, creator, 123456L, 123456L, "ACTIVE");
        region.setMember(member, RegionRole.MEMBER);
        region.setFlag("pvp", "DENY");

        // Save
        regionRepository.save(region);

        // Load and check
        List<Region> loaded = regionRepository.loadAll();
        assertEquals(1, loaded.size());
        Region loadedReg = loaded.get(0);

        assertEquals("regA", loadedReg.getId());
        assertEquals("Region A", loadedReg.getName());
        assertEquals(RegionType.ADMIN_REGION, loadedReg.getType());
        assertEquals(1000, loadedReg.getPriority());
        assertNull(loadedReg.getOwnerUuid());
        assertEquals(creator, loadedReg.getCreatedByUuid());
        assertEquals(123456L, loadedReg.getCreatedAt());
        assertEquals("ACTIVE", loadedReg.getStatus());
        
        // Members and flags
        assertEquals(RegionRole.MEMBER, loadedReg.getRole(member));
        assertEquals("DENY", loadedReg.getFlagValue("pvp"));

        // Audit log
        auditRepository.log("regA", creator, "CREATE_REGION", null, "ADMIN_REGION", "{\"test\":true}");
        
        // Query audit manually to confirm write
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM region_audit_logs;")) {
            assertTrue(rs.next());
            assertEquals("regA", rs.getString("regionId"));
            assertEquals(creator.toString(), rs.getString("actorUuid"));
            assertEquals("CREATE_REGION", rs.getString("action"));
            assertEquals("{\"test\":true}", rs.getString("metadataJson"));
            assertFalse(rs.next());
        }

        // Delete
        regionRepository.delete("regA");
        assertTrue(regionRepository.loadAll().isEmpty());
    }

    @Test
    public void testConfigSafeFallback() throws IOException {
        // Construct ConfigManager with path to a non-existent dir/file
        Path testConfigDir = tempDir.resolve("config_fallback");
        ConfigManager configManager = new ConfigManager(testConfigDir);
        
        // Must load defaults first (file doesn't exist)
        configManager.load();
        
        assertNotNull(configManager.getConfig());
        assertEquals(1, configManager.getConfig().getSchemaVersion());
        assertEquals(1000, configManager.getConfig().getDefaultPriorities().getAdminRegion());

        // Now write garbage data to config file and load again
        Path configFile = testConfigDir.resolve("config.json");
        Files.writeString(configFile, "{ invalid json garbage }");

        // Load again. It should print error, NOT crash, and keep using defaults (fallback)
        ConfigManager brokenManager = new ConfigManager(testConfigDir);
        brokenManager.load();
        
        assertNotNull(brokenManager.getConfig());
        assertEquals(1000, brokenManager.getConfig().getDefaultPriorities().getAdminRegion());
        // Original file should remain intact (not deleted)
        assertTrue(Files.exists(configFile));
        assertEquals("{ invalid json garbage }", Files.readString(configFile).trim());
    }
}
