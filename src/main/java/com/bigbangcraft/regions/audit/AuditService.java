package com.bigbangcraft.regions.audit;

import com.bigbangcraft.regions.repository.AuditRepository;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuditService {
    private final AuditRepository auditRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BigBangRegions-AuditExecutor");
        thread.setDaemon(true);
        return thread;
    });

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void log(String regionId, UUID actorUuid, String action, String beforeValue, String afterValue, String metadataJson) {
        executor.submit(() -> {
            auditRepository.log(regionId, actorUuid, action, beforeValue, afterValue, metadataJson);
        });
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
