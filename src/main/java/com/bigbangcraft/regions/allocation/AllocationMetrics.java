package com.bigbangcraft.regions.allocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public final class AllocationMetrics {
    private static final ConcurrentHashMap<String, LongAdder> COUNTERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> GAUGES = new ConcurrentHashMap<>();

    private AllocationMetrics() {
    }

    public static void increment(String name) {
        COUNTERS.computeIfAbsent(name, ignored -> new LongAdder()).increment();
    }

    public static void add(String name, long value) {
        COUNTERS.computeIfAbsent(name, ignored -> new LongAdder()).add(value);
    }

    public static void setGauge(String name, long value) {
        GAUGES.computeIfAbsent(name, ignored -> new AtomicLong()).set(value);
    }

    public static long counterValue(String name) {
        LongAdder adder = COUNTERS.get(name);
        return adder == null ? 0L : adder.sum();
    }

    public static long gaugeValue(String name) {
        AtomicLong gauge = GAUGES.get(name);
        return gauge == null ? 0L : gauge.get();
    }

    public static Map<String, Long> snapshot() {
        Map<String, Long> counters = COUNTERS.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().sum()));
        GAUGES.forEach((name, value) -> counters.put(name, value.get()));
        return counters;
    }
}
