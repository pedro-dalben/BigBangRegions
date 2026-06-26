-- Version: 4
-- Description: Add index on region_audit_logs for better query performance

CREATE INDEX IF NOT EXISTS idx_audit_regionId ON region_audit_logs(regionId);
CREATE INDEX IF NOT EXISTS idx_audit_createdAt ON region_audit_logs(createdAt);
