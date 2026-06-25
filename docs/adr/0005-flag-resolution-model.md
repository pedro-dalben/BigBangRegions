# ADR 0005: Modelo de Resolução de Flags

## Contexto
O valor de uma flag em uma coordenada pode depender de heranças e padrões. Administradores precisam de flexibilidade para definir regras globais para todo o servidor, regras específicas para tipos de regiões (como spawn admin vs terreno de jogador) e overrides específicos para uma única região.

## Decisão
Implementar um modelo de flags tipado e extensível baseado em três valores políticos: `ALLOW` (permitido), `DENY` (negado) e `INHERIT` (herdar).
A cascata de resolução final de uma flag segue a seguinte ordem de precedência:
1. **Bypass administrativo** ativo do jogador.
2. **Valor explícito** da flag configurado na região efetiva (se diferente de INHERIT).
3. **Valor padrão configurado para o tipo de região** (ex: `defaults.adminRegion` no config.json).
4. **Valor padrão configurado globalmente** para a dimensão (ex: `defaults.global` no config.json).
5. **Valor padrão de fallback estático** registrado para a flag no código (definido na inicialização da flag).

## Consequências
* **Positivas**:
  * Flexibilidade máxima de configuração com mínimo esforço de manutenção.
  * O padrão `INHERIT` evita a duplicação de dados salvos na base de dados SQLite.
* **Negativas**:
  * A resolução requer a consulta do arquivo de configurações, o que foi resolvido mantendo a configuração também em cache na memória (`ConfigManager`).
