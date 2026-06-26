# Full Project Independent Audit

## SHA auditado

```
HEAD:     c8ac4abd8ac272a0de6f658f6a52f18081e67869
Base:     f1a70d0a3453226e61f85546af1d383daa438c7d (Fase 2A aprovada)
Branch:   master
Status:   working tree limpo
Tag:      nenhuma
```

## Ambiente

| Item | Valor |
|------|-------|
| Projeto | BigBang Regions |
| Mod ID | bigbangregions |
| Grupo | com.bigbangcraft |
| Autor | pedro.dalben |
| Minecraft | 1.21.1 |
| Loader | Fabric 0.19.3 |
| Fabric Loom | 1.17.12 |
| Fabric API | 0.116.12+1.21.1 |
| Java | OpenJDK 21 |
| Gradle | 9.5.1 |
| SO | Linux |
| Dependências sombreadas | sqlite-jdbc 3.46.0.0, fabric-permissions-api 0.3.1 |

## Histórico de fases

| Fase | Commits | SHA inicial | Status |
|------|---------|-------------|--------|
| Fase 1 — Fundação | d9d5bde → 50b755c (5 commits) | d9d5bde | ✅ Aprovado |
| Fase 1 — Correções | d1cf927 + 05b618a (2 commits) | d1cf927 | ✅ Aprovado |
| Fase 2A — Ownership | a823113 → f1a70d0 (3 commits) | a823113 | ✅ Aprovado |
| Fase 2B — Alocação | 6028b97 → c8ac4ab (4 commits) | 6028b97 | ❌ BLOCKED (Phase 2B review) |

## Inventário de módulos

### Mapa de pacotes real vs. documentado

| Pacote | Existe? | Arquivos | Função |
|--------|---------|----------|--------|
| `api` | ✅ | 2 | `BigBangRegionsApi`, `BigBangRegionsApiImpl` |
| `audit` | ✅ | 1 | `AuditService` |
| `cache` | ✅ | 3 | `RegionCache`, `ChunkSpatialIndex`, `RegionMembershipCache` |
| `command` | ✅ | 1 | `RegionsCommand` (1213 linhas) |
| `config` | ✅ | 2 | `Config`, `ConfigManager` |
| `domain` | ✅ | 5 | `Region`, `RegionBounds`, `RegionMember`, `RegionRole`, `RegionType` |
| `flag` | ✅ | 4 | `FlagRegistry`, `FlagResolver`, `RegionFlag`, `EffectiveRegionPolicy` + `FlagPolicy` |
| `mixin` | ✅ | 3 | `BasePressurePlateBlockMixin`, `ItemEntityMixin`, `PlayerMixin` |
| `permission` | ✅ | 1 | `PermissionManager` |
| `protection` | ✅ | 5 | `ProtectionService`, `ProtectionContext`, `ProtectionResult`, `RegionAccessService`, `BlockInteractionClassifier` |
| `region` | ✅ | 3 | `RegionResolver`, `RegionRoleResolver`, `RegionMembershipService` |
| `repository` | ✅ | 5 | `RegionRepository`, `AuditRepository`, `AllocationRequestRepository`, `PlotSlotRepository`, `PlayerRegionHomeRepository` |
| `storage` | ✅ | 1 | `DatabaseManager` |
| `allocation` | ✅ | 11 | Ver Phase 2B review para detalhes |
| `membership` | ❌ | - | Inexistente (lógica em `region/` e `cache/`) |
| `util` | ✅ | 2 | `MessageHelper`, `SelectionManager` |
| `integration` | ❌ | - | Inexistente |

### Diferenças do mapa documentado

- **`membership/`**: Não existe como pacote separado. A lógica de membership está distribuída entre `region/RegionMembershipService.java`, `cache/RegionMembershipCache.java`, `region/RegionRoleResolver.java` e `protection/RegionAccessService.java`.
- **`integration/`**: Inexistente. Não há suporte de integração com outros mods.

## Build e testes

```
./gradlew clean test build  →  BUILD SUCCESSFUL
80 testes, 0 falhas
JAR: 13.7 MB (bigbang-regions-1.0.0.jar)
```

### Cobertura de testes por área

| Área | Testes | Status |
|------|--------|--------|
| Migrations (V1-V3) | 2 (MigrationTest + PlayerRegionMigrationTest) | ✅ |
| Repositórios | 2 (PlotSlotRepository + PlayerRegionHomeRepository) | ✅ |
| Alocação - Config | 6 (AllocationConfigValidationTest) | ✅ |
| Alocação - Estado | 8 (AllocationRequestStateTest) | ✅ |
| Alocação - Biomas | 3 (BiomeOptionRegistryTest) | ✅ |
| Alocação - Slots | 4 (PlotSlotEligibilityTest) | ✅ |
| Alocação - Geometria | 1 (PlotSlotGeometryTest) | ✅ |
| Alocação - SafeSpawn | 1 (SafeSpawnFinderTest) | ✅ |
| Região - Repositório | ~20 (RegionRepositoryTest) | ✅ |
| Região - Resolver | ~10 (RegionResolverTest) | ✅ |
| Flags | ~10 (FlagResolverTest) | ✅ |
| Proteção | ~10 (ProtectionServiceTest) | ✅ |
| Membros | ~5 (RegionMembershipServiceTest) | ✅ |

