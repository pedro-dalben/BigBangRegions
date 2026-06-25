# ADR 0004: Resolução de Prioridades de Região

## Contexto
Em servidores complexos, regiões frequentemente se sobrepõem. Por exemplo, uma "Arena PvP" administrativa pode estar dentro de uma região maior de "Spawn" que bloqueia o PvP. O sistema precisa decidir qual região é a "região efetiva" de forma previsível e determinística.

## Decisão
Implementar um resolvedor único (`RegionResolver`) que avalia todas as regiões cobrindo uma coordenada com base nas seguintes regras determinísticas ordenadas:
1. **Prioridade Maior**: A região com maior valor numérico de prioridade vence.
2. **Menor Volume**: Em caso de empate de prioridade, a região com menor volume em blocos (mais específica) vence.
3. **ID Alfabético**: Em caso de novo empate, a região com menor ID alfabético (critério determinístico arbitrário) vence.

## Consequências
* **Positivas**:
  * Comportamento extremamente previsível e livre de empates aleatórios.
  * Facilidade para sobrepor áreas específicas (ex: arenas PvP) dentro de grandes áreas seguras (ex: spawns públicos).
* **Negativas**:
  * Nenhuma conhecida.
