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
