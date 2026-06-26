# Plano de Teste Manual

Siga estes passos em um servidor de testes de desenvolvimento para validar o comportamento do mod.

## Requisitos de Ambiente
* Servidor Fabric 1.21.1 com o mod instalado.
* Pelo menos dois jogadores logados:
  * **Jogador Admin**: Operador (OP level 4) ou com permissões adequadas.
  * **Jogador Teste**: Sem privilégios (vanilla/visitante padrão).

---

## Roteiro de Testes

### 1. Seleção e Criação de Região
1. Com o **Jogador Admin**, vá até o ponto A do spawn e digite `/regions pos1`.
2. Vá até o ponto B (diagonal oposta, formando um cubo de 15x15x15) e digite `/regions pos2`.
3. Digite `/regions create admin spawn_area 1000`.
4. *Resultado Esperado:* Mensagem de sucesso na tela e a região `spawn_area` criada.

### 2. Validação da Flag `player-build`
1. Vá até a área criada com o **Jogador Teste**.
2. Tente quebrar um bloco de grama.
   * *Resultado Esperado:* O bloco não quebra e exibe "Você não pode construir nesta região (spawn_area)" na action bar.
3. Tente colocar um bloco de terra.
   * *Resultado Esperado:* O bloco é bloqueado e exibe a mensagem de restrição.

### 3. Validação da Flag `container-access`
1. Coloque um Baú (Chest) dentro da região.
2. Peça para o **Jogador Teste** tentar abrir o baú.
   * *Resultado Esperado:* O baú não abre e exibe "Você não pode acessar este container (spawn_area)" na action bar.
3. Repita o teste com um Barril (Barrel) e uma Fornalha (Furnace).
   * *Resultado Esperado:* Ambos são bloqueados.

### 4. Validação da Flag `door-use`
1. Coloque uma porta de carvalho na região.
2. O **Jogador Teste** tenta abrir a porta.
   * *Resultado Esperado:* A porta não abre e exibe a mensagem de restrição.

### 5. Validação da Flag `redstone-use`
1. Coloque uma alavanca e um botão de pedra.
2. O **Jogador Teste** tenta clicar neles.
   * *Resultado Esperado:* Bloqueio com mensagem correspondente.
3. Coloque uma placa de pressão e faça o **Jogador Teste** andar sobre ela.
   * *Resultado Esperado:* A placa não ativa e exibe a mensagem.

### 6. Validação da Flag `entity-interact`
1. Coloque um Armor Stand ou uma Moldura (Item Frame) dentro da região.
2. O **Jogador Teste** tenta interagir ou bater neles.
   * *Resultado Esperado:* Protegido com mensagem correspondente.

### 7. Validação da Flag `pvp`
1. Com ambos os jogadores dentro da região protegida, o **Jogador Admin** tenta atacar o **Jogador Teste**.
   * *Resultado Esperado:* Nenhum dano é causado, e a mensagem de PvP desativado é exibida.
2. Tente atacar utilizando um arco e flecha de fora para dentro da região.
   * *Resultado Esperado:* O dano é bloqueado.

### 8. Validação de Bypass Administrativo
1. O **Jogador Admin** tenta quebrar um bloco ou abrir um baú na região `spawn_area`.
   * *Resultado Esperado:* Ação permitida normalmente, pois administradores possuem bypass por padrão.

### 9. Validação de Precedência de Flags
1. Em uma região com `player-interact = DENY` (padrão de Admin), configure a flag de baús: `/regions flag set spawn_area container-access ALLOW`.
2. O **Jogador Teste** (visitante) tenta abrir o baú e o barril.
   * *Resultado Esperado:* Abertura permitida (a flag específica `ALLOW` tem precedência sobre a flag geral `player-interact = DENY`).
3. O **Jogador Teste** tenta interagir com a Crafting Table.
   * *Resultado Esperado:* Bloqueado (pois não há flag específica e cai no fallback `player-interact = DENY`).
4. Configure `/regions flag set spawn_area container-access DENY` e `/regions flag set spawn_area player-interact ALLOW`.
5. O **Jogador Teste** tenta abrir o baú.
   * *Resultado Esperado:* Bloqueado (pois a flag específica `container-access = DENY` bloqueia mesmo se a geral `player-interact` estiver `ALLOW`).

### 10. Validação de Restrição de Dimensões na Seleção
1. Com o **Jogador Admin** no Overworld, vá a um bloco e execute `/regions pos1`.
2. Vá para o Nether, selecione um bloco e execute `/regions pos2`.
3. Tente criar uma região: `/regions create admin dim_test`.
   * *Resultado Esperado:* Falha na criação com a mensagem "A Posição 1 e a Posição 2 devem estar na mesma dimensão." Nenhuma região é gravada no banco, carregada em cache ou registrada na auditoria.
