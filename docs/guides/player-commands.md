# Comandos do Jogador — BigBang Regions

## Gerenciamento de Terreno

| Comando | Descrição | Permissão |
|---------|-----------|-----------|
| `/regiao criar <bioma>` | Inicia pedido de alocação de terreno | `bigbangregions.player.create` |
| `/regiao criar status` | Mostra status do pedido de alocação | `bigbangregions.player.create` |
| `/regiao criar cancelar` | Cancela pedido de alocação ativo | `bigbangregions.player.create` |
| `/regiao biomas` | Lista opções de bioma disponíveis | — |
| `/regiao casa` | Teleporta para a casa da sua região | `bigbangregions.player.home` |
| `/regiao sethome` | Define a casa na posição atual (dentro da região) | `bigbangregions.player.home` |
| `/regiao expandir status` | Consulta uma expansão em andamento | `bigbangregions.player.expand` |
| `/regiao expandir cancelar` | Cancela uma expansão antes do redimensionamento | `bigbangregions.player.expand` |
| `/regiao explorar` | Teleporta para o centro da zona de exploração | `bigbangregions.player.explore` |

## Membros

| Comando | Descrição |
|---------|-----------|
| `/regiao membros listar` | Lista membros da sua região |
| `/regiao membros adicionar <player>` | Adiciona MEMBER |
| `/regiao membros remover <player>` | Remove membro |
| `/regiao membros promover <player>` | Promove MEMBER → LEADER |
| `/regiao membros rebaixar <player>` | Rebaixa LEADER → MEMBER |
| `/regiao sair` | Sai da região atual |

## Flags

| Comando | Descrição |
|---------|-----------|
| `/regiao flags listar` | Lista flags da região |
| `/regiao flags ver <flag>` | Ver valor de uma flag |
| `/regiao flags definir <flag> <ALLOW/DENY/INHERIT>` | Define flag |

## Mapa e Limites

| Comando | Descrição | Permissão |
|---------|-----------|-----------|
| `/regiao mapa` | Mostra visibilidade atual do mapa | `bigbangregions.player.mapvisibility` |
| `/regiao mapa publico` | Mapa visível para todos | `bigbangregions.player.mapvisibility` |
| `/regiao mapa privado` | Mapa visível apenas para você | `bigbangregions.player.mapvisibility` |
| `/regiao mapa membros` | Mapa visível para membros da região | `bigbangregions.player.mapvisibility` |
| `/regiao limites` | Alterna limites visuais (partículas) | `bigbangregions.player.boundaries` |
| `/regiao limites on` | Ativa limites visuais | `bigbangregions.player.boundaries` |
| `/regiao limites off` | Desativa limites visuais | `bigbangregions.player.boundaries` |

## Informação

| Comando | Descrição |
|---------|-----------|
| `/regiao info` | Informações da região na posição atual |

## Chunk Loader

Abra `/regiao` e clique em `Chunk loader`. O menu mostra os chunks da sua região em uma grade. Use as setas para navegar quando a região tiver mais chunks que o espaço do menu.

Somente o OWNER pode selecionar chunks. Membros, líderes e amigos não podem selecionar chunks e os créditos deles nunca são somados à quota do owner.

O item de status mostra:

- tamanho atual da região em blocos;
- quantidade total de chunks cobertos pela região;
- chunks selecionados e carregados no momento;
- créditos vindos da permissão;
- créditos extras concedidos internamente;
- créditos ainda disponíveis.

Os chunks selecionados ficam salvos no SQLite. Eles deixam de receber tickets quando o owner sai do servidor e voltam a ser carregados quando ele entra novamente.
| `/regiao pos1` / `pos2` | Define seleção (admin) |
