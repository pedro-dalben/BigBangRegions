-- Version: 7
-- Description: Plot slot metadata columns and biome option mapping table

ALTER TABLE plot_slots ADD COLUMN max_x INTEGER NOT NULL DEFAULT 0;
ALTER TABLE plot_slots ADD COLUMN max_z INTEGER NOT NULL DEFAULT 0;
ALTER TABLE plot_slots ADD COLUMN allocation_request_id TEXT;
ALTER TABLE plot_slots ADD COLUMN consumed_at INTEGER;
ALTER TABLE plot_slots ADD COLUMN invalidated_at INTEGER;
ALTER TABLE plot_slots ADD COLUMN invalidation_reason TEXT;
ALTER TABLE plot_slots ADD COLUMN validation_schema_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE plot_slots ADD COLUMN validated_at INTEGER;

CREATE TABLE IF NOT EXISTS plot_slot_biome_options (
    plot_slot_id TEXT NOT NULL,
    biome_option_key TEXT NOT NULL,
    matched_biome_id TEXT NOT NULL,
    validation_details_json TEXT,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (plot_slot_id, biome_option_key),
    FOREIGN KEY (plot_slot_id) REFERENCES plot_slots(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_plot_slots_state_dim ON plot_slots(state, dimension_key);
CREATE INDEX IF NOT EXISTS idx_plot_slots_available ON plot_slots(dimension_key, state, validated_at);
CREATE INDEX IF NOT EXISTS idx_plot_slot_biome_options_key ON plot_slot_biome_options(biome_option_key);

UPDATE plot_slots SET max_x = min_x + slot_size - 1, max_z = min_z + slot_size - 1;
