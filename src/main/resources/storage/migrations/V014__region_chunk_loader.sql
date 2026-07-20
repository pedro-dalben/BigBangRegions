CREATE TABLE IF NOT EXISTS region_chunk_loader_chunks (
    regionId TEXT NOT NULL,
    chunkX INTEGER NOT NULL,
    chunkZ INTEGER NOT NULL,
    PRIMARY KEY (regionId, chunkX, chunkZ),
    FOREIGN KEY (regionId) REFERENCES regions (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS player_chunk_loader_credits (
    ownerUuid TEXT PRIMARY KEY,
    extraCredits INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_chunk_loader_region ON region_chunk_loader_chunks(regionId);
