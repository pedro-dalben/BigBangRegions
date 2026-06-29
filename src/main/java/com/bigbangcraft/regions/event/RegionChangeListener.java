package com.bigbangcraft.regions.event;

@FunctionalInterface
public interface RegionChangeListener {
    void onRegionChange(RegionChangeEvent event);
}
