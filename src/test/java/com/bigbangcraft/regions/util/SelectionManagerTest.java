package com.bigbangcraft.regions.util;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class SelectionManagerTest {
    private SelectionManager selectionManager;
    private UUID playerId;

    @BeforeEach
    public void setUp() {
        selectionManager = new SelectionManager();
        playerId = UUID.randomUUID();
    }

    @Test
    public void testDimensionAwareSelection() {
        BlockPos p1 = new BlockPos(10, 64, 10);
        BlockPos p2 = new BlockPos(20, 70, 20);

        selectionManager.setPos1(playerId, p1, "minecraft:overworld");
        selectionManager.setPos2(playerId, p2, "minecraft:the_nether");

        assertTrue(selectionManager.hasSelection(playerId));

        SelectionManager.Selection sel1 = selectionManager.getPos1(playerId);
        SelectionManager.Selection sel2 = selectionManager.getPos2(playerId);

        assertNotNull(sel1);
        assertNotNull(sel2);

        assertEquals(p1, sel1.getPos());
        assertEquals("minecraft:overworld", sel1.getDimension());

        assertEquals(p2, sel2.getPos());
        assertEquals("minecraft:the_nether", sel2.getDimension());

        // Test clear
        selectionManager.clear(playerId);
        assertFalse(selectionManager.hasSelection(playerId));
        assertNull(selectionManager.getPos1(playerId));
        assertNull(selectionManager.getPos2(playerId));
    }
}
