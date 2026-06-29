-- Version: 7
-- Description: Add region invites persistence

CREATE TABLE IF NOT EXISTS region_invites (
    id TEXT PRIMARY KEY,
    regionId TEXT NOT NULL,
    invitedUuid TEXT NOT NULL,
    invitedByUuid TEXT NOT NULL,
    role TEXT NOT NULL,
    status TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    expiresAt INTEGER NOT NULL,
    acceptedAt INTEGER,
    respondedAt INTEGER,
    FOREIGN KEY (regionId) REFERENCES regions (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_region_invites_region ON region_invites(regionId, status);
CREATE INDEX IF NOT EXISTS idx_region_invites_invited ON region_invites(invitedUuid, status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_region_invites_unique_pending
    ON region_invites(regionId, invitedUuid)
    WHERE status = 'PENDING';
