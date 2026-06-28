-- Version: 5
-- Description: Add plot_slot_id column to allocation requests for slot/region identity separation

ALTER TABLE player_region_allocation_requests ADD COLUMN plot_slot_id TEXT;

CREATE INDEX IF NOT EXISTS idx_allocation_requests_plot_slot ON player_region_allocation_requests(plot_slot_id);
