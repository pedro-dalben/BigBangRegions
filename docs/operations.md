# Guia de Operações e Administração

Guia prático para administradores gerenciarem o BigBang Regions no servidor de produção.

## Sequência de Entrada no Servidor

Não há warmup manual. A criação de terrenos usa a busca normal do mod e o servidor pode subir sem nenhuma ação adicional.

## Gerenciamento de Regiões Administrativas

### Criar um Spawn Protegido
1. Defina os cantos com `/regions pos1` e `/regions pos2`.
2. Execute `/regions create admin spawn 2000`.
3. Por padrão, todas as flags administrativas em `config.json` são aplicadas (Build: DENY, Interact: DENY, Container: DENY, PvP: DENY).

### Liberar Apenas Acesso a Baús e Portas
Caso queira que os jogadores possam abrir portas e baús no spawn (ex: baús públicos de eventos ou portas de vilas):
```bash
/regions flag set spawn container-access ALLOW
/regions flag set spawn door-use ALLOW
```

### Ativar PvP em uma Arena de Eventos
Se você criar uma arena e quiser que o PvP seja ativado nela (mesmo estando dentro do Spawn):
1. Selecione a arena e crie uma região de prioridade superior (ex: prioridade 3000):
   ```bash
   /regions create admin arena_pvp 3000
   ```
2. Defina a flag de pvp para permitir:
   ```bash
   /regions flag set arena_pvp pvp ALLOW
   ```
Como `arena_pvp` tem prioridade 3000, ela sobrepõe a restrição de PvP do spawn (prioridade 2000).

## Administração da Base de Dados SQLite

* **Caminho do arquivo:** `config/bigbangregions/regions.db`.
* O banco de dados é atualizado em tempo real quando comandos são executados.
* Você pode realizar backups copiando o arquivo `regions.db` com o servidor ligado (o SQLite permite leituras concorrentes seguras).

## Logs de Auditoria

Todas as alterações estruturais são registradas na tabela `region_audit_logs` do SQLite. Para inspecionar modificações, você pode abrir o banco de dados com qualquer visualizador SQLite (como DB Browser for SQLite) e inspecionar a tabela.

Eventos auditados:
* `CREATE_REGION`: Criação de regiões.
* `DELETE_REGION`: Exclusão de regiões.
* `SET_FLAG`: Alteração de flags (armazena valores antigos e novos).
* `RELOAD`: Recarga do mod.

## Performance da Criação de Terrenos

Se a criação de terrenos (`/regiao criar <bioma>`) estiver demorando ou gerando `Can't keep up!`,
pré-gerue o mundo com o **Chunky** — a busca de bioma é instantânea quando os chunks já existem em
disco. Veja o guia completo em [`docs/bigbangregions/chunky-pregen.md`](bigbangregions/chunky-pregen.md).

Ajustes de tempo por tick ficam em `config.json` > `playerLandAllocation.scheduler`:
`maxCandidateEvaluationsPerTick` (hard cap) e `maxBiomeSearchMillisPerTick` (limite por tempo).
