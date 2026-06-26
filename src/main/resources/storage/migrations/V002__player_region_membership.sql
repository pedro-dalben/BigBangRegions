-- Version: 2
-- Description: Add columns for player region membership details

ALTER TABLE region_members ADD COLUMN addedByUuid TEXT;
ALTER TABLE region_members ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE region_members ADD COLUMN updatedAt INTEGER;