### Lacunas de teste identificadas

- **Sem teste para mixins** (BasePressurePlateBlockMixin, ItemEntityMixin, PlayerMixin)
- **Sem teste de integração para `handlePlayerAction`**
- **SafeSpawnFinderTest**: apenas 1 happy path (sem cenário de borda)
- **Sem teste de comando** (RegionsCommand — 1213 linhas sem cobertura unitária)
- **Sem teste de concorrência**
- **Sem teste de restart/crash recovery**

## Arquitetura

### Classificação: **COHERENT** (com gaps)

### Pontos fortes

1. **Separação de responsabilidades**: Cada camada tem uma função clara — mixins delegam para `handlePlayerAction()` → `ProtectionService` → `RegionAccessService` → `FlagResolver` + `RegionRoleResolver`

2. **SQL ausente no hot path de proteção**: Todas as verificações de proteção são in-memory (caches + ConcurrentHashMap)

3. **Caches bem implementados**: `RegionCache`, `RegionMembershipCache`, `ChunkSpatialIndex` usando ConcurrentHashMap

4. **BlockInteractionClassifier**: Única fonte de classificação de interação com precedência correta (container > door > redstone > build > interact)

5. **FlagResolver**: Cadeia de fallback correta (region flag > type default > global default > mod default)

### Gaps de arquitetura

| ID | Severidade | Descrição |
|----|------------|-----------|
| A-01 | HIGH | `RegionMembershipService` modifica `Region.members` (HashMap) sem sincronização. `Region.getMembers()` retorna `Collections.unmodifiableMap(members)` mas o mapa subjacente é `HashMap`. Se dois comandos de membership forem executados concorrentemente, há race condition. |
| A-02 | MEDIUM | `RegionResolver.checkOverlap()` é código morto — nunca chamado. A validação de overlap real está em `RegionsCommand.checkPlayerRegionOverlap()`. |
| A-03 | MEDIUM | `createAdmin()` em `RegionsCommand` não verifica overlap com regiões existentes. Admin regions podem ser criadas sobre player regions sem aviso. |
| A-04 | LOW | `BlockInteractionClassifier` não usa `player.isSecondaryUseActive()` (sneak) para distinguir entre "usar container" e "colocar bloco no container". |
| A-05 | MEDIUM | `BlockInteractionClassifier` classifica coleta de balde (bucket pickup) como `BLOCK_PLACE`, não como `INTERACT`. |
| A-06 | HIGH | Fase 2B: Nenhum orquestrador de alocação instanciado em `BigBangRegions.java`. |

## Regiões, bounds e prioridade

### Bounds

- Inclusivos: `maxX - minX + 1` = largura — **correto**
- Coordenadas negativas: `RegionBounds` usa `int` — suporta naturalmente
- Dimensões: `intersects()` compara `dimension` — **correto**, sem vazamento cross-dimension
- Cuboides: min/max em X, Y, Z — **correto**
- Limites verticais: -64 a 320 (Overworld) — **correto**
- Volume: `long` — previne overflow em `int`

### Prioridade

- Tipo: SYSTEM(3) > ADMIN(2) > PLAYER(1) — **correto**
- Campo priority: maior vence — **correto**
- Desempate: menor volume, depois menor ID (determinístico) — **correto**
- Comparador imutável e stateless — **correto**

### Cache espacial

- `ChunkSpatialIndex`: mapeia chunk → region IDs — **correto**
- `add()` e `remove()` são `synchronized` — **correto**
- `getRegionIdsInChunk()` não é `synchronized` mas usa `ConcurrentHashMap` + `ConcurrentHashMap.newKeySet()` — **efetivamente thread-safe**
- `RegionCache.getRegionsAt()`: filtra por dimensão + chunk, depois bounds.contains() — **correto**, O(1) lookup por chunk

### Sobreposição

- `createPlayerRegion()` usa `checkPlayerRegionOverlap()` com verificação por tipo e configuração — **correto**
- `createAdmin()` **não** verifica overlap — permite que admin regions sejam criadas sobre qualquer região
- `RegionResolver.checkOverlap()` é código morto

## Flags e classificação de interação

### Precedência verificada

O `BlockInteractionClassifier` implementa a precedência correta:

1. Se block entity é `Container` ou `MenuProvider` → `CONTAINER`
2. Se block é `DoorBlock`, `TrapDoorBlock`, `FenceGateBlock` → `DOOR`
3. Se block é `ButtonBlock`, `LeverBlock`, `DiodeBlock` → `REDSTONE`
4. Se held item é `BlockItem` ou `BucketItem` → `BLOCK_PLACE` (posição adjacente)
5. Fallback → `INTERACT`

