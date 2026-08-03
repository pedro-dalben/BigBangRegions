# Pré-geração com Chunky e Alocação de Terrenos

A fase de **busca de bioma** (`SEARCHING`) da alocação de terrenos precisa ler o bioma de muitos
candidatos para encontrar um slot compatível com a escolha do jogador. Para que essa busca seja
**incremental** e não concentre trabalho na thread principal do servidor. A
busca virtual é limitada por trabalho e passos em cada tick em
`playerLandAllocation.worldgenSearch`.

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

O alcance ativo é definido pelas entradas de `worldgenSearch.allocationBands`
(padrão: 2.000 a 30.000 blocos). Você não precisa pré-gerar o alcance inteiro —
pré-gerar a partir da **zona de exploração** até uma distância razoável (ex.:
20.000 blocos) costuma cobrir a vasta maioria das alocações.

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

No `config/bigbangregions/config.json`, em
`playerLandAllocation.worldgenSearch`:

| Campo | Padrão | Efeito |
|---|---|---|
| `maxSearchWorkNanosPerTick` | `750000` | Orçamento de trabalho virtual por tick (0,75 ms). |
| `maxSearchStepsPerTick` | `1` | Número máximo de etapas de busca por tick. |
| `maxLocateCallsPerSearchStep` | `1` | Número máximo de localizações de biome em cada etapa. |
| `allocationBands` | `2000`–`30000` | Faixas radiais nas quais a busca reserva slots. |

Recomendado manter os padrões. Para reduzir ainda mais o custo por tick,
reduza `maxSearchWorkNanosPerTick` ou mantenha `maxSearchStepsPerTick` em `1`;
isso aumenta o tempo total de procura, mas não libera uma etapa maior no mesmo
tick. Veja também o [guia de limites e budgets](../virtual-pastures.md).

## Biomas disponíveis

Agora o `Config` registra todos os grupos principais de biomas da overworld:
planicies, floresta, taiga, deserto, savana, selva, praia, oceano, montanha, pantano, neve,
cerejeira, cogumelo, rio e costa de pedra. Adicione/edite entradas em `biomeOptions` do `config.json`
para refletir biomas customizados de datapacks.
