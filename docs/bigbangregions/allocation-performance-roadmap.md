# Allocation performance roadmap

## Implemented in this change

`WorldgenBiomeAnchorLocator` now samples the biome source directly in quart
coordinates in bounded batches. The search position is persisted in
`allocation_search_cursor`, so `maxSearchWorkNanosPerTick` can interrupt a
large search without restarting it on the next scheduler tick.

Before starting the incremental scan for each sector, the locator now makes
one call to Minecraft's optimized `BiomeSource.findBiomeHorizontal`. Successful
lookups go directly to candidate validation; only a miss falls back to the
bounded quart scan below. This keeps the resilient behavior for sparse or
unusual worldgen sources while restoring the fast path used by vanilla biome
search.

Migrations V12 and V13 add:

- `anchor_search_y_index`;
- `anchor_search_ring_quart`;
- `anchor_search_point_index`.
- `anchor_search_interval_quart`.

The search keeps the configured block radius and sample budget converted to
quart units. This is important for sparse biomes such as `minecraft:cherry_grove`:
the search no longer delegates an entire large radius to one uninterruptible
`findBiomeHorizontal` call, and it does not lose its progress between ticks.
The locator now caps spatial spacing at 16 blocks while keeping the per-step
sample budget independent, so a 64-block work budget cannot skip small biome
patches.

## Implemented in this change: irregular-biome fallback

The strict footprint validation now honors all configured values:

- `minimumMatchPercentage`;
- `minimumBorderMatchPercentage`;
- `requireFullBorderMatch`.

When every strict candidate near a confirmed biome anchor fails, the allocator
also evaluates the same candidate with the bounded relaxed profile:

- `relaxedFallbackEnabled` (default `true`);
- `relaxedMinimumMatchPercentage` (default `30`);
- `relaxedMinimumBorderMatchPercentage` (default `0`).

The relaxed profile still requires the center of the initial claim to match
the requested biome. It only permits nearby biome transitions at the border
and interior, allowing irregular cherry groves and mountain biomes to receive
a terrain without pretending that the full claim is a single biome.

Candidates are already ordered by distance to the found biome anchor, so the
first relaxed candidate accepted is the nearest eligible slot. This preserves
the fixed slot-center geometry required by the current expansion system.

### Next structural improvement

Allowing a claim center to move freely inside a 512-block slot must be paired
with an expansion redesign. Expansion currently recenters the region in its
slot; moving only the initial claim would make a later expansion shift the
player's region. The future implementation must persist the selected claim
center and expand around that same center (or use deliberate asymmetric
growth), while enforcing the future-maximum-size and internal-margin bounds.

## Implemented in this change: reduce snapshot capture cost

The capture now uses `mutation_snapshot_v2`: it records the original states of
the border, optional ceiling, and exact spawn-platform footprint before those
blocks are changed. With the default 80x80 claim and the normal vertical range
of -64..320, the previous full-volume path read approximately 2,464,000 block
positions. The new path reads the border shell and the small platform footprint
only.

The restore contract is explicit: deletion restores blocks changed by region
creation. Player-built blocks inside the region are not treated as terrain
created by the allocator and are therefore not removed by this snapshot mode.
Existing `structure_template_v1` and legacy block snapshots remain readable.

The implementation preserves these invariants:

1. deleting a region can restore every block changed by region creation;
2. restore must remain dimension-safe and must not load chunks implicitly;
3. existing `structure_template_v1` and legacy snapshots must remain readable;
4. a failed capture must not leave a snapshot that looks complete.

Validation completed:

1. timing and serialized-size measurements for capture and restore;
2. round-trip tests for terrain, border, spawn platform, legacy compatibility,
   and missing chunks;

Remaining operational validation is to benchmark creation and deletion on a
real server with representative terrain and the configured claim size.

If the product contract later changes to reset player-built blocks on deletion,
the full-volume format must remain available as an explicit mode; mutation-only
capture must not silently be used for that requirement.
