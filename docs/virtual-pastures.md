# Integração Virtual Pasture

O BigBangRegions pode limitar o bloco **Virtual Pasture** do VirtualLoot para
evitar que uma região privada acumule block entities que processam a cada tick.
O identificador registrado e validado pela integração é:

```text
virtualloot:virtual_pasture
```

O limite é aplicado antes da colocação. Quando ela é bloqueada, o mundo não é
alterado e o item continua na mão do jogador.

## Pré-requisitos

- Servidor Fabric para Minecraft 1.21.1 com BigBangRegions.
- VirtualLoot instalado no **servidor**. Cobblemon e Cobbleworkers continuam
  sendo responsabilidade da instalação do VirtualLoot.
- Opcionalmente, `fabric-permissions-api` com LuckPerms para permissões por
  grupo. Sem ela, o fallback de OP configurado pelo BigBangRegions é usado.

VirtualLoot não é uma dependência obrigatória: se ele não estiver instalado,
ou se `blockId` não existir no registry, o BigBangRegions mantém o servidor
funcionando e desativa somente esta limitação. Há um aviso no log na primeira
tentativa de uso da integração.

## Configuração

Após iniciar o servidor uma vez, edite
`config/bigbangregions/config.json`. O bloco abaixo representa os valores
padrão:

```json
"virtualPasture": {
  "enabled": true,
  "blockId": "virtualloot:virtual_pasture",
  "maxPerRegion": 2,
  "maxPerPlayer": 2,
  "maxPerChunk": 1,
  "adminBypassPermission": "bigbangregions.virtualpasture.bypass",
  "limits": {
    "default": 2,
    "vip": 3
  }
}
```

| Campo | Efeito |
| --- | --- |
| `enabled` | `false` desativa a integração sem remover os registros existentes. |
| `blockId` | Identifier do bloco a limitar. Mantenha `virtualloot:virtual_pasture` para VirtualLoot 0.3; um valor inválido desativa a proteção em vez de bloquear itens errados. |
| `maxPerRegion` | Máximo somado dentro de uma região. `0` desativa somente este teto. |
| `maxPerPlayer` | Teto de fallback somado em todas as regiões do mesmo proprietário. Ele é usado quando `limits.default` não foi definido. `0` desativa somente esse fallback. |
| `maxPerChunk` | Máximo em cada chunk 16×16. `0` desativa somente este teto. |
| `adminBypassPermission` | Permissão que ignora os três tetos, mas ainda registra o bloco. |
| `limits` | Teto por proprietário para grupos de permissão; `default` é o teto comum e substitui o fallback `maxPerPlayer`. |

`maxPerPlayer` significa **proprietário da região**, e não quem clicou para
colocar. Portanto, membros compartilham a cota do dono e não conseguem burlá-la
ao se revezarem na colocação. A ordem de avaliação é região, proprietário e
chunk; a primeira cota atingida é mostrada ao jogador.

### Perfis sugeridos

| Perfil | `maxPerRegion` | `maxPerPlayer` | `maxPerChunk` | `limits` |
| --- | ---: | ---: | ---: | --- |
| Conservador | 1 | 1 | 1 | `{ "default": 1, "vip": 2 }` |
| Equilibrado (padrão) | 2 | 2 | 1 | `{ "default": 2, "vip": 3 }` |
| Servidor com folga medida | 3 | 3 | 1 | `{ "default": 3, "vip": 4 }` |

Comece pelo perfil conservador ou padrão. Só eleve os limites depois de medir
o tick do servidor com os mesmos mods, quantidade de jogadores e farms reais.
O teto por chunk em `1` evita concentrar a busca recorrente do Cobbleworkers
num único ponto.

## Permissões

| Permissão | Uso |
| --- | --- |
| `bigbangregions.virtualpasture.bypass` | Ignora limites de colocação; este é o valor padrão de `adminBypassPermission`. |
| `bigbangregions.virtualpasture.limit.<tier>` | Aplica o valor de `limits.<tier>` ao proprietário. Ex.: `bigbangregions.virtualpasture.limit.vip`. |
| `bigbangregions.admin.virtualpasture` | Consulta contagens e reconcilia chunks carregados. |
| `bigbangregions.admin.reload` | Autoriza `/regions reload`. |

O maior valor entre os tiers que o proprietário possui é usado. Para manter a
regra segura quando um membro coloca o bloco, a colocação por membro usa o teto
`default`; ela nunca usa a permissão VIP do membro para aumentar a cota do dono.

## Aplicar alterações

1. Faça backup de `config/bigbangregions/regions.db` antes de uma atualização.
2. Edite `config/bigbangregions/config.json`.
3. Execute `/regions reload` ou reinicie o servidor.
4. Confira a configuração com os comandos abaixo e faça uma reconciliação dos
   chunks já carregados.

