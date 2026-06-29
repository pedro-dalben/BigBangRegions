package com.bigbangcraft.regions.domain;

public enum RegionRole {
    OWNER(4),
    LEADER(3),
    MANAGER(2),
    MEMBER(1),
    VISITOR(0);

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
