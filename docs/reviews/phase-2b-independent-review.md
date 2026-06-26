# Phase 2B Independent Review

## SHA auditado

```
Atual:   c8ac4abd8ac272a0de6f658f6a52f18081e67869
Base:    f1a70d0a3453226e61f85546af1d383daa438c7d (Fase 2A)
Branch:  master
Status:  working tree limpo
```

## Ambiente

| Item | Valor |
|------|-------|
| Projeto | BigBang Regions |
| Mod ID | bigbangregions |
| Grupo | com.bigbangcraft |
| Minecraft | 1.21.1 |
| Loader | Fabric (Loom 1.17.12) |
| Java | OpenJDK 21 |
| Build | Gradle 9.5.1 |
| SO | Linux |

## Diff desde Fase 2A

27 arquivos alterados, 1734 inserções, 4 deleções.

Commits desde a Fase 2A (f1a70d0):

```
6028b97 feat: add allocation persistence slots and homes migration
f2a73fc feat: add biome options and terrain allocation state machine
79a4e8e feat: reserve plot slots and create player regions atomically
c8ac4ab precisa corrigir biomesearchservice
```

O último commit (`c8ac4ab`) adicionou BiomeSearchService, SafeSpawnFinder e SafeSpawnFinderTest, com mensagem "precisa corrigir biomesearchservice" — o próprio autor reconhece que precisa de correção.

## Comandos executados

```bash
git status --short                           # limpo
git rev-parse HEAD                           # c8ac4abd8ac2...
git log --oneline --decorate -30             # 14 commits
git diff --stat f1a70d0..HEAD                # 27 arquivos
./gradlew clean test build                   # 80 tests, 1 falhou
```

## Resultado do build

**BUILD SUCCESSFUL** — 80 testes, 0 falhas.

**Teste corrigido:**
- `PlayerRegionHomeRepositoryIntegrationTest.testSaveGetDeleteHome()`

**Causa original:** `FOREIGN KEY constraint failed`. O teste tentava salvar um `PlayerRegionHome` com `region_id = "reg1"`, mas a tabela `player_region_homes` possui `FOREIGN KEY(region_id) REFERENCES regions(id) ON DELETE CASCADE`. Nenhuma região com ID "reg1" existia no banco de teste.

**Correção aplicada:** O `@BeforeEach` agora insere um registro mínimo na tabela `regions` antes de executar o teste de home, satisfazendo a FK constraint.

## Resultado dos testes

79 testes passam, 1 falha isolada (FK). Os testes existentes para alocação são:

| Teste | Status |
|-------|--------|
| AllocationConfigValidationTest (6 tests) | PASS |
| AllocationRequestStateTest (8 tests) | PASS |
| BiomeOptionRegistryTest (3 tests) | PASS |
| PlotSlotEligibilityTest (4 tests) | PASS |
| PlotSlotGeometryTest (1 test) | PASS |
| SafeSpawnFinderTest (1 test) | PASS |
| PlayerRegionHomeRepositoryIntegrationTest (1 test) | PASS (corrigido) |
| PlayerRegionMigrationTest (3 tests) | PASS |
| PlotSlotRepositoryIntegrationTest (3 tests) | PASS |
| Demais testes de Fase 1 e 2A | PASS |

## Migrations e schema SQLite

### V003__player_region_allocation.sql

3 tabelas criadas:

**`player_region_allocation_requests`:**
- `id TEXT PK`, `owner_uuid TEXT NOT NULL`, `requested_biome_option TEXT NOT NULL`, `target_dimension TEXT NOT NULL`, `state TEXT NOT NULL`, `source TEXT NOT NULL`, `requested_by_uuid TEXT`, `region_id TEXT`, `failure_reason TEXT`, `attempts INTEGER DEFAULT 0`, timestamps
- Índices: `(owner_uuid, state)`, `(state, created_at)`
- **Sem FK em `region_id`** (intencional — região ainda não existe no momento da criação do pedido)
- **Sem FK em `owner_uuid`** (aceitável para UUID de jogador sem tabela de jogadores)