### FlagResolver

Cadeia de resolução:
1. Flag explícita na região (`Region.getFlagValue(flagId)`)
2. Default por tipo (Admin/Player config)
3. Default global (config)
4. Fallback do mod (`FlagRegistry.get(flagId).getDefaultValue()` = `ALLOW`)

### Problemas identificados

| Flag | Problema |
|------|----------|
| `player-build` | ✅ Correto (BlockBreak BEFORE + UseBlock para BlockItem) |
| `player-interact` | ✅ Correto (fallback do classifier) |
| `container-access` | ✅ Correto (Container/MenuProvider check) |
| `door-use` | ✅ Correto (DoorBlock/TrapDoor/FenceGate) |
| `redstone-use` | ✅ Correto + mixin de pressure plate |
| `entity-interact` | ✅ Correto (UseEntityCallback) |
| `pvp` | ⚠️ Double-check: ambas as posições (vítima e atacante) — pode ser restritivo em bordas |
| `item-pickup` | ⚠️ Hopper bypass: itens podem ser coletados por hoppers |
| `item-drop` | ✅ Correto (PlayerMixin.onDrop) |
| `fluid-flow` | ❌ Planejado mas não implementado |
| `explosion` | ❌ Planejado mas não implementado |
| `fire-spread` | ❌ Planejado mas não implementado |
| `mob-griefing` | ❌ Planejado mas não implementado |
| `crop-trample` | ❌ Planejado mas não implementado |

### INHERIT

- `INHERIT` é tratado corretamente: cada nível da cadeia retorna apenas se policy != INHERIT
- Valores inválidos (ex: "TRUE", "1") são parseados como `INHERIT` — **silencioso**, sem feedback ao usuário

## Mixins

### Inventário

| Mixin | Target | Injection | Ação | Risco |
|-------|--------|-----------|------|-------|
| `PlayerMixin.onHurt` | `Player.hurt()` | HEAD + cancel | Verifica PVP | Risco de falso positivo em bordas (double-check) |
| `PlayerMixin.onDrop` | `Player.drop()` | HEAD + cancel | Verifica item-drop | Retorno de stack ao inventário pode falhar se cheio |
| `ItemEntityMixin.onPlayerTouch` | `ItemEntity.playerTouch()` | HEAD + cancel | Verifica item-pickup | Hopper bypass |
| `BasePressurePlateBlockMixin.onEntityInside` | `BasePressurePlateBlock.entityInside()` | HEAD + cancel | Verifica redstone | Só bloqueia para ServerPlayer |

### Análise de segurança

- **Thread safety**: Todos os mixins são chamados na server thread — seguros.
- **SQL no mixin**: Nenhum — todos delegam para `BigBangRegions.handlePlayerAction()`.
- **Perda de item em `onDrop`**: Se o inventário estiver cheio após `player.getInventory().add(stack)`, o item pode ser perdido. O código tenta `add(stack)`, depois `setCarried(stack)`, mas itens que não couberem no inventário serão dropados no chão (comportamento vanilla do `add()`). Isso pode resultar em duplicação se o jogador tentar dropar um item em região com `item-drop=DENY` — o item não é dropado mas pode aparecer no inventário se houver espaço, ou pode cair no chão se não houver.
- **Cancelamento de `playerTouch`**: O item permanece no chão, acessível para coleta por hopper. Isso é intencional (hoppers não são afetados) mas pode ser surpreendente para jogadores.

### Teste manual de mixins

Por limitação de ambiente (servidor sem jogadores conectados), os testes manuais de mixins não foram executados. Os caminhos de bypass documentados acima são baseados em análise de código.

## Papéis e membership

### Hierarquia

```
OWNER (4) > LEADER (3) > MEMBER (2) > VISITOR (1)
```

### Regras validadas

| Regra | Status |
|-------|--------|
| Owner não pode ser adicionado como member | ✅ Bloqueado |
| Owner não pode sair | ✅ Bloqueado |
| Leader pode adicionar/remover MEMBER | ✅ Permitido |
| Leader não pode adicionar/remover LEADER | ✅ Bloqueado |
| Leader não pode promover/demover | ✅ Bloqueado (só OWNER) |
| Member não gerencia | ✅ Bloqueado |
| Visitor não acessa (role check) | ✅ Bloqueado (DENY_REASON_VISITOR_ROLE) |
| Flag DENY bloqueia owner/leader/member | ✅ Implementado (RegionAccessService linha 52) |
| Bypass administrativo ultrapassa DENY | ✅ Implementado (ProtectionService linha 46) |
| OWNER não duplicado em region_members | ✅ Owner não é salvo em region_members (fica em regions.ownerUuid) |
| Cache carregado no boot | ✅ `membershipCache.loadFromRegion()` loop em `onInitialize()` |
| Cache removido ao deletar região | ⚠️ `regionRepository.delete()` não remove do cache de membership (RegionCache.remove é chamado, mas `membershipCache.loadFromRegion()` não tem reverse cleanup) |
| FK CASCADE ao excluir region | ✅ V003 garante remoção de homes; V001 garante remoção de members |

