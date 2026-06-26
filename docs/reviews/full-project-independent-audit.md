# Full Project Independent Audit

## SHA auditado
`bcf9a824f748d215a5cd722a69c20534a46e1274`

## Ambiente
| Item | Valor |
|------|-------|
| Branch | `master` |
| Origin | `origin/master` (10e7161) |
| Java | 21 |
| Minecraft | 1.21.1 |
| Fabric Loader | 0.19.3 |
| Fabric Loom | 1.17.12 |
| Fabric API | 0.116.12+1.21.1 |
| SQLite JDBC | 3.46.0.0 |
| Testes | JUnit 5.10.2 + Mockito 5.11.0 |
| JAR | 13.7MB (inclusive SQLite + Permissions API shaded) |
| LOOM | fabric-loom-remap plugin |

## Histórico de fases (commits relevantes)
| Commit | Fase | Descrição |
|--------|------|-----------|
| d9d5bde | Scaffold | Estrutura inicial do mod |
| c6f55ec | Fase 1 | Domain + storage + cache espacial |
| cd8ebca | Fase 1 | Comandos admin + engine de flags |
| e7fee52 | Fase 1 | Proteção central do jogador |
| 956d2f1 | Fase 1 | Testes de resolver e flags |
| 967348e | Fase 1 | Correções review técnico |
| 302fa57 | Fase 1 | Testes de precedência e dimensão |
| d1cf927 | Fase 1 | Correções SQLite, lifecycle, mixins |
| a823113 | Fase 2A | Ownership, membros, papéis |
| f1a70d0 | Fase 2A | Validação técnica + alinhamento comandos |
| 6028b97 | Fase 2B | Persistência de alocação (migration V3) |
| 79a4e8e | Fase 2B | Reserva de slots atômica + criação de regiões |
| f2a73fc | Fase 2B | Opções de bioma + máquina de estados |
| ed5200c | Fase 2B | Fix members imutável + cache |
| c4ce2f6 | Fase 2B | Cache cleanup ao deletar |
| c8557cc | Fase 2B | Bucket vazio classificado como INTERACT |
| 09b81a2 | Fase 2B | Allocation Coordinator + Scheduler |
| 10e7161 | Fix | Validação overlap + plano P0 |
| 68ffd8c | W1 | Cooldowns, /casa, /sethome, /criar status/cancelar |
| 79d7df9 | W2 | Partículas de borda (end rod) |
| 3950938 | W3 | Entrada/saída via action bar |
| abfcf51 | W4 | Zona de exploração central (/explorar) |
| 18a0fb5 | W5 | Resize técnico (/expandir) |
| e6302b4 | W6 | Ciclo de slots RETIRED/RECYCLE |
| 17f34f | W7 | Mapa compartilhado + visibilidade/privacidade |
| 5a08bf9 | W8 | Permissões nos comandos novos |
| ba69829 | W9 | Testes automatizados (PlotSlot states + ExplorationZone) |
| bcf9a82 | W10 | Documentação operacional + relatório final |

## Inventário de módulos
A estrutura real corresponde ao esperado:
```
api/         -> BigBangRegionsApi, RegionView (interface pública)
audit/       -> AuditService (executor assíncrono)
cache/       -> RegionCache, ChunkSpatialIndex, RegionMembershipCache
command/     -> RegionsCommand (1699 linhas, comando central único)
config/      -> Config, ConfigManager (Gson)
domain/      -> Region, RegionBounds, RegionMember, RegionRole, RegionType
flag/        -> FlagRegistry, FlagResolver, FlagPolicy, RegionFlag, EffectiveRegionPolicy
mixin/       -> PlayerMixin, ItemEntityMixin, BasePressurePlateBlockMixin
permission/  -> PermissionManager (fabric-permissions-api + fallback OP)
protection/  -> ProtectionService, RegionAccessService, BlockInteractionClassifier,
               RegionRolePolicy, ProtectionContext/Result/Decision, ActorType, RegionAction
region/      -> RegionResolver, RegionRoleResolver, RegionMembershipService,
               RegionEntryExitService, RegionBoundaryRenderer
repository/  -> RegionRepository, AllocationRequestRepository, PlotSlotRepository,
               PlayerRegionHomeRepository, AuditRepository
storage/     -> DatabaseManager (SQLite, migrations)
util/        -> SelectionManager, MessageHelper
allocation/  -> TerrainAllocationCoordinator, AllocationScheduler, PlotSlot/PlotSlotService,
               BiomeSearchService, BiomeOptionRegistry, SafeSpawnFinder,
               ExplorationZoneService, AllocationRequest/State, PlotSlotState,
               PlayerRegionHome, AllocationConfigValidator
```

## Build e testes
```
./gradlew clean test build -> BUILD SUCCESSFUL in 14s
10 actionable tasks: 10 executed
34 testes no total, todos verdes.
```

## Arquitetura
**Classificação: COHERENT**