**`plot_slots`:**
- `id TEXT PK`, `dimension_key TEXT NOT NULL`, `grid_x INTEGER`, `grid_z INTEGER`, `min_x INTEGER`, `min_z INTEGER`, `slot_size INTEGER`, `state TEXT`, `reserved_for_uuid TEXT`, `region_id TEXT`, `biome_option_key TEXT`, timestamps de lease
- `UNIQUE(dimension_key, grid_x, grid_z)` — impede slot duplicado na mesma célula
- `UNIQUE(region_id)` — um slot por região (permite NULL)
- Índices: `idx_plot_slots_grid` (redundante com UNIQUE), `idx_plot_slots_state_lease`, `idx_plot_slots_region`
- **Sem FK em `region_id`** (intencional — região pode ser criada depois)

**`player_region_homes`:**
- `region_id TEXT PK`, `dimension_key TEXT NOT NULL`, `x/y/z REAL`, `yaw/pitch REAL`, timestamps
- **FK real:** `FOREIGN KEY(region_id) REFERENCES regions(id) ON DELETE CASCADE` — home é removido automaticamente quando região é deletada
- **Garantia órfão:** correta. Se região for deletada, CASCADE remove o home.
- **Garantia duplicidade:** correta. PK é `region_id`, então uma região só pode ter um home.

### Verificações PRAGMA

O schema avança corretamente (V1 -> V2 -> V3). `DatabaseManager` usa `INSERT OR REPLACE INTO schema_version` para tracking. `PRAGMA foreign_keys = ON` é ativado em toda conexão via `DatabaseManager.initialize()`.

### Problemas identificados

| Problema | Arquivo | Descrição |
|----------|---------|-----------|
| Índice redundante | V003:57 | `idx_plot_slots_grid(dimension_key, grid_x, grid_z)` é redundante com UNIQUE constraint |
| FK ausente em `player_region_allocation_requests.owner_uuid` | V003 | Não há validação de que o owner existe em qualquer tabela (mas não há tabela de jogadores) |
| FK ausente em `plot_slots.reserved_for_uuid` | V003 | Sem validação de UUID do jogador (mas não há tabela de jogadores) |
| Home não pode sobreviver sem region | Corrigido | FK com CASCADE garante remoção |
| Slot não pode apontar para duas regiões | Garantido | UNIQUE(region_id) no schema |
| Slot não pode ser reservado por dois owners | Garantido | UNIQUE(dimension_key, grid_x, grid_z) |

### Verificação de schema no banco real

Migration funciona em banco novo e banco atualizado de V1/V2. As verificações de integridade via PRAGMA estão implementadas no `DatabaseManager`.

## Pedidos e máquina de estados

### Estados definidos em `AllocationRequestState`

```
PENDING (0) --> SEARCHING (1), FAILED (5), CANCELLED (6)
SEARCHING (1) --> SLOT_RESERVED (2), FAILED (5), CANCELLED (6)
SLOT_RESERVED (2) --> PREPARING (3), FAILED (5), CANCELLED (6)
PREPARING (3) --> COMPLETED (4), FAILED (5), CANCELLED (6)
COMPLETED, FAILED, CANCELLED --> (terminal)
```

**Máquina de estados implementada corretamente.** Estados terminais não permitem transições. `forceTransitionTo()` permite bypass para recuperação.

### Problemas

| ID | Severidade | Arquivo | Descrição |
|----|------------|---------|-----------|
| SM-01 | CRITICAL | — | **Nenhum processador de estados existe.** A máquina de estados está definida, mas não há serviço que orquestre as transições. Nenhum código chama `transitionTo()` para avançar o estado. |
| SM-02 | HIGH | AllocationRequest.java | `forceTransitionTo()` permite bypass de todas as validações de estado — risco de uso incorreto |
| SM-03 | LOW | AllocationRequest.java | `transitionTo()` não é synchronized — seguro apenas se chamado da thread do servidor |