### Race condition

`RegionMembershipService` modifica `Region.members` (HashMap) em:
- `addMember()` → `region.setMember()` (HashMap.put)
- `removeMember()` → `region.removeMember()` (HashMap.remove)

Enquanto `RegionAccessService` lê (`region.getMembers().get()`) na proteção. Ambos rodam na server thread, então na prática não há concorrência. Mas se houver no futuro processamento assíncrono, esta é uma race condition real.

## Banco SQLite e migrations

### Migrations

| Migration | Conteúdo | Status |
|-----------|----------|--------|
| V001 | Schema inicial: regions, region_members, region_flags, region_audit_logs | ✅ |
| V002 | ALTER TABLE region_members: addedByUuid, createdAt, updatedAt | ✅ |
| V003 | player_region_allocation_requests, plot_slots, player_region_homes | ✅ |

### Verificações no banco real (servidor dedicado)

- **PRAGMA foreign_keys**: Ativado em toda conexão (`DatabaseManager.initialize()`)
- **schema_version**: 3 (V1, V2, V3 aplicados)
- **Integridade**: Testes de migration passam com banco novo e atualizado
- **Dados órfãos**: `player_region_homes` tem FK CASCADE; `plot_slots` e `allocation_requests` não têm FK em region_id (intencional)

### Problemas

| ID | Problema |
|----|----------|
| DB-01 | `player_region_allocation_requests.region_id` sem FK (aceitável — região pode não existir ainda) |
| DB-02 | `plot_slots.region_id` sem FK (aceitável — mesma razão) |
| DB-03 | `idx_plot_slots_grid` redundante com UNIQUE(dimension_key, grid_x, grid_z) |
| DB-04 | Sem transações multi-tabela em operações de alocação (crash risk) |
| DB-05 | `RegionRepository.save()` faz DELETE + INSERT de todos os members e flags a cada save |

## Alocação de terrenos

### Resultado da auditoria independente (Phase 2B review)

**Veredito: BLOCKED**

A Fase 2B possui os building blocks (POJOs, repositórios, serviços de utilidade, schema) mas não possui orquestração:

1. **Nenhum componente instanciado** em BigBangRegions.java
2. **Nenhum comando registrado** — sem `/regiao criar`, `/regiao casa`, etc.
3. **Nenhum orquestrador** que execute a máquina de estados
4. **Nenhum tick scheduler** para processamento rate-limited

Ver `docs/reviews/phase-2b-independent-review.md` para análise completa.

## Comandos e permissões

### Comandos de jogador (Phase 1 + 2A)

| Comando | Permissão | Status |
|---------|-----------|--------|
| `/regiao pos1` | — | ✅ |
| `/regiao pos2` | — | ✅ |
| `/regiao info` | — | ✅ |
| `/regiao membros listar` | — | ✅ |
| `/regiao membros adicionar` | — | ✅ |
| `/regiao membros remover` | — | ✅ |
| `/regiao membros promover` | — | ✅ |
| `/regiao membros rebaixar` | — | ✅ |
| `/regiao sair` | — | ✅ |
| `/regiao flags listar` | — | ✅ |
| `/regiao flags ver` | — | ✅ |
| `/regiao flags definir` | — | ✅ |
| `/regiao biomas` | — | ❌ Não implementado |
| `/regiao criar <bioma>` | `bigbangregions.player.create` | ❌ Não implementado |
| `/regiao criar status` | — | ❌ Não implementado |
| `/regiao criar cancelar` | — | ❌ Não implementado |
| `/regiao casa` | `bigbangregions.player.home` | ❌ Não implementado |

### Comandos administrativos

| Comando | Permissão | Status |
|---------|-----------|--------|
| `/regions create admin <id>` | `bigbangregions.admin.create` | ✅ |
| `/regions create player <id> <owner>` | `bigbangregions.admin.player.create` | ✅ |
| `/regions delete <id>` | `bigbangregions.admin.delete` | ✅ |
| `/regions info` | `bigbangregions.inspect` | ✅ |
| `/regions list` | `bigbangregions.admin.list` | ✅ |
| `/regions flag set/get/flags` | `bigbangregions.admin.flags` | ✅ |
| `/regions player owner/members/addmember/removemember/setrole` | `bigbangregions.admin.player.*` | ✅ |
| `/regions reload` | `bigbangregions.admin.reload` | ✅ |
| `/regions player allocate <player> <bioma>` | `bigbangregions.admin.player.allocate` | ❌ Não implementado |
| `/regions player allocation <player>` | `bigbangregions.admin.player.allocation.inspect` | ❌ Não implementado |
| `/regions player allocation cancel <player>` | `bigbangregions.admin.player.allocation.cancel` | ❌ Não implementado |

