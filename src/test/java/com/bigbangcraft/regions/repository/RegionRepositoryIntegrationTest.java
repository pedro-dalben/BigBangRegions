package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionMember;
import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.domain.RegionType;
import com.bigbangcraft.regions.storage.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        Map<UUID, RegionMember> members = new HashMap<>();
        members.put(member, new RegionMember(member, RegionRole.MEMBER, creator, 1000L, 1000L));
        Region region = new Region("reg1", "Main Region", RegionType.PLAYER_REGION, bounds, 100, owner, creator, 1000L, 1000L, "ACTIVE", members);
        region.setFlag("pvp", "DENY");
        region.setFlag("player-build", "ALLOW");

        // Create / Save
        repository.save(region);
        repository.saveMembers(region.getId(), region.getMembers());

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
        repository.save(reg);
        Map<UUID, RegionMember> updatedMembers = new HashMap<>(reg.getMembers());
        updatedMembers.put(member, new RegionMember(member, RegionRole.LEADER, creator, 1000L, System.currentTimeMillis()));
        repository.saveMembers("reg1", updatedMembers);

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
