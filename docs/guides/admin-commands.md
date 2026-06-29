# Comandos de Administração — BigBang Regions

## Gerenciamento de Regiões

| Comando | Descrição | Permissão |
|---------|-----------|-----------|
| `/regions create admin <id> [priority]` | Cria região administrativa (seleção pos1/pos2) | `bigbangregions.admin.create` |
| `/regions admin create <sizeX> <sizeZ> [id] [priority]` | Cria região admin centrada no jogador (sem seleção) | `bigbangregions.admin.create` |
| `/regions create player <id> <owner> [priority]` | Cria região de jogador (seleção) | `bigbangregions.admin.player.create` |
| `/regions rename <novoNome>` | Renomeia a região em que o jogador está | `bigbangregions.admin.create` |
| `/regions delete <id|nick>` | Deleta região por ID ou todas as regiões do dono | `bigbangregions.admin.delete` |
| `/regions list [page]` | Lista todas as regiões | `bigbangregions.admin.list` |
| `/regions reload` | Recarrega config + regiões do banco | `bigbangregions.admin.reload` |

## Gerenciamento de Alocação

| Comando | Descrição | Permissão |
|---------|-----------|-----------|
| `/regions player allocate <player> <bioma>` | Aloca terreno para jogador | `bigbangregions.admin.player.allocate` |
| `/regions player allocation <player>` | Ver status de alocação | `bigbangregions.admin.player.allocation.inspect` |
| `/regions player allocation <player> cancel` | Cancela alocação | `bigbangregions.admin.player.allocation.cancel` |
| `/regions player recycle <slotId>` | Recicla slot RETIRED → RELEASED | `bigbangregions.admin.slot.recycle` |

## Membros (Admin)

| Comando | Descrição |
|---------|-----------|
| `/regions player owner <regionId>` | Mostra dono da região |
| `/regions player members <regionId>` | Lista membros |
| `/regions player addmember <regionId> <player>` | Adiciona MEMBER |
| `/regions player removemember <regionId> <player>` | Remove membro |
| `/regions player setrole <regionId> <player> <role>` | Define papel (LEADER/MEMBER) |

## Flags (Admin)

| Comando | Descrição |
|---------|-----------|
| `/regions flag set <regionId> <flag> <value>` | Define flag |
| `/regions flag get <regionId> <flag>` | Consulta flag |
| `/regions flags <regionId>` | Lista flags da região |

## Permissões Disponíveis

```
bigbangregions.admin.create           - Criar regiões administrativas
bigbangregions.admin.edit             - Editar regiões (pos1/pos2)
bigbangregions.admin.delete           - Deletar regiões
bigbangregions.admin.list             - Listar regiões
bigbangregions.admin.flags            - Gerenciar flags (admin)
bigbangregions.admin.reload           - Recarregar config
bigbangregions.admin.player.create    - Criar região para jogador
bigbangregions.admin.player.owner     - Ver dono
bigbangregions.admin.player.members   - Gerenciar membros (admin)
bigbangregions.admin.player.allocate  - Alocar para jogador
bigbangregions.admin.player.allocation.inspect - Ver status alocação
bigbangregions.admin.player.allocation.cancel  - Cancelar alocação
bigbangregions.admin.slot.recycle     - Reciclar slot
bigbangregions.inspect                - Inspecionar regiões (/regions info)
bigbangregions.bypass                 - Bypass geral de proteção
bigbangregions.bypass.<flag>          - Bypass específico

bigbangregions.player.create          - Criar pedido de terreno
bigbangregions.player.home            - Usar /casa e /sethome
bigbangregions.player.explore         - Usar /explorar
bigbangregions.player.expand          - Expandir terreno
bigbangregions.player.mapvisibility   - Gerenciar visibilidade do mapa
bigbangregions.player.boundaries      - Ver limites visuais (partículas)
```