### Permissões

O sistema usa `fabric-permissions-api` (LuckPerms) com fallback para nível de operador (config: 2).

Permissões existentes no código:

| Nó | Verificado em |
|----|---------------|
| `bigbangregions.admin.create` | createAdmin, createPlayerRegion |
| `bigbangregions.admin.edit` | createAdmin |
| `bigbangregions.admin.delete` | deleteRegion |
| `bigbangregions.admin.flags` | flagSet, flagGet, flagList |
| `bigbangregions.admin.list` | listRegions |
| `bigbangregions.admin.reload` | reloadConfig |
| `bigbangregions.admin.player.create` | createPlayerRegion |
| `bigbangregions.admin.player.owner` | showOwner |
| `bigbangregions.admin.player.members` | showMembers, addMember, removeMember, setRole |
| `bigbangregions.inspect` | showInfo (comando /regions info) |
| `bigbangregions.bypass` | PermissionManager.hasBypass |
| `bigbangregions.bypass.<flag>` | PermissionManager.hasBypass |

## Cache e performance

### Avaliação de performance

| Operação | Complexidade | Hot path? |
|----------|-------------|-----------|
| `RegionResolver.resolveRegionAt()` | O(k) onde k = regiões no chunk (média ~1-3) | ✅ Sim |
| `RegionCache.getRegionsAt()` | O(1) lookup por chunk + O(k) filter por contains() | ✅ Sim |
| `ChunkSpatialIndex.getRegionIdsInChunk()` | O(1) ConcurrentHashMap.get | ✅ Sim |
| `RegionMembershipCache.getRole()` | O(1) ConcurrentHashMap.get | ✅ Sim |
| `FlagResolver.resolve()` | O(1) Map lookups | ✅ Sim |
| `PermissionManager.hasBypass()` | O(1) permission API call | ✅ Sim |
| `ProtectionService.check()` | O(k) total (todas as anteriores) | ✅ Sim |
| `RegionRepository.save()` | O(n + m) DELETE + INSERT (members + flags) | ❌ Apenas comandos |
| `isSlotEligible()` | O(n) itera todas as regiões | ❌ Apenas alocação |

### Gargalos identificados

1. **`isSlotEligible()` — O(n) linear**: Itera `regionCache.getAll()` para cada candidato. Com 100 candidatos e 1000 regiões, isso são 100k checks. Aceitável para operação única por jogador, mas pode crescer.

2. **`RegionRepository.save()` — full rewrite**: Salva região + todos os membros + todas as flags a cada alteração. Ineficiente para regiões grandes.

3. **`synchronized(dbManager)` — gargalo global**: Todo acesso ao SQLite é serializado. Leituras também são bloqueadas por escritas. Aceitável para SQLite (single-writer), mas escritas longas bloqueiam todas as leituras.

4. **`MessageHelper.cooldownCache` — crescimento ilimitado**: `ConcurrentHashMap` para cooldown de mensagens nunca é limpo. `cleanCache()` existe mas é privado e nunca chamado.

### Caches

| Cache | Estrutura | Thread-safe | Limpeza |
|-------|-----------|-------------|---------|
| `RegionCache` | `Map<String, Region>` + `Map<String, Set<String>>` (ConcurrentHashMap) | ✅ | ✅ remove em delete |
| `ChunkSpatialIndex` | `Map<ChunkKey, Set<String>>` (ConcurrentHashMap) | ✅ (synchronized em add/remove) | ✅ clear() |
| `RegionMembershipCache` | `Map<String, Map<UUID, RegionRole>>` (ConcurrentHashMap) | ✅ | ⚠️ Não é limpo em delete (só em update) |
| `MessageHelper.cooldowns` | `ConcurrentHashMap` | ✅ | ❌ Crescimento ilimitado |

### Vazamento de cache de membership

`regionRepository.delete()` na `RegionsCommand.deleteRegion()`:
```java
regionRepository.delete(id);
regionCache.remove(id);
```
Mas `membershipCache.loadFromRegion(region)` nunca tem uma chamada de `removeRegion()` no delete. A linha `membershipCache.loadFromRegion(region)` é chamada apenas no boot e no `createPlayerRegion`. Membros de uma região deletada permanecem no cache de membership.

## Compatibilidade

### Matriz de compatibilidade