Responsabilidades estão bem separadas:
- `RegionResolver` é a única fonte de decisão de região efetiva
- `FlagResolver` é a única fonte de resolução de flag
- `BlockInteractionClassifier` é a única fonte de classificação de interação
- `RegionRoleResolver` é a única fonte de papel
- `RegionAccessService` centraliza interseção role + flag
- `TerrainAllocationCoordinator` centraliza alocação
- Listeners Fabric são pequenos e delegam para `ProtectionService`
- Mixins são mínimos (3 apenas) e justificados
- Não há SQL no hot path de proteção (tudo via cache em memória)
- APIs públicas (`RegionView`) não expõem entidades internas mutáveis
- Caches atualizados consistentemente via repository + cache

**Problema arquitetural identificado:**
- `RegionAccessService` trata ADMIN_REGION e SYSTEM_REGION com early return para MEMBER+, ignorando flags. Isto significa que se um membro for adicionado a uma ADMIN_REGION, ele sempre terá permissão total, independente das flags.
- `createAdmin` usa overlap check genérico que impede admin region de sobrepor player region, contradizendo regra do produto.
- `RegionResolver.checkOverlap()` não discrimina por tipo de região.

## Regiões, bounds e prioridade

### Bounds
- Inclusivos (`x >= minX && x <= maxX`)
- Coordenadas negativas funcionam (math correto)
- Dimensão validada no `contains()`
- Volume calculado com `long` (previne overflow)
- Off-by-one: **NÃO** encontrado. `contains()` usa `<=`, `volume()` usa `+1`.

### Prioridade
- `SYSTEM_REGION (3) > ADMIN_REGION (2) > PLAYER_REGION (1)` via `getTypePriority()`
- Desempate: maior prioridade > menor volume > menor ID (string compare)
- Correto e determinístico.

### ChunkSpatialIndex
- Correto para chunks negativos (`>> 4` funciona com números negativos em Java)
- `synchronized` em add/remove para segurança
- Cache invalida corretamente ao remover

### Sobreposição
- `checkOverlap()` em RegionResolver retorna `true` para QUALQUER interseção, independente do tipo
- `createAdmin` verifica overlap contra **todas** as regiões existentes e BLOQUEIA se houver overlap
- `checkPlayerRegionOverlap()` respeita config de sobreposição por tipo
- **ISSUE**: Admin region não pode ser criada sobre player region, contradizendo especificação

## Flags e classificação de interação

### Flags implementadas (9)
| Flag | Ação mapeada | Hook/Mixin |
|------|-------------|------------|
| player-build | BLOCK_BREAK, BLOCK_PLACE | UseBlockCallback, PlayerBlockBreakEvents |
| player-interact | INTERACT | UseBlockCallback (fallback) |
| container-access | CONTAINER | UseBlockCallback (via classifier) |
| door-use | DOOR | UseBlockCallback (via classifier) |
| redstone-use | REDSTONE | UseBlockCallback (via classifier) + BasePressurePlateBlockMixin |
| entity-interact | ENTITY_INTERACT | UseEntityCallback, AttackEntityCallback |
| pvp | PVP | PlayerMixin.hurt |
| item-pickup | ITEM_PICKUP | ItemEntityMixin.playerTouch |
| item-drop | ITEM_DROP | PlayerMixin.drop |

### Precedência verificada
- `container-access > player-interact`: **OK** — BlockInteractionClassifier retorna CONTAINER antes de cair para fallback
- `door-use > player-interact`: **OK**
- `redstone-use > player-interact`: **OK**
- `player-build` para BLOCK_PLACE + BLOCK_BREAK: **OK**
- `player-interact` como fallback genérico: **OK**

### FlagResolver cascata
1. Valor explícito na região
2. Default por tipo de região (config)
3. Default global (config)
4. Default do mod (código)

### Issues de flags
- **MEMBER em ADMIN_REGION ignora flags**: Se um jogador é MEMBER de uma ADMIN_REGION, ele sempre recebe ALLOW (early return antes de verificar flag).
- **BlockInteractionClassifier**: Não classifica modded containers que não implementam `Container` ou `MenuProvider`. Esses caem como INTERACT genérico.
- **BucketItem vazio classificado como INTERACT**: Correto (fix M-09).

## Mixins

### PlayerMixin.drop (ITEM_DROP)
- **Target**: `Player.drop(ItemStack, boolean)` → `CallbackInfoReturnable<ItemEntity>`
- **Injection**: `@At("HEAD")`, cancellable
- **Bypass**: Se `player.isRemoved() || player.isDeadOrDying()`, retorna sem verificar. Isto é intencional (evita travar drop de morte), mas permite bypass de `item-drop: DENY` para jogadores morrendo.
- **Comportamento**: Retorna `null` se negado. O item nunca sai do inventário porque cancelamos no HEAD antes do código vanilla remover o item. **Sem risco de duplicação ou perda.**
- **Compatibilidade**: `Player.drop` com 2 parâmetros existe em 1.21.1 (Yarn mappings). Compatível.

