# ADR 0002: Mod Estritamente Server-Side

## Contexto
O BigBang Regions deve rodar em um servidor público onde jogadores entram usando clientes vanilla ou modpacks padrões sem a necessidade de instalar mods adicionais de proteção do lado do cliente.

## Decisão
Projetar o mod como **Server-Side-Only**. Toda a lógica, comandos e feedbacks serão processados no servidor. Mensagens de aviso e feedback visual serão feitas usando pacotes e recursos vanilla (como Action Bar e partículas nativas). Não haverá pacotes de rede próprios ou telas customizadas do lado do cliente nesta fase.

## Consequências
* **Positivas**:
  * Barreira de entrada zero para jogadores vanilla.
  * Facilidade de atualização e manutenção do mod (apenas atualizações no servidor).
* **Negativas**:
  * Menus gráficos complexos e customizados não podem ser criados facilmente sem mods cliente, necessitando do uso de comandos ou menus baseados em inventários virtuais vanilla no futuro.
