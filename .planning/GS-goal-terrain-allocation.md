# GOAL — BigBang Regions: Alocação Física de Terreno + Saga Persistente de Pagamento com BigBang Essentials

## Papel e forma de trabalho

Você está trabalhando diretamente no repositório do mod **BigBang Regions**.

Implemente esta fase completa sem usar agentes, orquestradores ou tarefas paralelas externas. Trabalhe de forma incremental, valide cada módulo e faça commits pequenos e bem separados.

Não peça confirmação para decisões já definidas neste documento. Quando houver detalhes do código atual que divergirem desta especificação, preserve as regras de negócio e adapte a implementação à arquitetura existente.

---

# 1. Contexto consolidado

## Projeto alvo

```txt
Mod: BigBang Regions
Mod ID: bigbangregions
Package base: com.bigbangcraft.regions
Minecraft: Fabric 1.21.1
Java: 21
Servidor: server-side only
Banco: SQLite
```

O BigBang Regions já possui ou está previsto para possuir:

```txt
- Regiões com prioridade:
  SYSTEM_REGION > ADMIN_REGION > PLAYER_REGION

- Papéis:
  OWNER
  LEADER
  MEMBER
  VISITOR

- Flags tipadas:
  ALLOW
  DENY
  INHERIT

- Cache de regiões
- Índice espacial
- Auditoria
- Migrations SQLite
- Proteções de região
- Um player region por owner, conforme configuração
```

Esta fase não implementa JourneyMap, partículas de limite, mapa, presença, visualização, GUI personalizada ou comandos de expansão pública. Isso será uma fase posterior.

O objetivo aqui é deixar o núcleo seguro e recuperável:

```txt
Jogador solicita uma terra
→ servidor encontra slot válido
→ reserva pagamento
→ prepara terreno
→ cria a região
→ captura pagamento
→ recupera corretamente após crash
```

---

# 2. Pré-condição obrigatória

Antes de iniciar a implementação, valide o estado atual do repositório.

Execute:

```bash
git status --short
git log -1 --oneline
git diff --check
./gradlew clean test build
```

Leia obrigatoriamente:

```txt
docs/reviews/p0-remediation-report.md
docs/reviews/terrain-allocation-p0-remediation-report.md
docs/architecture/regions.md
docs/architecture/database.md
docs/architecture/protection.md
```

Os nomes podem variar. Localize os documentos equivalentes existentes.

## Gate obrigatório

Esta fase só pode começar se o BigBang Regions já tiver resolvido os bloqueios P0 anteriores relacionados a:

```txt
- concorrência em memberships;
- limpeza de cache após delete;
- proteção de drop sem perda de item;
- bucket vazio tratado como interação;
- bucket cheio tratado como build;
- coordenador de alocação inexistente ou incompleto;
- slots sem reserva transacional;
- operações que alteram mundo fora da thread principal.
```

Caso essa base ainda não esteja realmente pronta, não implemente uma versão paralela ou duplicada. Corrija somente o bloqueio necessário e registre no relatório final.

---

# 3. Decisões obrigatórias de produto

## 3.1 Primeiro terreno

Cada player recebe, no máximo, uma região de jogador ativa conforme a configuração existente.

O claim inicial deve ter exatamente:

```txt
50 x 50 blocos
```

A definição precisa ser inequívoca:

```txt
minX até minX + 49, inclusive
minZ até minZ + 49, inclusive
```

Portanto:

```txt
largura = 50
profundidade = 50
área = 2500 blocos
```

Nunca gerar 49x49 ou 51x51 por erro de limite inclusivo.

---

## 3.2 Plot Slot reservado para expansão futura

Cada terreno inicial pertence a um slot lógico de:

```txt
256 x 256 blocos
```

O slot é reservado para futura expansão, mas não é uma região protegida de 256x256.

Apenas o claim ativo de 50x50 deve ser protegido agora.

Parâmetros:

```txt
PLOT_SLOT_WIDTH = 256
PLOT_SLOT_DEPTH = 256
INITIAL_REGION_WIDTH = 50
INITIAL_REGION_DEPTH = 50
FUTURE_MAX_WIDTH = 240
FUTURE_MAX_DEPTH = 240
SLOT_MARGIN = 8
```

O claim inicial deve ficar centralizado dentro do slot.

Como:

```txt
(256 - 50) / 2 = 103
```

o claim inicial deve começar em:

```txt
slotMinX + 103
slotMinZ + 103
```

e ocupar exatamente 50 blocos em cada eixo.

Nunca transformar o Plot Slot em proteção de região. Ele é apenas uma reserva territorial interna.

---

## 3.3 Área central de exploração

Existe uma zona central pública destinada à exploração do servidor.

Configuração padrão:

```txt
centralZone.minX = -20000
centralZone.maxX = 20000
centralZone.minZ = -20000
centralZone.maxZ = 20000
centralZone.buffer = 1000
```

Portanto, nenhum Plot Slot pode intersectar a zona expandida:

```txt
X: -21000 até 21000
Z: -21000 até 21000
```

A validação deve considerar a área inteira do slot de 256x256, não apenas o centro ou o spawn.

---

## 3.4 Biomas permitidos

O jogador deve escolher um bioma permitido.

Comandos esperados futuramente:

