# JourneyMap Region Overlay Integration

**API Version:** JourneyMap v2 API `2.0.0-1.21.1`  
**Minecraft:** 1.21.1 (Fabric)  
**Mod:** `bigbangregions`  

## Overview

This integration draws region boundaries and center markers on the JourneyMap minimap and fullscreen map for authorized players. Regions are never sent to unauthorized clients.

## Requirements

- JourneyMap mod installed on the server (not just clients)
- BigBangRegions with JourneyMap API dependency

## Enabling / Disabling

In `config/bigbangregions/config.json`:

```json
{
  "journeyMap": {
    "enabled": true,
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

### Center Marker (waypoint)

- Placed at center of region bounds
- Color matches region type
- Name matches region display name
- Shows region type on hover

## Event-Driven Updates

The integration listens for these events and updates only the affected players:

- Region created / deleted / resized / renamed
- Player joins or leaves region
- Member role changed
- Region status changed
- Player joins server or changes dimension

## Known Limitations

- Only works when JourneyMap mod is on the server (dedicated server mode)
- Requires JourneyMap API v2 compatible with 1.21.1
- Player dimension changes may require re-opening JourneyMap to refresh
- Admin menu "Visualizar como jogador" is a planned feature

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| No overlays appear | JourneyMap not installed on server, or integration disabled in config |
| Wrong players see regions | Check permission nodes and adminRegionVisibility setting |
| Duplicate overlays after relog | Clear JourneyMap cache (`/journeymap reset`) |
| Deprecation warnings in log | WaypointFactory API is internal — safe to ignore |
