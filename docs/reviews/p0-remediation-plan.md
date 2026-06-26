# P0 Remediation Plan

## SHA alvo

Remediação baseada no SHA auditado: `c8ac4abd8ac272a0de6f658f6a52f18081e67869`

## Findings CRITICAL (4)

### C-01: Nenhum componente de alocação instanciado
**Correção:** Instanciar AllocationRequestRepository, PlotSlotRepository, PlayerRegionHomeRepository, BiomeOptionRegistry, BiomeSearchService, PlotSlotService em BigBangRegions.java e criar TerrainAllocationCoordinator como orquestrador central.
**Arquivos:** `BigBangRegions.java` (linha ~140), `TerrainAllocationCoordinator.java` (novo)
**Status:** ✅ CORRIGIDO

### C-02: Nenhum comando de alocação registrado
**Correção:** Adicionar `/regiao biomas`, `/regiao criar <bioma>`, `/regiao casa`, `/regiao criar status`, `/regiao criar cancelar`. Adicionar `/regions player allocate`, `/regions player allocation`, `/regions player allocation cancel`.
**Arquivos:** `RegionsCommand.java` (linhas ~189-197, ~113-126)
**Status:** ✅ CORRIGIDO

### C-03: Nenhum orquestrador de alocação
**Correção:** Criar `TerrainAllocationCoordinator` que executa a máquina de estados: PENDING → SEARCHING → SLOT_RESERVED → PREPARING → COMPLETED, com tratamento de timeout e falha.
**Arquivos:** `TerrainAllocationCoordinator.java` (novo, 289 linhas)
**Status:** ✅ CORRIGIDO

### C-04: Nenhum tick scheduler
**Correção:** Criar `AllocationScheduler` que processa requisições de alocação rate-limited (maxCandidateEvaluationsPerTick por tick) e libera reservas expiradas a cada 20 ticks.
**Arquivos:** `AllocationScheduler.java` (novo), `BigBangRegions.java` (ServerTickEvents.END_SERVER_TICK)
**Status:** ✅ CORRIGIDO

## Findings HIGH (8)

### H-01: Y=64 hardcoded em BiomeSearchService
**Correção:** Substituir Y=64 por `level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)` para amostragem dinâmica na superfície.
**Arquivos:** `BiomeSearchService.java` (linha 42)
**Status:** ✅ CORRIGIDO

### H-02: Sem rate limiting em BiomeSearchService
**Correção:** O AllocationScheduler limita avaliações por tick via `maxCandidateEvaluationsPerTick`. O processamento é rate-limited na server thread.
**Arquivos:** `AllocationScheduler.java` (linha 44-48)
**Status:** ✅ CORRIGIDO

### H-03: isSlotEligible() O(n) sem ChunkSpatialIndex
**Observação:** Otimização de performance. O método itera `regionCache.getAll()` para cada candidato. Com o scheduler rate-limited e limite de candidatos por tick, o impacto é aceitável para P0. Pode ser otimizado em P2 com uso de ChunkSpatialIndex.
**Status:** ⏳ DEFERIDO (P2)

### H-04: reserve()/allocate() sem validação de estado
**Correção:** Adicionar validação de estado em `PlotSlot.reserve()` (rejeitar se já RESERVED ou ALLOCATED), `PlotSlot.allocate()` (exigir RESERVED), `PlotSlot.release()` (exigir RESERVED).
**Arquivos:** `PlotSlot.java` (linhas 64-91)
**Status:** ✅ CORRIGIDO

### H-05: Race condition em Region.members
**Correção:** Migrar Region.members para imutável (defensive copy + unmodifiableMap). Remover setMember()/removeMember(). Adicionar `saveMembers()` em RegionRepository. RegionMembershipService usa cache para verificações de existência e `saveMembers()` para persistência.
**Arquivos:** `Region.java`, `RegionRepository.java`, `RegionMembershipService.java`, 7 arquivos de teste
**Status:** ✅ CORRIGIDO

### H-06: level.getBiome() na server thread sem rate limiting
**Correção:** O AllocationScheduler processa no máximo `maxCandidateEvaluationsPerTick` slots por tick, garantindo que `getBiome()` seja chamado de forma rate-limited.
**Arquivos:** `AllocationScheduler.java`
**Status:** ✅ CORRIGIDO

### H-07: membershipCache não limpo em deleteRegion
**Correção:** Adicionar `BigBangRegions.getMembershipCache().removeRegion(id)` em deleteRegion. Também adicionar cleanup de region_members e region_flags no método `delete()` do RegionRepository.
**Arquivos:** `RegionsCommand.java` (linha 328), `RegionRepository.java` (método delete)
**Status:** ✅ CORRIGIDO