```txt
/regiao biomas
/regiao criar <bioma>
```

Nesta fase, implemente o backend necessário para isso.

O sistema deve usar aliases configuráveis, por exemplo:

```txt
plains -> minecraft:plains
forest -> minecraft:forest
desert -> minecraft:desert
taiga -> minecraft:taiga
savanna -> minecraft:savanna
```

Nunca aceitar um identificador arbitrário enviado pelo player.

A escolha deve ser validada contra uma lista configurada pelo servidor.

---

## 3.5 Validação real de bioma

Um terreno não pode ser aceito apenas porque um bloco isolado pertence ao bioma solicitado.

Cada candidato deve usar uma malha de amostragem 5x5 distribuída na área útil do claim inicial.

Requisito mínimo:

```txt
25 amostras
pelo menos 60% devem corresponder ao bioma solicitado
```

Logo:

```txt
mínimo: 15 de 25 amostras
```

A coleta de bioma deve ocorrer somente na thread principal do servidor, usando a API real do Minecraft/Fabric da versão atual do projeto.

Não inventar classes ou métodos de biome sampling. Inspecione as mappings e APIs disponíveis antes de implementar.

---

# 4. Integração obrigatória com BigBang Essentials

## 4.1 Estado do BigBang Essentials

O BigBang Essentials já está aprovado para integração com Regions.

Baseline aprovado:

```txt
Repository: pedro-dalben/BigBangEssentials
SHA aprovado: 63d4a030
Veredito: GEMS_API_APPROVED_FOR_REGIONS_INTEGRATION
```

O sistema Gems já oferece garantias de:

```txt
- reserva de saldo;
- renovação de reserva;
- captura de reserva;
- liberação de reserva;
- idempotência persistida;
- fingerprint de request;
- pending audit entries;
- ledger deduplicado por transactionId;
- recuperação após crash;
- expiração state-first;
- concorrência coberta;
- ausência de dependência com Regions.
```

---

## 4.2 Regra principal de integração

O BigBang Regions nunca pode:

```txt
- ler gems_state.json;
- ler gems_transactions.jsonl;
- alterar arquivos do BigBang Essentials;
- alterar saldo diretamente;
- chamar internals de persistence;
- chamar internals de GemsManager;
- acessar classes privadas ou não documentadas;
- depender de Coins, Vault ou outro sistema econômico;
- modificar o BigBang Essentials.
```

O Regions deve consumir somente a API pública, estável e suportada do BigBang Essentials.

Antes de escrever o adapter, inspecione no repositório do Essentials:

```txt
- API pública disponível;
- classes de request;
- classes de result;
- enum de falhas;
- método de reserve;
- método de renew;
- método de capture;
- método de release;
- método público para consultar uma reserva por idempotency key, caso exista;
- mod id real no fabric.mod.json;
- configuração Gradle e coordenadas Maven/publicação.
```

Não suponha nomes de classes, packages ou construtores.

Se a API pública não oferecer uma operação necessária para a saga de forma segura, registre o bloqueio detalhado no relatório final. Não burle a ausência da API acessando classes internas.

---

## 4.3 Arquitetura obrigatória de desacoplamento

Criar no BigBang Regions uma abstração própria:

```txt
LandPaymentGateway
```

Ela deve existir no núcleo do Regions e não pode importar classes do BigBang Essentials.

Responsabilidades mínimas:

```txt
reserve(...)
renew(...)
capture(...)
release(...)
isAvailable(...)
getProviderStatus(...)
```

Criar tipos próprios do Regions, por exemplo:

```txt
LandPaymentReserveRequest
LandPaymentRenewRequest
LandPaymentCaptureRequest
LandPaymentReleaseRequest

LandPaymentReserveResult
LandPaymentOperationResult
LandPaymentFailure
LandPaymentProviderStatus
```

Os tipos do Regions devem conter somente dados necessários para a operação de terreno.

Criar uma implementação isolada:

```txt
BigBangEssentialsGemsGateway
```

Essa implementação será o único local do repositório que poderá importar a API pública do BigBang Essentials.

Estrutura sugerida:

```txt
com.bigbangcraft.regions.payment
com.bigbangcraft.regions.payment.api
com.bigbangcraft.regions.payment.essentials
```

A camada core nunca pode importar classes de:

```txt
com.pedrodalben.bigbangessentials.*
```

---

## 4.4 Dependência opcional e boot seguro

BigBang Regions deve continuar iniciando mesmo se BigBang Essentials não estiver instalado.

Comportamento obrigatório:

```txt
BigBang Essentials instalado e API disponível
→ registrar BigBangEssentialsGemsGateway.

BigBang Essentials ausente
→ registrar NoPaymentGateway / UnavailablePaymentGateway.

API incompatível ou falha no bootstrap
→ registrar gateway indisponível e logar mensagem clara para administradores.
```

Não usar fallback para pagamento gratuito quando o servidor configurou um preço maior que zero.

Se pagamento for necessário e o Essentials estiver indisponível:

```txt
resultado: ECONOMY_UNAVAILABLE
nenhum slot deve ser consumido permanentemente
nenhuma região deve ser criada
```

Use a infraestrutura existente de Fabric Loader para detectar o mod.

O adapter pode ser carregado de forma lazy para evitar erro de classloading sem Essentials.

