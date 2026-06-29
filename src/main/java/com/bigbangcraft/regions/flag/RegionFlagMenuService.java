package com.bigbangcraft.regions.flag;

import com.bigbangcraft.regions.domain.Region;
import com.bigbangcraft.regions.gui.RegionGuiHandler;
import net.minecraft.server.level.ServerPlayer;

public class RegionFlagMenuService {
    public void openFlagsMenu(ServerPlayer player, Region region) {
        RegionGuiHandler.openFlagsMenu(player, region);
    }

    public void openCategoryMenu(ServerPlayer player, Region region, String category) {
        RegionGuiHandler.openFlagsCategoryMenu(player, region, category);
    }

    public boolean toggleBooleanFlag(Region region, String flagId) {
        String current = region.getFlagValue(flagId);
        String next = "ALLOW".equalsIgnoreCase(current) ? "DENY" : "ALLOW";
        region.setFlag(flagId, next);
        return true;
    }
}