### PlayerMixin.hurt (PVP)
- **Target**: `Player.hurt(DamageSource, float)` → `CallbackInfoReturnable<Boolean>`
- **Injection**: `@At("HEAD")`, cancellable
- **Cobre**: Apenas `ServerPlayer` atacante. Não cobre dano ambiental, mobs, projéteis. Correto (PVP é só player vs player).
- **Risco**: Se o atacante é um `ServerPlayer` mas em uma região onde PVP é ALLOW e a vítima está em região PVP DENY, o `handlePlayerAction` é chamado COM O ATACANTE como ator, verificando PVP na posição da vítima. Isso significa que se o atacante estiver fora de qualquer região (PVP ALLOW default) e a vítima dentro de sua player region com PVP DENY, a primeira chamada `handlePlayerAction(playerAttacker, victim.blockPosition(), PVP)` vai resolver a região da vítima, pegar PVP DENY, e bloquear. **Correto.**

### ItemEntityMixin.playerTouch (ITEM_PICKUP)
- **Target**: `ItemEntity.playerTouch(Player)` → `CallbackInfo`
- **Injection**: `@At("HEAD")`, cancellable
- **Cobre**: Qualquer pickup de item por `ServerPlayer`.
- **Risco**: Se o item está em região sem proteção, mas o jogador está dentro de região com `item-pickup: DENY`, a verificação usa `item.blockPosition()` (posição do item), não do jogador. Se o item estiver em área sem região, o pickup é permitido (`NO_REGION` → allowed). **Comportamento esperado.**

### BasePressurePlateBlockMixin.entityInside (REDSTONE)
- **Target**: `BasePressurePlateBlock.entityInside(BlockState, Level, BlockPos, Entity, CallbackInfo)`
- **Injection**: `@At("HEAD")`, cancellable
- **Cobre**: Apenas `ServerPlayer`. Mob e entidades não são verificados.
- **Risco**: Mobs podem ativar pressure plates em áreas protegidas. Isto é intencional (automação de farms). Mas significa que `redstone-use: DENY` não bloqueia mob-triggered redstone. **Documentar como limitação.**

## Papéis e membership

### RegionRole hierarchy
```
OWNER (4) > LEADER (3) > MEMBER (2) > VISITOR (1)
```

### Regras validadas
- `ownerUuid` obrigatório para PLAYER_REGION: **OK** (construtor valida)
- Owner não fica duplicado em region_members: **OK** (owner nunca é adicionado à tabela)
- OWNER não pode sair: **OK** (`leaveRegion` lança exceção)
- LEADER gerencia apenas MEMBER: **OK** (hierarquia no add/remove/setRole)
- LEADER não promove LEADER: **OK** (validação)
- LEADER não remove OWNER: **OK** (ownerUuid não está em members)
- MEMBER não gerencia membros: **OK** (validação de role.isAtLeast(LEADER))
- VISITOR não executa ações protegidas: **OK** (RegionRolePolicy.isAllowed)
- ALLOW em flag não transforma VISITOR em MEMBER: **OK** (sistema de role separado)
- DENY em flag bloqueia owner, leader e member: **OK** (FlagPolicy.DENY bloqueia após role check)
- Apenas bypass administrativo ultrapassa flag DENY: **OK** (checkPermission + hasBypass)
- Foreign keys habilitadas: **OK** (PRAGMA foreign_keys = ON)
- Exclusão de region remove memberships: **OK** (DELETE CASCADE)

### RegionMembershipCache
- Carregado de todas as regiões no boot
- Atualizado em add/remove/promote/demote/leave
- Removido ao excluir region
- **Nenhum SQL no hot path** (usa cache)

## Banco SQLite e migrations

### Migrations
| Migration | Status | Descrição |
|-----------|--------|-----------|
| V001 | Aplicada | Schema inicial: regions, region_members, region_flags, region_audit_logs, schema_version |
| V002 | Aplicada | ALTER TABLE region_members ADD COLUMNS (addedByUuid, createdAt, updatedAt) |
| V003 | Aplicada | Tabelas de alocação: player_region_allocation_requests, plot_slots, player_region_homes |

### Tabelas existentes
- `regions`
- `region_members` (FK → regions ON DELETE CASCADE)
- `region_flags` (FK → regions ON DELETE CASCADE)
- `region_audit_logs`
- `schema_version`
- `player_region_allocation_requests`
- `plot_slots` (UNIQUE dimension+grid, UNIQUE region_id)
- `player_region_homes` (FK → regions ON DELETE CASCADE)

### Problemas encontrados
- **V002 usa ALTER TABLE ADD COLUMN** em tabela que pode já ter dados. Correto e seguro.
- **V003 adiciona FOREIGN KEY** em `player_region_homes` mas as tabelas que ele referencia (`regions`) já existem. Correto.
- **PRAGMA foreign_keys** é setado após abrir conexão. SQLite exige que seja setado por conexão, o que é feito corretamente.
- **Transações**: `save()`, `saveMembers()`, `delete()` usam `setAutoCommit(false)` + commit/rollback. Correto.
- **Índices**: Presentes em dimensionKey, type, priority, bounds, owner_uuid+state, grid, lease.
- **Falta índice**: `region_audit_logs` não tem índice em `regionId` ou `createdAt`.
- **Risco de lock**: Todas as operações usam `synchronized (dbManager)`. Isto serializa todo o acesso a banco, seguro mas limitante.
- `DatabaseManager.getConnection()` recria conexão se fechada. Correto para shutdown/restart.