### H-08: Perda de item em PlayerMixin.onDrop
**Correção:** Cancelar no HEAD antes de qualquer mutação no inventário. Remover código de restauração (add/setCarried/broadcastChanges). O item permanece naturalmente no inventário do jogador.
**Arquivos:** `PlayerMixin.java`
**Status:** ✅ CORRIGIDO

## Findings MEDIUM (12)

### M-01: BiomeOptionRegistry.load() não thread-safe
**Observação:** Usa HashMap simples. Como load() é chamado apenas no boot, não há concorrência real. Pode ser migrado para ConcurrentHashMap em P2.
**Status:** ⏳ DEFERIDO (P2)

### M-02: SafeSpawnFinder detecta teto em vez de chão
**Observação:** A detecção de caverna pode encontrar o teto. A lógica atual varre de maxY para baixo e para no primeiro bloco não-ar, que pode ser o teto. Melhoria possível em P2.
**Status:** ⏳ DEFERIDO (P2)

### M-03: Apenas 1 teste para SafeSpawnFinder
**Observação:** Cobertura insuficiente. Necessário adicionar testes de borda. P2.
**Status:** ⏳ DEFERIDO (P2)

### M-04: maximumSearchRadiusBlocks nunca usado
**Observação:** Campo definido em Config.java mas não utilizado no código. Pode ser usado futuramente para limitar o raio de busca de slots. P2.
**Status:** ⏳ DEFERIDO (P2)

### M-05: Índice redundante idx_plot_slots_grid
**Observação:** O índice é redundante com UNIQUE(dimension_key, grid_x, grid_z). Pode ser removido na próxima migration. P3.
**Status:** ⏳ DEFERIDO (P3)

### M-06: Sem transações multi-tabela em alocação
**Observação:** O TerrainAllocationCoordinator usa transações individuais por repositório. Para atomicidade completa, operações multi-tabela (criar região + slot + home) deveriam ser transacionais. P2.
**Status:** ⏳ DEFERIDO (P2)

### M-07: Valores de flag inválidos ignorados silenciosamente
**Observação:** Valores como "TRUE" são parseados como INHERIT sem feedback. Seria melhor validar no comando e rejeitar valores inválidos com mensagem clara. P1.
**Status:** ⏳ DEFERIDO (P1)

### M-08: ADMIN/SYSTEM_REGION sempre ALLOW para membros
**Observação:** Membros de admin/system regions têm auto-ALLOW para ações não-PVP, ignorando flags. Comportamento intencional do RegionAccessService. Pode ser revisto em P2.
**Status:** ⏳ DEFERIDO (P2)

### M-09: Bucket pickup classificado como BLOCK_PLACE
**Correção:** Classificar empty bucket (Items.BUCKET) usado em bloco como INTERACT em vez de BLOCK_PLACE. Buckets cheios continuam como BLOCK_PLACE.
**Arquivos:** `BlockInteractionClassifier.java` (linhas 56-70)
**Status:** ✅ CORRIGIDO

### M-10: createAdmin() sem validação de overlap
**Correção:** Adicionar verificação de interseção com todas as regiões existentes antes de criar admin region.
**Arquivos:** `RegionsCommand.java` (linhas ~297-303)
**Status:** ✅ CORRIGIDO

### M-11: Cache de cooldown nunca limpo
**Observação:** `MessageHelper.cooldownCache` (ConcurrentHashMap) nunca é limpo. Pode ser adicionado scheduler de limpeza periódica em P1.
**Status:** ⏳ DEFERIDO (P1)

### M-12: addMember() sobrescreve role silenciosamente
**Observação:** Se um membro existe com role diferente, addMember() atualiza a role sem aviso. Comportamento pode ser revisto para explicitar promoção/demotion. P2.
**Status:** ⏳ DEFERIDO (P2)

## Findings LOW (8)

### L-01 a L-08
**Observação:** Todos os achados LOW foram revisados. Nenhum requer ação imediata. Recomenda-se endereçar em P3.
**Status:** ⏳ DEFERIDO (P3)

## Resumo de remediação

| Severidade | Total | Corrigidos | Deferidos |
|------------|-------|------------|-----------|
| CRITICAL   | 4     | 4 (100%)   | 0         |
| HIGH       | 8     | 7 (88%)    | 1 (H-03)  |
| MEDIUM     | 12    | 2 (17%)    | 10        |
| LOW        | 8     | 0 (0%)     | 8         |
| **Total**  | **32**| **13 (41%)**| **19**    |