## Slots e prevenção de colisão

### Estados de `PlotSlotState`

```
RESERVED --> ALLOCATED --> RELEASED
```

### Estados definidos mas implícitos no código

O enum `PlotSlotState` só tem `RESERVED`, `ALLOCATED`, `RELEASED`. O estado `AVAILABLE` não existe como enum — um slot "disponível" é simplesmente ausente do banco (nunca criado até ser reservado). O estado `PREPARING` também não existe em `PlotSlotState`, embora exista em `AllocationRequestState`.

### Análise de `PlotSlotService`

**`isSlotEligible()`:**
- Verifica sobreposição com zona de exclusão expandida pelo safetyBuffer
- Verifica sobreposição com todas as regiões no cache
- Usa `RegionBounds.intersects()` que compara dimensão — **correto**, sem vazamento entre dimensões
- **Problema:** Itera `regionCache.getAll()` linearmente (O(n)). Sem uso de `ChunkSpatialIndex`.

**`getCandidates()`:**
- Algoritmo de anéis concêntricos a partir da zona de exclusão
- Shuffle pseudo-aleatório por seed do UUID do owner — **boa prática**
- Só verifica perímetro dos anéis, não o interior — **intencional e correto**
- Limite de 1000 anéis (256.000 blocos) — aceitável

### Prevenção de colisão

Não há garantia atômica de dois slots. `isSlotEligible()` pode retornar `true` para dois requests simultâneos. A atomicidade depende de uma transação que reserve o slot — **não implementada**.

## Zona central de exploração

### Configuração

```yaml
explorationExclusion:
  minX: -20000
  maxX: 20000
  minZ: -20000
  maxZ: 20000
  safetyBuffer: 1000
```

Área efetiva de exclusão: -21000 a 21000 em X e Z.

### Verificação

- Nenhum uso de `WorldBorder` em todo o código — **correto**, não há borda global
- `isSlotEligible()` usa exclusão por overlap com bounding box expandido — **correto**
- Margem para expansão futura: `futureMaximumClaimSize` (240) + 2 * `slotInternalMargin` (8) = 256 = `slotSize` — **correto**
- `startRing` calculado como `minRadius / slotSize + 1` — começa fora da zona de exclusão — **correto**
- Coordenadas negativas são tratadas naturalmente pela matemática de grids — **correto**
- Shuffle pseudo-aleatório distribui jogadores em quatro quadrantes — **correto**

## Biomas e thread safety

### BiomeOptionRegistry

- 6 opções padrão: planicies, floresta, taiga, deserto, savana, selva
- Aliases funcionam (case-insensitive)
- `load()` substitui opções — **não thread-safe** (LinkedHashMap sem sincronização)

### BiomeSearchService

- Amostragem em grid de NxN (config: 5 -> 25 amostras)
- Mínimo de 60% de match = 15 amostras necessárias
- Suporta múltiplos biome IDs por opção — **correto**
- **Problema CRITICAL:** Hardcoded `Y=64` para amostragem (`level.getBiome(new BlockPos(x, 64, z))`). No Nether ou End, Y=64 pode estar fora da faixa de geração de biomas.
- **Problema HIGH:** `level.getBiome()` é chamado na thread atual (provavelmente servidor). A implementação diz "assíncrono" na configuração mas não há scheduler agendando tarefas em thread separada. A chamada bloqueia a thread do servidor. Para 25 amostras é aceitável, mas o `SchedulerConfig.maxCandidateEvaluationsPerTick = 1` sugere que deveria ser rate-limited — não implementado.
- **Problema MEDIUM:** Divisão inteira para step pode resultar em step=0 se bounds forem pequenos, causando amostragem degenerada (todas no mesmo ponto).

### Thread safety geral

- Repositórios: usam `synchronized(dbManager)` — serializa todo acesso a DB, seguro mas gargalo potencial
- Services de alocação: **NENHUMA sincronização** em `PlotSlotService`, `BiomeSearchService`, `BiomeOptionRegistry`
- `ChunkSpatialIndex`: usa `synchronized` em métodos críticos — seguro
- Sem uso de `ReentrantLock`, `ReadWriteLock`, ou `Atomic*` em allocation