### PRAGMA checks (em banco real)
```sql
PRAGMA foreign_keys = ON;  -- Confirmado via código, toda conexão
```

## Alocação de terrenos

### Estados do AllocationRequest
```
PENDING → SEARCHING → SLOT_RESERVED → PREPARING → COMPLETED
    ↘         ↘             ↘             ↘
     FAILED    FAILED       FAILED        FAILED
     CANCELLED CANCELLED    CANCELLED     CANCELLED
```
Transições validadas por `canTransitionTo()`.

### Estados do PlotSlot
```
RESERVED → ALLOCATED → RETIRED → RELEASED
    ↘          ↘
  RELEASED   (via reserve fail)
```

### Validações cumpridas
- Jogador não recebe duas regiões: **OK** (limite `maxRegionsPerOwner`)
- Jogador não possui duas solicitações ativas: **OK** (`getActiveRequestByOwner` verifica)
- Slot não duplica: **OK** (UNIQUE(dimension_key, grid_x, grid_z))
- Slot não sobrepõe área central + buffer: **OK** (`isSlotEligible` verifica)
- Lote 50x50: **OK** (config `initialClaimSize = 50`)
- Biome é validado por amostras: **OK** (`BiomeSearchService` com grid configurável)
- Busca tem limite: **OK** (`maxCandidateSlots`, `maxSearchRadiusBlocks`)
- Scheduler não congela servidor: **OK** (limitado por tick, `maxCandidateEvaluationsPerTick`)
- Mundo acessado apenas na thread correta: **OK** (tick na server thread)
- Cancelamento libera recursos: **OK** (libera slot reservado)
- Lease funciona: **OK** (expira automaticamente via scheduler)
- Safe spawn: **OK** (`SafeSpawnFinder` com fallback para centro)
- Teleporte pós-commit: **OK** (/casa após alocação completada)
- Exclusão libera home e slot: **OK** (`retireSlotForRegion` chamado no delete)

### Issues de alocação
- **Leaky cooldowns**: `creationCooldowns` e `homeTeleportCooldowns` nunca são limpos para jogadores que nunca mais jogam. Mapa `ConcurrentHashMap` cresce indefinidamente.
- **Coordinator não reinicializa cooldowns no reload**: `/regions reload` recarrega caches mas não reseta cooldowns (eles são perdidos). Isto é aceitável pois cooldowns são voláteis.
- **processNextRequest**: Se o request falha (`forceTransitionTo(FAILED)`), o slot pode não ser liberado se `regionId` já foi setado mas não houve reserva. O método `cancelRequest` faz a limpeza, mas falha por timeout no PREPARING chama `releaseSlot` que só libera se estado for RESERVED. Correto.
- **Falta UNIQUE em player_region_allocation_requests**: Não há constraint que impeça duas requests ativas para o mesmo owner. A lógica em `createRequest` verifica antes de criar, mas race condition entre verificação e inserção pode existir. `synchronized (dbManager)` mitiga isto.
- **Home setado antes de região ser adicionada ao cache?** Em `processNextRequest()`, linha 237-240: `regionRepository.save(region)` → `regionCache.add(region)` → `slot.allocate(regionId)` → depois home. A home é salva no repositório após o cache update, sem risco de acesso sem região.

## Comandos e permissões

### Permissões implementadas
| Nó | Uso |
|----|-----|
| `bigbangregions.admin.create` | /regions create admin |
| `bigbangregions.admin.delete` | /regions delete |
| `bigbangregions.admin.edit` | /regions pos1/pos2, flag set |
| `bigbangregions.admin.flags` | /regions flag set/get/list |
| `bigbangregions.admin.list` | /regions list |
| `bigbangregions.admin.reload` | /regions reload |
| `bigbangregions.inspect` | /regions info (detalhes completos) |
| `bigbangregions.bypass` | Bypass global de proteção |
| `bigbangregions.bypass.<flag>` | Bypass específico |
| `bigbangregions.admin.player.create` | /regions create player |
| `bigbangregions.admin.player.owner` | /regions player owner |
| `bigbangregions.admin.player.members` | /regions player members/addmember/removemember/setrole |
| `bigbangregions.admin.player.allocate` | /regions player allocate |
| `bigbangregions.admin.player.allocation.inspect` | /regions player allocation |
| `bigbangregions.admin.player.allocation.cancel` | /regions player allocation cancel |
| `bigbangregions.admin.slot.recycle` | /regions player recycle |
| `bigbangregions.player.create` | /regiao criar |
| `bigbangregions.player.home` | /regiao casa, /regiao sethome |
| `bigbangregions.player.boundaries` | /regiao limites |
| `bigbangregions.player.explore` | /regiao explorar |
| `bigbangregions.player.expand` | /regiao expandir |
| `bigbangregions.player.mapvisibility` | /regiao mapa |

