package com.bigbangcraft.regions.allocation;

@FunctionalInterface
public interface PreparationCompletionCallback {
    void onCompleted(PreparationHandle handle, PreparationResult result);
}
