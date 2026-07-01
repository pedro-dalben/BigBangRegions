package com.bigbangcraft.regions.allocation;

public record AllocationSearchSector(
    String bandId,
    int sectorIndex,
    int sectorX,
    int sectorZ,
    int sectorSizeBlocks,
    int minRadiusBlocks,
    int maxRadiusBlocks
) {
    public int centerBlockX() {
        return sectorX * sectorSizeBlocks + (sectorSizeBlocks / 2);
    }

    public int centerBlockZ() {
        return sectorZ * sectorSizeBlocks + (sectorSizeBlocks / 2);
    }

    public int halfSizeBlocks() {
        return sectorSizeBlocks / 2;
    }
}
