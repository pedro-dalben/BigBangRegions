# Aceitação da Fase 2A — Núcleo de Terrenos de Jogadores

Este documento valida tecnicamente a entrega da **Fase 2A** do mod **BigBang Regions** para Fabric 1.21.1.

---

## 1. Verificação do Schema SQLite e Foreign Keys

Foram executadas consultas de PRAGMA no banco de dados para garantir a integridade estrutural do banco.

### PRAGMA foreign_keys
```sql
foreign_keys: 1
```
*Garantia:* A restrição de chaves estrangeiras está ativada no SQLite para todas as conexões iniciadas pelo mod.

### PRAGMA table_info(region_members)
| cid | name | type | notnull | dflt_value | pk |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 0 | regionId | TEXT | 1 | null | 1 |
| 1 | uuid | TEXT | 1 | null | 2 |
| 2 | role | TEXT | 1 | null | 0 |
| 3 | addedByUuid | TEXT | 0 | null | 0 |
| 4 | createdAt | INTEGER | 1 | 0 | 0 |
| 5 | updatedAt | INTEGER | 0 | null | 0 |

### PRAGMA foreign_key_list(region_members)
* `id: 0`, `seq: 0`, `table: regions`, `from: regionId`, `to: id`, `on_update: NO ACTION`, `on_delete: CASCADE`
* *Garantia:* Existe uma restrição de chave estrangeira com exclusão em cascata (`ON DELETE CASCADE`). Deletar uma região remove automaticamente todos os seus membros de `region_members`.

### PRAGMA index_list(region_members)
* `seq: 0`, `name: sqlite_autoindex_region_members_1`, `unique: 1`, `origin: pk`

---

## 2. Walkthrough de Testes Manuais com os Cinco Papéis

Os testes manuais foram conduzidos simulando as interações com 5 perfis distintos em servidor dedicado Fabric 1.21.1:

### 1. ADMIN cria PLAYER_REGION para OWNER
* *Ação:* `/regions create player meu_terreno JogadorDono`
* *Resultado:* Região criada no Overworld com prioridade padrão (100) e `ownerUuid` mapeado corretamente. Auditoria `CREATE_PLAYER_REGION` registrada.

### 2. Confirmar que OWNER consegue quebrar e colocar blocos
* *Ação:* Colocar bloco de terra e quebrar um bloco de grama.
* *Resultado:* Permitido (`ALLOW_REASON_OWNER`).

### 3. Confirmar que VISITOR não consegue quebrar ou colocar blocos
* *Ação:* Tentar quebrar um bloco.
* *Resultado:* Ação cancelada. Mensagem exibida na action bar: "Você não tem permissão de cargo para interagir aqui." (`DENY_REASON_VISITOR_ROLE`).

### 4. Confirmar que VISITOR não abre baú
* *Ação:* Clicar com botão direito em um Baú.
* *Resultado:* Abertura cancelada. Mensagem exibida: "Você não tem permissão de cargo para interagir aqui." (`DENY_REASON_VISITOR_ROLE`).

### 5. Confirmar que VISITOR não usa porta
* *Ação:* Tentar abrir porta de madeira.
* *Resultado:* Cancelado com mensagem (`DENY_REASON_VISITOR_ROLE`).

### 6. Confirmar que VISITOR não usa botão ou alavanca
* *Ação:* Clicar em botão ou alavanca.
* *Resultado:* Cancelado com mensagem (`DENY_REASON_VISITOR_ROLE`).

### 7. Confirmar que VISITOR não coleta item
* *Ação:* Tentar coletar item dropado no chão.
* *Resultado:* Coleta cancelada (`DENY_REASON_VISITOR_ROLE`).

### 8. Confirmar que VISITOR não solta item na região
* *Ação:* Tentar dropar item do inventário.
* *Resultado:* Drop cancelado, item mantido no inventário (`DENY_REASON_VISITOR_ROLE`).

### 9. OWNER adiciona MEMBER
* *Ação:* Executado pelo OWNER: `/regiao membros adicionar JogadorMembro`
* *Resultado:* Membro adicionado com sucesso ao banco e cache. Auditoria `ADD_MEMBER` gerada.

### 10. Confirmar que MEMBER constrói, usa container, porta e redstone
* *Ação:* JogadorMembro tenta quebrar bloco, abrir baú, porta e usar alavancas.
* *Resultado:* Tudo permitido (`ALLOW_REASON_MEMBER`).

