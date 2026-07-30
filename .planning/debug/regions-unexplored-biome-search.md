---
status: resolved
trigger: "Depois da integração com Regions Unexplored, a alocação não encontra região; logs mostram biome=selva, VIRTUAL_SEARCHING, anchors=0, candidates=0, lastRejection=searching_biome."
created: 2026-07-30
updated: 2026-07-30
---

## Symptoms

- Expected: `/regions criar selva` encontra uma área válida e cria a região.
- Actual: a busca permanece em `VIRTUAL_SEARCHING`, com milhares de amostras, `anchors=0` e `lastRejection=searching_biome`.
- Related logs: C2ME Jigsaw `minecraft:` template-pool warnings appear separately; Cobblemon placeholder warnings also appear separately.
- Timeline: começou após a atualização da integração com Regions Unexplored.
- Reproduction: request `b6745da7`, option `selva`.

## Current Focus

- hypothesis: a divergência entre IDs/categorias RU e a amostragem virtual impede o reconhecimento do bioma; ainda é necessário separar isso de limite/configuração de busca.
- test: rastrear a opção `selva` até `BiomeVirtualSampler`/`WorldgenSearchContext` e comparar com IDs reais do jar RU e a configuração ativa.
- expecting: identificar se os IDs são aceitos mas nunca retornados pelo sampler, ou se a busca está procurando fora do alcance/configuração.
- next_action: gather initial evidence

## Evidence

- timestamp: 2026-07-30
  observation: "The request progresses from 41s to 201s, but anchors remain 0 and lastRejection remains searching_biome."
- timestamp: 2026-07-30
  observation: "Historical run/logs/latest.log loads 50 mods without regions_unexplored (lines 1-9) and resolves selva only to minecraft:jungle, minecraft:sparse_jungle, minecraft:bamboo_jungle (line 93)."
- timestamp: 2026-07-30
  observation: "Current BiomeOptionRegistry adds RU IDs only when FabricLoader reports regions_unexplored loaded (lines 42-43, 57-82); the current build jar contains the RU strings, so the artifact is not proof that the runtime loaded the mod."
- timestamp: 2026-07-30
  observation: "The installed RU 0.6.2 jar contains bamboo_forest, rainforest, sparse_rainforest, and tropics biome resources; the curated selva IDs are valid."
- timestamp: 2026-07-30
  observation: "Option lookup success and pending search are separate: processVirtualSearch resolves the option before searching (TerrainAllocationCoordinator.java:529-536), while a bounded locator Continue sets searching_biome (TerrainAllocationCoordinator.java:568-604)."
- timestamp: 2026-07-30
  observation: "Active config scans one sector at a time with locateRadiusBlocks=1300, blockCheckInterval=64, maxSearchStepsPerTick=1, and only the primary 2000-30000 block allocation band; maximumSearchRadiusBlocks=120000 is not the active locator bound (run/config/bigbangregions/config.json:92-127)."

## Eliminated

- hypothesis: "The posted Jigsaw and Cobblemon warnings directly caused the allocation failure."
  reason: "They are emitted by separate worker/server paths and do not explain the BigBangRegions biome-search counters."

## Resolution

- root_cause: "The historical runtime used for the local reproduction did not load Regions Unexplored, so BiomeOptionRegistry intentionally resolved selva to vanilla IDs only; if the report used that runtime, the RU integration was inactive. The observed VIRTUAL_SEARCHING state is otherwise consistent with a legitimate sparse search, not an option-lookup failure."
- fix: "Not applied. Deploy the RU jar and its required Lithostitched dependency to the target runtime, restart, and require both a Fabric loader entry for regions_unexplored and a selva registry log containing the four RU IDs before judging search behavior. Separately, correct WorldgenBiomeAnchorLocator.java:149/162 to pass the block radius to findBiomeHorizontal; the current quart radius only shrinks the fast path and is a latency defect, not proof of zero matches."
- verification: "No production or test files changed. Evidence checked against source, run/config/bigbangregions/config.json, run/logs/latest.log, build/libs/bigbang-regions-1.0.0.jar, and the installed regions-unexplored-0.6.2-fabric-21.1.jar."
- files_changed: "none"