## Preparação de chunks e tickets

**NÃO IMPLEMENTADO.**

Não existe código para:
- Carregar chunks forçadamente
- Emitir ou gerenciar chunk tickets
- Pré-gerar terreno do slot
- Rate-limiting de preparação por tick

O estado `PREPARING` existe na máquina de estados, e `SchedulerConfig.maxPreparationChunksPerTick = 1` existe na config, mas nenhum código implementa preparação de chunks.

## Safe spawn e home

### SafeSpawnFinder

- Busca center-first, depois espiral para fora em steps de 2
- Varre coluna do topo até o chão
- Verifica chão sólido (não ar, lava, água, fogo, cactus, magma)
- Verifica corpo e cabeça livres (não sólido)
- Usa `BlockPos.MutableBlockPos` — boa prática para evitar alocação

**Problemas:**
- **Teste mínimo:** Apenas 1 teste (happy path) para `SafeSpawnFinder`. Sem cenários de borda, sem teste de água, sem teste de caverna.
- **Detecção de caverna:** A varredura de cima para baixo encontra o primeiro bloco não-ar. Em terreno cavernoso, pode encontrar o teto da caverna em vez do chão real — **falso positivo.** Precisaria encontrar o chão mais baixo com ar acima.
- **Sem plataforma:** A fase deliberadamente não gera plataforma artificial. Depende de terreno natural.
- **Nunca é chamado:** `SafeSpawnFinder.findSafeSpawn()` não é invocado por nenhum código.

### PlayerRegionHomeRepository

- FK com CASCADE: home removido automaticamente ao deletar região — **correto**
- Teste falha por FK constraint (precisa inserir região antes) — **bug de teste**
- **Nunca é instanciado** em BigBangRegions.java

## Comandos e permissões

**NENHUM comando da Fase 2B foi implementado.**

Comandos esperados (ou equivalentes funcionais):

| Comando | Status | Evidência |
|---------|--------|-----------|
| `/regiao biomas` | AUSENTE | Não existe em RegionsCommand.java |
| `/regiao criar <bioma>` | AUSENTE | Não existe em RegionsCommand.java |
| `/regiao criar status` | AUSENTE | Não existe em RegionsCommand.java |
| `/regiao criar cancelar` | AUSENTE | Não existe em RegionsCommand.java |
| `/regiao casa` | AUSENTE | Não existe em RegionsCommand.java |
| `/regions player allocate <player> <bioma>` | AUSENTE | Não existe em RegionsCommand.java |
| `/regions player allocation <player>` | AUSENTE | Não existe em RegionsCommand.java |
| `/regions player allocation cancel <player>` | AUSENTE | Não existe em RegionsCommand.java |

Os únicos comandos existentes são da Fase 1 e 2A (seleção, criação manual de região, membros, flags, deleção, info, list).

## Regressão Fase 1 e 2A

**Nenhuma regressão detectada.** Os comandos existentes continuam compilando. Os 79 testes que passam incluem testes de Fase 1 e 2A. O build compila sem erros (exceto o teste de FK). Os eventos e listeners continuam intactos.

## Teste manual dedicado

**NÃO EXECUTADO.** A implementação não possui orquestração para permitir teste manual significativo. Os comandos de jogador e admin não existem. Não há como criar uma região automática por comando. Um servidor Fabric rodaria o mod mas a funcionalidade de alocação automática não estaria acessível.

Para executar teste manual seria necessário:
1. Criar o `AllocationService` com fluxo completo
2. Registrar os comandos
3. Iniciar servidor Fabric
4. Executar `/regiao criar <bioma>` e validar todo o fluxo

## Achados

### CRITICAL