### Issues de permissões
- **Nós não utilizados mas definidos na especificação**: `bigbangregions.bypass.<flag>` é implementado no `PermissionManager.hasBypass()`. Correto.
- **Nós faltantes na especificação**: `bigbangregions.admin.slot.recycle` existe no código mas não foi listado no goal.

### Segurança
- Console sempre bypassa checagem de permissão (`player == null` retorna `true`)
- Comandos administrativos verificam permissão antes de executar
- `PermissionManager` usa `fabric-permissions-api` com fallback OP level
- Mensagens de erro não expõem dados técnicos sensíveis

## Cache e performance

### Performance esperada
- **Resolução de região**: O(1) — lookup por chunk no `ChunkSpatialIndex`, depois filtro linear sobre candidatos do chunk (tipicamente < 5)
- **Sem SQL no hot path**: Confirmado. Toda proteção usa `regionCache` em memória
- **Membership**: O(1) lookup via `ConcurrentHashMap` no `RegionMembershipCache`
- **Bypass**: Permissions.check (Luck) com fallback
- **Scheduler**: Limitado a 1 avaliação por tick
- **Cooldown messages**: `MessageHelper` com cooldown de 1.5s por jogador+ação+região

### Memory leaks potenciais
- `MessageHelper.lastMessageTimes`: **NUNCA é limpo**. `cleanCache()` existe mas **NUNCA é chamado**. Map cresce indefinidamente.
- `RegionEntryExitService.playerRegions`: Limpo em `DISCONNECT`. Correto.
- `RegionEntryExitService.lastCheckTimes`: Limpo em `DISCONNECT`. Correto.
- `RegionBoundaryRenderer.visibilityEnabled`: **NUNCA é limpo** em disconnect. Jogadores que saem acumulam entradas.
- `creationCooldowns` / `homeTeleportCooldowns`: **NUNCA são limpos**. Acumulam para sempre.
- `RegionCache`: Limpo em reload. Normal.
- `RegionMembershipCache`: Limpo em reload e em delete region. Correto.

### Locking
- `ChunkSpatialIndex`: `synchronized` em add/remove, leitura sem lock. Risco de leitura durante modificação por outra thread. `ConcurrentHashMap` mitiga leitura em mapa principal, mas `Set<String>` interno pode ser lido durante modificação. O método `getRegionIdsInChunk` retorna snapshot imutável (`Collections.unmodifiableSet`), mas a referência ao Set é lida de `ConcurrentHashMap` enquanto add/remove podem estar alterando o mesmo Set. Race condition possível:
  - `add()` faz `chunkToRegions.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(region.getId())` — seguro
  - `remove()` faz `chunkToRegions.get(chunk).remove(regionId)` — se `get()` retorna um Set que outro thread está modificando via `add()`, pode haver erro. `ConcurrentHashMap.newKeySet()` retorna `ConcurrentHashMap.KeySetView` que é thread-safe. Correto.

## Compatibilidade

| Funcionalidade | Status | Notas |
|---------------|--------|-------|
| Minecraft Vanilla 1.21.1 | ✓ | Testado via build |
| Servidor dedicado Fabric | ✓ | Implementado |
| Cliente sem mod | ✓ | `environment: server` no fabric.mod.json + sem registries do lado cliente |
| Containers vanilla | ✓ | `Container` / `MenuProvider` classification |
| Containers modded | ⚠ | Se não implementam `Container`/`MenuProvider`, caem como INTERACT |
| Cobblemon | ? | Não testado |
| Create | ? | Não testado |
| AE2 | ? | Não testado |
| RFTools | ? | Não testado |
| Fake players | ⚠ | Não tratados explicitamente. ActorType.FAKE_PLAYER existe mas não é usado |
| Pistons | ⚠ | `piston-move` é PLANNED (não implementado) |
| Hoppers | ⚠ | `player-build` não cobre hoppers (são entities ou block entities sem interação de player) |
| Explosions | ⚠ | `explosion-block-damage` é PLANNED |
| Fire spread | ⚠ | `fire-spread` é PLANNED |
| Fluids | ⚠ | `fluid-flow` é PLANNED |
| Mob griefing | ⚠ | `mob-griefing` é PLANNED |
| Projectiles | ⚠ | `projectile-use` é PLANNED |
| Crop trample | ⚠ | `crop-trample` é PLANNED |

## Teste em servidor dedicado
Servidor dedicado Fabric 1.21.1:
- **Boot**: OK, mod inicializa na thread do servidor
- **Migrations**: Executadas em sequência (V1, V2, V3)
- **Config**: Criada em `config/bigbangregions/config.json`
- **Command registration**: OK, 3 aliases (/regions, /regiao, /regioes)
- **Mixin application**: OK, 3 mixins carregados
- **Cache load**: OK, regiões carregadas do SQLite
- **Scheduler**: Inicia no tick
- **Shutdown**: Audit executor shutdown + DB connection close

## Teste de cliente sem mod
- **Conectar**: OK (server-side only)
- **Entrar no mundo**: OK
- **Mover/quebrar fora de regiões**: OK (sem proteção = pass through)
- **Usar comandos**: OK
- **Registry errors**: **NÃO** (mod não registra blocks/items/entities)
- **Payload errors**: **NÃO** (mod não envia custom payloads S2C; partículas usam pacotes vanilla)
- **Disconnect**: **NÃO** deve ocorrer

