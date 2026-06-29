# Commands and Permissions

This document covers all mod commands, their permissions, and examples for both administrators and players.

## Command Aliases
Player commands can be executed using the English `/regions` prefix or the Portuguese aliases:
- `/regiao`
- `/regioes`

---

## Administrative Commands

These commands require administrative permissions or OP status.

### 1. Creation and Deletion
- **Create Admin Region**
  `/regions create admin <regionId> [priority]`
  - Permission: `bigbangregions.admin.create`
  - Example: `/regions create admin spawn 2000`
- **Create Player Region**
  `/regions create player <regionId> <owner> [priority]`
  - Permission: `bigbangregions.admin.player.create`
  - Example: `/regions create player pedro_claim PedropsRei`
- **Delete Region**
  `/regions delete <regionId|playerName>`
  - Permission: `bigbangregions.admin.delete`
  - Example: `/regions delete spawn`
  - Example: `/regions delete PedropsRei`

### 2. Admin Member Management
- **View Owner**
  `/regions player owner <regionId>`
  - Permission: `bigbangregions.admin.player.owner`
  - Show owner of a region. (Note: Owner transfer is out of scope for this phase and will be implemented in a future release).
- **List Members**
  `/regions player members <regionId>`
  - Permission: `bigbangregions.admin.player.members`
- **Add Member**
  `/regions player addmember <regionId> <player>`
  - Permission: `bigbangregions.admin.player.members`
- **Remove Member**
  `/regions player removemember <regionId> <player>`
  - Permission: `bigbangregions.admin.player.members`
- **Set Role**
  `/regions player setrole <regionId> <player> <leader|member>`
  - Permission: `bigbangregions.admin.player.members`

### 3. Mod Utilities
- **Reload Mod**
  `/regions reload`
  - Permission: `bigbangregions.admin.reload`
- **List Regions**
  `/regions list [page]`
  - Permission: `bigbangregions.admin.list`

---

## Player Commands

These commands allow players to manage their claims. Permissions are checked contextually based on their role in the region.

### 0. Main Menu
- **Open Region Menu**
  `/regiao`
  - Opens the chest-style region menu when the player already belongs to a region.
  - Opens biome selection when the player does not yet have a region.

### 1. Region Information
- **Show Info**
  `/regiao info`
  - Permission: None (contextual based on location/role)
  - Displays ID, type, owner, dim, and coordinates of the region they are standing in. If owner/leader/admin, lists members and flags too.

### 2. Membership Management
- **List Members**
  `/regiao membros listar`
  - Lists the region's owner, leaders, and members.
- **Add Member**
  `/regiao membros adicionar <player>`
  - Adds a player as a `MEMBER`. Requires `OWNER` or `LEADER`.
- **Remove Member**
  `/regiao membros remover <player>`
  - Removes a member/leader. Requires `OWNER` or `LEADER` (leaders cannot remove other leaders/owners).
- **Invite Friend**
  Use `/regiao` > `Membros` > `Convidar membro`.
  - Sends a pending invite that must be accepted by the invited player.
- **Transfer Owner**
  Use `/regiao` > `Membros` and right click a member item while you are the owner.
  - Sends a transfer request that becomes effective only after the target accepts it.
- **Promote Member**
  `/regiao membros promover <player>`
  - Promotes a member to `LEADER`. Requires `OWNER`.
- **Demote Leader**
  `/regiao membros rebaixar <player>`
  - Demotes a leader to `MEMBER`. Requires `OWNER`.
- **Leave Region**
  `/regiao sair`
  - Leaves the region voluntarily. Owners cannot leave.

### 3. Claim Flags Management
- **List Flags**
  `/regiao flags listar`
  - Displays the current value for all player-editable flags. Requires `OWNER` or `LEADER`.
- **View Flag**
  `/regiao flags ver <flag>`
  - Displays the value of a specific flag. Requires `OWNER` or `LEADER`.
- **Set Flag**
  `/regiao flags definir <flag> <allow|deny|inherit>`
  - Sets a flag value. Requires `OWNER` or `LEADER`.

### 4. Invite Inbox
- **Accept/Decline**
  Use `/regiao` > `Convites` > `Convites recebidos`.
  - Left click accepts the invite.
  - Right click declines the invite.
- **Cancel Sent Invites**
  Use `/regiao` > `Convites` > `Convites enviados`.
  - Cancels pending invitations sent by the current player.
