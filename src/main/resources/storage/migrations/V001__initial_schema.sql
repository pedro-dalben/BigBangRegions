-- Version: 1
-- Description: Initial schema for BigBang Regions

CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY,
    appliedAt INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS regions (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    dimensionKey TEXT NOT NULL,
    minX INTEGER NOT NULL,
    minY INTEGER NOT NULL,
    minZ INTEGER NOT NULL,
    maxX INTEGER NOT NULL,
    maxY INTEGER NOT NULL,
    maxZ INTEGER NOT NULL,
    priority INTEGER NOT NULL,
    ownerUuid TEXT,
    createdByUuid TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    status TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS region_members (
    regionId TEXT NOT NULL,
    uuid TEXT NOT NULL,
    role TEXT NOT NULL,
    PRIMARY KEY (regionId, uuid),
    FOREIGN KEY (regionId) REFERENCES regions (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS region_flags (
    regionId TEXT NOT NULL,
    flag TEXT NOT NULL,
    value TEXT NOT NULL,
    PRIMARY KEY (regionId, flag),
    FOREIGN KEY (regionId) REFERENCES regions (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS region_audit_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    regionId TEXT,
    actorUuid TEXT,
    action TEXT NOT NULL,
    beforeValue TEXT,
    afterValue TEXT,
    createdAt INTEGER NOT NULL,
    metadataJson TEXT
);

CREATE INDEX IF NOT EXISTS idx_regions_dimension ON regions(dimensionKey);
CREATE INDEX IF NOT EXISTS idx_regions_type ON regions(type);
CREATE INDEX IF NOT EXISTS idx_regions_priority ON regions(priority);
CREATE INDEX IF NOT EXISTS idx_regions_bounds ON regions(minX, maxX, minZ, maxZ);