Não usar reflection para chamar métodos internos de Gems.

Reflection pode ser usada apenas para isolar o carregamento da classe do adapter opcional, caso isso seja necessário pela arquitetura Gradle/Fabric atual.

---

## 4.5 Build e dependência Gradle

Inspecione o build atual do BigBang Regions antes de alterar o Gradle.

Regras:

```txt
- Não sombrear BigBang Essentials dentro do jar do Regions.
- Não copiar classes do Essentials.
- Não adicionar jar manual versionado no repositório.
- Preferir compileOnly para compilar o adapter.
- Usar a configuração de desenvolvimento/teste compatível com o build atual.
- Não tornar BigBang Essentials uma dependência obrigatória para o servidor iniciar.
```

Caso o workspace permita composite build ou dependência local de desenvolvimento, use a estrutura já adotada no projeto.

Documente o procedimento de desenvolvimento necessário para compilar o adapter.

---

# 5. Política de preço

## 5.1 Não inventar valores

Não inventar preço fixo para terreno.

Criar uma abstração:

```txt
LandPricingPolicy
LandPriceQuote
```

O valor deve ser persistido na operação no momento da criação.

Nunca recalcular ou alterar o preço depois que a reserva de pagamento foi iniciada.

---

## 5.2 Configuração inicial segura

Criar configuração explícita, por exemplo:

```txt
payments:
  provider: bigbangessentials
  initial_allocation_cost_gems: 0
  reservation_lease_seconds: 900
  renew_before_expiry_seconds: 300
  max_capture_retries_before_manual_block: 10
  retry_backoff_seconds: 30
```

A configuração padrão para o primeiro terreno deve ser:

```txt
initial_allocation_cost_gems: 0
```

Isso evita cobrança acidental ao instalar a versão.

Quando o administrador configurar valor maior que zero:

```txt
- o fluxo de reserve/capture passa a ser obrigatório;
- o player não recebe terreno sem pagamento confirmado;
- todos os testes devem cobrir esse fluxo.
```

Mesmo quando o custo for zero, a operação deve continuar usando a mesma máquina de estados de alocação, apenas pulando o gateway de pagamento.

---

## 5.3 Identificadores de integração

Os identificadores enviados ao Essentials devem ser estáveis, determinísticos e compatíveis com as validações atuais.

Usar:

```txt
source: bigbangregions
purpose: land_allocation
externalReference: operationId
```

Não usar `:` em campos que possam ser validados como identificadores pelo Essentials.

Para cada operação, criar chaves idempotentes determinísticas usando UUID sem hífens:

```txt
regions_<compactOperationId>_reserve
regions_<compactOperationId>_renew_<sequence>
regions_<compactOperationId>_capture
regions_<compactOperationId>_release
```

Exemplo:

```txt
operationId:
a91e5d84-2118-42ff-8997-7c9657c2b4f1

reserve:
regions_a91e5d84211842ff89977c9657c2b4f1_reserve
```

Nunca gerar uma nova idempotency key para retry da mesma etapa.

---

# 6. Máquina de estados persistida

Criar uma entidade persistida de operação de terreno.

Nome sugerido:

```txt
LandOperation
```

Cada operação deve possuir `operationId` UUID imutável.

Estados obrigatórios:

```txt
REQUESTED
SEARCHING_TERRAIN
SLOT_RESERVED

PAYMENT_RESERVE_PENDING
PAYMENT_RESERVED

TERRAIN_PREPARING
REGION_CREATING
REGION_CREATED_PAYMENT_CAPTURE_PENDING

COMPLETED

RELEASE_PENDING
CANCELLED_BEFORE_REGION_CREATION

FAILED_NO_TERRAIN
FAILED_ECONOMY_UNAVAILABLE
BLOCKED_FOR_MANUAL_RECONCILIATION
```

## 6.1 Estados anteriores à criação física

Antes da região ser criada, o sistema pode:

```txt
- cancelar;
- liberar reserva de Gems;
- liberar Plot Slot;
- informar falha ao player;
- permitir nova tentativa posterior.
```

Isso inclui:

```txt
REQUESTED
SEARCHING_TERRAIN
SLOT_RESERVED
PAYMENT_RESERVE_PENDING
PAYMENT_RESERVED
TERRAIN_PREPARING
REGION_CREATING, desde que a transação de criação ainda não tenha sido confirmada.
```

---

## 6.2 Fronteira irreversível

A fronteira irreversível ocorre quando a transação SQLite confirma simultaneamente:

```txt
- criação da região;
- associação do owner;
- criação do home;
- vínculo com Plot Slot;
- vínculo com LandOperation;
- mudança da operação para REGION_CREATED_PAYMENT_CAPTURE_PENDING.
```

Depois disso:

```txt
- nunca chamar release automaticamente;
- nunca liberar o slot;
- nunca apagar a região automaticamente;
- nunca tentar "desfazer" geometria;
- nunca cobrar novamente com uma nova reserva;
- nunca gerar novo operationId;
- nunca criar uma segunda região.
```

Após a fronteira irreversível, a única ação financeira automática permitida é:

```txt
capture com a mesma reservationId
e com a mesma capture idempotency key persistida.
```

---

## 6.3 Falha de capture após criação da região

Se a região foi criada e `capture` falhar:

