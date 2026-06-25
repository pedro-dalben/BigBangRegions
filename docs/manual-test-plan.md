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

### 9. Persistência
1. Reinicie o servidor (`/stop` e reinicie).
2. Tente realizar as interações bloqueadas novamente com o **Jogador Teste**.
   * *Resultado Esperado:* Todas as restrições devem persistir e a base SQLite carregar as regiões com sucesso.
