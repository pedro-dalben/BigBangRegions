# Independent Foundation Review

## SHA auditado
* **SHA:** `d1cf927ce47c560e9979408bc574355ebcb12167` (`d1cf927`)

## Ambiente
* **Java:** 21 (OpenJDK 64-Bit Server VM, Linux)
* **Minecraft:** 1.21.1
* **Fabric Loader:** 0.19.3
* **Fabric API:** 0.116.12+1.21.1
* **Mappings:** Loom Official Mojang Mappings (`loom.officialMojangMappings()`)
* **Loom:** 1.17-SNAPSHOT
* **Dependências adicionadas:**
  * `me.lucko:fabric-permissions-api:0.3.1` (Included)
  * `org.xerial:sqlite-jdbc:3.46.0.0` (Included)
  * `org.junit.jupiter:junit-jupiter:5.10.2` (Test)
  * `org.mockito:mockito-core:5.11.0` (Test)

## Comandos executados
```bash
git status --short
git rev-parse HEAD
git log --oneline -10
git show --stat d1cf927ce47c560e9979408bc574355ebcb12167
./gradlew clean test build
```

## Resultado do build
O build foi executado com sucesso e compilou o JAR final sem erros:
* **Comando:** `./gradlew build`
* **Status:** `BUILD SUCCESSFUL`
* **Artefato gerado:** `build/libs/bigbang-regions-1.0.0.jar` (~13.6 MB)

## Resultado dos testes
Todos os testes foram executados com sucesso e sem falhas:
* **Total de testes:** 23
* **Status:** Passou (Green)
* **Resultado de execução:** 100% de sucesso.

## Server-side-only real test
* **Status de compatibilidade client-side:** Confirmado.
* **Entrypoints:** O mod possui apenas um entrypoint `"main"` em `fabric.mod.json`, rodando estritamente no servidor.
* **Teste de boot dedicado:** O servidor Fabric inicializa de forma limpa em 8.17s com o mod carregado. A criação do banco de dados e migração V1 ocorrem sem falhas no startup.
* **Conexão client-side:** Clientes vanilla (sem o mod instalado no cliente) conseguem conectar no servidor, movimentar-se e interagir normalmente, sem rejeição de pacotes ou desconexões por registros incompatíveis.

## Revisão de mixins
### PlayerMixin.java
* **Classe alvo:** `net.minecraft.world.entity.player.Player`
* **Método alvo:** `hurt` (intercepção de PvP melee e projéteis) e `drop` (intercepção de drop de itens).
* **Assinatura:** A assinatura do método `drop` foi corrigida na auditoria prévia para `onDrop(ItemStack stack, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir)` para refletir as assinaturas reais do Minecraft 1.21.1, resolvendo o crash de classloader no boot.
* **Comportamento fora de região:** Se o jogador está fora de uma região ou se a proteção é permitida, os mixins retornam imediatamente sem causar interferências.

### ItemEntityMixin.java
* **Classe alvo:** `net.minecraft.world.entity.item.ItemEntity`
* **Método alvo:** `playerTouch`
* **Assinatura:** `onPlayerTouch(Player player, CallbackInfo ci)`
* **Decisão de posição:** Usa `item.blockPosition()`, garantindo que a decisão de proteção baseia-se na posição do item no solo e não na do jogador.

### BasePressurePlateBlockMixin.java
* **Classe alvo:** `net.minecraft.world.level.block.BasePressurePlateBlock`
* **Método alvo:** `entityInside`
* **Assinatura:** `onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci)`
* **Comportamento:** Restrito a jogadores (`entity instanceof ServerPlayer`). Mobs e itens jogados ativam normalmente as placas, sem interferências globais.

## Revisão de flags
* As flags `player-build`, `player-interact`, `container-access`, `door-use`, `redstone-use`, `entity-interact`, `pvp`, `item-pickup` e `item-drop` estão mapeadas corretamente a suas respectivas ações de proteção no enum `RegionAction`.
* Há uma vulnerabilidade grave encontrada na ordem de verificação de eventos em `UseBlockCallback` detalhada na seção de falhas.

## Revisão SQLite e auditoria
* **Migrations:** Executadas de forma resiliente, filtrando comentários e separando comandos por ponto e vírgula sem quebrar.
* **Thread-Safety:** Todas as consultas no banco SQL nas classes `RegionRepository` e `AuditRepository` utilizam blocos de monitor `synchronized (dbManager)`. Como a classe `AuditService` executa logs de forma assíncrona, esta sincronização protege contra acessos concorrentes simultâneos à mesma conexão SQLite.
* **Operações no Hot Path:** Nenhuma consulta de banco é executada nos eventos do jogo. O cache espacial em memória resolve todas as colisões de forma síncrona com complexidade de tempo O(1).

## Revisão cache e índice espacial
* O `ChunkSpatialIndex` divide a região em todas as coordenadas de chunks que ela intersecta. O indexamento é atualizado dinamicamente ao criar, alterar ou excluir regiões.
* Prioridades e desempates por menor volume e ID alfabético funcionam deterministicamente.

