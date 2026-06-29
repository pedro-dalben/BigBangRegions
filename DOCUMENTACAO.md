# Documentação Completa — BigBang Regions

---

## Sumário

1. [Visão Geral](#1-visão-geral)
2. [Arquitetura e Módulos](#2-arquitetura-e-módulos)
3. [Tipos de Região](#3-tipos-de-região)
4. [Hierarquia de Membros (Roles)](#4-hierarquia-de-membros-roles)
5. [Flags de Proteção](#5-flags-de-proteção)
6. [Sistema de Proteção](#6-sistema-de-proteção)
7. [Sistema de Alocação de Terrenos](#7-sistema-de-alocação-de-terrenos)
8. [Comandos de Jogador](#8-comandos-de-jogador)
9. [Comandos de Administração](#9-comandos-de-administração)
10. [Permissões Completas](#10-permissões-completas)
11. [Integração JourneyMap (Opcional)](#11-integração-journeymap-opcional)
12. [Estrutura de Configuração](#12-estrutura-de-configuração)
13. [Banco de Dados (SQLite)](#13-banco-de-dados-sqlite)
14. [API Pública](#14-api-pública)
15. [Eventos e Mixins](#15-eventos-e-mixins)
16. [Cache e Performance](#16-cache-e-performance)
17. [Limitações Conhecidas](#17-limitações-conhecidas)

---

## 1. Visão Geral

**BigBang Regions** é um mod server-side para **Fabric 1.21.1** (Java 21) desenvolvido para o servidor **BigBang Craft**. Ele implementa um sistema completo de proteção territorial com:

- Regiões administrativas, de jogador e de sistema
- 9 flags de proteção com valores ALLOW/DENY/INHERIT
- Alocação automatizada de terrenos com matching por bioma
- Gerenciamento de membros com 4 níveis hierárquicos
- Renderização de limites com partículas
- Sistema de home com cooldown
- Zona de exploração teleportável
- Expansão/redimensionamento de claims
- Persistência SQLite com auditoria
- API pública para integração

| Propriedade | Valor |
|---|---|
| **Mod ID** | `bigbangregions` |
| **Classe Principal** | `com.bigbangcraft.regions.BigBangRegions` |
| **Loader** | Fabric |
| **Minecraft** | 1.21.1 |
| **Java** | 21 |
| **Ambiente** | Server-only |
| **Dependência Opcional** | `fabric-permissions-api` (LuckPerms) |

---

## 2. Arquitetura e Módulos

O mod segue uma arquitetura em camadas com pacotes bem definidos:

```
com.bigbangcraft.regions
├── BigBangRegions.java              -- Inicializador principal
├── api/                             -- API pública para outros mods
├── allocation/                      -- Alocação automatizada de terrenos
├── audit/                           -- Logging de auditoria assíncrono
├── cache/                           -- Caches em memória
├── command/                         -- Registro de comandos (Brigadier)
├── config/                          -- Configuração JSON
├── domain/                          -- Modelos de domínio
├── flag/                            -- Sistema de flags
├── mixin/                           -- Mixins de proteção
├── permission/                      -- Gerenciamento de permissões
├── protection/                      -- Motor de proteção
├── region/                          -- Serviços de região
├── repository/                      -- Camada de acesso a dados
├── storage/                         -- Conexão SQLite e migrações
└── util/                            -- Utilitários
```

### Detalhamento dos Módulos

#### `api/` — API Pública
- `BigBangRegionsApi` — Interface pública com métodos para consultar regiões e verificar proteção
- `BigBangRegionsApiImpl` — Implementação acessível via `BigBangRegions.getApi()`
- `RegionView` — Record imutável com dados da região

#### `allocation/` — Alocação de Terrenos
- `AllocationScheduler` — Agendador baseado em server tick
- `BiomeSearchService` — Busca por bioma em grid
- `PlotSlotService` — Geração e filtragem de slots candidatos
- `SafeSpawnFinder` — Encontra posição segura para spawn
- `TerrainAllocationCoordinator` — Orquestrador do pipeline de alocação
- `ExplorationZoneService` — Teleporte para zona de exploração

#### `audit/` — Auditoria
- `AuditService` — Logging assíncrono em thread única

#### `cache/` — Caches
- `RegionCache` — Cache de regiões em memória
- `RegionMembershipCache` — Cache de papéis dos jogadores
- `ChunkSpatialIndex` — Índice espacial por chunk para lookup rápido

#### `command/` — Comandos
- `RegionsCommand` — Todos os comandos do mod registrados via Brigadier

#### `config/` — Configuração
- `Config` — POJO com todas as opções de configuração
- `ConfigManager` — Carregamento e salvamento JSON

#### `domain/` — Domínio
- `Region` — Modelo central de região
- `RegionBounds` — Bounding box 3D
- `RegionMember` — Associação jogador-região
- `RegionRole` — Enum hierárquico: OWNER, LEADER, MEMBER, VISITOR
- `RegionType` — Enum: SYSTEM_REGION, ADMIN_REGION, PLAYER_REGION

#### `flag/` — Flags
- `FlagRegistry` — Registro de definições de flag
- `FlagResolver` — Resolve a política efetiva de uma flag
- `FlagPolicy` — ALLOW / DENY / INHERIT
- `RegionFlag` — Metadados da flag

#### `mixin/` — Mixins
- `PlayerMixin` — Proteção PVP e item-drop
- `ItemEntityMixin` — Proteção item-pickup
- `BasePressurePlateBlockMixin` — Proteção de placas de pressão

#### `permission/` — Permissões
- `PermissionManager` — Verificação de permissões com fallback para OP

#### `protection/` — Proteção
- `ProtectionService` — Motor principal de avaliação de proteção
- `RegionAccessService` — Avaliação de acesso por papel
- `BlockInteractionClassifier` — Classificador de tipos de interação
- `RegionAction` — Mapeamento ação → flag
- `ProtectionDecision` — ALLOW / DENY / NO_REGION / BYPASS
- `ProtectionResult` — Resultado completo da verificação

#### `region/` — Serviços
- `RegionResolver` — Lookup de região com ordenação por prioridade
- `RegionRoleResolver` — Resolução de papel via cache
- `RegionMembershipService` — Gerenciamento de membros
- `RegionEntryExitService` — Notificações de entrada/saída
- `RegionBoundaryRenderer` — Renderização de limites com partículas

#### `repository/` — Dados
- `RegionRepository` — CRUD de regiões, membros e flags
- `AllocationRequestRepository` — Operações de alocação
- `PlotSlotRepository` — Operações de slots
- `PlayerRegionHomeRepository` — Homes dos jogadores
- `AuditRepository` — Logs de auditoria

#### `storage/` — Persistência
- `DatabaseManager` — Gerenciamento SQLite + runner de migrações

---

## 3. Tipos de Região

O mod define 3 tipos hierárquicos de região:

| Tipo | Prioridade Padrão | Uso |
|---|---|---|
| `SYSTEM_REGION` | 10000 | Regiões de sistema (maior prioridade) |
| `ADMIN_REGION` | 1000 | Regiões administrativas do servidor |
| `PLAYER_REGION` | 100 | Terrenos de jogadores |

Regras de prioridade:
1. Regiões com maior `priority` têm precedência
2. `SYSTEM_REGION` > `ADMIN_REGION` > `PLAYER_REGION` independente do valor numérico

---

## 4. Hierarquia de Membros (Roles)

### Tabela de Ações por Papel

| Ação | OWNER | LEADER | MEMBER | VISITOR |
|---|---|---|---|---|
| Construir (quebrar/colocar blocos) | Sim | Sim | Sim | Não |
| Abrir baús/fornalhas/containers | Sim | Sim | Sim | Não |
| Usar portas/alçapões/portões | Sim | Sim | Sim | Não |
| Usar botões/alavancas/redstone | Sim | Sim | Sim | Não |
| Interagir com entidades | Sim | Sim | Sim | Não |
| Pegar itens do chão | Sim | Sim | Sim | Não |
| Soltar itens | Sim | Sim | Sim | Não |
| Adicionar/remover MEMBER | Sim | Sim | Não | Não |
| Promover/rebaixar LEADER | Sim | Não | Não | Não |
| Alterar flags | Sim | Sim | Não | Não |
| Sair da região | Não | Sim | Sim | N/A |

> **Importante:** A tabela assume que a flag correspondente está ALLOW. Uma flag em DENY bloqueia a ação para TODOS os papéis, incluindo OWNER.

### OWNER (Dono)
- Definido por `regions.owner_uuid`
- Não pode ser removido da região
- Não pode sair voluntariamente
- Controle total: membros, flags, expansão

### LEADER (Líder)
- Salvo em `region_members` com papel LEADER
- Gerencia membros (adicionar/remover MEMBER)
- Altera flags do terreno
- **Não pode** promover/demover outros líderes nem remover o dono

### MEMBER (Membro)
- Salvo em `region_members` com papel MEMBER
- Acesso total a construções e containers
- **Não pode** gerenciar outros membros
- Pode sair voluntariamente com `/regiao sair`

### VISITOR (Visitante)
- Qualquer jogador não listado como OWNER/LEADER/MEMBER
- **Sem entrada** em `region_members`
- Ações governadas estritamente pelas flags
- Pode circular fisicamente pelo terreno

---

## 5. Flags de Proteção

### Flags Suportadas

| Flag ID | Categoria | Global | Admin | Player | Descrição |
|---|---|---|---|---|---|
| `player-build` | proteção | ALLOW | DENY | ALLOW | Quebrar/colocar blocos |
| `player-interact` | proteção | ALLOW | DENY | ALLOW | Interagir com blocos (clique direito genérico) |
| `container-access` | proteção | ALLOW | DENY | ALLOW | Acessar baús, fornalhas, barris, máquinas |
| `door-use` | proteção | ALLOW | DENY | ALLOW | Abrir/fechar portas, alçapões, portões |
| `redstone-use` | proteção | ALLOW | DENY | ALLOW | Interagir com botões, alavancas, placas |
| `entity-interact` | proteção | ALLOW | DENY | ALLOW | Interagir com armor stands, item frames, barcos |
| `pvp` | combate | ALLOW | DENY | DENY | Combate direto entre jogadores |
| `item-pickup` | utilidade | ALLOW | ALLOW | ALLOW | Coletar itens do chão |
| `item-drop` | utilidade | ALLOW | ALLOW | ALLOW | Soltar itens no chão |

### Valores de Flag

| Valor | Significado |
|---|---|
| `ALLOW` | Ação permitida |
| `DENY` | Ação bloqueada |
| `INHERIT` | Herda do padrão do tipo de região |

### Ordem de Resolução (cascata)

1. Valor explícito salvo na região (ALLOW/DENY)
2. Padrão do tipo de região (`adminRegion`, `playerRegion` no config.json)
3. Padrão global (`global` no config.json)
4. Fallback estático definido no código

### Flags Planejadas (futuras)

`hostile-mob-spawn`, `passive-mob-spawn`, `fire-spread`, `fluid-flow`, `explosion-block-damage`, `piston-move`, `projectile-use`, `mob-griefing`, `crop-trample`, `teleport-in`, `teleport-out`

---

## 6. Sistema de Proteção

### Fluxo de Resolução

```
Ação do jogador
  → Mixin/Evento intercepta
  → ProtectionService.check(context)
    → Jogador tem bypass? → BYPASS (permite)
    → Resolve região na posição (RegionResolver)
      → Nenhuma região → NO_REGION (permite)
    → Ator UNKNOWN em ação destrutiva? → DENY
    → Região PLAYER_REGION?
      → Jogador é OWNER/LEADER/MEMBER? → ALLOW
      → VISITOR → avalia flag
    → Região ADMIN/SYSTEM?
      → Avalia flag diretamente
  → ProtectionResult (decisão + região + motivo)
```

### Decisões de Proteção

| Decisão | Efeito |
|---|---|
| `ALLOW` | Ação permitida |
| `DENY` | Ação bloqueada (mensagem enviada ao jogador) |
| `NO_REGION` | Nenhuma região na posição (permitido) |
| `BYPASS` | Jogador tem permissão de bypass (permitido) |

### Ações Mapeadas (RegionAction)

| Ação | Flag | Evento/Mixin |
|---|---|---|
| `BLOCK_BREAK` | `player-build` | `PlayerBlockBreakEvents.BEFORE` |
| `BLOCK_PLACE` | `player-build` | `UseBlockCallback` (BlockItem/BucketItem) |
| `INTERACT` | `player-interact` | `UseBlockCallback` (fallback) |
| `CONTAINER` | `container-access` | `UseBlockCallback` (Container/MenuProvider) |
| `DOOR` | `door-use` | `UseBlockCallback` (DoorBlock/TrapdoorBlock/FenceGateBlock) |
| `REDSTONE` | `redstone-use` | `UseBlockCallback` + `BasePressurePlateBlockMixin` |
| `ENTITY_INTERACT` | `entity-interact` | `UseEntityCallback`, `AttackEntityCallback` |
| `PVP` | `pvp` | `PlayerMixin.onHurt` |
| `ITEM_PICKUP` | `item-pickup` | `ItemEntityMixin.playerTouch` |
| `ITEM_DROP` | `item-drop` | `PlayerMixin.onDrop` |

### Bypass

- `bigbangregions.bypass` — Ignora TODAS as proteções
- `bigbangregions.bypass.<flagId>` — Ignora uma flag específica

---

## 7. Sistema de Alocação de Terrenos

### Pipeline de Alocação

```
PENDING → SEARCHING → SLOT_RESERVED → PREPARING → COMPLETED
  ↓          ↓             ↓               ↓
CANCELLED  FAILED       FAILED          FAILED
```

1. **PENDING** — Jogador executa `/regiao criar <bioma>`
2. **SEARCHING** — `AllocationScheduler` busca slots em espiral a partir da zona de exploração
3. **SLOT_RESERVED** — Slot encontrado, reservado temporariamente (lease)
4. **PREPARING** — Região criada no banco e no cache com tamanho inicial
5. **COMPLETED** — Spawn seguro encontrado, home criado, jogador notificado

### Estados de Plot Slot

`RESERVED` → `ALLOCATED` → `RETIRED` → `RELEASED` (via recycle)

### Expansão de Claim

- Comando: `/regiao expandir <tamanho>` (1–256)
- Não pode exceder `futureMaximumClaimSize` (config)
- Não pode ultrapassar o limite do plot slot

### Zona de Exploração

- Região central (config: `explorationExclusion`)
- Teleporte via `/regiao explorar`
- Slots são gerados a partir do perímetro desta zona

---

## 8. Comandos de Jogador

### Aliases
- `/regions`
- `/regiao`
- `/regioes`

### Gerenciamento do Terreno

| Comando | Descrição | Permissão |
|---|---|---|
| `/regiao criar <bioma>` | Inicia pedido de alocação de terreno | `bigbangregions.player.create` |
| `/regiao criar status` | Status do pedido de alocação | `bigbangregions.player.create` |
| `/regiao criar cancelar` | Cancela pedido ativo | `bigbangregions.player.create` |
| `/regiao casa` | Teleporta para a home da região | `bigbangregions.player.home` |
| `/regiao sethome` | Define a home na posição atual (dentro do terreno) | `bigbangregions.player.home` |
| `/regiao expandir <tamanho>` | Expande/contrai o claim (1–256) | `bigbangregions.player.expand` |
| `/regiao biomas` | Lista opções de bioma disponíveis | — |
| `/regiao explorar` | Teleporta para zona de exploração | `bigbangregions.player.explore` |

### Membros

| Comando | Descrição | Requisito |
|---|---|---|
| `/regiao membros listar` | Lista membros da região | OWNER/LEADER |
| `/regiao membros adicionar <player>` | Adiciona MEMBER | OWNER/LEADER |
| `/regiao membros remover <player>` | Remove membro | OWNER/LEADER |
| `/regiao membros promover <player>` | Promove MEMBER → LEADER | OWNER |
| `/regiao membros rebaixar <player>` | Rebaixa LEADER → MEMBER | OWNER |
| `/regiao sair` | Sai da região atual | MEMBER/LEADER |

### Flags

| Comando | Descrição | Requisito |
|---|---|---|
| `/regiao flags listar` | Lista flags da região | OWNER/LEADER |
| `/regiao flags ver <flag>` | Ver valor de flag específica | OWNER/LEADER |
| `/regiao flags definir <flag> <ALLOW/DENY/INHERIT>` | Define valor da flag | OWNER/LEADER |

### Mapa e Limites

| Comando | Descrição | Permissão |
|---|---|---|
| `/regiao mapa` | Mostra visibilidade atual | `bigbangregions.player.mapvisibility` |
| `/regiao mapa publico` | Visível para todos | `bigbangregions.player.mapvisibility` |
| `/regiao mapa privado` | Visível apenas para você | `bigbangregions.player.mapvisibility` |
| `/regiao mapa membros` | Visível para membros | `bigbangregions.player.mapvisibility` |
| `/regiao limites` | Alterna limites visuais | `bigbangregions.player.boundaries` |
| `/regiao limites on` | Ativa limites | `bigbangregions.player.boundaries` |
| `/regiao limites off` | Desativa limites | `bigbangregions.player.boundaries` |

### Informação

| Comando | Descrição |
|---|---|
| `/regiao info` | Informações da região na posição atual |

### Comandos Desabilitáveis via Config

Os seguintes comandos podem ser desabilitados no `config.json` em `disabledCommands`:
`criar`, `casa`, `sethome`, `biomas`, `limites`, `explorar`, `expandir`, `mapa`

---

## 9. Comandos de Administração

### Gerenciamento de Regiões

| Comando | Descrição | Permissão |
|---|---|---|
| `/regions pos1` | Define posição 1 na localização atual | `bigbangregions.admin.create` |
| `/regions pos2` | Define posição 2 na localização atual | `bigbangregions.admin.create` |
| `/regions create admin <id> [priority]` | Cria região admin da seleção (pos1/pos2) | `bigbangregions.admin.create` |
| `/regions admin create <sizeX> <sizeZ> [id] [priority]` | Cria região admin centrada no jogador (sem seleção) | `bigbangregions.admin.create` |
| `/regions create player <id> <owner> [priority]` | Cria região de jogador da seleção | `bigbangregions.admin.player.create` |
| `/regions rename <novoNome>` | Renomeia a região em que o jogador está | `bigbangregions.admin.create` |
| `/regions delete <id>` | Deleta região + libera slot (PLAYER) | `bigbangregions.admin.delete` |
| `/regions list [page]` | Lista todas as regiões (paginado, 10/página) | `bigbangregions.admin.list` |
| `/regions reload` | Recarrega config + regiões do banco | `bigbangregions.admin.reload` |
| `/regions info` | Info da região na posição atual | `bigbangregions.inspect` |

### Gerenciamento de Alocação

| Comando | Descrição | Permissão |
|---|---|---|
| `/regions player allocate <player> <bioma>` | Aloca terreno para jogador | `bigbangregions.admin.player.allocate` |
| `/regions player allocation <player>` | Status da alocação | `bigbangregions.admin.player.allocation.inspect` |
| `/regions player allocation <player> cancel` | Cancela alocação | `bigbangregions.admin.player.allocation.cancel` |
| `/regions player recycle <slotId>` | Recicla slot (RETIRED → RELEASED) | `bigbangregions.admin.slot.recycle` |

### Membros (Admin)

| Comando | Descrição | Permissão |
|---|---|---|
| `/regions player owner <regionId>` | Mostra dono da região | `bigbangregions.admin.player.owner` |
| `/regions player members <regionId>` | Lista membros | `bigbangregions.admin.player.members` |
| `/regions player addmember <regionId> <player>` | Adiciona MEMBER | `bigbangregions.admin.player.members` |
| `/regions player removemember <regionId> <player>` | Remove membro | `bigbangregions.admin.player.members` |
| `/regions player setrole <regionId> <player> <leader\|member>` | Define papel | `bigbangregions.admin.player.members` |

### Flags (Admin)

| Comando | Descrição | Permissão |
|---|---|---|
| `/regions flag set <regionId> <flag> <value>` | Define flag em qualquer região | `bigbangregions.admin.flags` |
| `/regions flag get <regionId> <flag>` | Consulta flag | `bigbangregions.admin.flags` |
| `/regions flags <regionId>` | Lista flags da região | `bigbangregions.admin.flags` |

---

## 10. Permissões Completas

### Permissões de Administrador

| Permissão | Descrição |
|---|---|
| `bigbangregions.admin.create` | Selecionar posições e criar regiões admin |
| `bigbangregions.admin.edit` | Editar regiões (pos1/pos2) |
| `bigbangregions.admin.delete` | Deletar regiões |
| `bigbangregions.admin.list` | Listar todas as regiões |
| `bigbangregions.admin.flags` | Gerenciar flags administrativamente |
| `bigbangregions.admin.reload` | Recarregar configuração e dados |
| `bigbangregions.admin.player.create` | Criar regiões de jogador manualmente |
| `bigbangregions.admin.player.owner` | Visualizar/alterar dono de terreno |
| `bigbangregions.admin.player.members` | Gerenciar membros administrativamente |
| `bigbangregions.admin.player.allocate` | Alocar terreno para jogador |
| `bigbangregions.admin.player.allocation.inspect` | Ver status de alocação de qualquer jogador |
| `bigbangregions.admin.player.allocation.cancel` | Cancelar alocação de qualquer jogador |
| `bigbangregions.admin.slot.recycle` | Reciclar slots de plot retirados |
| `bigbangregions.inspect` | Inspecionar regiões com info detalhada |

### Permissões de Jogador

| Permissão | Descrição |
|---|---|
| `bigbangregions.player.create` | Criar pedido de alocação de terreno |
| `bigbangregions.player.home` | Usar /casa e /sethome |
| `bigbangregions.player.explore` | Usar /explorar |
| `bigbangregions.player.expand` | Expandir/redimensionar claim |
| `bigbangregions.player.mapvisibility` | Gerenciar visibilidade no mapa |
| `bigbangregions.player.boundaries` | Visualizar limites com partículas |

### Permissões de Bypass

| Permissão | Descrição |
|---|---|
| `bigbangregions.bypass` | Ignorar TODAS as proteções |
| `bigbangregions.bypass.<flagId>` | Ignorar proteção de uma flag específica |

### Fallback
Se o `fabric-permissions-api` (LuckPerms) não estiver presente, o mod usa o nível de operador (`operatorFallbackLevel`, padrão: 2) como fallback.

---

## 11. Integração JourneyMap (Opcional)

O mod possui integração opcional com o JourneyMap via API v2 (`journeymap-api-fabric:2.0.0-1.21.1`).

### Ativação
A integração é ativada automaticamente quando o JourneyMap está instalado no servidor e a configuração `journeyMap.enabled` está como `true`.

### Funcionalidades
- Overlay poligonal com contorno e preenchimento para cada região
- Marcador central com nome e tipo da região
- Cores e opacidade configuráveis por tipo de região
- Visibilidade controlada por permissões

### Permissões Específicas

| Permissão | Descrição |
|---|---|
| `bigbangregions.journeymap.view-own` | Ver própria região (automático para membros) |
| `bigbangregions.journeymap.view-public` | Ver regiões públicas de jogadores |
| `bigbangregions.journeymap.view-admin` | Ver regiões administrativas (STAFF_ONLY/PERMISSION) |
| `bigbangregions.journeymap.view-all` | Ver TODAS as regiões |

### Visibilidade de Regiões Administrativas

| Modo | Efeito |
|---|---|
| `PUBLIC` | Visível para todos |
| `STAFF_ONLY` | Requer permissão `view-admin` (padrão) |
| `HIDDEN` | Invisível para todos |
| `PERMISSION` | Requer permissão `view-admin` |

### Documentação Completa
- `docs/bigbangregions/journeymap-integration.md`
- `docs/bigbangregions/journeymap-qa.md`

---

## 12. Estrutura de Configuração

Arquivo: `config/bigbangregions/config.json` (gerado automaticamente na primeira execução)

```json
{
  "schemaVersion": 1,

  "defaultPriorities": {
    "systemRegion": 10000,
    "adminRegion": 1000,
    "playerRegion": 100
  },

  "permissions": {
    "operatorFallbackLevel": 2
  },

  "defaults": {
    "global": {
      "player-build": "ALLOW",
      "player-interact": "ALLOW",
      "container-access": "ALLOW",
      "door-use": "ALLOW",
      "redstone-use": "ALLOW",
      "entity-interact": "ALLOW",
      "pvp": "ALLOW",
      "item-pickup": "ALLOW",
      "item-drop": "ALLOW"
    },
    "adminRegion": {
      "player-build": "DENY",
      "player-interact": "DENY",
      "container-access": "DENY",
      "door-use": "DENY",
      "redstone-use": "DENY",
      "entity-interact": "DENY",
      "pvp": "DENY",
      "item-pickup": "ALLOW",
      "item-drop": "ALLOW"
    },
    "playerRegion": {
      "player-build": "ALLOW",
      "player-interact": "ALLOW",
      "container-access": "ALLOW",
      "door-use": "ALLOW",
      "redstone-use": "ALLOW",
      "entity-interact": "ALLOW",
      "pvp": "DENY",
      "item-pickup": "ALLOW",
      "item-drop": "ALLOW"
    }
  },

  "playerRegions": {
    "maxRegionsPerOwner": 1,
    "rejectOverlapWithAdminRegions": true,
    "rejectOverlapWithSystemRegions": true,
    "rejectOverlapWithPlayerRegions": true
  },

  "playerLandAllocation": {
    "enabled": true,
    "targetDimension": "minecraft:overworld",
    "initialClaimSize": 50,
    "slotSize": 256,
    "futureMaximumClaimSize": 240,
    "slotInternalMargin": 8,
    "maxRegionsPerOwner": 1,
    "explorationExclusion": {
      "minX": -20000,
      "maxX": 20000,
      "minZ": -20000,
      "maxZ": 20000,
      "safetyBuffer": 1000
    },
    "biomeSearch": {
      "minimumMatchPercentage": 60,
      "sampleGridSize": 5,
      "maximumCandidateSlots": 100,
      "maximumSearchRadiusBlocks": 120000
    },
    "scheduler": {
      "maxActiveRequests": 1,
      "maxCandidateEvaluationsPerTick": 256,
      "maxPreparationChunksPerTick": 1,
      "maxBiomeSearchMillisPerTick": 25,
      "requestTimeoutSeconds": 180,
      "reservationLeaseSeconds": 300,
      "creationCooldownSeconds": 60,
      "homeTeleportCooldownSeconds": 30
    },
    "notifications": {
      "entryExitEnabled": true,
      "otherPlayerEntryEnabled": false
    }
  },

  "biomeOptions": {
    "planicies": {
      "displayName": "Planícies",
      "aliases": ["planicie", "plains", "planice"],
      "acceptedBiomeIds": [
        "minecraft:plains",
        "minecraft:sunflower_plains",
        "minecraft:meadow"
      ]
    },
    "floresta": {
      "displayName": "Floresta",
      "aliases": ["florest", "forest", "floresta"],
      "acceptedBiomeIds": [
        "minecraft:forest",
        "minecraft:flower_forest",
        "minecraft:birch_forest",
        "minecraft:old_growth_birch_forest",
        "minecraft:dark_forest"
      ]
    },
    "taiga": {
      "displayName": "Taiga",
      "acceptedBiomeIds": [
        "minecraft:taiga",
        "minecraft:old_growth_pine_taiga",
        "minecraft:old_growth_spruce_taiga",
        "minecraft:snowy_taiga"
      ]
    },
    "deserto": {
      "displayName": "Deserto",
      "aliases": ["desert", "deserto"],
      "acceptedBiomeIds": [
        "minecraft:desert"
      ]
    },
    "savana": {
      "displayName": "Savana",
      "aliases": ["savanna", "savana"],
      "acceptedBiomeIds": [
        "minecraft:savanna",
        "minecraft:savanna_plateau",
        "minecraft:windswept_savanna"
      ]
    },
    "selva": {
      "displayName": "Selva",
      "aliases": ["jungle", "selva"],
      "acceptedBiomeIds": [
        "minecraft:jungle",
        "minecraft:bamboo_jungle",
        "minecraft:sparse_jungle"
      ]
    }
  },

  "disabledCommands": []
}
```

### Opções de Configuração Detalhadas

| Seção | Campo | Tipo | Padrão | Descrição |
|---|---|---|---|---|
| `defaultPriorities` | `systemRegion` | int | 10000 | Prioridade padrão de regiões de sistema |
| `defaultPriorities` | `adminRegion` | int | 1000 | Prioridade padrão de regiões admin |
| `defaultPriorities` | `playerRegion` | int | 100 | Prioridade padrão de regiões de jogador |
| `permissions` | `operatorFallbackLevel` | int | 2 | Nível de OP para fallback sem LuckPerms |
| `playerRegions` | `maxRegionsPerOwner` | int | 1 | Máximo de terrenos por jogador |
| `playerRegions` | `rejectOverlapWithAdminRegions` | bool | true | Rejeitar sobreposição com regiões admin |
| `playerRegions` | `rejectOverlapWithSystemRegions` | bool | true | Rejeitar sobreposição com regiões de sistema |
| `playerRegions` | `rejectOverlapWithPlayerRegions` | bool | true | Rejeitar sobreposição com outros terrenos |
| `playerLandAllocation` | `enabled` | bool | true | Ativar/desativar alocação automática |
| `playerLandAllocation` | `targetDimension` | string | `minecraft:overworld` | Dimensão alvo para alocação |
| `playerLandAllocation` | `initialClaimSize` | int | 50 | Tamanho inicial do claim (blocos) |
| `playerLandAllocation` | `slotSize` | int | 256 | Tamanho do slot de plot |
| `playerLandAllocation` | `futureMaximumClaimSize` | int | 240 | Tamanho máximo após expansão |
| `playerLandAllocation` | `slotInternalMargin` | int | 8 | Margem interna entre slots |
| `playerLandAllocation.explorationExclusion` | `minX/maxX/minZ/maxZ` | int | ±20000 | Limites da zona de exploração |
| `playerLandAllocation.explorationExclusion` | `safetyBuffer` | int | 1000 | Buffer de segurança da zona |
| `playerLandAllocation.biomeSearch` | `minimumMatchPercentage` | int | 60 | % mínima de matching de bioma |
| `playerLandAllocation.biomeSearch` | `sampleGridSize` | int | 5 | Grid de amostragem (NxN) |
| `playerLandAllocation.biomeSearch` | `maximumCandidateSlots` | int | 100 | Máx. de slots candidatos |
| `playerLandAllocation.biomeSearch` | `maximumSearchRadiusBlocks` | int | 120000 | Raio máx. de busca |
| `playerLandAllocation.scheduler` | `maxActiveRequests` | int | 1 | Máx. de requisições ativas |
| `playerLandAllocation.scheduler` | `maxCandidateEvaluationsPerTick` | int | 256 | Candidatos verificados por tick (hard cap) |
| `playerLandAllocation.scheduler` | `maxBiomeSearchMillisPerTick` | int | 25 | Tempo máx. por tick gasto na busca de bioma |
| `playerLandAllocation.scheduler` | `requestTimeoutSeconds` | int | 180 | Timeout da requisição |
| `playerLandAllocation.scheduler` | `reservationLeaseSeconds` | int | 300 | Lease da reserva do slot |
| `playerLandAllocation.scheduler` | `creationCooldownSeconds` | int | 60 | Cooldown entre criações |
| `playerLandAllocation.scheduler` | `homeTeleportCooldownSeconds` | int | 30 | Cooldown do teleporte home |
| `playerLandAllocation.notifications` | `entryExitEnabled` | bool | true | Notificação ao entrar/sair |
| `playerLandAllocation.notifications` | `otherPlayerEntryEnabled` | bool | false | Notificar entrada de outros players |
| `disabledCommands` | — | string[] | [] | Comandos desabilitados |

---

## 13. Banco de Dados (SQLite)

**Driver:** `org.xerial:sqlite-jdbc:3.46.0.0`
**Arquivo:** `config/bigbangregions/regions.db`

### Schema (4 migrações)

#### `schema_version`
Controle de versão das migrações.

#### `regions`
| Coluna | Tipo | Descrição |
|---|---|---|
| `id` | TEXT PK | Identificador único |
| `name` | TEXT | Nome de exibição |
| `type` | TEXT | ADMIN_REGION / PLAYER_REGION / SYSTEM_REGION |
| `dimension_key` | TEXT | Ex: `minecraft:overworld` |
| `min_x` / `min_y` / `min_z` | INTEGER | Coordenadas mínimas |
| `max_x` / `max_y` / `max_z` | INTEGER | Coordenadas máximas |
| `priority` | INTEGER | Prioridade de resolução |
| `owner_uuid` | TEXT | UUID do proprietário (nullable) |
| `created_by_uuid` | TEXT | UUID do criador |
| `created_at` / `updated_at` | INTEGER | Timestamps Unix |
| `status` | TEXT | ACTIVE / SUSPENDED |

#### `region_members`
| Coluna | Tipo | Descrição |
|---|---|---|
| `region_id` | TEXT FK | Região |
| `uuid` | TEXT | UUID do jogador |
| `role` | TEXT | OWNER / LEADER / MEMBER |
| `added_by_uuid` | TEXT | UUID de quem adicionou |
| `created_at` / `updated_at` | INTEGER | Timestamps |

#### `region_flags`
| Coluna | Tipo | Descrição |
|---|---|---|
| `region_id` | TEXT FK | Região |
| `flag` | TEXT | ID da flag |
| `value` | TEXT | ALLOW / DENY / INHERIT |

#### `region_audit_logs`
| Coluna | Tipo | Descrição |
|---|---|---|
| `id` | INTEGER PK AUTO | ID do log |
| `region_id` | TEXT FK | Região |
| `actor_uuid` | TEXT | UUID do executor |
| `action` | TEXT | CREATE_REGION, DELETE_REGION, SET_FLAG, etc. |
| `before_value` / `after_value` | TEXT | Valores antes/depois |
| `created_at` | INTEGER | Timestamp |
| `metadata_json` | TEXT | Metadados adicionais |

#### `player_region_allocation_requests`
Requisições de alocação de terreno.

#### `plot_slots`
Slots de terreno com coordenadas de grid, estado, reserva e dados de alocação.

#### `player_region_homes`
Posições de home por região de jogador (dimensão, x, y, z, yaw, pitch).

### Ações Auditadas
`CREATE_REGION`, `DELETE_REGION`, `SET_FLAG`, `SET_PLAYER_REGION_FLAG`, `ADD_MEMBER`, `REMOVE_MEMBER`, `PROMOTE_MEMBER`, `DEMOTE_LEADER`, `MEMBER_LEAVE`, `RELOAD`

---

## 14. API Pública

A API pública pode ser acessada via `BigBangRegions.getApi()`:

```java
public interface BigBangRegionsApi {
    Optional<RegionView> getRegionAt(ServerLevel world, BlockPos pos);
    ProtectionResult check(ProtectionContext context);
    boolean canPlayer(ServerPlayer player, BlockPos pos, RegionAction action);
}
```

### RegionView (Record)

```java
public record RegionView(
    String id,
    String name,
    RegionType type,
    String dimensionKey,
    int minX, int minY, int minZ,
    int maxX, int maxY, int maxZ,
    int priority,
    UUID ownerUuid,
    Map<UUID, String> members,
    Map<String, String> flags,
    long createdAt, long updatedAt,
    String status
) {
    static RegionView from(Region region)
}
```

---

## 15. Eventos e Mixins

### Eventos Registrados

| Evento | Handler |
|---|---|
| `PlayerBlockBreakEvents.BEFORE` | Proteção BLOCK_BREAK |
| `UseBlockCallback.EVENT` | Proteção INTERACT/CONTAINER/DOOR/REDSTONE/BLOCK_PLACE |
| `UseEntityCallback.EVENT` | Proteção ENTITY_INTERACT |
| `AttackEntityCallback.EVENT` | Proteção PVP e ENTITY_INTERACT |
| `ServerTickEvents.END_SERVER_TICK` | Tick do scheduler, entry/exit, boundary renderer, limpeza de cache |
| `ServerPlayConnectionEvents.DISCONNECT` | Limpeza de tracking por jogador |
| `ServerLifecycleEvents.SERVER_STOPPING` | Shutdown do audit service e database |

### Mixins

| Mixin | Alvo | Proteção |
|---|---|---|
| `PlayerMixin.onHurt` | `Player.hurt` | PVP (atacante e vítima) |
| `PlayerMixin.onDrop` | `Player.drop` | Item drop |
| `ItemEntityMixin.onPlayerTouch` | `ItemEntity.playerTouch` | Item pickup |
| `BasePressurePlateBlockMixin.onEntityInside` | `BasePressurePlateBlock` | Placas de pressão |

---

## 16. Cache e Performance

### Caches em Memória

- **RegionCache** — Todas as regiões carregadas em memória
- **RegionMembershipCache** — Papéis dos jogadores por região
- **ChunkSpatialIndex** — Índice espacial chunk-based para lookup O(1)

### Otimizações

- Lookup de região usa índice por chunk (não varredura linear)
- Membership checks usam cache instead of DB queries
- Audit logging é assíncrono (thread única)
- Scheduler de alocação limitado por tick (avaliações e preparação)
- Limpeza automática de caches a cada 200 ticks

---

## 17. Limitações Conhecidas

1. **Formato geométrico:** Apenas cubóide 3D disponível (sem polígonos)
2. **Automações:** Proteção de mods como Create, Mekanism requer adaptadores adicionais
3. **Flags planejadas:** 11 flags estão documentadas mas não implementadas
4. **Transferência de terreno:** Transferência de propriedade não implementada
5. **Visualização:** Limites visíveis apenas com partículas (sem WorldEdit-style)
