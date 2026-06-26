# Member Roles

This document defines the player roles within a player region and their corresponding access levels.

## Hierarchy and Management Rules

| Papel | Build | Containers | Portas | Redstone | Entidades | Pickup | Drop | Gerenciar membros | Alterar flags |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **OWNER** | Sim | Sim | Sim | Sim | Sim | Sim | Sim | Sim | Sim |
| **LEADER** | Sim | Sim | Sim | Sim | Sim | Sim | Sim | Apenas MEMBER | Sim |
| **MEMBER** | Sim | Sim | Sim | Sim | Sim | Sim | Sim | Não | Não |
| **VISITOR** | Não | Não | Não | Não | Não | Não | Não | Não | Não |

> [!IMPORTANT]
> The table above assumes that the corresponding flag of the region is set to `ALLOW`.
> A flag set to `DENY` overrides and blocks the action for all roles (including owner and leaders). Only players with administrative bypass permissions can bypass a `DENY` flag.

## Roles Description

### OWNER
The owner of the region.
- Defined by `regions.owner_uuid`.
- Cannot be removed from the region.
- Cannot leave their own region.
- Full build, container, redstone, and entity access.
- Can add, remove, promote, or demote members and leaders.

### LEADER
A delegate administrator of the region.
- Saved under `region_members` with role `LEADER`.
- Can build, open containers, use doors/redstone, interact with entities.
- Can add or remove `MEMBER`s.
- Can alter player region flags.
- Cannot promote members to leader, demote leaders, or remove the owner.

### MEMBER
A regular member of the region.
- Saved under `region_members` with role `MEMBER`.
- Can build, open containers, use doors/redstone, interact with entities.
- Cannot add/remove/promote/demote anyone.
- Can leave the region voluntarily at any time using `/regiao sair`.

### VISITOR
Any player who is not the owner, a leader, or a member of the region.
- No entry in `region_members`.
- Cannot build, destroy, open containers, use doors, redstone, or pickup/drop items.
- Can walk through the claim if physically accessible.
- PvP is determined solely by the region's `pvp` flag.