```txt
estado:
REGION_CREATED_PAYMENT_CAPTURE_PENDING
```

O sistema deve:

```txt
- manter região e slot;
- manter operação persistida;
- não liberar Gems;
- não criar nova região;
- não enviar teleporte final ao player;
- não exibir coordenadas exatas ao player;
- reagendar capture com backoff;
- registrar auditoria;
- alertar administradores após exceder o limite de retry.
```

Ao exceder a política configurada de retry:

```txt
estado:
BLOCKED_FOR_MANUAL_RECONCILIATION
```

Esse estado exige ação de administrador.

Não criar débito alternativo, não usar reset administrativo e não usar compensação automática.

---

# 7. Banco de dados e migration

Criar migration nova respeitando a numeração atual do projeto.

Exemplo esperado:

```txt
V003__land_allocation_and_payment_saga.sql
```

Ajustar o nome se já existirem migrations posteriores.

## 7.1 Tabela land_operations

Criar tabela equivalente a:

```sql
CREATE TABLE land_operations (
    operation_id TEXT PRIMARY KEY,
    owner_uuid TEXT NOT NULL,

    operation_type TEXT NOT NULL,
    requested_biome_alias TEXT NOT NULL,
    requested_biome_id TEXT NOT NULL,
    dimension_id TEXT NOT NULL,

    state TEXT NOT NULL,
    state_version INTEGER NOT NULL DEFAULT 1,

    price_gems INTEGER NOT NULL DEFAULT 0,
    payment_required INTEGER NOT NULL DEFAULT 0,

    gems_reservation_id TEXT,
    reserve_idempotency_key TEXT,
    renew_idempotency_key TEXT,
    renew_sequence INTEGER NOT NULL DEFAULT 0,
    capture_idempotency_key TEXT,
    release_idempotency_key TEXT,

    plot_slot_id INTEGER,
    region_id INTEGER,

    home_x INTEGER,
    home_y INTEGER,
    home_z INTEGER,
    home_yaw REAL,
    home_pitch REAL,

    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at INTEGER,
    reservation_lease_expires_at INTEGER,

    failure_code TEXT,
    failure_detail TEXT,

    requested_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    region_created_at INTEGER,
    payment_captured_at INTEGER,

    FOREIGN KEY (plot_slot_id) REFERENCES plot_slots(slot_id),
    FOREIGN KEY (region_id) REFERENCES regions(id)
);
```

Adaptar nomes de tabelas e tipos ao banco já existente.

Criar índices para:

```txt
owner_uuid + state
state + next_retry_at
plot_slot_id
region_id
gems_reservation_id
```

Garantir um único processo não-terminal por owner.

Isso pode ser assegurado por índice parcial SQLite, caso compatível, ou por validação transacional forte.

---

## 7.2 Tabela plot_slots

Criar estrutura persistida equivalente a:

```sql
CREATE TABLE plot_slots (
    slot_id INTEGER PRIMARY KEY AUTOINCREMENT,

    dimension_id TEXT NOT NULL,

    slot_min_x INTEGER NOT NULL,
    slot_min_z INTEGER NOT NULL,
    slot_width INTEGER NOT NULL,
    slot_depth INTEGER NOT NULL,

    state TEXT NOT NULL,

    operation_id TEXT,
    owner_uuid TEXT,
    region_id INTEGER,

    reserved_until INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,

    UNIQUE (dimension_id, slot_min_x, slot_min_z),
    UNIQUE (operation_id),

    FOREIGN KEY (operation_id) REFERENCES land_operations(operation_id),
    FOREIGN KEY (region_id) REFERENCES regions(id)
);
```

Estados mínimos:

```txt
RESERVED
OCCUPIED
RETIRED
```

Não reutilizar automaticamente um slot que já teve região.

Se uma região for removida no futuro, o slot deve continuar:

```txt
RETIRED
```

A reciclagem futura de slot será exclusivamente administrativa e não pertence a este goal.

---

## 7.3 Homes

Criar tabela de homes apenas se não existir estrutura equivalente.

Exemplo:

```sql
CREATE TABLE region_homes (
    region_id INTEGER PRIMARY KEY,
    dimension_id TEXT NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    yaw REAL NOT NULL DEFAULT 0,
    pitch REAL NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,

    FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE CASCADE
);
```

Não duplicar uma estrutura já existente.

---

# 8. Reserva transacional de Plot Slot

## 8.1 Regras de concorrência

Nunca confiar apenas em memória para reservar slot.

A reserva precisa ser garantida no SQLite.

Usar transação com bloqueio apropriado para SQLite, por exemplo:

```txt
BEGIN IMMEDIATE
```

ou equivalente já adotado pelo repositório.

O algoritmo deve garantir:

```txt
- dois players não recebem o mesmo slot;
- duas solicitações do mesmo player não criam duas operações;
- restart não perde slot reservado;
- slot vencido antes de criação física pode ser recuperado;
- slot ocupado nunca é liberado automaticamente;
- slot com região criada nunca é reutilizado automaticamente.
```

---

## 8.2 Lease de slot

Cada slot reservado antes de criar a região deve ter lease persistido.

Configuração sugerida:

```txt
allocation:
  plot_slot_lease_seconds: 300
```

No boot:

```txt
- operações anteriores à fronteira irreversível com lease vencido devem ser reconciliadas;
- se havia reserva Gems, tentar release idempotente;
- somente após release confirmado ou ausência de pagamento, liberar o slot;
- operações após criação física nunca liberam slot automaticamente.
```

---

## 8.3 Algoritmo de busca

A busca deve ser determinística e limitada.

Implementar uma enumeração por anéis ou grade, começando além da zona central proibida.

Regras:

```txt
- nunca procurar dentro da centralZone + buffer;
- nunca usar slot que intersecta região existente;
- nunca usar slot já RESERVED, OCCUPIED ou RETIRED;
- nunca usar slot que intersecta SYSTEM_REGION ou ADMIN_REGION;
- verificar dimensão correta;
- respeitar limite de tentativas por operação;
- registrar motivo de rejeição apenas em logs de debug, evitando spam.
```

Configurações sugeridas:

```txt
allocation:
  search_start_distance_blocks: 22000
  search_step_blocks: 256
  max_candidates_per_request: 300
  candidates_per_tick: 1
  max_active_searches: 1
```

Nunca fazer loop pesado no comando do player.

---

# 9. Threading e performance

## 9.1 Regras absolutas

Minecraft world access deve acontecer somente na thread principal do servidor.

Inclui:

```txt
- biome lookup;
- heightmap;
- block state;
- fluid state;
- chunk loading;
- chunk tickets;
- spawn validation;
- criação de região que dependa de posição/world;
- teleporte;
- mensagens ao player.
```

Banco e cálculos sem mundo podem usar a infraestrutura assíncrona existente, mas:

```txt
- nunca acessar ServerWorld fora da thread principal;
- nunca chamar API de Minecraft em worker thread;
- nunca manter transação SQLite aberta enquanto chama BigBang Essentials;
- nunca manter transação SQLite aberta enquanto espera chunk carregar;
- nunca bloquear tick aguardando IO.
```

---

## 9.2 Coordinator

Criar ou consolidar:

```txt
TerrainAllocationCoordinator
```

Responsabilidades:

```txt
- receber LandOperation;
- controlar avanço de estado;
- limitar candidatos processados por tick;
- chamar busca de slot;
- chamar sampling de biome;
- solicitar preparação de chunks;
- validar safe spawn;
- encaminhar para saga de pagamento;
- reagendar retries;
- reconciliar operações no boot;
- manter métricas de operação.
```

A coordenação precisa ser idempotente.

Chamar `tick()` apenas a partir de um ponto central, por exemplo evento de fim de tick do servidor.

Não criar um scheduler por operação.

---

# 10. Saga de pagamento

## 10.1 Regra de persistência antes de chamada externa

Antes de chamar qualquer operação externa do BigBang Essentials, o Regions deve persistir a intenção.

Exemplo para reserve:

```txt
1. Persistir state = PAYMENT_RESERVE_PENDING.
2. Persistir reserveIdempotencyKey.
3. Persistir preço.
4. Commit SQLite.
5. Chamar gateway.reserve(...).
6. Persistir reservationId e state = PAYMENT_RESERVED.
```

Se ocorrer crash entre 5 e 6:

```txt
- no boot, a operação continua PAYMENT_RESERVE_PENDING;
- Regions repete reserve usando a mesma idempotency key;
- Essentials retorna a mesma reserva sem duplicar retenção.
```

Aplicar exatamente a mesma regra para:

```txt
renew
capture
release
```

---

## 10.2 Reserve

Pré-condições:

```txt
- slot reservado;
- preço persistido;
- Essentials disponível, quando price_gems > 0;
- owner válido;
- operação ainda anterior à criação física.
```

Fluxo:

```txt
SLOT_RESERVED
→ PAYMENT_RESERVE_PENDING
→ gateway.reserve()
→ PAYMENT_RESERVED
→ TERRAIN_PREPARING
```

Se saldo insuficiente:

```txt
- operation = CANCELLED_BEFORE_REGION_CREATION;
- slot é liberado;
- player recebe mensagem clara;
- não criar região;
- não registrar como falha interna.
```

Se falha transitória:

```txt
- manter PAYMENT_RESERVE_PENDING;
- registrar next_retry_at;
- retry com mesma key;
- após limite de retry, BLOCKED_FOR_MANUAL_RECONCILIATION.
```

---

## 10.3 Renew

Enquanto a operação estiver anterior à fronteira irreversível e possuir reserva ativa:

```txt
- verificar lease;
- renovar antes do vencimento;
- persistir renew sequence;
- persistir renew idempotency key;
- repetir renew com mesma key se houver crash;
- não usar renew após capture;
- não criar região sem reserva ativa.
```

Imediatamente antes da transação que cria a região:

```txt
- verificar se a reserva ainda está válida;
- tentar renew se estiver perto do vencimento;
- somente avançar para REGION_CREATING se a reserva estiver ativa.
```

---

## 10.4 Criação da região

A criação deve acontecer depois de:

```txt
- slot reservado;
- bioma aprovado;
- chunks preparados;
- spawn seguro aprovado;
- pagamento reservado, quando preço > 0;
- última validação de lease feita.
```

A transação de criação deve persistir:

```txt
- região;
- owner;
- home;
- slot OCCUPIED;
- operation.region_id;
- operation.home;
- operation.state = REGION_CREATED_PAYMENT_CAPTURE_PENDING;
- region lifecycle = PENDING_PAYMENT, caso seja necessário para impedir uso antes de capture.
```

A região criada durante capture pending deve:

```txt
- ser protegida;
- não ser reutilizável;
- não ser removida automaticamente;
- não permitir exploração ou uso pelo player antes de pagamento capturado;
- não revelar coordenadas finais ao player;
- permitir somente administração e recuperação interna.
```

Após capture com sucesso:

```txt
- ativar região para owner;
- atualizar lifecycle para ACTIVE;
- state = COMPLETED;
- registrar auditoria;
- enviar mensagem ao player;
- teleportar o player ao home seguro.
```

Não criar a região duas vezes em retries ou recovery.

---

## 10.5 Capture

Fluxo:

```txt
REGION_CREATED_PAYMENT_CAPTURE_PENDING
→ persistir captureIdempotencyKey, se ainda não houver
→ gateway.capture()
→ COMPLETED
```

Em caso de falha:

```txt
- manter REGION_CREATED_PAYMENT_CAPTURE_PENDING;
- nunca chamar release;
- nunca excluir região;
- nunca liberar slot;
- retry com a mesma capture idempotency key;
- aplicar backoff;
- depois do limite, BLOCKED_FOR_MANUAL_RECONCILIATION.
```

Caso a reserva tenha vencido antes da captura por downtime muito longo:

```txt
- não tentar cobrar por outro caminho;
- não criar nova reserva automaticamente;
- não liberar a região;
- state = BLOCKED_FOR_MANUAL_RECONCILIATION;
- registrar motivo claro;
- alertar admins.
```

---

## 10.6 Release

Release só pode ocorrer antes da fronteira irreversível.

Estados permitidos:

```txt
PAYMENT_RESERVE_PENDING
PAYMENT_RESERVED
TERRAIN_PREPARING
REGION_CREATING, somente se região ainda não foi confirmada no banco.
RELEASE_PENDING
```

Fluxo:

```txt
→ persistir RELEASE_PENDING e releaseIdempotencyKey
→ gateway.release()
→ confirmar cancelamento
→ liberar slot
→ CANCELLED_BEFORE_REGION_CREATION
```

Estados proibidos para release:

```txt
REGION_CREATED_PAYMENT_CAPTURE_PENDING
COMPLETED
BLOCKED_FOR_MANUAL_RECONCILIATION após criação física
```

---

# 11. Preparação de terreno e spawn seguro

## 11.1 Sem pregen massivo

Não usar:

```txt
- Chunky;
- pré-geração completa;
- geração de milhares de chunks;
- loops síncronos de chunk;
- alterações visuais permanentes no terreno;
- paredes;
- blocos de borda;
- estruturas artificiais.
```

Apenas carregar os chunks mínimos necessários para:

```txt
- validar bioma;
- validar área;
- encontrar spawn;
- criar home.
```

---

## 11.2 Chunk loading

Usar o mecanismo compatível com a versão atual para solicitar chunks/tickets.

Aguardar de forma não bloqueante.

Configuração sugerida:

```txt
terrain:
  chunk_ticket_radius: 1
  chunk_prepare_timeout_seconds: 30
```

Ao expirar timeout antes de criar a região:

```txt
- cancelar de forma segura;
- liberar pagamento, quando houver;
- liberar slot;
- registrar erro;
- informar player.
```

---

## 11.3 Safe spawn

Criar serviço:

```txt
SafeSpawnFinder
```

Critérios mínimos:

```txt
- bloco abaixo sólido e seguro;
- posição dos pés livre;
- posição da cabeça livre;
- não estar em líquido;
- não estar sobre magma;
- não estar sobre fogo;
- não estar sobre cactus;
- não estar sobre campfire;
- não estar em folha instável, neve em pó ou bloco perigoso;
- não estar abaixo de altura insegura;
- não estar em posição sufocante;
- preferir posição próxima ao centro do claim.
```

Usar heightmaps e block checks reais da versão atual.

Não inventar APIs.

Persistir:

```txt
x
y
z
yaw
pitch
dimension
```

---

# 12. Comandos

Implementar comandos sem GUI.

## 12.1 Player

```txt
/regiao biomas
```

Mostra biomas permitidos e aliases válidos.

```txt
/regiao criar <bioma>
```

Cria uma LandOperation.

Deve validar:

```txt
- permissão;
- bioma permitido;
- player não possui região ativa;
- player não possui operação ativa;
- Essentials disponível se preço > 0.
```

Responder imediatamente sem travar:

```txt
"Seu terreno está sendo localizado. Use /regiao criar status para acompanhar."
```

```txt
/regiao criar status
```

Exibir:

```txt
- estado atual;
- bioma;
- pagamento requerido ou não;
- preço, quando aplicável;
- etapa atual;
- mensagem segura de erro;
- sem coordenadas antes de COMPLETED.
```

```txt
/regiao criar cancelar
```

Só permitir cancelamento antes da fronteira irreversível.

Após criação física:

```txt
"Esta operação já criou a região e não pode ser cancelada automaticamente. Um administrador precisa verificar a operação."
```

```txt
/regiao casa
```

Nesta fase, só funcionar para região ativa e COMPLETED.

