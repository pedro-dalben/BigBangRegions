# Player Regions

This document covers the definition, rules, and constraints for `PLAYER_REGION` territories in BigBang Regions.

## Overview
A `PLAYER_REGION` represents a private player terrain or claim. Unlike administrative or system regions, player regions are owned by a specific player and support membership management, leader delegations, and custom flags restricted to the owner's terrain.

## Creation
In this phase (Fase 2A), the creation of player regions is manual and administrative:
- Administrators define the boundaries using selection tools (`/regions pos1` and `/regions pos2`).
- They create the region and assign an owner using the command:
  `/regions create player <regionId> <owner> [priority]`

## Constraints and Limits
- **One Claim Per Player**: By default, each player can own at most one `PLAYER_REGION`. This is configurable via the configuration file (`maxRegionsPerOwner`).
- **Owner Requirement**: A `PLAYER_REGION` cannot exist without a valid owner (`ownerUuid` is mandatory).
- **No Self-Membership**: The owner cannot be added as a member or leader under `region_members`.
- **Precedence**: Overlapping administrative or system regions always override player regions regardless of numerical priority.

## Role Policy vs Flags
The final access decision is an intersection of the player's role policy and the region's flags:
1. The player's role must allow the action.
2. The effective flag on the region must allow the action.

*A flag `ALLOW` does not grant permissions to a visitor. A flag `DENY` blocks all roles, including the owner.*
