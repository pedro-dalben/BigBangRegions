package com.bigbangcraft.regions.protection;

import com.bigbangcraft.regions.domain.RegionRole;

public class RegionRolePolicy {
    public static boolean isAllowed(RegionRole role, RegionAction action) {
        if (action == RegionAction.PVP) {
            // PVP is not decided by role in this phase
            return true;
        }
        if (action == RegionAction.FIRE_SPREAD) {
            // Fire spread is decided by region flags, not by membership role.
            return true;
        }
        if (action == RegionAction.FIRE_BLOCK_DAMAGE) {
            // Fire block damage is decided by region flags, not by membership role.
            return true;
        }
        if (action == RegionAction.WATER_FLOW) {
            // Water flow is decided by region flags, not by membership role.
            return true;
        }
        if (action == RegionAction.LAVA_FLOW) {
            // Lava flow is decided by region flags, not by membership role.
            return true;
        }
        if (action == RegionAction.EXPLOSION_BLOCK_DAMAGE) {
            // Explosion damage is decided by region flags, not by membership role.
            return true;
        }
        if (action == RegionAction.PISTON_MOVE) {
            // Piston movement is decided by region flags, not by membership role.
            return true;
        }
        if (action == RegionAction.MOB_GRIEFING) {
            // Mob griefing is decided by region flags, not by membership role.
            return true;
        }
        return role != null && role.isAtLeast(RegionRole.MEMBER);
    }
}
