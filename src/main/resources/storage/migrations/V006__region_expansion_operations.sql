CREATE TABLE IF NOT EXISTS region_expansion_operations (
    operation_id TEXT PRIMARY KEY NOT NULL,
    region_id TEXT NOT NULL,
    owner_uuid TEXT NOT NULL,
    plot_slot_id TEXT,
    dimension_key TEXT NOT NULL,

    current_size INTEGER NOT NULL,
    target_size INTEGER NOT NULL,

    old_min_x INTEGER NOT NULL,
    old_min_z INTEGER NOT NULL,
    old_max_x INTEGER NOT NULL,
    old_max_z INTEGER NOT NULL,

    target_min_x INTEGER NOT NULL,
    target_min_z INTEGER NOT NULL,
    target_max_x INTEGER NOT NULL,
    target_max_z INTEGER NOT NULL,

    price_gems INTEGER NOT NULL DEFAULT 0,
    pricing_policy_version INTEGER NOT NULL DEFAULT 1,
    state TEXT NOT NULL,

    gems_reservation_id TEXT,

    reserve_idempotency_key TEXT,
    renew_idempotency_key TEXT,
    renew_sequence INTEGER NOT NULL DEFAULT 0,
    capture_idempotency_key TEXT,
    release_idempotency_key TEXT,

    reservation_lease_expires_at INTEGER,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at INTEGER,

    requested_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    resize_applied_at INTEGER,
    payment_captured_at INTEGER,

    failure_code TEXT,
    failure_detail TEXT
);

CREATE INDEX IF NOT EXISTS idx_expansion_region_state
    ON region_expansion_operations (region_id, state);

CREATE INDEX IF NOT EXISTS idx_expansion_owner_state
    ON region_expansion_operations (owner_uuid, state);

CREATE INDEX IF NOT EXISTS idx_expansion_state_retry
    ON region_expansion_operations (state, next_retry_at);

CREATE INDEX IF NOT EXISTS idx_expansion_gems_reservation
    ON region_expansion_operations (gems_reservation_id);
