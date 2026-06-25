package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class RegionRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;
    private RegionRepository repository;

    @BeforeEach
    public void setUp() throws Exception {
        dbManager = new DatabaseManager(tempDir.resolve("regions.db"));
        dbManager.initialize();
        repository = new RegionRepository(dbManager);
    }

    @AfterEach
    public void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    public void testCrudOperations() {
        UUID creator = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();

        RegionBounds bounds = new RegionBounds("overworld", 0, 0, 0, 100, 100, 100);
        Region region = new Region("reg1", "Main Region", RegionType.PLAYER_REGION, bounds, 100, owner, creator, 1000L, 1000L, "ACTIVE");
        region.setMember(member, RegionRole.MEMBER);
        region.setFlag("pvp", "DENY");
        region.setFlag("player-build", "ALLOW");

        // Create / Save
        repository.save(region);

        // Load & Read
        List<Region> loaded = repository.loadAll();
        assertEquals(1, loaded.size());
        
        Region reg = loaded.get(0);
        assertEquals("reg1", reg.getId());
        assertEquals("Main Region", reg.getName());
        assertEquals(RegionType.PLAYER_REGION, reg.getType());
        assertEquals(owner, reg.getOwnerUuid());
        assertEquals(creator, reg.getCreatedByUuid());
        assertEquals(1000L, reg.getCreatedAt());
        assertEquals("ACTIVE", reg.getStatus());

        // Check members
        assertEquals(RegionRole.MEMBER, reg.getRole(member));
        assertEquals(RegionRole.OWNER, reg.getRole(owner));

        // Check flags
        assertEquals("DENY", reg.getFlagValue("pvp"));
        assertEquals("ALLOW", reg.getFlagValue("player-build"));

        // Update
        reg.setFlag("pvp", "ALLOW");
        reg.setMember(member, RegionRole.LEADER);
        repository.save(reg);

        List<Region> updatedList = repository.loadAll();
        assertEquals(1, updatedList.size());
        Region updated = updatedList.get(0);
        assertEquals("ALLOW", updated.getFlagValue("pvp"));
        assertEquals(RegionRole.LEADER, updated.getRole(member));

        // Delete
        repository.delete("reg1");
        assertTrue(repository.loadAll().isEmpty());
    }
}
