# ADR 0003: SQLite e Cache de Memória Otimizado

## Contexto
O Minecraft é extremamente sensível a latência de ticks (TPS). Executar consultas SQL diretas no banco de dados durante cliques, quebras de bloco ou movimentos de jogadores (hot paths) causaria gargalos inaceitáveis e travamentos no servidor.

## Decisão
1. Utilizar **SQLite** local como banco de dados relacional simples e confiável para armazenamento persistente das regiões.
2. Carregar todas as regiões em memória (`RegionCache`) durante o boot do servidor.
3. Utilizar um **Índice Espacial baseado em Chunks** para associar cada chunk da dimensão com a lista de regiões que o cortam. Apenas os blocos dentro das regiões candidatas daquele chunk são testados com bounding boxes exatas, garantindo consultas espaciais O(1).
4. Operações de persistência e escrita de logs de auditoria são delegadas para uma thread pool assíncrona dedicada (`AuditService`).

## Consequências
* **Positivas**:
  * Impacto de tick virtualmente zero (TPS estável).
  * Otimização para centenas de regiões ativas ao mesmo tempo.
* **Negativas**:
  * O uso de memória RAM aumenta linearmente com a quantidade de regiões (impacto desprezível para o tamanho típico de regiões ativas em servidores Minecraft).
