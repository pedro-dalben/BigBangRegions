package com.bigbangcraft.regions.flag;

public enum FlagPolicy {
    ALLOW,
    DENY,
    INHERIT;

    public static FlagPolicy parse(String val) {
        if (val == null) return INHERIT;
        try {
            return valueOf(val.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INHERIT;
        }
    }
}