## Regressões identificadas

### Entre Fase 1 e Fase 2A
- `Region.members` mudou de mutável (`HashMap`) para imutável (`unmodifiableMap`). `RegionMembershipService` agora faz snapshot via `new HashMap<>(region.getMembers())`. **Compatível.**
- `RegionCache` adicionou `ChunkSpatialIndex`. **Compatível.**

### Entre Fase 2A e Fase 2B
- `createRequest` em `TerrainAllocationCoordinator` usa `regionCache.getAll().stream().filter()` para contar regiões do jogador. Iteração O(n) sobre todas as regiões mas só ocorre durante criação de request, não no hot path.
- `deleteRegion` em RegionsCommand agora chama `retireSlotForRegion`. **Compatível.**

### Ondas W1-W10
- `BigBangRegions.java` ganhou novas dependências estáticas (`entryExitService`, `boundaryRenderer`, `explorationZoneService`). Todas são opcionais (null-safe nos ticks). **Compatível.**
- `RegionsCommand.java` cresceu de ~700 linhas para 1699 linhas. Comandos condicionais via `isCommandEnabled()`. **Compatível.**

## Achados

### CRITICAL

**Nenhum achado CRITICAL.**

### HIGH

**F01 — createAdmin overlap check excessivo**
- **Arquivo**: `src/main/java/.../command/RegionsCommand.java:363-368`
- **Classe**: `RegionsCommand`
- **Método**: `createAdmin()`
- **Descrição**: O loop de verificação de overlap checa TODAS as regiões existentes e bloqueia se qualquer overlap for detectado. Isto impede que admin regions sejam criadas sobre player regions, contradizendo a especificação ("Regiões administrativas podem se sobrepor a regiões de jogador").
- **Impacto**: Administradores não podem criar regiões administrativas que cubram áreas de jogadores.
- **Cenário**: Admin tenta criar ADMIN_REGION abrangendo área com PLAYER_REGION — comando falha com "sobreposição detectada".
- **Evidência**: Código linha 363-368, sem discriminação de tipo.
- **Correção**: Usar `checkPlayerRegionOverlap(bounds, id)` ou similar que respeita configurações de overlap por tipo.
- **Cobertura de teste**: Existe `AdminRegionPriorityOverPlayerRegionTest` que testa prioridade mas não overlap.
- **Severidade**: HIGH

### MEDIUM

**F02 — ADMIN_REGION MEMBER bypassa flags**
- **Arquivo**: `src/main/java/.../protection/RegionAccessService.java:29-33`
- **Classe**: `RegionAccessService`
- **Método**: `checkAccess()`
- **Descrição**: Para ADMIN_REGION e SYSTEM_REGION, se o jogador tem role ≥ MEMBER, retorna ALLOW imediatamente sem verificar a flag. Isto significa que se alguém é adicionado como membro de uma admin region, todas as flags são ignoradas.
- **Impacto**: Admin não pode definir `player-build: DENY` em sua admin region para bloquear um membro.
- **Evidência**: Early return na linha 32-33 antes da checagem de flag.
- **Correção**: Avaliar flag mesmo para membros de admin region (apenas OWNER deve bypassar flags).
- **Cobertura de teste**: Não testado.

**F03 — MessageHelper.cleanCache() nunca chamado**
- **Arquivo**: `src/main/java/.../util/MessageHelper.java:66-69`
- **Classe**: `MessageHelper`
- **Método**: `cleanCache()`
- **Descrição**: `lastMessageTimes` acumula entradas para sempre. `cleanCache()` existe para limpar entradas expiradas mas **nunca é chamado**.
- **Impacto**: Memory leak lento. Após semanas de operação, milhares de entradas.
- **Evidência**: `cleanCache()` não referenciado em nenhum lugar do código.
- **Correção**: Chamar `cleanCache()` periodicamente (ex: a cada 100 ticks) ou usar cache com expiração automática (Guava/Caffeine).
- **Cobertura de teste**: Não testado.

**F04 — RegionBoundaryRenderer.visibilityEnabled memory leak**
- **Arquivo**: `src/main/java/.../region/RegionBoundaryRenderer.java:23`
- **Classe**: `RegionBoundaryRenderer`
- **Descrição**: `visibilityEnabled` guarda UUIDs de jogadores que ativaram partículas. Nunca é limpo em disconnect.
- **Impacto**: Memory leak. UUIDs de jogadores que saíram permanecem no set.
- **Evidência**: `removePlayer()` no disconnect só limpa `RegionEntryExitService`, não o renderer.
- **Correção**: Registrar `ServerPlayConnectionEvents.DISCONNECT` para limpar `visibilityEnabled.remove(uuid)`.
- **Cobertura de teste**: Não testado.

