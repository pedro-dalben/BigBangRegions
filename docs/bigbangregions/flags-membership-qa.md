# Flags, Members, and Invites QA

## Verified By Test
- `./gradlew test`

## Coverage Added
- Safe spawn finder avoids cave-style spawns.
- Biome search rejects mixed edges that leak into neighboring biomes.
- Invite service supports send, accept, cancel, and duplicate blocking.
- Membership service respects the new `MANAGER` role.

## Manual Checks
- `/regiao` opens the region menu when the player already owns or belongs to a region.
- `/regiao` opens biome selection only when the player has no region.
- `/regions delete <nick>` removes that player's regions and restores terrain when configured.
