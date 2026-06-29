# 📚 Wiki Geral — BigBang Regions

Esta wiki serve como a referência central para todos os comandos e permissões do sistema de regiões do servidor BigBang Craft.

---

## 🛠️ Comandos do Sistema

### 👤 Comandos de Jogador (Prefixos: `/regiao`, `/regioes`, `/regions`)
Comandos voltados para a gestão do próprio terreno e interação com membros.

| Comando | Descrição | Requisito / Permissão |
| :--- | :--- | :--- |
| `/regiao criar <bioma>` | Inicia pedido de alocação de terreno | `bigbangregions.player.create` |
| `/regiao criar status` | Verifica o status do pedido de alocação | `bigbangregions.player.create` |
| `/regiao criar cancelar` | Cancela pedido de alocação ativo | `bigbangregions.player.create` |
| `/regiao biomas` | Lista biomas disponíveis para alocação | — |
| `/regiao casa` | Teleporta para a home da região | `bigbangregions.player.home` |
| `/regiao sethome` | Define a home na posição atual | `bigbangregions.player.home` |
| `/regiao expandir <tamanho>` | Ajusta o tamanho do claim (1-256) | `bigbangregions.player.expand` |
| `/regiao explorar` | Teleporta para a zona de exploração | `bigbangregions.player.explore` |
| `/regiao info` | Mostra informações da região atual | — |
| `/regiao membros listar` | Lista proprietário, líderes e membros | `OWNER` / `LEADER` |
| `/regiao membros adicionar <player>` | Adiciona um jogador como `MEMBER` | `OWNER` / `LEADER` |
| `/regiao membros remover <player>` | Remove membro ou líder | `OWNER` / `LEADER` |
| `/regiao membros promover <player>` | Promove `MEMBER` $\rightarrow$ `LEADER` | `OWNER` |
| `/regiao membros rebaixar <player>` | Rebaixa `LEADER` $\rightarrow$ `MEMBER` | `OWNER` |
| `/regiao sair` | Sai voluntariamente da região | `MEMBER` / `LEADER` |
| `/regiao flags listar` | Lista todas as flags da região | `OWNER` / `LEADER` |
| `/regiao flags ver <flag>` | Consulta o valor de uma flag específica | `OWNER` / `LEADER` |
| `/regiao flags definir <flag> <valor>` | Define `ALLOW`, `DENY` ou `INHERIT` | `OWNER` / `LEADER` |
| `/regiao mapa <visibilidade>` | Altera visibilidade: `publico`, `privado`, `membros` | `bigbangregions.player.mapvisibility` |
| `/regiao limites <on/off>` | Alterna visualização de limites (partículas) | `bigbangregions.player.boundaries` |

### 🛡️ Comandos de Administração (Prefixo: `/regions`)
Comandos de nível administrativo para controle total do servidor.

| Comando | Descrição | Permissão |
| :--- | :--- | :--- |
| `/regions pos1` / `pos2` | Define a seleção de área para criação | `bigbangregions.admin.create` |
| `/regions create admin <id> [pri]` | Cria região administrativa da seleção (pos1/pos2) | `bigbangregions.admin.create` |
| `/regions admin create <sizeX> <sizeZ> [id] [pri]` | Cria região admin centrada no jogador (sem seleção) | `bigbangregions.admin.create` |
| `/regions create player <id> <owner> [pri]` | Cria região de jogador da seleção | `bigbangregions.admin.player.create` |
| `/regions rename <novoNome>` | Renomeia a região em que o jogador está | `bigbangregions.admin.create` |
| `/regions delete <id\|nick>` | Deleta região ou todas as claims de um jogador | `bigbangregions.admin.delete` |
| `/regions list [página]` | Lista todas as regiões do servidor | `bigbangregions.admin.list` |
| `/regions reload` | Recarrega configurações e banco de dados | `bigbangregions.admin.reload` |
| `/regions info` | Inspeção detalhada da região na posição | `bigbangregions.inspect` |
| `/regions player allocate <player> <bioma>` | Força a alocação de terreno para um jogador | `bigbangregions.admin.player.allocate` |
| `/regions player allocation <player>` | Consulta status de alocação de um jogador | `bigbangregions.admin.player.allocation.inspect` |
| `/regions player allocation <player> cancel` | Cancela alocação de terceiros | `bigbangregions.admin.player.allocation.cancel` |
| `/regions player recycle <slotId>` | Libera slot de terreno `RETIRED` $\rightarrow$ `RELEASED` | `bigbangregions.admin.slot.recycle` |
| `/regions player owner <id>` | Mostra o dono de uma região específica | `bigbangregions.admin.player.owner` |
| `/regions player members <id>` | Lista membros administrativamente | `bigbangregions.admin.player.members` |
| `/regions player addmember <id> <player>` | Adiciona membro via admin | `bigbangregions.admin.player.members` |
| `/regions player removemember <id> <player>` | Remove membro via admin | `bigbangregions.admin.player.members` |
| `/regions player setrole <id> <player> <role>` | Define papel (`LEADER`/`MEMBER`) | `bigbangregions.admin.player.members` |
| `/regions flag set <id> <flag> <valor>` | Define flag em qualquer região | `bigbangregions.admin.flags` |
| `/regions flag get <id> <flag>` | Consulta flag de qualquer região | `bigbangregions.admin.flags` |
| `/regions flags <id>` | Lista flags de qualquer região | `bigbangregions.admin.flags` |

