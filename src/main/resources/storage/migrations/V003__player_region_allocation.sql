-- Version: 3
-- Description: Add player region allocation requests, plot slots, and player region homes tables and indexes

CREATE TABLE IF NOT EXISTS player_region_allocation_requests (
    id TEXT PRIMARY KEY,
    owner_uuid TEXT NOT NULL,
    requested_biome_option TEXT NOT NULL,
    target_dimension TEXT NOT NULL,
    state TEXT NOT NULL,
    source TEXT NOT NULL,
    requested_by_uuid TEXT,
    region_id TEXT,
    failure_reason TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    completed_at INTEGER,
    cancelled_at INTEGER,
    -- Payment fields
    price_gems INTEGER NOT NULL DEFAULT 0,
    payment_required INTEGER NOT NULL DEFAULT 0,
    gems_reservation_id TEXT,
    reserve_idempotency_key TEXT,
    renew_idempotency_key TEXT,
    renew_sequence INTEGER NOT NULL DEFAULT 0,
    capture_idempotency_key TEXT,
    release_idempotency_key TEXT,
    reservation_lease_expires_at INTEGER,
    payment_captured_at INTEGER,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at INTEGER
);

CREATE TABLE IF NOT EXISTS plot_slots (
    id TEXT PRIMARY KEY,
    dimension_key TEXT NOT NULL,
    grid_x INTEGER NOT NULL,
    grid_z INTEGER NOT NULL,
    min_x INTEGER NOT NULL,
    min_z INTEGER NOT NULL,
    slot_size INTEGER NOT NULL,
    state TEXT NOT NULL,
    reserved_for_uuid TEXT,
    region_id TEXT,
    biome_option_key TEXT,
    reserved_at INTEGER,
    lease_expires_at INTEGER,
    allocated_at INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    UNIQUE(dimension_key, grid_x, grid_z),
    UNIQUE(region_id)
);

CREATE TABLE IF NOT EXISTS player_region_homes (
    region_id TEXT PRIMARY KEY,
    dimension_key TEXT NOT NULL,
    x REAL NOT NULL,
    y REAL NOT NULL,
    z REAL NOT NULL,
    yaw REAL NOT NULL DEFAULT 0,
    pitch REAL NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(region_id) REFERENCES regions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_allocation_requests_owner ON player_region_allocation_requests(owner_uuid, state);
CREATE INDEX IF NOT EXISTS idx_allocation_requests_state ON player_region_allocation_requests(state, created_at);
CREATE INDEX IF NOT EXISTS idx_allocation_requests_payment_state ON player_region_allocation_requests(state, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_plot_slots_grid ON plot_slots(dimension_key, grid_x, grid_z);
CREATE INDEX IF NOT EXISTS idx_plot_slots_state_lease ON plot_slots(state, lease_expires_at);
CREATE INDEX IF NOT EXISTS idx_plot_slots_region ON plot_slots(region_id);