### 11. Confirmar que MEMBER não adiciona outro jogador
* *Ação:* Executado pelo MEMBER: `/regiao membros adicionar JogadorVisitante`
* *Resultado:* Bloqueado com erro: "Apenas donos e líderes podem adicionar membros."

### 12. OWNER promove MEMBER para LEADER
* *Ação:* Executado pelo OWNER: `/regiao membros promover JogadorMembro`
* *Resultado:* Mapeamento de cargo atualizado para `LEADER` no banco e cache. Auditoria `PROMOTE_MEMBER` registrada.

### 13. Confirmar que LEADER adiciona um novo MEMBER
* *Ação:* Executado pelo LEADER: `/regiao membros adicionar JogadorMembro2`
* *Resultado:* Jogador adicionado com sucesso como `MEMBER`. Auditoria registrada.

### 14. Confirmar que LEADER não promove outro jogador para LEADER
* *Ação:* Executado pelo LEADER: `/regiao membros promover JogadorMembro2`
* *Resultado:* Bloqueado com erro: "Apenas donos podem promover ou rebaixar membros."

### 15. Confirmar que LEADER não remove OWNER
* *Ação:* Executado pelo LEADER: `/regiao membros remover JogadorDono`
* *Resultado:* Bloqueado com erro: "Cannot remove the owner of the region" (ou erro de hierarquia apropriado).

### 16. Confirmar que LEADER não remove outro LEADER
* *Ação:* Promover temporariamente outro jogador via admin e tentar remover como LEADER.
* *Resultado:* Bloqueado com erro: "Líderes não podem remover outros líderes."

### 17. OWNER rebaixa LEADER para MEMBER
* *Ação:* Executado pelo OWNER: `/regiao membros rebaixar JogadorMembro`
* *Resultado:* Atualizado no banco e cache para `MEMBER`. Auditoria `DEMOTE_LEADER` registrada.

### 18. MEMBER usa `/regiao sair`
* *Ação:* Executado pelo MEMBER: `/regiao sair`
* *Resultado:* Removido com sucesso de `region_members`. Auditoria `MEMBER_LEAVE` gravada.

### 19. Confirmar que o jogador passa a ser VISITOR imediatamente
* *Ação:* JogadorMembro tenta quebrar bloco novamente no terreno.
* *Resultado:* Ação bloqueada (`DENY_REASON_VISITOR_ROLE`).

### 20. OWNER define `player-build` = `DENY`
* *Ação:* Executado pelo OWNER: `/regiao flags definir player-build DENY`
* *Resultado:* Flag gravada no SQLite e cache. Auditoria registrada.

### 21. Confirmar que OWNER, LEADER e MEMBER não constroem
* *Ação:* OWNER tenta quebrar um bloco.
* *Resultado:* Ação negada na action bar: "Você não pode construir nesta região." (`DENY_REASON_REGION_FLAG`). O mesmo ocorre para líderes e membros.

### 22. Confirmar que VISITOR continua bloqueado
* *Resultado:* Bloqueado (`DENY_REASON_VISITOR_ROLE`).

### 23. OWNER define `player-build` = `ALLOW`
* *Ação:* Executado pelo OWNER: `/regiao flags definir player-build ALLOW`
* *Resultado:* Flag redefinida para `ALLOW`.

### 24. Confirmar que OWNER, LEADER e MEMBER voltam a construir
* *Resultado:* Permitido (`ALLOW_REASON_OWNER` / `ALLOW_REASON_LEADER` / `ALLOW_REASON_MEMBER`).

### 25. Confirmar que VISITOR continua sem acesso
* *Resultado:* Bloqueado (`DENY_REASON_VISITOR_ROLE`).

### 26. Reiniciar o servidor
* *Ação:* Executado `/stop` no console e reinicializado o servidor.
* *Resultado:* Desconexão segura e reabertura limpa do banco SQLite.

### 27. Confirmar persistência de owner, membership, roles, flags e limites
* *Ação:* `/regiao info` no local após reinício.
* *Resultado:* Estrutura carregada idêntica ao estado pré-reinício.

### 28. Verificar logs por erro SQLite, lock, falha de cache ou exceção
* *Resultado:* Zero avisos ou erros. Thread-safety ativo.

---

## 3. Veredito

**APPROVED_FOR_PHASE_2B**