Configurações anteriores à schema `3` são migradas automaticamente no primeiro
load/reload: os blocos `virtualPasture` e `regionExpansionPerformance` são
gravados no mesmo arquivo com seus campos ausentes preenchidos. Valores já
definidos pelo servidor são preservados; não é necessário substituir o arquivo
inteiro por este exemplo.

`/regions reload` recarrega a configuração e revalida se o bloco configurado
está presente no registry. Alterar `blockId` para um Identifier inexistente não
substitui nem remove blocos; apenas torna o limitador inativo até a configuração
ser corrigida.

## Comandos administrativos

```text
/regions pastagemvirtual regiao <regionId>
/regions pastagemvirtual jogador <player>
/regions pastagemvirtual reconciliar
```

Os dois primeiros mostram a quantidade indexada por região ou proprietário.
`reconciliar` revisa somente chunks que o Minecraft já carregou; ele nunca gera
ou carrega chunks apenas para contar blocos.

## Dados existentes, índice e reinicialização

As contagens são persistidas na tabela SQLite `virtual_pastures`; o índice em
memória é apenas um cache rápido. Uma reserva curta é gravada antes de a
colocação prosseguir, impedindo duas colocações no mesmo tick de ocuparem a
mesma vaga. Reservas cuja colocação não se conclui expiram automaticamente.

Blocos antigos passam a ser conhecidos quando seus chunks forem carregados e
também ao executar `reconciliar`. Chunks descarregados permanecem sem varredura
intencional: forçá-los para contar criaria exatamente o custo de I/O e geração
que esta integração evita. Após a atualização, faça a reconciliação durante um
período de manutenção e novamente depois que as áreas mais usadas forem
visitadas.

O índice é atualizado ao colocar, quebrar ou mover o bloco por APIs/commands,
transferir o proprietário da região e excluir a região. Se a gravação SQLite de
uma reserva falhar, a colocação é negada para não permitir uma contagem
inconsistente.

## Ajuste de operações de região

As operações grandes também usam as chaves abaixo no mesmo `config.json`:

```json
"regionExpansionPerformance": {
  "snapshotCaptureBudgetMs": 3,
  "snapshotCaptureMaxBlocksPerTick": 250,
  "borderApplicationBudgetMs": 3,
  "borderApplicationMaxBlocksPerTick": 250,
  "persistenceWorkers": 1,
  "persistenceQueueCapacity": 32,
  "shutdownTimeoutSeconds": 10,
  "deletionRestoreTimeoutSeconds": 120
}
```

`snapshotCapture*` limita a captura da restauração antes da criação;
`borderApplication*` limita borda e restauração na exclusão. A gravação NBT é
feita em I/O dedicado, mas leitura e alteração de mundo continuam na thread do
servidor em lotes. Os budgets são limitados internamente a 1–20 ms, os limites
de blocos a 1–5.000, os workers a 1–2 e o timeout de exclusão a 30–1.800 s.

Mantenha os dois budgets em **3 ms** inicialmente. Em um servidor já medido e
com folga, 5 ms é um teto inicial razoável. Reduza para 1–2 ms se houver picos;
a operação ficará mais lenta, mas cada tick terá menos trabalho deliberado.

A busca virtual de biome é ajustada separadamente em
`playerLandAllocation.worldgenSearch`:

```json
"worldgenSearch": {
  "maxSearchWorkNanosPerTick": 750000,
  "maxSearchStepsPerTick": 1,
  "maxLocateCallsPerSearchStep": 1
}
```

Reduzir esses valores distribui a busca por mais ticks; aumentá-los reduz o
tempo total de espera, mas aumenta o trabalho permitido em cada tick. O
coordenador processa uma solicitação de alocação por vez, preservando a ordem
dos slots e evitando operações concorrentes na mesma região.

## Validação operacional

Depois de instalar ou ajustar os limites:

1. Coloque blocos até cada teto e confirme que a mensagem mostra `atual/máximo`.
2. Tente com um membro da mesma região; a cota do dono deve prevalecer.
3. Quebre um bloco e confirme que a vaga é liberada.
4. Transfira o proprietário e consulte a contagem novamente.
5. Reinicie o servidor, carregue um chunk com bloco antigo e execute
   `/regions pastagemvirtual reconciliar`.
6. Com Spark, capture criação, expansão e exclusão de região; compare o maior
   tick e procure por etapas de alocação acima dos budgets configurados.

Em caso de falha de restauração ou timeout na exclusão, a região permanece
protegida e o snapshot é preservado para nova tentativa administrativa.
