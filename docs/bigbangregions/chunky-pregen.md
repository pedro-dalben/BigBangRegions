# Pré-geração com Chunky e Alocação de Terrenos

A fase de **busca de bioma** (`SEARCHING`) da alocação de terrenos precisa ler o bioma de muitos
candidatos para encontrar um slot compatível com a escolha do jogador. Para que essa busca seja
**instantânea** e não sobrecarregue a thread principal do servidor, ela lê o bioma via
`ChunkStatus.BIOMES` (sem forçar geração de terreno completo) e é **limitada por tempo** a cada tick
(`maxBiomeSearchMillisPerTick`).

Mesmo assim, candidatos cujos chunks ainda não existem em disco precisam ser gerados (mesmo que
somente até o estágio de biomas). Pré-gerar o mundo elimina esse custo e torna a alocação praticamente
instantânea.

## Por que usar o Chunky

O [Chunky](https://github.com/pop4959/Chunky) pré-gera chunks e os salva em disco. Com o mundo pré-
gerado:

- A leitura de bioma da busca apenas **desserializa** paletas já salvas (barato), sem gerar nada.
- Não ocorre o "Can't keep up! Is the server overloaded?" durante a criação de terrenos.
- Os candidatos são encontrados dentro do primeiro ou segundo ciclo de ticks.

Sem pré-geração, a busca ainda funciona (limite por tempo evita lag explosivo), mas pode levar
alguns segundos a dezenas de segundos dependendo da distância até um bioma compatível.

## Configuração recomendada

### 1. Definir o raio necessário

O raio máximo de busca é `maximumSearchRadiusBlocks` em `config.json` (padrão `120000`). Você não
precisa pré-gerar o raio inteiro — pré-gerar a partir da **zona de exploração** até uma distância
razoável (ex.: 20000 blocos) costuma cobrir a vasta maioria das alocações.

A zona de exploração é `explorationExclusion` (centro onde os slots iniciam a busca). Os slots
crescem em anéis a partir dela.

### 2. Pré-gerar com Chunky

A partir do console/OP:

```
chunky start minecraft:overworld -20000 -20000 40000 40000
chunky continue
```

Isso gera o quadrado `[-20000, 20000] x [-20000, 20000]` da overworld. Acompanhe com:

```
chunky pause
chunky continue
```

Pré-gerar é um processo demorado (HTML/CPU alto). Faça em momentos de baixa ocupação ou em um
servidor dedicado temporário.

> Recomendado: gere também o retângulo correspondente aoolvimento da zona de exploração
> (`explorationExclusion`) para que os primeiros slots (mais próximos) já estejam prontos.

### 3. Confirmar

```
chunky quiet
```

Após concluir, a alocação de terrenos deve ocorrer em poucos ticks, sem warnings de "Can't keep up"
e sem warnings de "Skipping direct biome palette mutation" excessivos.

## Ajustes finos de performance

No `config/bigbangregions/config.json`, em `playerLandAllocation.scheduler`:

| Campo | Padrão | Efeito |
|---|---|---|
| `maxCandidateEvaluationsPerTick` | `256` | Número máximo de candidatos verificados por tick (hard cap). |
| `maxBiomeSearchMillisPerTick` | `25` | Tempo máximo por tick gasto na busca de bioma. Evita lag monopolizando a thread. |
| `maximumSearchRadiusBlocks` | `120000` | Raio máximo de busca (acentua inevitabilidade de match com pré-gen próximo). |

Recomendado manter os padrões. Em servidores com muitos pedidos simultâneos, reduza
`maxBiomeSearchMillisPerTick` para `10`.

## Biomas disponíveis

Agora o `Config` registra todos os grupos principais de biomas da overworld:
planicies, floresta, taiga, deserto, savana, selva, praia, oceano, montanha, pantano, neve,
cerejeira, cogumelo, rio e costa de pedra. Adicione/edite entradas em `biomeOptions` do `config.json`
para refletir biomas customizados de datapacks.