| Item | Status | Observação |
|------|--------|------------|
| Vanilla Minecraft 1.21.1 | ✅ IMPLEMENTED_AND_TESTED | Testado em servidor dedicado |
| Fabric API 0.116.x | ✅ IMPLEMENTED_AND_TESTED | Dependência obrigatória |
| Servidor dedicado Fabric | ✅ IMPLEMENTED_AND_TESTED | Boot, migrations, config OK |
| Cliente sem mod (server-side) | ✅ IMPLEMENTED_PARTIAL | `environment: server` no fabric.mod.json; sem mixins de cliente |
| LuckPerms / Permissions API | ✅ IMPLEMENTED_AND_TESTED | fabric-permissions-api incluído (shadowed) |
| Modded containers (Create, AE2, etc.) | ⚠️ IMPLEMENTED_PARTIAL | BlockInteractionClassifier usa `Container`/`MenuProvider` genérico — deve funcionar |
| Cobblemon | ❓ NOT_TESTED | Sem integração específica |
| Fake players (Carpet, etc.) | ⚠️ IMPLEMENTED_PARTIAL | São `ServerPlayer` — serão verificados |
| Hoppers / item collection | ⚠️ BYPASS CONFIRMADO | Hoppers coletam itens mesmo em região com item-pickup=DENY |
| Explosions | ❌ NOT_SUPPORTED | Sem mixin para explosões |
| Fire spread | ❌ NOT_SUPPORTED | Sem mixin para fogo |
| Fluids | ❌ NOT_SUPPORTED | Sem mixin para fluidos |
| Mob griefing | ❌ NOT_SUPPORTED | Sem mixin para mob griefing |
| Crop trample | ❌ NOT_SUPPORTED | Sem mixin para crop trample |
| Piston movement | ❌ NOT_SUPPORTED | Sem mixin para pistões |
| Projectiles | ❌ NOT_SUPPORTED | Sem mixin para projéteis |

## Teste em servidor dedicado

### Executado via `./gradlew runServer`

**Resultado: MOD INICIALIZOU COM SUCESSO**

```
[01:20:39] BigBangRegions: Initializing BigBang Regions...
[01:20:44] BigBangRegions-Config: Configuration file not found. Creating default at: ./config/bigbangregions/config.json
[01:20:44] BigBangRegions-DB: Connecting to SQLite database: jdbc:sqlite:./config/bigbangregions/regions.db
[01:20:44] BigBangRegions-DB: Current schema version: 0
[01:20:44] BigBangRegions-DB: Applying migration V1... V2... V3... applied successfully.
[01:20:44] BigBangRegions: Loaded 0 regions into cache.
[01:20:44] BigBangRegions: BigBang Regions initialized successfully.
```

**Verificado:**
- ✅ Config criada com defaults (biome options, allocation config, flags)
- ✅ Banco SQLite criado com todas as migrations
- ✅ 44 mods carregados (fabric-api + bigbangregions + dependências)
- ✅ Mixins aplicados (3 mixins)
- ✅ Sem erros, sem exceptions, sem crashes

**Não verificado (por limitação de ambiente):**
- ❌ Conexão de jogador (sem cliente Minecraft disponível)
- ❌ Comandos em jogo (requer jogador conectado)
- ❌ Proteção em tempo real

## Teste de cliente sem mod

**Resultado: CONFIRMADO SERVER-SIDE-ONLY**

- `fabric.mod.json`: `"environment": "server"` — o mod não carrega no cliente
- Nenhum mixin de cliente presente
- Nenhum registry customizado de block/item/entity que exigiria sincronização
- Nenhum payload de rede customizado
- Nenhum `@ClientOnly` ou referência a classes de renderização

**Risco:** Baixo. Clientes vanilla devem conectar sem problemas.

## Regressões identificadas

| Regressão | Fases | Severidade | Status |
|-----------|-------|------------|--------|
| Nenhuma regressão crítica detectada | 1 ↔ 2A ↔ 2B | — | ✅ |
| `checkOverlap()` código morto | Fase 1 | LOW | Não afeta funcionalidade |
| `membershipCache` não limpo em delete | Fase 2A | MEDIUM | Vazamento de memória menor |

## Achados

### CRITICAL

| ID | Severidade | Fase | Arquivo | Descrição |
|----|------------|------|---------|-----------|
| C-01 | CRITICAL | 2B | BigBangRegions.java | **Nenhum componente de alocação instanciado.** AllocationRequestRepository, PlotSlotRepository, PlayerRegionHomeRepository, PlotSlotService, BiomeSearchService, BiomeOptionRegistry não são criados. |
| C-02 | CRITICAL | 2B | RegionsCommand.java | **Nenhum comando de alocação registrado.** `/regiao criar`, `/regiao biomas`, `/regiao casa` e administrativos não existem. |
| C-03 | CRITICAL | 2B | — | **Nenhum orquestrador de alocação.** A máquina de estados não avança. Nenhum código executa transições. |
| C-04 | CRITICAL | 2B | — | **Nenhum tick scheduler.** `SchedulerConfig` define rate limiting mas não há implementação. |

### HIGH

