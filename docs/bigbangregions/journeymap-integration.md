# JourneyMap Region Overlay Integration

**API Version:** JourneyMap v2 API `2.0.0-1.21.1`  
**Minecraft:** 1.21.1 (Fabric)  
**Mod:** `bigbangregions`  

## Overview

This integration draws region boundaries and center markers on the JourneyMap minimap and fullscreen map for authorized players. Regions are never sent to unauthorized clients.

## Requirements

- JourneyMap mod installed on the server (not just clients). JourneyMap supplies the API implementation.
- Do not copy `journeymap-api-fabric` alone to the server; it is a soft/development dependency and has no implementation.
- BigBangRegions built for the same Minecraft/JourneyMap API line

## Enabling / Disabling

In `config/bigbangregions/config.json`:

```json
{
  "journeyMap": {
    "enabled": true,
    "memberRegion": {
      "fillColor": 4367861,
      "strokeColor": 4367861,
      "fillOpacity": 0.14,
      "strokeOpacity": 0.8
    },
    "publicRegion": {
      "fillColor": 11583173,
      "strokeColor": 11583173,
      "fillOpacity": 0.08,
      "strokeOpacity": 0.55
    },
    "staffRegion": {
      "fillColor": 11225020,
      "strokeColor": 11225020,
      "fillOpacity": 0.12,
      "strokeOpacity": 0.72
    },
    "playerRegion": {
      "fillColor": 5220560,
      "strokeColor": 5220560,
      "fillOpacity": 0.16,
      "strokeOpacity": 0.85
    },
    "adminRegion": {
      "fillColor": 15024693,
      "strokeColor": 15024693,
      "fillOpacity": 0.2,
      "strokeOpacity": 0.95
    },
    "blockedRegion": {
      "fillColor": 7699829,
      "strokeColor": 7699829,
      "fillOpacity": 0.12,
      "strokeOpacity": 0.7
    },
    "maintenanceRegion": {
      "fillColor": 16750592,
      "strokeColor": 16750592,
      "fillOpacity": 0.14,
      "strokeOpacity": 0.8
    },
    "chunkLoaderActive": {
      "fillColor": 4431943,
      "strokeColor": 4431943,
      "fillOpacity": 0.3,
      "strokeOpacity": 1.0
    },
    "chunkLoaderSelected": {
      "fillColor": 16761095,
      "strokeColor": 16761095,
      "fillOpacity": 0.22,
      "strokeOpacity": 0.95
    },
    "publicRegions": {
      "showOnMap": true
    },
    "adminRegionVisibility": "STAFF_ONLY"
  }
}
```

Set `"enabled": false` to disable entirely without removing JourneyMap.

## Visibility Rules

### Player Regions

| Who | Sees region? |
|---|---|
| OWNER / LEADER / MANAGER / MEMBER | Always |
| Staff with `bigbangregions.journeymap.view-all` | Yes |
| Visitor (region marked public) | If `publicRegions.showOnMap` = true and player has `bigbangregions.journeymap.view-public` |
| Visitor (region private) | No |

### Chunk Loaders

Chunk tiles are intentionally more restricted than the region outline: only the region OWNER and staff with `bigbangregions.journeymap.view-all` receive them. Members and visitors never receive chunk-loader state.

### Admin Regions

| `adminRegionVisibility` | Effect |
|---|---|
| `PUBLIC` | Visible to everyone |
| `STAFF_ONLY` | Requires `bigbangregions.journeymap.view-admin` |
| `HIDDEN` | Not shown to anyone |
| `PERMISSION` | Requires `bigbangregions.journeymap.view-admin` |

## Permissions

| Permission | Effect |
|---|---|
| `bigbangregions.journeymap.view-own` | See own player region (automatic for members) |
| `bigbangregions.journeymap.view-public` | See public player regions |
| `bigbangregions.journeymap.view-admin` | See admin regions (if STAFF_ONLY or PERMISSION) |
| `bigbangregions.journeymap.view-all` | See ALL regions bypassing rules |

## What Gets Rendered

### Polygon Overlay (region boundary)

- Full rectangle from `minX,minZ` to `maxX,maxZ`
- Fill with configurable color and opacity
- Stroke border with configurable color and opacity
- Label shows region name on hover
- At zoom 0–7, only the light area and name remain visible for map orientation
- At zoom 8+, the full fill and chunk-loader detail are shown
- Player-region palette follows the viewer relationship: owner, member, public, or staff

### Center Marker (waypoint)

- Placed at center of region bounds
- Color matches region type
- Name matches region display name
- Shows region type on hover

### Chunk Loader Tiles

- Green: a selected chunk with a ticket active in this server session
- Yellow: a selected chunk saved in the database but without an active ticket
- Tiles are clipped to the region boundary and grouped into two compact overlays per region

## Event-Driven Updates

The integration listens for these events and updates only the affected players:

- Region created / deleted / resized / renamed
- Player joins or leaves region
- Member role changed
- Region status changed
- Player joins server or changes dimension
- Chunk loader selected, removed, activated, or released

## Known Limitations

- Only works when JourneyMap mod is on the server (dedicated server mode)
- Requires JourneyMap API v2 compatible with 1.21.1
- Admin menu "Visualizar como jogador" is a planned feature

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| No overlays appear | JourneyMap not installed on server, or integration disabled in config |
| Wrong players see regions | Check permission nodes and adminRegionVisibility setting |
| Duplicate overlays after relog | Clear JourneyMap cache (`/journeymap reset`) |
| Deprecation warnings in log | Verify that the server is using the current JourneyMap API implementation supplied by the JourneyMap mod |
