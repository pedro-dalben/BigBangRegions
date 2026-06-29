# JourneyMap Integration — QA Plan

## Prerequisites

- Fabric server with BigBangRegions + JourneyMap installed
- OP on the server or relevant permissions

## Test Cases

### TC01 — BigBangRegions starts without JourneyMap
1. Remove or disable JourneyMap from mods folder
2. Start server
3. **Expected:** BigBangRegions loads normally, no crash, log shows no JM-related activity

### TC02 — JourneyMap present activates integration
1. Install JourneyMap on server
2. Start server
3. **Expected:** Log shows "JourneyMap region overlay integration initialized"

### TC03 — Create player region generates overlay + marker
1. Create a player claim via `/regiao criar <bioma>`
2. Open JourneyMap fullscreen map
3. **Expected:** Region outline visible, center marker present with correct name

### TC04 — Create admin region (centered) generates overlay + marker
1. Run `/regions admin create 50 50`
2. Open JourneyMap
3. **Expected:** Admin region outlined in red, center marker visible

### TC05 — Delete region removes overlay + marker
1. Delete a region via `/regions delete <id>`
2. Check JourneyMap
3. **Expected:** Overlay and marker removed immediately

### TC06 — Owner sees own region
1. Player A creates a region
2. Player A opens JourneyMap
3. **Expected:** Player A sees own region outline and marker

### TC07 — Member sees region after accepting invite
1. Player A creates region, invites Player B
2. Player B accepts invite
3. Player B opens JourneyMap
4. **Expected:** Player B sees Player A's region

### TC08 — Removed member loses overlay
1. Player A removes Player B from region
2. Player B checks JourneyMap
3. **Expected:** Region no longer visible to Player B

### TC09 — Visitor does not see private region
1. Player A creates region (no public flag)
2. Player C (visitor) opens JourneyMap
3. **Expected:** Player C does not see Player A's region

### TC10 — Public region visible to visitors
1. Set region flag map-visibility to `public`
2. Set `publicRegions.showOnMap` to `true` in config
3. Visitor opens JourneyMap
4. **Expected:** Visitor sees region outline and marker

### TC11 — Staff with permission sees admin regions
1. Set `adminRegionVisibility` to `STAFF_ONLY`
2. Give player `bigbangregions.journeymap.view-admin`
3. **Expected:** Player sees admin regions

### TC12 — Staff without permission does not see admin regions
1. Set `adminRegionVisibility` to `STAFF_ONLY`
2. Do NOT give `bigbangregions.journeymap.view-admin`
3. **Expected:** Player does not see admin regions

### TC13 — Region update changes overlay without duplication
1. Resize or rename a region
2. Check JourneyMap
3. **Expected:** Overlay reflects new bounds/name, no duplicate overlays

### TC14 — Dimension change resets overlays
1. Player has region in Overworld, opens JourneyMap
2. Player goes to Nether
3. **Expected:** No Overworld region overlays in Nether view

### TC15 — Re-log does not duplicate markers
1. Player logs out and back in
2. Open JourneyMap
3. **Expected:** Single overlay and marker per visible region

### TC16 — Client without JourneyMap is unaffected
1. Player connects with vanilla client (no JourneyMap)
2. **Expected:** No errors, no logs about missing JM for that player

### TC17 — Private data not sent to unauthorized players
1. Use network sniffer or mod log
2. **Expected:** Region data only sent to authorized players per visibility rules

### TC18 — Maintenance region visible only to staff
1. Set region status to non-ACTIVE (via direct DB edit)
2. Staff player checks JourneyMap
3. **Expected:** Region visible to staff in maintenance styling
4. Non-staff player checks
5. **Expected:** Region not visible or shown as blocked

## Edge Cases

- Region with invalid dimension → not rendered, logged as error
- Region without valid bounds → not rendered
- Player with no regions → clean map, no errors
- Server with 100+ regions → smooth performance (test with profiler)
