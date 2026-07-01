package com.bigbangcraft.regions.allocation;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public class RegionPreparationQueue {
    private final EnumMap<RegionPreparationPriority, Queue<String>> queues = new EnumMap<>(RegionPreparationPriority.class);
    private final Set<String> enqueued = new HashSet<>();
    private String activeRequestId;

    public RegionPreparationQueue() {
        for (RegionPreparationPriority priority : RegionPreparationPriority.values()) {
            queues.put(priority, new ArrayDeque<>());
        }
    }

    public synchronized boolean enqueue(String requestId, RegionPreparationPriority priority) {
        if (requestId.equals(activeRequestId) || !enqueued.add(requestId)) {
            return false;
        }
        queues.get(priority).add(requestId);
        AllocationMetrics.setGauge("bigbangregions_prepare_queue_size", size());
        return true;
    }

    public synchronized boolean isQueued(String requestId) {
        return enqueued.contains(requestId);
    }

    public synchronized Optional<String> activeRequestId() {
        return Optional.ofNullable(activeRequestId);
    }

    public synchronized Optional<String> tryStartNext() {
        if (activeRequestId != null) {
            return Optional.empty();
        }
        for (RegionPreparationPriority priority : RegionPreparationPriority.values()) {
            String next = queues.get(priority).poll();
            if (next != null) {
                enqueued.remove(next);
                activeRequestId = next;
                AllocationMetrics.setGauge("bigbangregions_prepare_active_total", 1);
                AllocationMetrics.setGauge("bigbangregions_prepare_queue_size", size());
                return Optional.of(next);
            }
        }
        return Optional.empty();
    }

    public synchronized void complete(String requestId) {
        if (requestId.equals(activeRequestId)) {
            activeRequestId = null;
        }
        AllocationMetrics.setGauge("bigbangregions_prepare_active_total", activeRequestId == null ? 0 : 1);
        AllocationMetrics.setGauge("bigbangregions_prepare_queue_size", size());
    }

    public synchronized void remove(String requestId) {
        if (requestId.equals(activeRequestId)) {
            activeRequestId = null;
        }
        if (enqueued.remove(requestId)) {
            for (Queue<String> queue : queues.values()) {
                queue.remove(requestId);
            }
        }
        AllocationMetrics.setGauge("bigbangregions_prepare_active_total", activeRequestId == null ? 0 : 1);
        AllocationMetrics.setGauge("bigbangregions_prepare_queue_size", size());
    }

    public synchronized int size() {
        int size = activeRequestId == null ? 0 : 1;
        for (Queue<String> queue : queues.values()) {
            size += queue.size();
        }
        return size;
    }
}
