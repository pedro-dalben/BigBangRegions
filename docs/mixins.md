# Documentação de Mixins

Para cenários onde a API de eventos padrão do Fabric não fornece interceptadores específicos ou eficientes, o BigBang Regions faz uso de mixins cirúrgicos e documentados.

## Mixins Implementados

### 1. `BasePressurePlateBlockMixin`
* **Classe Alvo:** `net.minecraft.world.level.block.BasePressurePlateBlock`
* **Método Alvo:** `entityInside`
* **Motivo de não usar evento Fabric:** O Fabric API não possui um callback genérico para quando entidades colidem ou andam sobre placas de pressão (ativação física). Interceptar via mixin na base das placas é o método mais limpo e econômico.
* **Comportamento:** Verifica se a entidade colidindo é um jogador e, caso a flag `redstone-use` esteja negada na região, cancela a execução do método de ativação.

### 2. `PlayerMixin`
* **Classe Alvo:** `net.minecraft.world.entity.player.Player`
* **Métodos Alvo:**
  * `hurt(DamageSource source, float amount)`: Intercepta o dano recebido. Rastreia o atacante real a partir da fonte de dano (`DamageSource.getEntity()`). Cancela o dano se o PvP estiver desativado na região do atacante ou do defensor.
  * `drop(ItemStack stack, boolean throwRandomly, boolean retainOwnership)`: Intercepta o drop de itens. Caso o drop seja negado na região atual do jogador, devolve o item imediatamente ao inventário do jogador e retorna `null` para evitar que o item seja instanciado no mundo ou excluído.
* **Motivo de não usar evento Fabric:** O Fabric não fornece eventos de pré-ataque baseados em projéteis (arco, poções) ou eventos canceláveis de drop de itens que previnam a remoção do item de forma segura.

### 3. `ItemEntityMixin`
* **Classe Alvo:** `net.minecraft.world.entity.item.ItemEntity`
* **Método Alvo:** `playerTouch`
* **Motivo de não usar evento Fabric:** O Fabric não expõe um evento pré-coleta de itens cancelável no servidor.
* **Comportamento:** Cancela o método `playerTouch` se a flag `item-pickup` estiver negada na região do item, prevenindo a coleta do item pelo jogador.

### 4. `ExplosionMixin`
* **Classe Alvo:** `net.minecraft.world.level.Explosion`
* **Método Alvo:** `explode`
* **Motivo de não usar evento Fabric:** A API pública do Fabric não expõe um callback estável para filtrar a lista de blocos afetados por explosões vanilla depois que a explosão é calculada.
* **Comportamento:** Intercepta o retorno de `explode()`, usa `getToBlow()` para filtrar blocos dentro de claims protegidas ou na borda da região, e preserva o restante da explosão normalmente.

### 5. `PistonStructureResolverMixin`
* **Classe Alvo:** `net.minecraft.world.level.block.piston.PistonStructureResolver`
* **Método Alvo:** `resolve`
* **Motivo de não usar evento Fabric:** A API pública do Fabric não expõe um callback de pré-resolução para a linha de empurrão de pistões vanilla.
* **Comportamento:** Intercepta o retorno de `resolve()`, percorre as listas `getToPush()` e `getToDestroy()`, e cancela o resultado se qualquer bloco cruzar bordas protegidas ou cair em uma região com `piston-move` negada.

### 6. `FireBlockMixin`
* **Classe Alvo:** `net.minecraft.world.level.block.FireBlock`
* **Métodos Alvo:**
  * `tick(...)`: Captura a posição da chama atual antes de qualquer lógica de propagação.
  * `getIgniteOdds(...)`: Cancela a chance de ignição quando a propagação entre a chama e o bloco alvo é negada pela região.
  * `checkBurnOut(...)`: Interrompe a atualização de fogo quando o espalhamento ou o dano de fogo em blocos está bloqueado.
* **Motivo de não usar evento Fabric:** A propagação de fogo e o burn-out de blocos de fogo não possuem um callback público granular que permita diferenciar ignição e dano em bloco.
* **Comportamento:** Separa `fire-spread` de `fire-block-damage` para impedir propagação sem necessariamente alterar o comportamento do fogo em outros cenários.

### 7. `FlowingFluidMixin`
* **Classe Alvo:** `net.minecraft.world.level.material.FlowingFluid`
* **Método Alvo:** `spreadTo`
* **Motivo de não usar evento Fabric:** O fluxo vanilla de fluidos não expõe um evento pré-spread cancelável com contexto suficiente para bloquear o movimento por borda ou entre claims.
* **Comportamento:** Verifica a posição de origem e destino antes de permitir a propagação de água ou lava, usando flags separadas para `water-flow` e `lava-flow`.

### 8. `LavaFluidMixin`
* **Classe Alvo:** `net.minecraft.world.level.material.LavaFluid`
* **Métodos Alvo:**
  * `randomTick(...)`: Captura a posição da lava corrente durante a atualização.
  * `hasFlammableNeighbours(...)`: Bloqueia a avaliação de vizinhos inflamáveis quando a propagação de fogo é negada.
  * `isFlammable(...)`: Interrompe a ignição derivada da lava em claims protegidas.
* **Motivo de não usar evento Fabric:** A ignição causada por lava é parte do fluxo interno do fluido, sem um evento público separado para a checagem de vizinhos inflamáveis.
* **Comportamento:** Impede que lava em regiões bloqueadas acenda blocos ou alimente propagação de fogo em claims negadas.

### 9. `EndermanTakeBlockGoalMixin`
* **Classe Alvo:** `net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal`
* **Método Alvo:** `tick`
* **Motivo de não usar evento Fabric:** A movimentação de blocos por Endermen não tem callback público de pré-remoção de bloco.
* **Comportamento:** Cancela a remoção do bloco carregado pelo Enderman quando o `mob-griefing` está negado na posição alvo.

### 10. `EndermanLeaveBlockGoalMixin`
* **Classe Alvo:** `net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal`
* **Método Alvo:** `canPlaceBlock`
* **Motivo de não usar evento Fabric:** A lógica de colocação de blocos por Endermen não expõe um evento cancelável antes da decisão vanilla.
* **Comportamento:** Impede que Endermen coloquem blocos em regiões onde `mob-griefing` está negado.
