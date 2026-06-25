package com.bigbangcraft.regions.protection;

public enum RegionAction {
    BLOCK_BREAK("player-build"),
    BLOCK_PLACE("player-build"),
    INTERACT("player-interact"),
    CONTAINER("container-access"),
    DOOR("door-use"),
    REDSTONE("redstone-use"),
    ENTITY_INTERACT("entity-interact"),
    PVP("pvp"),
    ITEM_PICKUP("item-pickup"),
    ITEM_DROP("item-drop");

    private final String flagId;

    RegionAction(String flagId) {
        this.flagId = flagId;
    }

    public String getFlagId() {
        return flagId;
    }
}