Nunca teleportar para região `PENDING_PAYMENT`.

---

## 12.2 Administração

```txt
/regions allocation list
/regions allocation inspect <operationId>
/regions allocation retry <operationId>
/regions allocation cancel <operationId> confirm
/regions allocation resolve <operationId> <capture|block> confirm
/regions allocation reconcile
/regions slot inspect <slotId>
```

Regras:

```txt
- comandos destrutivos exigem confirm literal;
- nunca oferecer release forçado após criação física;
- retry de capture deve usar mesma key;
- retry de reserve deve usar mesma key;
- logs de auditoria devem registrar admin UUID, operação e ação;
- mensagens devem explicar claramente se a região já cruzou a fronteira irreversível.
```

Permissões sugeridas:

```txt
bigbangregions.land.create
bigbangregions.land.home
bigbangregions.land.cancel
bigbangregions.allocation.admin
bigbangregions.allocation.reconcile
bigbangregions.slot.inspect
```

Adaptar ao sistema de permissões existente.

---

# 13. Proteção de região durante pagamento pendente

Criar status de lifecycle para região, se ainda não existir:

```txt
PENDING_PAYMENT
ACTIVE
```

Enquanto estiver `PENDING_PAYMENT`:

```txt
- ninguém além de admins/sistema pode construir;
- ninguém além de admins/sistema pode interagir;
- owner ainda não recebe home;
- a região não aparece em comandos públicos;
- não aparece no JourneyMap, que ainda não será implementado;
- não deve expor coordenadas para o player.
```

Depois do capture:

```txt
PENDING_PAYMENT → ACTIVE
```

A mudança deve atualizar cache apenas após commit SQLite.

---

# 14. Recovery no boot

Implementar:

```txt
LandOperationRecoveryService
```

No boot, antes de aceitar novas alocações:

```txt
1. carregar operações não-terminais;
2. reconstruir vínculo com slot, região e home;
3. identificar leases vencidos;
4. reconciliar chamadas externas com a mesma idempotency key;
5. retomar busca ou preparação apenas quando seguro;
6. capturar pagamentos pendentes após região criada;
7. não criar região duplicada;
8. não liberar reserva após criação física;
9. não reutilizar slots ocupados ou retired;
10. reconstruir cache de regiões somente depois de consistência confirmada.
```

Cenários críticos:

```txt
Crash após slot reservado antes do reserve Gems
→ continuar PAYMENT_RESERVE_PENDING.

Crash após reserve Gems antes de persistir reservationId
→ repetir reserve com mesma idempotency key.

Crash após reserva de Gems antes de preparar chunks
→ retomar preparação.

Crash após preparar chunks antes de criar região
→ revalidar lease e criar uma vez.

Crash durante transação de criação de região
→ SQLite decide tudo ou nada.

Crash após região criada antes do capture
→ somente capture retry.

Crash após capture antes de COMPLETED
→ repetir capture com mesma key.

Crash após release antes de liberar slot
→ repetir release e então liberar slot.

Crash após região criada e reserva vencida
→ BLOCKED_FOR_MANUAL_RECONCILIATION.
```

---

# 15. Testes obrigatórios

Criar testes unitários e de integração compatíveis com a estrutura atual.

## 15.1 Core sem Essentials instalado

Usar `FakeLandPaymentGateway` para testar todo o core sem dependência do Essentials.

Cobrir:

```txt
- operação gratuita;
- reserva bem-sucedida;
- saldo insuficiente;
- erro transitório de reserve;
- retry idempotente;
- renew;
- release anterior à criação;
- capture após criação;
- erro persistente de capture;
- bloqueio manual;
- recovery.
```

---

## 15.2 Integração com adapter

Quando o ambiente de build permitir usar BigBang Essentials como dependência de desenvolvimento:

```txt
- validar bootstrap do adapter;
- validar mod ausente;
- validar gateway indisponível;
- validar request fields enviados;
- validar idempotency keys determinísticas;
- validar source/purpose/externalReference;
- validar mapeamento de erros.
```

Não criar mocks de classes internas do Essentials.

---

## 15.3 Concorrência

Cobrir pelo menos:

```txt
- duas operações do mesmo owner;
- dois owners tentando mesmo slot;
- cancelamento e recovery concorrentes;
- retry de reserve concorrente;
- retry de capture concorrente;
- boot recovery e tick coordinator não duplicando trabalho;
- cache não sendo atualizado antes de commit.
```

---

## 15.4 Geometria e busca

Cobrir:

```txt
- claim exato de 50x50;
- claim centralizado em slot 256x256;
- slot não intersecta central zone + buffer;
- slot não intersecta região existente;
- 5x5 biome sampling;
- limite de 60%;
- candidato com 14 de 25 amostras é rejeitado;
- candidato com 15 de 25 amostras é aceito;
- safe spawn inválido é rejeitado;
- slot retired não é reutilizado.
```

---

## 15.5 Saga de crash/recovery

Criar testes usando fake gateway com pontos de interrupção controlados.

Cobrir:

```txt
1. crash após persistir PAYMENT_RESERVE_PENDING;
2. crash após reserve remoto e antes de salvar reservationId;
3. crash após PAYMENT_RESERVED;
4. crash antes de criação da região;
5. crash após criação da região e antes de capture;
6. crash após capture remoto e antes de COMPLETED;
7. crash após release remoto e antes de liberar slot;
8. retry com mesma key não duplica reserva;
9. retry com mesma key não duplica capture;
10. pós-criação nunca chama release;
11. região pendente não é ativada para player;
12. operação bloqueada preserva dados para admin.
```

---

# 16. Documentação obrigatória

Criar ou atualizar:

```txt
docs/architecture/land-allocation.md
docs/architecture/land-payment-saga.md
docs/integrations/bigbangessentials-gems.md
docs/operations/land-allocation-recovery.md
docs/commands/regions-land-allocation.md
docs/configuration/land-allocation.md
docs/reviews/terrain-payment-saga-acceptance.md
```

## 16.1 Documento de integração Gems

Explicar:

```txt
- adapter isolado;
- não leitura de arquivos internos;
- diferença entre reserve, renew, capture e release;
- idempotency keys;
- source/purpose;
- externalReference;
- fronteira irreversível;
- PAYMENT_CAPTURE_PENDING;
- por que release é proibido após criação física;
- como proceder em BLOCKED_FOR_MANUAL_RECONCILIATION.
```

---

## 16.2 Documento de operação

Explicar como administradores devem:

```txt
- consultar operação;
- identificar pagamento pendente;
- identificar reserva vencida;
- disparar retry seguro;
- quando não cancelar;
- quando bloquear operação;
- quais dados coletar antes de intervenção;
- como ler auditoria.
```

Nunca sugerir editar SQLite ou arquivos Gems manualmente como fluxo normal.

---

# 17. Commits obrigatórios

Faça commit após cada módulo concluído.

Ordem esperada:

```txt
1. feat: add persistent land operation and plot slot schema
2. feat: add transactional plot slot reservation and terrain search
3. feat: add biome validation and safe spawn preparation
4. feat: add land payment gateway abstraction
5. feat: add BigBang Essentials gems payment adapter
6. feat: add crash-safe land payment saga recovery
7. feat: add player and admin land allocation commands
8. test: cover terrain allocation payment and recovery scenarios
9. docs: document terrain allocation and BigBang Essentials integration
```

Regras:

```txt
- cada commit deve compilar;
- cada mudança de comportamento deve ter teste;
- não misturar refactor amplo com funcionalidade;
- não commitar arquivos runtime;
- não commitar mundos;
- não commitar banco SQLite real;
- não commitar logs;
- não commitar credentials;
- não misturar JourneyMap nesta fase.
```

---

# 18. Validação final

Executar ao final:

```bash
git status --short
git diff --check
./gradlew clean test build
```

Também executar qualquer teste específico do projeto para boot de servidor dedicado.

Validar manualmente em servidor de desenvolvimento:

```txt
1. Essentials ausente e preço > 0:
   /regiao criar deve bloquear sem reservar slot permanentemente.

2. Essentials presente e preço = 0:
   criar região sem pagamento.

3. Essentials presente e preço > 0:
   reserve → região → capture → teleporte.

4. Saldo insuficiente:
   não criar região e liberar slot.

5. Cancelamento antes da região:
   release e slot liberado.

6. Crash simulado após region create:
   recovery faz capture sem criar segunda região.

7. Capture falhando:
   região permanece pending, sem release automático.

8. Admin retry:
   usa mesma operação e mesma idempotency key.

9. Região completed:
   /regiao casa funciona.

10. Região pending payment:
   player não recebe coordenadas nem home.
```

---

# 19. Relatório final obrigatório

Criar:

```txt
docs/reviews/terrain-payment-saga-acceptance.md
```

Estrutura mínima:

```txt
# Terrain Allocation and Gems Payment Saga Acceptance

## Baseline
- SHA inicial
- SHA final
- branch
- resultado do build
- total de testes

## Banco e migrations
- migrations criadas
- tabelas
- índices
- estratégia de concorrência

## Alocação
- 50x50
- Plot Slot 256x256
- zona central
- biome sampling
- spawn seguro
- chunk preparation

## Pagamento
- gateway abstraction
- adapter BigBang Essentials
- idempotency keys
- reserve
- renew
- capture
- release
- no direct file access

## Recovery
- estados recuperáveis
- fronteira irreversível
- crash tests
- slots
- region pending payment
- manual reconciliation

## Comandos
- player
- admin
- permissões

## Verificações
- build
- testes
- servidor dedicado
- smoke tests

## Veredito
READY_FOR_INDEPENDENT_REVIEW
ou
BLOCKED
```

Nunca declarar aprovação independente por conta própria.

---

# 20. Entrega final esperada

Ao concluir, responda com:

```txt
1. SHA final.
2. Lista de commits.
3. Migrations criadas.
4. Arquivos principais alterados.
5. Resultado de ./gradlew clean test build.
6. Total de testes.
7. Resultado dos testes de crash/recovery.
8. Resultado do boot de servidor dedicado.
9. Resultado do smoke test de pagamento.
10. Estado final do worktree.
11. Conteúdo resumido de docs/reviews/terrain-payment-saga-acceptance.md.
12. Limitações restantes e motivos.
```

Não iniciar JourneyMap, visualização de bordas, partículas, presença de região, expansão pública ou mapa nesta fase.
