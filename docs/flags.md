# Catálogo de Flags

Este documento descreve todas as flags suportadas e planejadas para o mod BigBang Regions.

## Flags Suportadas (Fase 1)

| Flag ID | Tipo | Padrão Global | Padrão Admin | Descrição | Ação Protegida | Hook Usado |
|---|---|---|---|---|---|---|
| `player-build` | BOOLEAN | ALLOW | DENY | Permite quebrar ou colocar blocos. | `BLOCK_BREAK`, `BLOCK_PLACE` | `PlayerBlockBreakEvents.BEFORE`, `UseBlockCallback` |
| `player-interact` | BOOLEAN | ALLOW | DENY | Permite interagir com blocos em geral (click). | `INTERACT` | `UseBlockCallback` (Fallback) |
| `container-access` | BOOLEAN | ALLOW | DENY | Permite acessar baús, fornalhas, barris e máquinas. | `CONTAINER` | `UseBlockCallback` (MenuProvider / Container checks) |
| `door-use` | BOOLEAN | ALLOW | DENY | Permite abrir/fechar portas, alçapões e portões. | `DOOR` | `UseBlockCallback` (DoorBlock/TrapdoorBlock/FenceGateBlock) |
| `redstone-use` | BOOLEAN | ALLOW | DENY | Permite interagir com botões, alavancas e placas de pressão. | `REDSTONE` | `UseBlockCallback` + `BasePressurePlateBlockMixin` |
| `entity-interact` | BOOLEAN | ALLOW | DENY | Permite interagir com molduras, stands, barcos e mounts. | `ENTITY_INTERACT` | `UseEntityCallback`, `AttackEntityCallback` |
| `pvp` | BOOLEAN | ALLOW | DENY | Permite combate direto/indireto entre jogadores. | `PVP` | `PlayerMixin.hurt` (DamageSource check) |
| `explosion-block-damage` | BOOLEAN | ALLOW | DENY | Controla dano de explosões em blocos. | `EXPLOSION_BLOCK_DAMAGE` | `ExplosionMixin` + `BigBangRegions.canWorldAction` |
| `item-pickup` | BOOLEAN | ALLOW | ALLOW | Permite coletar itens do chão. | `ITEM_PICKUP` | `ItemEntityMixin.playerTouch` |
| `item-drop` | BOOLEAN | ALLOW | ALLOW | Permite dropar itens no chão. | `ITEM_DROP` | `PlayerMixin.drop` |

## Flags Planejadas (Fases Futuras)

* `hostile-mob-spawn` (Padrão: ALLOW) - Controla spawn de monstros.
* `passive-mob-spawn` (Padrão: ALLOW) - Controla spawn de animais.
* `fire-spread` (Padrão: ALLOW) - Evita propagação de fogo.
* `fluid-flow` (Padrão: ALLOW) - Controla escoamento de água/lava.
* `piston-move` (Padrão: ALLOW) - Controla movimento de pistões na borda.
* `projectile-use` (Padrão: ALLOW) - Restringe uso de projéteis.
* `mob-griefing` (Padrão: ALLOW) - Evita que mobs destruam blocos.
* `crop-trample` (Padrão: ALLOW) - Evita destruição de plantações.
* `teleport-in` (Padrão: ALLOW) - Restringe teleporte para dentro da região.
* `teleport-out` (Padrão: ALLOW) - Restringe teleporte para fora da região.