| ID | Severidade | Fase | Arquivo | Descrição |
|----|------------|------|---------|-----------|
| H-01 | HIGH | 2B | BiomeSearchService.java | Y=64 hardcoded para amostragem de bioma. |
| H-02 | HIGH | 2B | BiomeSearchService.java | Sem rate limiting para amostragem de bioma na server thread. |
| H-03 | HIGH | 2B | PlotSlotService.java | `isSlotEligible()` itera `regionCache.getAll()` O(n) sem usar ChunkSpatialIndex. |
| H-04 | HIGH | 2B | PlotSlot.java | `reserve()`/`allocate()` sem validação de estado anterior. |
| H-05 | HIGH | 2A | RegionMembershipService.java | **Race condition:** modifica `Region.members` (HashMap) sem sincronização. |
| H-06 | HIGH | 2B | BiomeSearchService.java | `level.getBiome()` na server thread sem rate limiting — risco de lag. |
| H-07 | HIGH | 2A | RegionsCommand.java | `deleteRegion()` não limpa `membershipCache`. Membros órfãos permanecem em cache após deleção de região. |
| H-08 | HIGH | 1 | PlayerMixin.java | `onDrop()` restaura item no inventário mas pode falhar se inventário estiver cheio (possível perda de item). |

### MEDIUM

| ID | Severidade | Fase | Arquivo | Descrição |
|----|------------|------|---------|-----------|
| M-01 | MEDIUM | 2B | BiomeOptionRegistry.java | `load()` não thread-safe. |
| M-02 | MEDIUM | 2B | SafeSpawnFinder.java | Detecção de caverna pode encontrar teto em vez de chão. |
| M-03 | MEDIUM | 2B | SafeSpawnFinderTest.java | Apenas 1 teste (happy path). |
| M-04 | MEDIUM | 2B | Config.java | `maximumSearchRadiusBlocks` definido mas nunca usado. |
| M-05 | MEDIUM | 2B | V003 | `idx_plot_slots_grid` redundante com UNIQUE. |
| M-06 | MEDIUM | 2B | — | Sem transações multi-tabela em operações de alocação. |
| M-07 | MEDIUM | 1 | FlagResolver.java | Valores de flag inválidos (ex: "TRUE") são silenciosamente ignorados (parse como INHERIT). |
| M-08 | MEDIUM | 1 | RegionAccessService.java | Membros de ADMIN/SYSTEM_REGION auto-ALLOW para todas as ações não-PVP, ignorando flags. |
| M-09 | MEDIUM | 1 | BlockInteractionClassifier.java | Bucket pickup classificado como BLOCK_PLACE em vez de INTERACT. |
| M-10 | MEDIUM | 1 | RegionsCommand.java | `createAdmin()` não verifica overlap com player regions existentes. |
| M-11 | MEDIUM | 1 | MessageHelper.java | Cache de cooldown nunca é limpo (vazamento de memória). |
| M-12 | MEDIUM | 1 | RegionMembershipService.java | `addMember()` sobrescreve role se membro existir com role diferente. |

### LOW

| ID | Severidade | Fase | Arquivo | Descrição |
|----|------------|------|---------|-----------|
| L-01 | LOW | 1 | RegionResolver.java | `checkOverlap()` é código morto. |
| L-02 | LOW | 1 | RegionsCommand.java | `checkPlayerRegionOverlap()` usa `equalsIgnoreCase` para IDs. |
| L-03 | LOW | 2B | BiomeOptionRegistry.java | Aliases duplicados entre opções não detectados. |
| L-04 | LOW | 1 | FlagResolver.java | Flag IDs inexistentes resolvem para ALLOW sem warning. |
| L-05 | LOW | 1 | ProtectionContext.java | Builder frágil: ordem de `.actor()` e `.player()` muda comportamento. |
| L-06 | LOW | 2B | Config.java | Biome options hardcoded no construtor. |
| L-07 | LOW | 1 | — | Sem suporte a `integration/` package. |
| L-08 | LOW | 1 | RegionsCommand.java | `createAdmin` sem validação de overlap. |

## Riscos futuros

1. **Race condition em RegionMembershipService**: Se houver futuro processamento assíncrono (ex: alocação de terrenos em background thread), a modificação do `HashMap` em `Region.members` causará `ConcurrentModificationException` ou perda de dados.

2. **Transações multi-tabela**: Operações de alocação (criar request + reservar slot + criar região + inserir home) não são atômicas. Um crash entre etapas deixa dados inconsistentes.

3. **Crescimento de cache**: `MessageHelper.cooldowns` e `RegionMembershipCache` após deleções podem crescer indefinidamente.

4. **Bucket pickup vs placement**: A classificação incorreta de bucket pickup como `BLOCK_PLACE` pode causar frustração em jogadores que tentam coletar água/lava em suas bases.

5. **Sneak-click em containers**: Jogadores não conseguem colocar blocos em cima de containers (ex: tocha em cima de baú) em regiões com `container-access=DENY`.

6. **Hoppers bypassam item-pickup**: Itens dropados em regiões protegidas podem ser coletados por hoppers, criando vetor de grief indireto.

7. **Sem proteção contra explosões/fogo/fluidos**: Explosões de creepers, fogo e fluidos podem danificar construções em player regions.

## Limitações confirmadas