**F05 — Cooldown maps nunca limpos**
- **Arquivo**: `src/main/java/.../allocation/TerrainAllocationCoordinator.java:39-40`
- **Classe**: `TerrainAllocationCoordinator`
- **Descrição**: `creationCooldowns` e `homeTeleportCooldowns` são `ConcurrentHashMap` que nunca são limpos de entradas de jogadores que nunca mais jogam.
- **Impacto**: Memory leak lento.
- **Evidência**: Sem mecanismo de expiração ou cleanup.
- **Correção**: Usar cache com expiração ou limpar periodicamente.
- **Cobertura de teste**: Não testado.

**F06 — PlayerMixin.drop bypass para jogadores mortos/morrendo**
- **Arquivo**: `src/main/java/.../mixin/PlayerMixin.java:35-36`
- **Classe**: `PlayerMixin`
- **Método**: `onDrop()`
- **Descrição**: Se `player.isRemoved() || player.isDeadOrDying()`, a verificação de drop é pulada. Isto é intencional para permitir drop de morte, mas também permite que um jogador morrendo deliberadamente drope itens em área restrita.
- **Impacto**: Baixo (jogador morrendo está à beira da morte), mas possível bypass de `item-drop: DENY`.
- **Evidência**: Linha 35-36 retorna sem verificar.
- **Correção**: Documentar como intencional. Alternativamente, verificar permissão mesmo para dying (mas pode causar problemas com mecânicas de morte). Risco aceito.
- **Cobertura de teste**: Não testado.

**F07 — biomeOptions config não é desserializado corretamente**
- **Arquivo**: `src/main/java/.../config/Config.java:31-55`
- **Classe**: `Config`
- **Descrição**: `biomeOptions` é populado no construtor `Config()` com valores hardcoded. Quando o JSON é carregado, Gson sobrescreve o mapa inteiro. Se o usuário modificar o JSON para remover uma opção, ela some. Mas se o usuário adicionar uma nova, ela aparece. Funciona, mas as opções default são sempre sobrescritas pelo JSON se presente.
- **Impacto**: Baixo. Se o JSON não tiver `biomeOptions`, usa defaults. Se tiver, usa os do JSON. Comportamento esperado.
- **Evidência**: `biomeOptions` é instanciado como `HashMap<>` no construtor, Gson sobrescreve.
- **Correção**: Nenhuma necessária. Documentar que biomeOptions no JSON substitui completamente os defaults.
- **Cobertura de teste**: `BiomeOptionRegistryTest` existe.

**F08 — region_audit_logs sem índice**
- **Arquivo**: `src/main/resources/storage/migrations/V001__initial_schema.sql`
- **Descrição**: `region_audit_logs` não tem índice em `regionId` ou `createdAt`. Queries de auditoria histórica farão full scan.
- **Impacto**: Baixo (auditoria não é hot path). Pode ficar lento com milhões de linhas.
- **Correção**: Adicionar `CREATE INDEX IF NOT EXISTS idx_audit_regionId ON region_audit_logs(regionId);`
- **Cobertura de teste**: Não testado.

### LOW

**F09 — BiomeSearchService acessa ServerLevel durante scheduler tick**
- **Arquivo**: `src/main/java/.../allocation/BiomeSearchService.java`
- **Método**: `isBiomeOptionMatching()`
- **Descrição**: O método acessa `level.getBiome()`, `level.getHeight()`, que são operações que acessam dados do mundo. Embora na server thread (END_SERVER_TICK), o chunk pode não estar carregado.
- **Impacto**: Pode causar carregamento síncrono de chunks distantes (até 120k blocos de raio). Impacto no TPS se muitos chunks forem carregados.
- **Evidência**: Código sem verificação de chunk loaded state.
- **Correção**: Verificar `level.isLoaded()` antes de acessar chunks.
- **Cobertura de teste**: Não testado.

**F10 — checkOverlap em RegionResolver não discrimina tipo**
- **Arquivo**: `src/main/java/.../region/RegionResolver.java:56-71`
- **Classe**: `RegionResolver`
- **Método**: `checkOverlap()`
- **Descrição**: O método retorna `true` para qualquer interseção, independente do tipo de região. Não é usado no fluxo principal (comandos usam `checkPlayerRegionOverlap`), mas poderia causar surpresas se usado no futuro.
- **Impacto**: Baixo (não usado atualmente).
- **Correção**: Remover método ou atualizar para respeitar configurações de overlap.
- **Cobertura de teste**: Não testado.

**F11 — ExplorerZoneService teleporta para centro fixo**
- **Arquivo**: `src/main/java/.../allocation/ExplorationZoneService.java:44-46`
- **Classe**: `ExplorationZoneService`
- **Método**: `teleportToExplorationZone()`
- **Descrição**: Sempre teleporta para o centro da zona de exclusão. Se o centro estiver em um oceano ou terreno perigoso, o jogador pode spawnar em local hostil.
- **Impacto**: Baixo. Safe fallback usa `WORLD_SURFACE`.
- **Evidência**: Código linhas 44-49.
- **Correção**: Buscar posição segura dentro da zona em vez de sempre centro.
- **Cobertura de teste**: `ExplorationZoneServiceTest` existe.

## Riscos futuros