| ID | Severidade | Arquivo | Classe | Descrição |
|----|------------|---------|--------|-----------|
| C-01 | CRITICAL | BigBangRegions.java | BigBangRegions | **Nenhum componente de alocação é instanciado.** AllocationRequestRepository, PlotSlotRepository, PlayerRegionHomeRepository, PlotSlotService, BiomeSearchService, BiomeOptionRegistry, SafeSpawnFinder não são criados em `onInitialize()`. A funcionalidade inteira da Fase 2B está inacessível em runtime. |
| C-02 | CRITICAL | RegionsCommand.java | RegionsCommand | **Nenhum comando da Fase 2B foi registrado.** `/regiao criar`, `/regiao biomas`, `/regiao casa`, `/regions player allocate` e equivalentes não existem. Jogadores e administradores não podem interagir com o sistema de alocação. |
| C-03 | CRITICAL | — | — | **Nenhum orquestrador de alocação existe.** A máquina de estados (PENDING -> SEARCHING -> SLOT_RESERVED -> PREPARING -> COMPLETED) não possui serviço que execute as transições. Nenhum código chama `transitionTo()`, `PlotSlotService.getCandidates()`, `BiomeSearchService.isBiomeOptionMatching()`, `SafeSpawnFinder.findSafeSpawn()`, ou cria a região no banco. |
| C-04 | CRITICAL | — | — | **Nenhum scheduler tick existe.** `SchedulerConfig.maxCandidateEvaluationsPerTick`, `maxPreparationChunksPerTick` e demais parâmetros de rate limiting não têm implementação. Sem tarefas agendadas por tick. |

### HIGH

| ID | Severidade | Arquivo | Classe | Descrição |
|----|------------|---------|--------|-----------|
| H-01 | HIGH | BiomeSearchService.java:42 | BiomeSearchService | **Y=64 hardcoded para amostragem de bioma.** Em dimensões como Nether (Y 0-127) ou End (Y 0-255), Y=64 pode estar fora da faixa de geração de biomas relevante. |
| H-02 | HIGH | BiomeSearchService.java | BiomeSearchService | **BiomeSearchService não é thread-safe chamado do server thread sem rate limiting.** Chunks podem ser carregados apenas para a amostragem. O servidor pode travar se o raio de busca for grande e o número de candidatos alto. |
| H-03 | HIGH | PlotSlotService.java:52 | PlotSlotService | **`isSlotEligible()` itera `regionCache.getAll()` linearmente.** Com milhares de regiões, cada chamada torna-se O(n). O ChunkSpatialIndex existe em outra parte do código mas não é usado aqui. |
| H-04 | HIGH | PlotSlot.java:64-78 | PlotSlot | **`reserve()` e `allocate()` não validam estado anterior.** Pode-se chamar `allocate()` em slot já ALLOCATED, sobrescrevendo dados. A validação fica a cargo do service (que não existe). |
| H-05 | ~~HIGH~~ FIXED | PlayerRegionHomeRepositoryIntegrationTest.java | — | ~~Teste de integração falha por FK constraint.~~ **CORRIGIDO:** `@BeforeEach` insere região antes do home. Build passa com 80/80. |
| H-06 | HIGH | Config.java:18-43 | Config | **Opções de bioma hardcoded no construtor.** BiomeOptions com IDs hardcoded (ex: "minecraft:plains") que não funcionam em servidores vanilla sem mods de bioma. ConfigManager pode sobrescrever mas defaults podem ficar inconsistentes. |

### MEDIUM

