# Cobertura de Proteção

Este documento detalha o que está coberto de fato pelo motor de proteção do BigBang Regions na Fase 1.

## Cobertura Garantida

* **Modificações do Terreno (Jogadores)**:
  * Quebra de blocos (sobrevivência ou criativo).
  * Colocação de blocos e uso de baldes (água, lava).
* **Interações e Equipamentos**:
  * Abertura de baús, baús de extremidade, barris e caixas shulker.
  * Interação com componentes de fornalhas, defumadores, alto-fornos, suportes de poções.
  * Uso de alavancas, botões, repetidores e comparadores.
  * Placas de pressão de madeira, pedra e metais (bloqueadas para jogadores não autorizados).
  * Abertura e fechamento de portas, alçapões e portões de cerca.
* **Segurança de Entidades**:
  * Interações e danos a Armor Stands, Item Frames, barcos e carrinhos de mina (Minecarts).
* **Segurança de Jogadores (PvP)**:
  * Ataques corpo a corpo (melee).
  * Ataques à distância (projéteis de arco, besta e poções arremessáveis).
* **Itens**:
  * Coleta de itens do chão.
  * Jogar itens no chão (retorna ao inventário se for bloqueado).

## Cobertura Parcial (Expansões Futuras)

* **Automações**:
  * Máquinas automáticas vanilla (Pistons, Dispensers, Hoppers) ainda não são bloqueadas para evitar sobrecarga de processamento por tick sem adapters específicos.
* **Danos Ambientais**:
  * Dano de explosão de TNT/Creeper e alastramento de fogo ainda usam comportamento vanilla (configurável futuramente pelas flags planejadas).

## Caminhos de Bypass Conhecidos e Prevenidos

* **Bypass de Borda**:
  * Resolvido checando o bloco a ser colocado (ao lado do bloco clicado) e não apenas o bloco clicado.
* **Bypass de Ataque de Projéteis**:
  * Resolvido mixando em `Player.hurt` e rastreando a causa real (através de `DamageSource.getEntity()`).
* **Bypass de Perda de Item no Drop**:
  * Resolvido devolvendo o item cancelado ao inventário do jogador ao invés de apenas cancelar o evento (o que poderia sumir com o item).
