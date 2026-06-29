package com.bigbangcraft.regions.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RegionEventBus {
    private static final List<RegionChangeListener> listeners = new CopyOnWriteArrayList<>();

    public static void register(RegionChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void unregister(RegionChangeListener listener) {
        listeners.remove(listener);
    }

    public static void fire(RegionChangeEvent event) {
        for (RegionChangeListener listener : listeners) {
            try {
                listener.onRegionChange(event);
            } catch (Exception e) {
                // Log but don't propagate — prevent listener from crashing caller
            }
        }
    }
}
