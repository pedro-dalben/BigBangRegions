# Comandos do Jogador — BigBang Regions

## Gerenciamento de Terreno

| Comando | Descrição | Permissão |
|---------|-----------|-----------|
| `/regiao criar <bioma>` | Inicia pedido de alocação de terreno | `bigbangregions.player.create` |
| `/regiao criar aqui` | Inicia pedido centrado na posição atual, sem exigir bioma da lista | `bigbangregions.player.create` |
| `/regiao criar status` | Mostra status do pedido de alocação | `bigbangregions.player.create` |
| `/regiao criar cancelar` | Cancela pedido de alocação ativo | `bigbangregions.player.create` |
| `/regiao biomas` | Lista opções de bioma disponíveis | — |
| `/regiao casa` | Teleporta para a casa da sua região | `bigbangregions.player.home` |
| `/regiao sethome` | Define a casa na posição atual (dentro da região) | `bigbangregions.player.home` |
| `/regiao expandir <tamanho>` | Inicia uma expansão paga para o tamanho permitido | `bigbangregions.player.expand` |
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

Abra `/regiao` e clique em `Chunk loader`. Se você estiver no seu terreno, o menu abre diretamente na página do seu chunk atual. O brilho e a mensagem `VOCÊ ESTÁ ATUALMENTE NESTE CHUNK` identificam a posição; clique esquerdo ativa ou desativa o loader. O menu não teleporta jogadores.

Somente o OWNER pode selecionar chunks. Membros, líderes e amigos não podem selecionar chunks e os créditos deles nunca são somados à quota do owner.

| Comando | Descrição |
|---|---|
| `/regiao chunks comprar` | Ativa o loader no chunk atual, se ele estiver dentro do seu terreno e houver quota. |
| `/regiao chunks ver` | Liga/desliga a grade visual 5×5 ao redor do jogador. Azul é o chunk atual; verde é um ticket ativo nesta sessão; amarelo é um chunk selecionado sem ticket. |

No JourneyMap, os mesmos chunks aparecem como tiles: verde para ticket ativo e amarelo para seleção persistida. Eles são privados ao OWNER; staff com `bigbangregions.journeymap.view-all` também pode vê-los.

O item de status mostra:

- tamanho atual da região em blocos;
- quantidade total de chunks cobertos pela região;
- chunks selecionados e carregados no momento;
- créditos vindos da permissão;
- créditos extras concedidos internamente;
- créditos ainda disponíveis.

Os chunks selecionados ficam salvos no SQLite. Eles deixam de receber tickets quando o owner sai do servidor e voltam a ser carregados quando ele entra novamente. Por isso, o menu separa `selecionado` de `ticket ativo nesta sessão`.
| `/regiao pos1` / `pos2` | Define seleção (admin) |
