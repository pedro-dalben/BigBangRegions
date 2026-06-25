# Matriz de Compatibilidade

Esta matriz detalha a compatibilidade e os limites do mod para diferentes ações e origens de eventos.

## Tabela de Ações e Status

| Ação | Origem | Hook Utilizado | Status | Limitações Conhecidas | Próxima Integração Necessária |
|---|---|---|---|---|---|
| Quebrar Bloco | Jogador (Survival/Creative) | `PlayerBlockBreakEvents.BEFORE` | `SUPPORTED` | Nenhuma. | Nenhuma. |
| Colocar Bloco | Jogador | `UseBlockCallback` | `SUPPORTED` | Prevê blocos e baldes de fluidos. | Nenhuma. |
| Quebrar Bloco | Automação (Pistão/etc) | Nenhum | `PLANNED` | Pistões de outros mods podem quebrar. | PistonMove event mixin. |
| Abrir Inventário | Jogador | `UseBlockCallback` | `SUPPORTED` | Bloqueia qualquer bloco que tenha BlockEntity e implemente MenuProvider ou Container. | Nenhuma. |
| Abrir Inventário | Hopper/Canos (Mods) | Nenhum | `PLANNED` | Funis retiram itens normalmente de baús protegidos. | Hopper & Item Transfer hook. |
| PvP (Espada) | Jogador -> Jogador | `AttackEntityCallback` / `PlayerMixin.hurt` | `SUPPORTED` | Nenhuma. | Nenhuma. |
| PvP (Flecha) | Jogador -> Jogador | `PlayerMixin.hurt` | `SUPPORTED` | Nenhuma. | Nenhuma. |
| Coletar Item | Jogador | `ItemEntityMixin.playerTouch` | `SUPPORTED` | Nenhuma. | Nenhuma. |
| Jogar Item | Jogador | `PlayerMixin.drop` | `SUPPORTED` | Devolve o item ao inventário. | Nenhuma. |
| Usar Botões/Alavancas| Jogador | `UseBlockCallback` | `SUPPORTED` | Nenhuma. | Nenhuma. |
| Stepar em Placa | Jogador | `BasePressurePlateBlockMixin` | `SUPPORTED` | Bloqueia placas de pressão para visitantes. | Nenhuma. |
| Usar Porta/Trapdoor | Jogador | `UseBlockCallback` | `SUPPORTED` | Nenhuma. | Nenhuma. |
| Danificar Moldura | Jogador | `AttackEntityCallback` | `SUPPORTED` | Nenhuma. | Nenhuma. |
| Interagir c/ Moldura| Jogador | `UseEntityCallback` | `SUPPORTED` | Nenhuma. | Nenhuma. |

## Observações para Mods e Automações

1. **Fake Players**:
   * O mod trata fake players (usados por mods de automação como Minecarts automáticos, Quarrys, etc.) como visitantes regulares, aplicando restrições rígidas baseadas nas flags locais da região.
2. **Adapters Futuros**:
   * Planejados adapters específicos para mods como `Create`, `Applied Energistics 2`, `Mekanism`, `Industrial Foregoing` para integrar suas ferramentas de quebra e redes com o BigBang Regions API.
