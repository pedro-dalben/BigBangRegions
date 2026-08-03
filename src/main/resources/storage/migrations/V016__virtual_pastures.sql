CREATE TABLE IF NOT EXISTS virtual_pastures (
    dimension_key TEXT NOT NULL,
    block_pos INTEGER NOT NULL,
    region_id TEXT,
    owner_uuid TEXT,
    chunk_x INTEGER NOT NULL,
    chunk_z INTEGER NOT NULL,
    state TEXT NOT NULL DEFAULT 'ACTIVE',
    pending_expires_at INTEGER,
    PRIMARY KEY (dimension_key, block_pos)
);
CREATE INDEX IF NOT EXISTS idx_virtual_pastures_region ON virtual_pastures(region_id);
CREATE INDEX IF NOT EXISTS idx_virtual_pastures_owner ON virtual_pastures(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_virtual_pastures_chunk ON virtual_pastures(dimension_key, chunk_x, chunk_z);
