# Matriz de Compatibilidade

Esta matriz detalha o comportamento do mod com mecânicas do Minecraft Vanilla e integrações com outros mods comuns.

## Vanilla Minecraft

O mod cobre 100% das ações diretas do jogador no Vanilla:
* Quebra e colocação de blocos (incluindo baldes de água e lava e colocação secundária/offhand).
* Interações de redstone (botões, alavancas e placas de pressão).
* Abertura de portas, alçapões e portões de cerca.
* Interação com entidades decorativas (Armor Stands, Item Frames) e veículos (Barcos, Minecarts).
* Combate PvP (corpo a corpo e projéteis como flechas, poções e tridentes).
* Coleta e drop manual de itens.

## Inventários Modded (Critério Técnico de Detecção)

Para garantir compatibilidade com containers de outros mods sem necessidade de dependências diretas, o mod adota um critério técnico genérico para detectar containers no `UseBlockCallback`:
* Qualquer bloco com `BlockEntity` na posição que implemente a interface `net.minecraft.world.Container` (como baús e barris modded) ou a interface `net.minecraft.world.MenuProvider` (que abre uma tela de inventário personalizada, comum em máquinas industriais) será identificado e bloqueado sob a flag `container-access`.

## Status de Compatibilidade de Mods Específicos

| Mod / Mecânica | Escopo | Status de Compatibilidade | Detalhes Técnicos e Limitações |
|---|---|---|---|
| **Cobblemon** | Pokémon/Batalhas | `NOT_VALIDATED` | Não validado nesta fase. Batalhas e spawn de pokémons não possuem hooks integrados no momento. |
| **Create** | Automações e Brocas | `PARTIALLY_SUPPORTED` | Fake players criados pelo Create para quebrar/interagir são tratados como visitantes (bloqueados), mas brocas físicas e contrações mecânicas não sofrem interceptação directa. |
| **AE2 / RFTools** | Redes e Pedreiras | `PARTIALLY_SUPPORTED` | Blocos colocados/quebrados por ferramentas de rede ou pedreiras (Quarry/Builder) não são prevenidos nesta fase. |
| **Pistons (Pistões)** | Movimentação Vanilla | `NOT_SUPPORTED` | Empurrar blocos com pistões normais de dentro para fora ou de fora para dentro de claims ainda não é interceptado nesta fase. |
| **Hoppers (Funis)** | Transferência Vanilla | `NOT_SUPPORTED` | Funis colocados fora da região podem extrair itens de baús protegidos dentro da região. Proteção de transferência de inventário está planejada para a Fase 2. |
| **Explosões (TNT/Creeper)** | Dano no Terreno | `PLANNED` | Blocos destruídos por explosões não são restaurados ou prevenidos. Planejado para a Fase 2. |
| **Fogo, Fluidos e Mob Griefing** | Alastramento e Danos | `PLANNED` | Propagação de fogo, fluxo de lava/água e destruição por Endermen/Creepers não são impedidos nesta fase. |
