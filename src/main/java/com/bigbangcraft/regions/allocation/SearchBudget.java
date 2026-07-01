package com.bigbangcraft.regions.allocation;

public record SearchBudget(int maxSamples, int maxLocateCalls) {
    public static SearchBudget unbounded() {
        return new SearchBudget(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public boolean hasSamplesRemaining() {
        return maxSamples > 0;
    }

    public boolean hasLocateCallsRemaining() {
        return maxLocateCalls > 0;
    }
}
