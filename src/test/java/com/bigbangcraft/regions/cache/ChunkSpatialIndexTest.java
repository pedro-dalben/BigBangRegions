package com.bigbangcraft.regions.cache;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.domain.RegionBounds;
import com.bigbangcraft.regions.domain.RegionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class ChunkSpatialIndexTest {
    private ChunkSpatialIndex spatialIndex;
    private UUID creator;

    @BeforeEach
    public void setUp() {
        spatialIndex = new ChunkSpatialIndex();
        creator = UUID.randomUUID();
    }

    @Test
    public void testCrossChunkMapping() {
        // Region: min=(0,0,0) max=(31,0,31)
        // covers X: [0..31] which maps to chunkX [0..1]
        // covers Z: [0..31] which maps to chunkZ [0..1]
        // covers 4 chunks: (0,0), (0,1), (1,0), (1,1)
        RegionBounds bounds = new RegionBounds("overworld", 0, 0, 0, 31, 0, 31);
        Region region = new Region("regA", "Region A", RegionType.ADMIN_REGION, bounds, 1000, null, creator, 0, 0, "ACTIVE");

        spatialIndex.add(region);

        // Chunks inside the region
        assertTrue(spatialIndex.getRegionIdsInChunk("overworld", 0, 0).contains("regA"));
        assertTrue(spatialIndex.getRegionIdsInChunk("overworld", 0, 1).contains("regA"));
        assertTrue(spatialIndex.getRegionIdsInChunk("overworld", 1, 0).contains("regA"));
        assertTrue(spatialIndex.getRegionIdsInChunk("overworld", 1, 1).contains("regA"));

        // Chunk outside the region
        assertFalse(spatialIndex.getRegionIdsInChunk("overworld", 2, 2).contains("regA"));
        
        // Wrong dimension
        assertTrue(spatialIndex.getRegionIdsInChunk("nether", 0, 0).isEmpty());
    }

    @Test
    public void testRemoveUpdatesIndex() {
        RegionBounds bounds = new RegionBounds("overworld", 0, 0, 0, 15, 0, 15); // chunk (0,0)
        Region region = new Region("regA", "Region A", RegionType.ADMIN_REGION, bounds, 1000, null, creator, 0, 0, "ACTIVE");

        spatialIndex.add(region);
        assertTrue(spatialIndex.getRegionIdsInChunk("overworld", 0, 0).contains("regA"));

        spatialIndex.remove("regA");
        assertFalse(spatialIndex.getRegionIdsInChunk("overworld", 0, 0).contains("regA"));
        assertTrue(spatialIndex.getRegionIdsInChunk("overworld", 0, 0).isEmpty());
    }

    @Test
    public void testUpdateReplacesIndex() {
        RegionBounds bounds1 = new RegionBounds("overworld", 0, 0, 0, 15, 0, 15); // chunk (0,0)
        Region region1 = new Region("regA", "Region A", RegionType.ADMIN_REGION, bounds1, 1000, null, creator, 0, 0, "ACTIVE");

        spatialIndex.add(region1);
        assertTrue(spatialIndex.getRegionIdsInChunk("overworld", 0, 0).contains("regA"));

        // Update region to be in chunk (2,2)
        RegionBounds bounds2 = new RegionBounds("overworld", 32, 0, 32, 47, 0, 47); // chunk (2,2)
        Region region2 = new Region("regA", "Region A", RegionType.ADMIN_REGION, bounds2, 1000, null, creator, 0, 0, "ACTIVE");

        spatialIndex.add(region2);

        // Should no longer be in (0,0)
        assertFalse(spatialIndex.getRegionIdsInChunk("overworld", 0, 0).contains("regA"));
        // Should now be in (2,2)
        assertTrue(spatialIndex.getRegionIdsInChunk("overworld", 2, 2).contains("regA"));
    }
}
