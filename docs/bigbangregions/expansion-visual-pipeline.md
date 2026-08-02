# Pipeline visual de expansao

## Auditoria da implementacao anterior

Esta auditoria foi feita antes da refatoracao a partir de `RegionExpansionCoordinator`,
`TerrainAllocationCoordinator`, `RegionTerrainSnapshot` e do ciclo de tick em
`BigBangRegions`.

`END_SERVER_TICK` chama `reconcileExpansionVisuals`. O coordenador consulta as
operacoes pendentes no seu executor serial e usa `MinecraftServer.execute` para
chamar `applyExpansionVisuals` na **Server thread**. Para cada operacao, esse
metodo chamava `TerrainAllocationCoordinator.refreshExpansionBorder`, ainda na
Server thread.

O caminho sincrono era:

```text
reconcileExpansionVisuals (END_SERVER_TICK)
  -> applyExpansionVisuals (Server thread)
    -> refreshExpansionBorder
      -> captureExpansionBorder
        -> NbtIo.readCompressed / NbtIo.writeCompressed / GZIP / Deflater
        -> Files.move
      -> clearLegacyExpansionBorder / generateGlassBorder
```

Logo, uma unica chamada podia ler o mundo (`getHeight`, `getBlockState`), ler o
snapshot anterior, serializar NBT, comprimir, escrever no disco e percorrer toda
a borda antes de devolver o tick. Esse e o caminho observado pelo Spark.

| Area | Situacao anterior | Risco |
| --- | --- | --- |
| Mundo/chunks | `captureExpansionBorder`, `borderStartY`, `isReplaceableBorderBlock`, `clearLegacyExpansionBorder` e `generateGlassBorder` usam `ServerLevel` e blocos na Server thread. | Correto quanto a thread, mas sem orcamento: uma borda inteira monopoliza um tick. |
| NBT e compressao | `captureExpansionBorder` le/grava NBT comprimido no mesmo metodo. | GZIP/Deflater e serializacao bloqueiam a Server thread. |
| I/O | `Files.createDirectories`, `NbtIo.readCompressed`, `NbtIo.writeCompressed` e `Files.move` eram sincronos. | Latencia do disco entra no tick. |
| Limite de blocos | Uma parede tem ate `2 * largura + 2 * profundidade` colunas; cada coluna vai do terreno ate `maxY`, e teto opcional tem `largura * profundidade` blocos. | Para `240x240`, teto tem 57.600 candidatos; paredes verticais podem somar centenas de milhares. |
| Identidade | A operacao duravel tem `operationId` e `regionId`; o arquivo e `terrain-restores/<regionId>.nbt`. | O arquivo nao distinguia uma gravacao em andamento de outra. |
| Versao | O root NBT usa `format=mutation_snapshot_v2`; snapshots legados continuam aceitos na restauracao. | Nao havia revisao do trabalho visual no arquivo. |
| Reconcile | Roda a cada tick. Um `Set` por `operationId` evitava somente concorrencia enquanto o loop sincrono estava ativo. | Solicitar de novo apos o retorno iniciava outra captura/escrita. |
| Concorrencia | Nao havia trabalho visual explicito por regiao nem token de callback. | Um callback tardio poderia concluir trabalho que ja nao representa o estado desejado. |
| Falha | Uma falha de snapshot devolvia `false` e agendava retry; uma falha apos mudancas de blocos nao tinha progresso em memoria. | Recuperacao dependia de um reconcile completo, sem diagnostico da etapa. |
| Shutdown | `RegionExpansionCoordinator.shutdown()` usava `shutdownNow()` no executor de operacoes. | Interromper uma escrita futura seria inseguro; nao havia tratamento de snapshots visuais em curso. |

Nao havia `Future.join()` ou `Future.get()` no caminho visual. Havia locks de
banco no executor de operacoes, mas nenhum lock de espera no tick. O problema e
o trabalho sincrono completo, nao uma espera de future.

## Contrato preservado

Uma expansao so marca `border_applied_at` depois que a borda visual terminou;
somente entao a captura de Gems pode concluir a operacao. O snapshot original
nao pode ser perdido: uma expansao acrescenta somente posicoes ainda ausentes
ao mesmo snapshot atomico. Chunks necessarios nao sao carregados pelo pipeline;
se uma coluna nao esta carregada, o trabalho falha de forma recuperavel e o
reconcile posterior tenta novamente.

## Arquitetura refatorada

```text
Server thread: planejar -> capturar em lotes -> DTO imutavel
                                      |
                                      v
executor I/O limitado: ler/mesclar NBT -> gzip -> temp -> move atomico
                                      |
                                      v (MinecraftServer.execute)
Server thread: remover/aplicar borda em lotes -> checkpoint duravel -> Gems
```

O plano e identificado por `regionId`, `operationId` e `generationId`, contem
dimension, bounds antigo/novo, colunas de captura/aplicacao, remocoes, caminho
final e assinatura deterministica. A captura e a aplicacao ficam exclusivamente
na Server thread; o DTO enviado ao executor contem apenas posicoes, estado
serializavel e NBT copiado de block entity, nunca `ServerLevel`, chunk, block
entity vivo, jogador ou servidor.

O executor de persistencia e limitado e serial (padrao: um worker), portanto
mantem a ordem das gravacoes do mesmo arquivo. A escrita usa temporario no
mesmo diretorio, fecha o stream, tenta `ATOMIC_MOVE`, usa `REPLACE_EXISTING`
somente como fallback e remove temporarios em falha. A geracao ainda e validada
quando o callback retorna para a Server thread; callbacks obsoletos nao alteram
o checkpoint da operacao atual.

O trabalho por regiao percorre `PENDING`, `CAPTURING`, `PERSISTING`,
`APPLYING`, `COMPLETED`, `FAILED` ou `CANCELLED`. Ha no maximo um trabalho
ativo por regiao. Pedidos com a mesma assinatura sao ignorados; outro pedido
invalida a geracao anterior. Persistencia bem-sucedida e pre-requisito para
aplicar a borda.

No shutdown, novos trabalhos sao recusados, capturas/aplicacoes pendentes sao
marcadas canceladas e o executor recebe `shutdown()` (nao `shutdownNow()`) com
espera limitada. O arquivo final atomico permanece valido; operacoes sem
`border_applied_at` sao reconciliadas no proximo startup.

## Operacao e Spark

Use uma expansao grande com o profiler do Spark e confirme que os custos de
`NbtIo`, `GZIPOutputStream`, `Deflater`, `FileChannel` e `Files.move` aparecem
na thread `BigBangRegions-ExpansionSnapshotIO`, nunca em `Server thread`.
Na Server thread devem aparecer somente lotes limitados de leitura/escrita de
bloco. Compare `ticksUsedForCapture`, `ticksUsedForApplication`, os avisos de
orcamento e as metricas `bigbangregions_expansion_visual_*` antes/depois.
