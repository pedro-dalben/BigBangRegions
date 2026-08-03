# JourneyMap Region Overlay Integration

**API Version:** JourneyMap v2 API `2.0.0-1.21.1`  
**Minecraft:** 1.21.1 (Fabric)  
**Mod:** `bigbangregions`  

## Overview

This integration draws region boundaries on the JourneyMap minimap and fullscreen map for authorized players. BigBangRegions builds a `ServerMapSnapshot` through the optional BigMonCraft API; BigMonCraft's JourneyMap server bridge sends native polygons to the client.

## Requirements

- BigBangRegions and BigMonCraft 0.1.0 installed on the server.
- JourneyMap 1.21.1-6.0.2 installed on the server and on clients that should receive region overlays.
- BigMonCraft may be distributed in the client modpack as the universal base mod; BigBangRegions remains server-only.
- Without BigMonCraft or JourneyMap, protection and region management continue normally and only the map is disabled.

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

Set `"enabled": false` to disable entirely without removing JourneyMap. The source ID registered by BigBangRegions is `bigbangregions`.

The `fillOpacity` fields remain accepted for configuration compatibility, but region and chunk-loader overlays are rendered without interior fill; only their colored borders are shown.

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
- No interior fill; only the colored border is rendered
- Stroke border uses the configured color and opacity
- Label shows region name on hover
- Player-region palette follows the viewer relationship: owner, member, public, or staff

### Chunk Loader Tiles

- Green border: a selected chunk with a ticket active in this server session
- Yellow border: a selected chunk saved in the database but without an active ticket
- Tiles are clipped to the region boundary, rendered without fill, and grouped into two compact overlays per region

## Event-Driven Updates

The integration listens for these events and updates only the affected players:

- Region created / deleted / resized / renamed
- Player joins or leaves region
- Member role changed
- Region status changed
- Player joins server or changes dimension
- Chunk loader selected, removed, activated, or released

## Known Limitations

- Requires BigMonCraft 0.1.0 and JourneyMap 6.0.2 on the server for the bridge
- Admin menu "Visualizar como jogador" is a planned feature

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| No overlays appear | BigMonCraft/JourneyMap missing on the server or client, bridge unavailable, or integration disabled in server config |
| Wrong players see regions | Check permission nodes and adminRegionVisibility setting |
| Duplicate overlays after relog | The bridge replaces the `bigbangregions` source; check for another mod using the same source ID |
| Bridge missing | Confirm `BigMonCraft JourneyMap server bridge initialized` and `BigBangRegions map source registered` in the server log |