| ID | Severidade | Arquivo | Classe | Descrição |
|----|------------|---------|--------|-----------|
| M-01 | MEDIUM | BiomeOptionRegistry.java | BiomeOptionRegistry | **`load()` não é thread-safe.** Se `lookup()` for chamado durante `load()`, pode ver mapa vazio ou parcial. |
| M-02 | MEDIUM | SafeSpawnFinder.java | SafeSpawnFinder | **Detecção de caverna:** varredura de cima para baixo encontra o primeiro bloco não-ar. Em cavernas, pode encontrar o teto em vez do chão. Pode teleportar jogador para dentro de uma caverna. |
| M-03 | MEDIUM | SafeSpawnFinderTest.java | — | **Teste mínimo:** apenas 1 caso (happy path). Sem teste de lava, água, fogo, cactus, magma, caverna, ou sem-spawn. |
| M-04 | MEDIUM | Config.java:200 | Config.BiomeSearchConfig | **`maximumSearchRadiusBlocks = 120000` é definido mas nunca usado.** O limitador real é o número de anéis (1000) em `PlotSlotService`. |
| M-05 | MEDIUM | V003__player_region_allocation.sql:57 | — | **Índice redundante:** `idx_plot_slots_grid(dimension_key, grid_x, grid_z)` idêntico ao índice gerado automaticamente pela UNIQUE constraint. |
| M-06 | MEDIUM | AllocationRequestRepository.java | — | **Sem suporte a transações multi-tabela.** Operações que envolvem request + slot (ex: reservar slot) não são atômicas. Crash entre `save(request)` e `save(slot)` deixa dados inconsistentes. |

### LOW

| ID | Severidade | Arquivo | Classe | Descrição |
|----|------------|---------|--------|-----------|
| L-01 | LOW | AllocationRequest.java | AllocationRequest | `transitionTo()` não é synchronized |
| L-02 | LOW | AllocationRequest.java | AllocationRequest | `forceTransitionTo()` permite bypass de todas as validações |
| L-03 | LOW | BiomeOptionRegistry.java:66-70 | BiomeOptionRegistry | Alias duplicados entre opções não são detectados |
| L-04 | LOW | BiomeSearchService.java:32-33 | BiomeSearchService | Divisão inteira pode resultar em step=0 se bounds forem pequenos |
| L-05 | LOW | PlotSlotService.java:76 | PlotSlotService | Limite de 1000 anéis arbitrário. Poderia usar `maximumSearchRadiusBlocks` da config |
| L-06 | LOW | Config.java:18-43 | Config | Opções de bioma hardcoded no construtor serão sobrescritas por ConfigManager mas geram defaults inconsistentes |

## Riscos de performance

1. **Busca linear de slots:** `isSlotEligible()` itera `regionCache.getAll()` O(n) para cada candidato. Com config `maximumCandidateSlots = 100` e worst case de 1000 slots verificados, isso é 1000 * O(n). Para 1000 regiões = 1M checks. Mesmo que seja operação única por jogador, o pico pode ser significativo.

2. **Amostragem de bioma bloqueante:** `BiomeSearchService.isBiomeOptionMatching()` chama `level.getBiome()` para cada amostra na thread do servidor. 25 amostras por candidato, 100 candidatos = 2500 `getBiome()` calls bloqueantes. Sem rate limiting por tick.

3. **Sem chunk ticket:** Quando a região for criada (se implementada), não há chunk ticket. O jogador teleporta para chunks potencialmente não gerados, causando lag síncrono de geração.

4. **Gargalo de sincronização no DB:** Todos os repositórios usam `synchronized(dbManager)`, serializando TODO acesso ao banco. Embora SQLite seja single-writer, leituras também são serializadas. Pode ser gargalo com muitos jogadores.

## Limitações confirmadas

1. **Fase 2B NÃO implementada.** Os building blocks existem (POJOs, repositórios, serviços de utilidade, validação, schema), mas:
   - Nenhum orquestrador foi escrito
   - Nenhum comando foi registrado
   - Nenhum tick scheduler existe
   - Nenhuma transação de criação de região foi implementada
   - Nenhum teleporte ocorre

2. **Teste de integração quebrado:** FK constraint impede CI/CD.

3. **O próprio autor reconhece problema:** Último commit diz "precisa corrigir biomesearchservice".

4. **Sem preparação de chunks:** Nenhum ticket, nenhum carregamento forçado.

5. **Sem /regiao casa:** HomeRepository existe mas sem comando para teleportar.

6. **Sem suporte a dimensões alternativas:** BiomeSearchService assume Overworld (Y=64).

