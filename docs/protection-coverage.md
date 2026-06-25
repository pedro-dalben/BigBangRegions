# Cobertura de Proteção

Este documento detalha a matriz de cobertura de proteção implementada no BigBang Regions na Fase 1.

## Matriz de Cobertura e Status

| Ação | Flag | Hook ou mixin | Testado unitariamente | Testado em servidor | Status | Limitações |
|---|---|---|---|---|---|---|
| Quebrar Bloco | `player-build` | `PlayerBlockBreakEvents.BEFORE` | Sim | Sim | `IMPLEMENTED_AND_TESTED` | Nenhuma. |
| Colocar Bloco | `player-build` | `UseBlockCallback` | Sim | Sim | `IMPLEMENTED_AND_TESTED` | Cobre blocos regulares, colocação pelo offhand e baldes de água/lava. |
| Interagir com Blocos | `player-interact` | `UseBlockCallback` (Fallback) | Sim | Sim | `IMPLEMENTED_AND_TESTED` | Interações específicas (redstone, portas, containers) possuem flags dedicadas. |
| Acessar Container | `container-access` | `UseBlockCallback` | Sim | Sim | `IMPLEMENTED_AND_TESTED` | Detecta qualquer bloco com `BlockEntity` que seja `Container` ou `MenuProvider`. |
| Abrir Porta/Alçapão | `door-use` | `UseBlockCallback` | Sim | Sim | `IMPLEMENTED_AND_TESTED` | Cobre `DoorBlock`, `TrapDoorBlock` e `FenceGateBlock`. |
| Usar Redstone | `redstone-use` | `UseBlockCallback` + `BasePressurePlateBlockMixin` | Sim | Sim | `IMPLEMENTED_AND_TESTED` | Placa de pressão só bloqueia jogadores (não impede mobs ou mecanismos). |
| Interagir com Entidade | `entity-interact` | `UseEntityCallback` + `AttackEntityCallback` | Sim | Sim | `IMPLEMENTED_AND_TESTED` | Cobre Armor Stands, Item Frames, barcos e minecarts. |
| Combate PvP | `pvp` | `PlayerMixin.hurt` | Sim | Sim | `IMPLEMENTED_AND_TESTED` | Checa a posição da vítima e do atacante. Rastreia projéteis via `DamageSource`. |
| Coletar Item | `item-pickup` | `ItemEntityMixin.playerTouch` | Sim | Sim | `IMPLEMENTED_AND_TESTED` | Usa a posição real do item no chão e não apenas a do jogador. |
| Dropar Item | `item-drop` | `PlayerMixin.drop` | Sim | Sim | `IMPLEMENTED_AND_TESTED` | Cancela o drop e retorna o item ao inventário ou cursor sem duplicações/perdas. |
| Automações (Funil/Pistão) | N/A | Nenhum | Não | Não | `NOT_SUPPORTED` | Automações não-jogador não são verificadas nesta fase para otimizar ticks. |
| Explosões/Fogo/Fluidos | N/A | Nenhum | Não | Não | `PLANNED` | Planejado para fases de proteção de claims de jogadores. |

## Detalhes de Implementação e Evidências

* **Bypass de Borda**: O bloqueio na colocação de blocos calcula o `placePos` correto usando a face clicada (`pos.relative(hitResult.getDirection())`), evitando colocação de fora para dentro de uma região protegida.
* **Bypass de Projéteis**: O mixin intercepta `Player.hurt` e valida o atacante original através do `DamageSource.getEntity()`, cobrindo flechas, poções e tridentes.
* **Retorno Seguro de Drop**: Quando a flag `item-drop` é negada, o item é reinserido no inventário do jogador (`player.getInventory().add()`). Se o inventário estiver cheio, o item permanece anexado ao cursor (`setCarried(stack)`), prevenindo perdas ou dessincronizações visuais (ghost items).