1. **Fase 2B não implementada** (BLOCKED): Building blocks existem mas orquestração não foi escrita.
2. **Sem suporte a integração com mods**: `integration/` package inexistente.
3. **Sem proteção contra eventos ambientais**: Explosões, fogo, fluidos, mob griefing, crop trample, pistões, projéteis.
4. **Sem teste de mixin**: Nenhum teste unitário para os 3 mixins.
5. **Sem teste de comando**: 1213 linhas de RegionsCommand sem cobertura.
6. **Bucket pickup classificado incorretamente**: Pode impedir coleta de água/lava.
7. **Sneak-click em containers**: Pode impedir colocação de blocos em containers.
8. **Hopper bypass**: Hoppers coletam itens independente da flag item-pickup.
9. **Cache de membership não limpo em delete**: Vazamento de memória lento mas contínuo.
10. **Cooldown de mensagens sem clean**: Vazamento de memória.

## Plano de correção priorizado

### Imediato (Antes de qualquer release)

| Prioridade | ID | Ação |
|------------|----|------|
| P0 | C-01 a C-04 | Implementar orquestração da Fase 2B (AllocationService + scheduler + comandos) |
| P0 | H-05 | Synchronizar acesso a Region.members ou migrar para ConcurrentHashMap |
| P0 | H-08 | Corrigir `onDrop()` para garantir restauração segura do item independente de espaço no inventário |

### Alta

| Prioridade | ID | Ação |
|------------|----|------|
| P1 | H-07 | Adicionar `membershipCache.removeRegion()` em `deleteRegion()` |
| P1 | M-09 | Corrigir classificação de bucket pickup para INTERACT |
| P1 | M-10 | Adicionar validação de overlap em `createAdmin()` |
| P1 | M-11 | Implementar limpeza periódica do cache de cooldown |
| P1 | M-07 | Validar valores de flag no comando e rejeitar inválidos |

### Média

| Prioridade | ID | Ação |
|------------|----|------|
| P2 | H-01 | Usar altura dinâmica para amostragem de bioma |
| P2 | H-03 | Otimizar `isSlotEligible()` com ChunkSpatialIndex |
| P2 | M-06 | Adicionar transações multi-tabela nas operações de alocação |
| P2 | M-08 | Revisar comportamento de membros em ADMIN/SYSTEM_REGION |
| P2 | M-12 | Validar role existente em `addMember()` |
| P2 | A-04 | Adicionar suporte a sneak-click (colocar bloco em container) |

### Baixa

| Prioridade | ID | Ação |
|------------|----|------|
| P3 | L-01 | Remover código morto `checkOverlap()` |
| P3 | M-04 | Usar `maximumSearchRadiusBlocks` da config |
| P3 | M-05 | Remover índice redundante |
| P3 | M-02 | Melhorar detecção de caverna em SafeSpawnFinder |
| P3 | L-05 | Tornar Builder mais robusto |

## Veredito final

```
PROJECT_BLOCKED
```

### Checklist

| Critério | Status |
|----------|--------|
| não há achado CRITICAL | ❌ (4 CRITICAL — Fase 2B não orquestrada) |
| não há achado HIGH | ❌ (8 HIGH — race condition, cache leak, perda de item) |
| todas as migrations funcionam | ✅ |
| banco passa integrity_check | ✅ (verificado via testes + servidor dedicado) |
| proteção central não tem SQL no hot path | ✅ |
| roles e flags estão corretos | ✅ (com ressalvas — M-08, M-12) |
| slot e region não duplicam | ✅ (constraints no banco) |
| teleporte é seguro | ❌ (teleporte não implementado) |
| restart não deixa estado preso | ❌ (não implementado) |
| cliente sem mod conecta | ✅ (server-side-only confirmado) |
| Fase 1, 2A e 2B não têm regressões críticas | ✅ (nenhuma regressão crítica) |
| documentação reflete a implementação real | ⚠️ (docs/reviews existem mas o código da Fase 2B não corresponde ao esperado) |
| limitações são declaradas sem promessas falsas | ⚠️ (config menciona scheduler que não existe) |

### Justificativa

O projeto **BigBang Regions** possui uma base sólida e bem arquitetada para as Fases 1 e 2A:

- Proteção eficiente sem SQL no hot path
- Caches thread-safe (ConcurrentHashMap)
- Classificação de interação com precedência correta
- Hierarquia de papéis completa com validações
- Migrations funcionais e íntegras
- Build e testes passando (80/80)

No entanto, a **Fase 2B está incompleta**: os building blocks existem mas a orquestração (serviço, comandos, scheduler) não foi implementada. Adicionalmente, existem **8 achados HIGH** que precisam de correção, incluindo uma race condition real em `RegionMembershipService`, vazamento de cache, e possível perda de item no mixin de drop.

**Recomendação**: Corrigir os achados P0 e P1 do plano de correção, implementar a orquestração da Fase 2B, e reavaliar.
