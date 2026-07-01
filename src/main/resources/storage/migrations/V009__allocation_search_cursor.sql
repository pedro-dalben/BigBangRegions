CREATE TABLE IF NOT EXISTS allocation_search_cursor (
    request_id TEXT PRIMARY KEY,
    current_band_id TEXT,
    current_sector_index INTEGER NOT NULL DEFAULT 0,
    sector_x INTEGER NOT NULL DEFAULT 0,
    sector_z INTEGER NOT NULL DEFAULT 0,
    anchor_attempt INTEGER NOT NULL DEFAULT 0,
    local_candidate_index INTEGER NOT NULL DEFAULT 0,
    total_sectors_checked INTEGER NOT NULL DEFAULT 0,
    total_virtual_candidates_checked INTEGER NOT NULL DEFAULT 0,
    total_biome_samples INTEGER NOT NULL DEFAULT 0,
    sectors_discarded INTEGER NOT NULL DEFAULT 0,
    anchors_found INTEGER NOT NULL DEFAULT 0,
    locate_calls_used INTEGER NOT NULL DEFAULT 0,
    current_anchor_x INTEGER,
    current_anchor_z INTEGER,
    current_anchor_biome_id TEXT,
    last_progress_at INTEGER NOT NULL DEFAULT 0,
    last_rejection_reason TEXT,
    fallback_mode TEXT,
    FOREIGN KEY(request_id) REFERENCES player_region_allocation_requests(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_allocation_search_cursor_band
    ON allocation_search_cursor(current_band_id, current_sector_index);
