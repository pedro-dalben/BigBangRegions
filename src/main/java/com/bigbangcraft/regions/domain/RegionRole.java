package com.bigbangcraft.regions.domain;

public enum RegionRole {
    OWNER(4),
    LEADER(3),
    MEMBER(2),
    VISITOR(1);

    private final int level;

    RegionRole(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean isAtLeast(RegionRole other) {
        return this.level >= other.level;
    }
}
