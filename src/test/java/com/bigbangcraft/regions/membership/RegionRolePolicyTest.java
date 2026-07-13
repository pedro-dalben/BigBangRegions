package com.bigbangcraft.regions.membership;

import com.bigbangcraft.regions.domain.RegionRole;
import com.bigbangcraft.regions.protection.RegionAction;
import com.bigbangcraft.regions.protection.RegionRolePolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RegionRolePolicyTest {

    @Test
    public void testRolePolicyRules() {
        for (RegionAction action : RegionAction.values()) {
            if (action == RegionAction.PVP ||
                action == RegionAction.FIRE_SPREAD ||
                action == RegionAction.FIRE_BLOCK_DAMAGE ||
                action == RegionAction.WATER_FLOW ||
                action == RegionAction.LAVA_FLOW ||
                action == RegionAction.EXPLOSION_BLOCK_DAMAGE ||
                action == RegionAction.PISTON_MOVE ||
                action == RegionAction.MOB_GRIEFING ||
                action == RegionAction.LEAF_DECAY ||
                action == RegionAction.ICE_MELT) {
                assertTrue(RegionRolePolicy.isAllowed(RegionRole.VISITOR, action));
                assertTrue(RegionRolePolicy.isAllowed(RegionRole.MEMBER, action));
                assertTrue(RegionRolePolicy.isAllowed(RegionRole.LEADER, action));
                assertTrue(RegionRolePolicy.isAllowed(RegionRole.OWNER, action));
            } else {
                assertFalse(RegionRolePolicy.isAllowed(RegionRole.VISITOR, action));
                assertTrue(RegionRolePolicy.isAllowed(RegionRole.MEMBER, action));
                assertTrue(RegionRolePolicy.isAllowed(RegionRole.LEADER, action));
                assertTrue(RegionRolePolicy.isAllowed(RegionRole.OWNER, action));
            }
        }
    }
}