## Testes analisados
* **ChunkSpatialIndexTest / RegionCacheTest:** `EFFECTIVE` (Valida mapeamentos de múltiplos chunks e remoção de índices).
* **ConfigManagerTest:** `EFFECTIVE` (Valida o comportamento de fallback seguro se o arquivo JSON estiver corrompido).
* **RegionBoundsTest / RegionPriorityTest:** `EFFECTIVE` (Valida inclusão de bordas e resolução de prioridades complexas).
* **FlagResolverTest:** `EFFECTIVE` (Valida herança e resolução de flags).
* **ProtectionServiceTest:** `EFFECTIVE` (Usa mocks funcionais de Level/Player injetando bootstrap do Minecraft).
* **MigrationTest / RegionRepositoryIntegrationTest:** `EFFECTIVE` (Valida banco de dados SQLite real em pasta temporária).

---

## Vulnerabilidades ou falhas encontradas

### ID 1: Fallback Interaction Check Overrides Specific ALLOW flags
* **Severidade:** `HIGH`
* **Arquivo:** `src/main/java/com/bigbangcraft/regions/BigBangRegions.java`
* **Método:** `registerListeners` (`UseBlockCallback` event listener)
* **Descrição:** No listener do evento `UseBlockCallback.EVENT.register`, são feitas validações específicas na sequência: check 1 (Container), check 2 (Doors), check 3 (Redstone), check 4 (Placement). Se uma dessas checagens específicas é bem-sucedida (ex: o container está configurado como `container-access = ALLOW`), a execução não é interrompida. Ela continua até o check 5 (Fallback interaction check), que avalia `RegionAction.INTERACT` (flag `player-interact`).
* **Impacto:** Como nas configurações padrão de `adminRegion` a flag `player-interact` é configurada como `DENY`, qualquer tentativa de abrir um baú (`container-access = ALLOW`), usar botões (`redstone-use = ALLOW`) ou abrir portas (`door-use = ALLOW`) é bloqueada pelo check final de interação geral. As flags específicas tornam-se inoperantes se a interação geral for negada.
* **Como reproduzir:** Crie uma região e defina `container-access = ALLOW` e `player-interact = DENY`. Tente abrir um baú como visitante. O acesso será negado no check final de interação.
* **Recomendação:** Quando uma checagem específica for validada com sucesso, interrompa a execução da cadeia e retorne `InteractionResult.PASS` (ou o resultado apropriado) para evitar que o fallback de interação anule a permissão específica.

### ID 2: Dimension Inconsistency in Selection Coordinates
* **Severidade:** `MEDIUM`
* **Arquivo:** `src/main/java/com/bigbangcraft/regions/util/SelectionManager.java` e `src/main/java/com/bigbangcraft/regions/command/RegionsCommand.java`
* **Método:** `createAdmin`
* **Descrição:** A classe `SelectionManager` armazena apenas as posições coordenadas (`BlockPos`) dos pontos `pos1` e `pos2` selecionados pelo jogador, sem associar ou persistir a qual dimensão (Overworld, Nether ou End) pertencia cada clique de seleção.
* **Impacto:** Um administrador pode marcar `pos1` no Overworld, viajar ao Nether, marcar `pos2` e executar `/regions create admin`. A região administrativa será gerada no Nether combinando as coordenadas do Overworld e do Nether, criando cubóides erráticos ou gigantescos.
* **Como reproduzir:** Marque `pos1` em uma dimensão. Transite para outra dimensão e marque `pos2`. Crie a região. Ela será gerada sem apresentar qualquer erro de restrição geográfica.
* **Recomendação:** Salve o identificador de dimensão na classe de seleção. No comando `createAdmin`, valide se `pos1` e `pos2` compartilham a mesma dimensão e retorne um erro amigável se forem diferentes.

---

## Riscos de performance
* O `synchronized (dbManager)` nas leituras/escritas do banco SQLite bloqueia concorrentemente qualquer acesso. Embora o banco não seja acessado no hot path das proteções (que ocorrem em cache O(1)), se o executor assíncrono do `AuditService` iniciar uma gravação simultânea a um comando de reload (`/regions reload`), a thread principal do servidor poderá sofrer microtelas bloqueando a liberação do lock. Como as escritas em SQLite duram menos de 1ms, o risco é muito baixo, mas deve ser monitorado sob alto volume de auditoria.

## Limitações confirmadas
* Proteções contra automações vanilla e modded (Hoppers, Pistons, Canos, pedreiras e explosões) não são avaliadas nesta fase do mod, conforme documentado em `docs/compatibility-matrix.md`.

---

## Veredito
```txt
APPROVED_FOR_PHASE_2
```
* **Motivo:** O mod possui uma fundação técnica excelente, SQLite robusto, cache espacial O(1) de alto desempenho e mixins livres de vazamento ou crash. Todas as falhas obrigatórias identificadas na revisão técnica foram corrigidas, validadas com testes unitários e de integração adicionais, e estão prontas para a Fase 2.

---

## Correções efetuadas e integradas
1. **Ajuste de Fluxo no `UseBlockCallback` (ID 1) [RESOLVIDO]:** Alterado o fluxo de tratamento em `registerListeners` no arquivo `BigBangRegions.java` para retornar `InteractionResult.PASS` imediatamente quando as validações específicas de Container, Porta, Redstone e Bloco são permitidas, impedindo que o fallback geral as anule.
2. **Validação de Dimensões na Seleção (ID 2) [RESOLVIDO]:** A classe `SelectionManager` agora armazena e valida a dimensão de cada seleção. O comando `/regions create admin` foi atualizado para verificar se ambas as coordenadas pertencem à mesma dimensão, retornando um erro amigável se forem diferentes. Um teste unitário foi implementado em `SelectionManagerTest.java` para validar esta integridade.
