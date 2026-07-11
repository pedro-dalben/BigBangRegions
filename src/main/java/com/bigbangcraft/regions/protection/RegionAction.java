package com.bigbangcraft.regions.protection;

public enum RegionAction {
    BLOCK_BREAK("visitor-build"),
    BLOCK_PLACE("visitor-build"),
    INTERACT("visitor-usage"),
    CONTAINER("visitor-usage"),
    DOOR("visitor-usage"),
    REDSTONE("visitor-usage"),
    ENTITY_INTERACT("visitor-item-frames"),
    PVP("pvp"),
    FIRE_SPREAD("fire-spread"),
    FIRE_BLOCK_DAMAGE("fire-block-damage"),
    WATER_FLOW("water-flow"),
    LAVA_FLOW("lava-flow"),
    EXPLOSION_BLOCK_DAMAGE("explosion-block-damage"),
    PISTON_MOVE("piston-move"),
    MOB_GRIEFING("mob-griefing"),
    ITEM_PICKUP("visitor-pickup-items"),
    ITEM_DROP("visitor-drop-items"),
    FALL_DAMAGE("fall-damage");

    private final String flagId;

    RegionAction(String flagId) {
        this.flagId = flagId;
    }

    public String getFlagId() {
        return flagId;
    }
}
