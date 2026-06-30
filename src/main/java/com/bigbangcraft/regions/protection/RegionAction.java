package com.bigbangcraft.regions.protection;

public enum RegionAction {
    BLOCK_BREAK("visitor-build"),
    BLOCK_PLACE("visitor-build"),
    INTERACT("visitor-interact"),
    CONTAINER("visitor-containers"),
    DOOR("visitor-doors"),
    REDSTONE("visitor-redstone"),
    ENTITY_INTERACT("visitor-item-frames"),
    PVP("pvp"),
    ITEM_PICKUP("visitor-pickup-items"),
    ITEM_DROP("visitor-drop-items");

    private final String flagId;

    RegionAction(String flagId) {
        this.flagId = flagId;
    }

    public String getFlagId() {
        return flagId;
    }
}