---

## 🔑 Sistema de Permissões

### 🚀 Permissões Administrativas
| Permissão | Descrição |
| :--- | :--- |
| `bigbangregions.admin.create` | Permite selecionar posições e criar regiões admin |
| `bigbangregions.admin.edit` | Permite editar as posições de regiões |
| `bigbangregions.admin.delete` | Permite deletar qualquer região |
| `bigbangregions.admin.list` | Permite listar todas as regiões do banco |
| `bigbangregions.admin.flags` | Permite gerenciar flags de qualquer região |
| `bigbangregions.admin.reload` | Permite recarregar o plugin e configurações |
| `bigbangregions.admin.player.create` | Permite criar regiões de jogador manualmente |
| `bigbangregions.admin.player.owner` | Permite visualizar/alterar proprietários |
| `bigbangregions.admin.player.members` | Permite gerenciar membros administrativamente |
| `bigbangregions.admin.player.allocate` | Permite forçar alocação de terrenos |
| `bigbangregions.admin.player.allocation.inspect` | Permite ver status de alocação de qualquer player |
| `bigbangregions.admin.player.allocation.cancel` | Permite cancelar alocações de qualquer player |
| `bigbangregions.admin.slot.recycle` | Permite reciclar slots de plot retirados |
| `bigbangregions.inspect` | Permite usar `/regions info` com detalhes técnicos |

### 🏠 Permissões de Jogador
| Permissão | Descrição |
| :--- | :--- |
| `bigbangregions.player.create` | Permite solicitar a criação de um terreno |
| `bigbangregions.player.home` | Permite usar `/regiao casa` e `/regiao sethome` |
| `bigbangregions.player.explore` | Permite usar `/regiao explorar` |
| `bigbangregions.player.expand` | Permite expandir ou contrair o claim |
| `bigbangregions.player.mapvisibility` | Permite alterar a visibilidade do mapa |
| `bigbangregions.player.boundaries` | Permite ligar/desligar a visualização de limites |

### ⚡ Bypass (Ignorar Proteção)
| Permissão | Descrição |
| :--- | :--- |
| `bigbangregions.bypass` | Ignora **absolutamente todas** as proteções do mod |
| `bigbangregions.bypass.<flagId>` | Ignora apenas a proteção da flag especificada |

---

## 👥 Hierarquia de Papéis (Roles)

A permissão final de ação é a interseção entre o **Papel do Jogador** e a **Flag da Região**. Se uma flag estiver como `DENY`, ela bloqueia a ação mesmo para o OWNER.

| Papel | Construir / Interagir | Gerenciar Membros | Alterar Flags | Observação |
| :--- | :---: | :---: | :---: | :--- |
| **OWNER** | ✅ | ✅ (Total) | ✅ | Dono absoluto do terreno. |
| **LEADER** | ✅ | ✅ (Apenas Members) | ✅ | Administrador delegado. |
| **MEMBER** | ✅ | ❌ | ❌ | Acesso básico de construção. |
| **VISITOR** | ❌ | ❌ | ❌ | Ações dependem apenas das flags. |

---

## 🚩 Referência de Flags
As flags controlam o que é permitido em cada região.

- `player-build`: Quebrar/colocar blocos.
- `player-interact`: Interação genérica com blocos.
- `container-access`: Acesso a baús, fornalhas, etc.
- `door-use`: Abrir/fechar portas, alçapões e portões.
- `redstone-use`: Botões, alavancas e placas de pressão.
- `entity-interact`: Armor stands, item frames, barcos.
- `pvp`: Combate entre jogadores.
- `item-pickup`: Coletar itens do chão.
- `item-drop`: Soltar itens no chão.

**Valores possíveis:** `ALLOW` (Permitido), `DENY` (Bloqueado), `INHERIT` (Herda do padrão).
