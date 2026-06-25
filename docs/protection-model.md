# Modelo de Proteção e Resolução

O motor de proteção do BigBang Regions atua sobre um fluxo de resolução rígido e determinístico.

## Ordem de Resolução de Proteção

Quando um evento é interceptado, o `ProtectionService` executa as seguintes verificações na ordem exata:

1. **Bypass Administrativo**:
   * O mod verifica se o jogador atacante possui a permissão `bigbangregions.bypass` ou `bigbangregions.bypass.<flag>`.
   * Se sim, a ação é permitida imediatamente como `BYPASS`.
2. **Resolução de Região**:
   * A coordenada alvo é consultada no `RegionResolver`.
   * Se nenhuma região cobrir a coordenada, a ação é avaliada com base nas políticas globais do servidor (`NO_REGION`).
3. **Validação de Atores**:
   * Atores do tipo `UNKNOWN` que realizam ações destrutivas (`BLOCK_BREAK`, `BLOCK_PLACE`, `PVP`) dentro de regiões protegidas são negados automaticamente.
4. **Membro da Região**:
   * Se o ator for um jogador e a ação for classificada como de membro (todas exceto `PVP`), o mod verifica o papel do jogador na região.
   * Se ele for `MEMBER`, `LEADER` ou `OWNER`, a ação é permitida como `ALLOW` ("Player is member of the region").
5. **Cascata de Flags**:
   * Caso o jogador seja um `VISITOR`, o `FlagResolver` entra em ação para avaliar as regras:
     1. **Valor Explícito**: Procura por ALLOW/DENY explicitamente salvos na região.
     2. **Padrão do Tipo de Região**: Configuração default do tipo de região (ex: `adminRegion` em `config.json`).
     3. **Política Global**: Configuração global padrão da dimensão/servidor em `config.json`.
     4. **Padrão do Mod**: Fallback estático definido no código.
