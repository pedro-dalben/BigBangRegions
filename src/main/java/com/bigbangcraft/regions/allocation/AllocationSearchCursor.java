package com.bigbangcraft.regions.allocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class AllocationSearchCursor {
    private final String requestId;
    private String currentBandId;
    private int currentSectorIndex;
    private int sectorX;
    private int sectorZ;
    private int anchorAttempt;
    private int localCandidateIndex;
    private int totalSectorsChecked;
    private int totalVirtualCandidatesChecked;
    private int totalBiomeSamples;
    private int sectorsDiscarded;
    private int anchorsFound;
    private int locateCallsUsed;
    private int anchorSearchYIndex;
    private int anchorSearchRingQuart;
    private int anchorSearchPointIndex;
    private int anchorSearchIntervalQuart;
    private Integer currentAnchorX;
    private Integer currentAnchorY;
    private Integer currentAnchorZ;
    private String currentAnchorBiomeId;
    private long lastProgressAt;
    private String lastRejectionReason;
    private String fallbackMode;
    private final Map<String, LongAdder> rejectionCounts = new ConcurrentHashMap<>();

    public AllocationSearchCursor(String requestId) {
        this.requestId = requestId;
    }

    public String getRequestId() { return requestId; }
    public String getCurrentBandId() { return currentBandId; }
    public int getCurrentSectorIndex() { return currentSectorIndex; }
    public int getSectorX() { return sectorX; }
    public int getSectorZ() { return sectorZ; }
    public int getAnchorAttempt() { return anchorAttempt; }
    public int getLocalCandidateIndex() { return localCandidateIndex; }
    public int getTotalSectorsChecked() { return totalSectorsChecked; }
    public int getTotalVirtualCandidatesChecked() { return totalVirtualCandidatesChecked; }
    public int getTotalBiomeSamples() { return totalBiomeSamples; }
    public int getSectorsDiscarded() { return sectorsDiscarded; }
    public int getAnchorsFound() { return anchorsFound; }
    public int getLocateCallsUsed() { return locateCallsUsed; }
    public int getAnchorSearchYIndex() { return anchorSearchYIndex; }
    public int getAnchorSearchRingQuart() { return anchorSearchRingQuart; }
    public int getAnchorSearchPointIndex() { return anchorSearchPointIndex; }
    public int getAnchorSearchIntervalQuart() { return anchorSearchIntervalQuart; }
    public Integer getCurrentAnchorX() { return currentAnchorX; }
    public Integer getCurrentAnchorY() { return currentAnchorY; }
    public Integer getCurrentAnchorZ() { return currentAnchorZ; }
    public String getCurrentAnchorBiomeId() { return currentAnchorBiomeId; }
    public long getLastProgressAt() { return lastProgressAt; }
    public String getLastRejectionReason() { return lastRejectionReason; }
    public String getFallbackMode() { return fallbackMode; }
    public Map<String, LongAdder> getRejectionCounts() { return rejectionCounts; }

    public void incrementRejection(String reason) {
        rejectionCounts.computeIfAbsent(reason, k -> new LongAdder()).increment();
    }

    public void setCurrentBandId(String currentBandId) { this.currentBandId = currentBandId; }
    public void setCurrentSectorIndex(int currentSectorIndex) { this.currentSectorIndex = currentSectorIndex; }
    public void setSectorX(int sectorX) { this.sectorX = sectorX; }
    public void setSectorZ(int sectorZ) { this.sectorZ = sectorZ; }
    public void setAnchorAttempt(int anchorAttempt) { this.anchorAttempt = anchorAttempt; }
    public void setLocalCandidateIndex(int localCandidateIndex) { this.localCandidateIndex = localCandidateIndex; }
    public void setTotalSectorsChecked(int totalSectorsChecked) { this.totalSectorsChecked = totalSectorsChecked; }
    public void setTotalVirtualCandidatesChecked(int totalVirtualCandidatesChecked) { this.totalVirtualCandidatesChecked = totalVirtualCandidatesChecked; }
    public void setTotalBiomeSamples(int totalBiomeSamples) { this.totalBiomeSamples = totalBiomeSamples; }
    public void setSectorsDiscarded(int sectorsDiscarded) { this.sectorsDiscarded = sectorsDiscarded; }
    public void setAnchorsFound(int anchorsFound) { this.anchorsFound = anchorsFound; }
    public void setLocateCallsUsed(int locateCallsUsed) { this.locateCallsUsed = locateCallsUsed; }
    public void setAnchorSearchYIndex(int anchorSearchYIndex) { this.anchorSearchYIndex = anchorSearchYIndex; }
    public void setAnchorSearchRingQuart(int anchorSearchRingQuart) { this.anchorSearchRingQuart = anchorSearchRingQuart; }
    public void setAnchorSearchPointIndex(int anchorSearchPointIndex) { this.anchorSearchPointIndex = anchorSearchPointIndex; }
    public void setAnchorSearchIntervalQuart(int anchorSearchIntervalQuart) { this.anchorSearchIntervalQuart = anchorSearchIntervalQuart; }
    public void setCurrentAnchorX(Integer currentAnchorX) { this.currentAnchorX = currentAnchorX; }
    public void setCurrentAnchorY(Integer currentAnchorY) { this.currentAnchorY = currentAnchorY; }
    public void setCurrentAnchorZ(Integer currentAnchorZ) { this.currentAnchorZ = currentAnchorZ; }
    public void setCurrentAnchorBiomeId(String currentAnchorBiomeId) { this.currentAnchorBiomeId = currentAnchorBiomeId; }
    public void setLastProgressAt(long lastProgressAt) { this.lastProgressAt = lastProgressAt; }
    public void setLastRejectionReason(String lastRejectionReason) { this.lastRejectionReason = lastRejectionReason; }
    public void setFallbackMode(String fallbackMode) { this.fallbackMode = fallbackMode; }
}