4. Volte ao Overworld e marque `pos1` e `pos2` no Overworld.
5. Vá para o Nether e execute `/regions create admin ovw_test 1000`.
   * *Resultado Esperado:* Sucesso na criação. O comando deve registrar que a região `ovw_test` foi criada no Overworld (usando a dimensão em que os blocos foram marcados).

### 11. Persistência
1. Reinicie o servidor (`/stop` e reinicie).
2. Execute `/regions info` dentro da região `ovw_test`.
   * *Resultado Esperado:* A dimensão reportada nos limites da região deve continuar como Overworld e as flags devem persistir exatamente com os mesmos valores antes da reinicialização.

---

## 12. Testes de PLAYER_REGION (Fase 2A)

### Ambiente Adicional Necessário
* **Dono**: Um jogador comum (por exemplo, `DonoTest`).
* **Líder / Membro**: Outros jogadores comuns.
* **Visitante**: Um jogador sem relação com o terreno.

### Roteiro de Testes

#### 12.1 Criação e Acesso Inicial
1. Defina uma seleção e execute como Admin: `/regions create player meu_terreno DonoTest`
2. Vá até o terreno como **DonoTest** e execute `/regiao info`.
   * *Resultado Esperado:* Mostra ID `meu_terreno`, Tipo `PLAYER_REGION`, dono `DonoTest`, e o papel atual dele (`OWNER`).
3. Tente construir e quebrar blocos como **DonoTest**.
   * *Resultado Esperado:* Permitido.
4. Tente construir e quebrar blocos como **Visitante**.
   * *Resultado Esperado:* Bloqueado (mensagem na action bar).
5. Como **Visitante**, tente abrir baú, usar portas, botões e coletar ou dropar itens.
   * *Resultado Esperado:* Todas as ações são bloqueadas.

#### 12.2 Gestão de Membros e Hierarquia
1. Como **DonoTest**, execute `/regiao membros adicionar JogadorMembro`.
2. Como **JogadorMembro**, tente construir e abrir containers.
   * *Resultado Esperado:* Permitido (pois agora é um membro da região).
3. Como **JogadorMembro**, tente adicionar outro jogador: `/regiao membros adicionar OutroJogador`.
   * *Resultado Esperado:* Bloqueado com erro de falta de permissão/papel inadequado.
4. Como **DonoTest**, promova o membro: `/regiao membros promover JogadorMembro`.
5. Como **JogadorMembro** (agora `LEADER`), adicione um novo membro: `/regiao membros adicionar JogadorMembro2`.
   * *Resultado Esperado:* Permitido.
6. Como **JogadorMembro** (`LEADER`), tente promover `JogadorMembro2` para `LEADER` ou remover o dono (`DonoTest`).
   * *Resultado Esperado:* Bloqueado pela validação de hierarquia do serviço de membros.
7. Como **DonoTest**, rebaixe o líder: `/regiao membros rebaixar JogadorMembro`.
8. Como **JogadorMembro** (agora `MEMBER`), execute `/regiao sair`.
   * *Resultado Esperado:* O jogador sai voluntariamente e perde todas as permissões no terreno (volta a ser `VISITOR`).

#### 12.3 Interação de Flags e Papéis
1. Como **DonoTest**, altere a flag `player-build` para `DENY`: `/regiao flags definir player-build DENY`.
2. Tente construir como **DonoTest**, líderes ou membros restantes.
   * *Resultado Esperado:* Bloqueado para todos eles (DENY na flag se sobrepõe a qualquer papel).
3. Como **DonoTest**, retorne a flag para `ALLOW`: `/regiao flags definir player-build ALLOW`.
   * *Resultado Esperado:* Todos os membros e donos conseguem construir novamente, mas o **Visitante** continua bloqueado (a política de papel do visitante nega build).

#### 12.4 Sobreposição de ADMIN_REGION
1. Crie uma `ADMIN_REGION` que se sobreponha ao `meu_terreno` (ex: `spawn_teste`).
2. Tente quebrar ou colocar blocos na área de sobreposição como **DonoTest**.
   * *Resultado Esperado:* Bloqueado (a região administrativa continua vencendo e o ownership da região de jogador não gera bypass).
3. Tente quebrar ou colocar blocos na mesma área com bypass administrativo.
   * *Resultado Esperado:* Permitido.

#### 12.5 Persistência de Memberships
1. Reinicie o servidor.
2. Reconecte e execute `/regiao info` no terreno.
   * *Resultado Esperado:* Toda a estrutura de cargos, flags e membros continua salva e ativa no SQLite e cache de memória.

