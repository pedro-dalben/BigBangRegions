# Changelog

## [1.0.0] - 2026-06-25

### Added
* Fundação completa do mod **BigBang Regions** para Fabric 1.21.1.
* Suporte completo para regiões administrativas cubóides com priorização e resolução determinística.
* Motor de flags com suporte para ALLOW, DENY e INHERIT.
* Persistência de dados utilizando SQLite (`config/bigbangregions/regions.db`) com migração de schema automática.
* Índice espacial de cache por Chunk para otimização de consultas espaciais O(1).
* Implementação das seguintes flags de proteção:
  - `player-build` (quebra e colocação de blocos).
  - `player-interact` (interação geral com blocos).
  - `container-access` (baús, fornalhas, inventários modded).
  - `door-use` (portas, alçapões, portões).
  - `redstone-use` (alavancas, botões, placas de pressão via mixin).
  - `entity-interact` (interações com armor stands, item frames, montarias).
  - `pvp` (combate melee e à distância entre jogadores).
  - `item-pickup` (coleta de itens no chão).
  - `item-drop` (arremessar itens no chão).
* Sistema de seleção de coordenadas em memória (`/regions pos1` e `/regions pos2`).
* Comandos completos `/regions` com suporte a aliases em português (`/regiao` e `/regioes`).
* Registros de auditoria locais (`CREATE_REGION`, `DELETE_REGION`, `SET_FLAG`, `RELOAD`).
* Testes de integração de banco de dados e testes unitários cobrindo o core de prioridades, colisão e resolução de flags.
* Documentação técnica detalhada das decisões arquiteturais (ADRs) e matriz de compatibilidade.

### Fixed during Audit Phase
* **PlayerMixin.onDrop Signature**: Fixed invalid method arguments signature for `Player.drop` in Minecraft 1.21.1 mappings to prevent Knots classloading transformation errors on Dedicated Servers.
* **Database Thread-Safety**: All database connection requests and query executions in `RegionRepository` and `AuditRepository` are now synchronized to prevent concurrency locks/failures during async audit logging.
* **Clean Lifecycle Management**: Properly registered a `SERVER_STOPPING` callback to safely flush async log queues and close the SQLite connection before shutdown.
* **Drop Prevention Security**: Enhanced drop cancellation logic to prevent any visual duplication (ghost items) or item loss when the inventory is full by updating the carried slots and broadcasting container changes.
