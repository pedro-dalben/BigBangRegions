# Flags, Members, and Invites Migration Notes

## Implemented
- Added the `MANAGER` internal role between `LEADER` and `MEMBER`.
- Added persistent region invites with status tracking.
- Added a centralized typed flag registry and category menu flow.
- Added a region main menu at `/regiao`.
- Added delete-by-nick support for admin cleanup.
- Added snapshot restore for player-region deletion.
- Added a safe spawn search that avoids cave spawns.

## Storage
- New invite table migration: `V007__region_invites.sql`.

## Operational Notes
- Deleting a player region now releases the slot back to `RELEASED`.
- Terrain restore on delete depends on `playerLandAllocation.border.restoreOnDelete`.
- The spawn marker now uses glowstone at the exact home center.
