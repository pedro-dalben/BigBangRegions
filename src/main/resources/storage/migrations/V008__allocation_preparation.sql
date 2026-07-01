-- Version: 8
-- Description: Persist physical terrain preparation progress and request attempts

ALTER TABLE player_region_allocation_requests ADD COLUMN preparation_attempt INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS allocation_request_preparation (
    allocation_request_id TEXT PRIMARY KEY,
    preparation_attempt INTEGER NOT NULL DEFAULT 0,
    started_at INTEGER NOT NULL,
    timeout_at INTEGER NOT NULL,
    candidate_id TEXT,
    chunk_plan_json TEXT,
    last_error_code TEXT,
    last_error_message TEXT,
    ticket_state TEXT,
    cleanup_required INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(allocation_request_id) REFERENCES player_region_allocation_requests(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_allocation_preparation_cleanup
    ON allocation_request_preparation(cleanup_required, timeout_at);