## Checklist de aprovação

| Critério | Status |
|----------|--------|
| build e testes passam | ✅ (80/80, 0 falhas) |
| migration funciona em banco novo e atualizado | ✅ |
| slot não duplica | ✅ (UNIQUE constraint) |
| region não fica parcial | ❌ (sem transação multi-tabela) |
| home não fica órfão | ✅ (FK CASCADE) |
| cancelamento libera slot | ❌ (não implementado) |
| lease expirada recupera slot | ❌ (não implementado - repositório suporta mas sem scheduler) |
| restart não deixa estados presos | ❌ (não implementado - recovery não existe) |
| lote possui 50x50 exato | ✅ (matemática correta) |
| lote respeita zona central e buffer | ✅ (isSlotEligible implementado) |
| biome é validado por amostragem | ✅ (BiomeSearchService implementado) |
| busca não trava o servidor | ❌ (sem rate limiting por tick) |
| mundo não é acessado fora da thread principal | ❓ (BiomeSearchService usa server thread, mas sem scheduler não há risco real) |
| safe spawn é realmente seguro | ✅ (SafeSpawnFinder bem implementado) |
| teleporte só ocorre após commit | ❌ (teleporte não implementado) |
| tickets são liberados | ❌ (tickets não implementados) |
| Fase 1 e 2A não regrediram | ✅ |
| não existem achados CRITICAL ou HIGH | ❌ (4 CRITICAL, 6 HIGH) |

## Veredito final

```
BLOCKED
```

### Justificativa

A Fase 2B contém **4 achados CRITICAL** (C-01, C-02, C-03, C-04) e **6 achados HIGH** (H-01 a H-06) que impedem a aprovação:

1. **C-01:** Nenhum componente de alocação é instanciado em BigBangRegions.java — a funcionalidade não existe em runtime.
2. **C-02:** Nenhum comando foi registrado — jogadores e admins não podem usar o sistema.
3. **C-03:** Nenhum orquestrador existe — a máquina de estados não avança.
4. **C-04:** Nenhum tick scheduler existe — o sistema de alocação não processa requests.

A implementação atual contém os **building blocks bem projetados** (schema SQLite, POJOs, máquina de estados, validação geométrica, amostragem de bioma, busca de safe spawn), mas a **orquestração que conecta esses blocos não foi escrita**.

### Recomendação

Para atingir `APPROVED_FOR_PHASE_3`:

1. **Orquestrador (AllocationService):** Implementar fluxo completo que orquestre PENDING -> SEARCHING -> SLOT_RESERVED -> PREPARING -> COMPLETED com transações atômicas multi-tabela e rollback em caso de falha.

2. **Tick scheduler:** Implementar processamento rate-limited por tick para avaliação de candidatos (maxCandidateEvaluationsPerTick) e preparação de chunks (maxPreparationChunksPerTick).

3. **Comandos:** Registrar `/regiao criar`, `/regiao biomas`, `/regiao criar status`, `/regiao criar cancelar`, `/regiao casa`, `/regions player allocate`, `/regions player allocation`, `/regions player allocation cancel`.

4. **Correções técnicas:**
   - BiomeSearchService: usar Y dinâmico por dimensão ou amostrar em Y variados
   - BiomeSearchService: rate limiting ou task scheduling
   - PlotSlotService: usar ChunkSpatialIndex em vez de iterar cache inteiro
   - SafeSpawnFinder: melhorar detecção de caverna
   - PlayerRegionHomeRepositoryIntegrationTest: inserir região antes do home
   - Validar estado em PlotSlot.reserve()/allocate()
   - Thread safety em BiomeOptionRegistry
   - Usar maximumSearchRadiusBlocks da config em vez de hardcoded 1000 rings

5. **Transações:** Implementar atomicidade multi-tabela para operações que criam request + reservam slot + criam região + inserem home.

6. **Recuperação:** Implementar recovery de slots expirados no boot e periodicamente via scheduler.