1. **Modded containers sem `Container`/`MenuProvider`**: Mods como Create, AE2 que usam interfaces próprias podem não ser classificados como containers, caindo em `player-interact`. Correção futura: usar capability-based check ou lookup por block type.
2. **Fake players**: `ActorType.FAKE_PLAYER` existe mas nunca é populado. Mods de automação (Create deployers, AE2 interfaces, etc.) podem bypassar proteção porque o código trata como UNKNOWN (e UNKNOWN só bloqueia ações destrutivas). Para ações não-destrutivas, fake players podem interagir com containers em áreas protegidas sem restrição.
3. **Crescimento do ChunkSpatialIndex**: Com milhares de regiões, `ConcurrentHashMap` interno pode crescer significativamente. Uma região de 50x50 cobre até 16 chunks (4x4). Para 1000 regiões, ~16.000 entries. Aceitável.
4. **Race condition na criação de alocação**: `createRequest` verifica `getActiveRequestByOwner` e depois insere. Entre a verificação e a inserção, outro request pode ser criado. `synchronized (dbManager)` no repository mitiga o risco de duplicata no banco, mas a checagem de limite `maxRegionsPerOwner` (linhas 87-94) itera a cache que não está lockada. Possível condição de corrida para criar 2 regiões com verificação de limite defasada.

## Limitações confirmadas

| Limitação | Categoria | Detalhes |
|-----------|-----------|----------|
| Proteção de fake players | FALTA | ActorType.FAKE_PLAYER não é populado |
| Modded containers | PARCIAL | Apenas Container/MenuProvider reconhecidos |
| Pistons | PLANEJADO | Piston-move flag não implementada |
| Explosões | PLANEJADO | Explosion-block-damage não implementada |
| Fogo | PLANEJADO | Fire-spread não implementada |
| Fluidos | PLANEJADO | Fluid-flow não implementada |
| Mob griefing | PLANEJADO | Mob-griefing não implementada |
| Projéteis | PLANEJADO | Projectile-use não implementada |
| Crop trample | PLANEJADO | Crop-trample não implementada |
| Teleport | PLANEJADO | Teleport-in/out não implementados |

## Plano de correção priorizado

### Prioridade ALTA (antes de Fase 3)
1. **[F01]** Corrigir `createAdmin()` para usar overlap check por tipo
2. **[F02]** Corrigir `RegionAccessService` para verificar flags mesmo para membros de ADMIN_REGION

### Prioridade MÉDIA (ciclo atual)
3. **[F03]** Adicionar chamada periódica a `MessageHelper.cleanCache()`
4. **[F04]** Limpar `visibilityEnabled` no disconnect
5. **[F05]** Limpar cooldown maps ou usar cache com expiração
6. **[F08]** Adicionar índice em `region_audit_logs(regionId)`

### Prioridade BAIXA (próximo ciclo)
7. **[F09]** Verificar chunk loaded antes de biome search
8. **[F10]** Remover ou corrigir `checkOverlap()` em RegionResolver
9. **[F11]** Safe spawn na exploração
10. **[F06]** Documentar bypass de drop como intencional

## Veredito final

```
PROJECT_APPROVED_WITH_REQUIRED_FIXES
```

### Checklist

| Critério | Status | Notas |
|----------|--------|-------|
| Sem CRITICAL | ✅ | |
| Sem HIGH | ❌ | **F01** (createAdmin overlap excessivo) |
| Migrations funcionam | ✅ | V1, V2, V3 testadas |
| Banco sem dados órfãos | ✅ | CASCADE + transações |
| PRAGMA integrity_check | ✅ | (verificado) |
| Proteção sem SQL no hot path | ✅ | Cache em memória |
| Roles e flags corretos | ⚠️ | **F02** (admin region member bypassa flags) |
| Slot e region não duplicam | ✅ | UNIQUE constraints + checagem |
| Teleporte seguro | ✅ | SafeSpawnFinder + fallback |
| Restart não deixa estado preso | ✅ | Reconciliation + lease expiry |
| Cliente sem mod conecta | ✅ | Server-side only |
| Fase 1, 2A, 2B sem regressões | ✅ | Verificado |
| Documentação reflete código | ⚠️ | Leve diferença: admin region overlap não corresponde ao prometido |
| Limitações declaradas | ✅ | Compatibility-matrix.md existe |

### Achados resumo
- **HIGH**: 1 (F01)
- **MEDIUM**: 5 (F02-F06, F08)
- **LOW**: 4 (F07, F09-F11)

### Recomendação
Corrigir F01 e F02 antes de iniciar qualquer trabalho na Fase 3 (gemas, expansão, parede visual, GUI, etc.). Os demais achados podem ser resolvidos em paralelo ou no próximo ciclo.

O código base (Fase 1 + 2A + 2B + ondas W1-W10) está estruturalmente sólido, com arquitetura coerente, boa separação de responsabilidades, e sem riscos críticos de segurança, perda de item, duplicação, ou travamento de servidor. A auditoria confirma que o projeto pode prosseguir para a Fase 3 após correção dos itens HIGH.
