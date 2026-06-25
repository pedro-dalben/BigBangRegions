# ADR 0001: Motor de Regiões Customizado

## Contexto
O servidor BigBang Craft necessita de um sistema flexível de proteção territorial. Embora soluções GPL existentes (como RedProtect ou WorldGuard) ofereçam bons recursos de produto, elas introduzem acoplamento a código legado, APIs Bukkit/Spigot e estruturas monolíticas difíceis de estender para a plataforma Fabric.

## Decisão
Implementar um motor de proteção territorial 100% independente do zero, adaptado diretamente para a API do Fabric 1.21.1, utilizando os conceitos de flags e prioridades do RedProtect apenas como inspiração conceitual.

## Consequências
* **Positivas**:
  * Código 100% limpo, legível e otimizado para o Fabric.
  * API pública estável para que outros mods da BigBang Craft possam interagir.
  * Livre de problemas de licença GPL e acoplamentos desnecessários.
* **Negativas**:
  * Requer implementação manual de listeners e mixins para cobrir todas as vias de bypass.
