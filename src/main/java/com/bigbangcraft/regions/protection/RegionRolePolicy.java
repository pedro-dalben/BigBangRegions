package com.bigbangcraft.regions.protection;

import com.bigbangcraft.regions.domain.RegionRole;

public class RegionRolePolicy {
    public static boolean isAllowed(RegionRole role, RegionAction action) {
        if (action == RegionAction.PVP) {
            // PVP is not decided by role in this phase
            return true;
        }
        return role != null && role.isAtLeast(RegionRole.MEMBER);
    }
}
