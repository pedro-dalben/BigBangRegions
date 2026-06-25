# BigBang Regions

## Objetivo
BigBang Regions é a fundação funcional do sistema de proteção territorial para o servidor BigBang Craft. Trata-se de um mod server-side para Fabric 1.21.1 que serve como núcleo de proteção territorial, protegendo regiões administrativas do servidor e servindo como base estrutural para futuros terrenos de jogadores.

## Requisitos
* **Minecraft:** 1.21.1
* **Java:** 21
* **Loader:** Fabric
* **Ambiente:** Server-only (não precisa ser instalado pelos jogadores)
* **Dependência Opcional:** `fabric-permissions-api` ( LuckPerms )

## Instalação
1. Baixe o jar do mod compilado (`bigbang-regions-<version>.jar`).
2. Coloque-o na pasta `mods` do seu servidor Fabric.
3. Inicie o servidor. A configuração padrão será gerada em `config/bigbangregions/config.json` e a base de dados SQLite em `config/bigbangregions/regions.db`.

## Compatibilidade
* Compatível com clientes vanilla Minecraft 1.21.1.
* Integração opcional e transparente com LuckPerms via `fabric-permissions-api`.
* Protege contra interações e danos em blocos vanilla e modded (que implementam `Container` ou `MenuProvider`).

## Comandos Disponíveis
O mod oferece comandos principais com os aliases `/regiao` e `/regioes`:

* `/regions pos1` - Salva a primeira posição (bloco atual) do jogador.
* `/regions pos2` - Salva a segunda posição (bloco atual) do jogador.
* `/regions create admin <id> [priority]` - Cria uma região cubóide administrativa com o ID e prioridade fornecidos.
* `/regions delete <id>` - Deleta uma região.
* `/regions info` - Mostra detalhes da região efetiva no bloco atual do jogador.
* `/regions list [page]` - Lista todas as regiões registradas de forma paginada.
* `/regions flag set <regionId> <flag> <allow|deny|inherit>` - Altera o valor de uma flag em uma região.
* `/regions flag get <regionId> <flag>` - Mostra o valor explícito e efetivo de uma flag na região.
* `/regions flags <regionId>` - Lista todas as flags customizadas configuradas na região.
* `/regions reload` - Recarrega a configuração e limpa/recarrega as regiões da base de dados.

## Permissões
* `bigbangregions.admin.create` - Permissão para selecionar posições e criar regiões.
* `bigbangregions.admin.delete` - Permissão para deletar regiões.
* `bigbangregions.admin.edit` - Permissão para editar propriedades e flags de regiões.
* `bigbangregions.admin.flags` - Permissão para visualizar e configurar flags.
* `bigbangregions.admin.list` - Permissão para listar regiões.
* `bigbangregions.admin.reload` - Permissão para recarregar o mod.
* `bigbangregions.inspect` - Permissão para inspecionar regiões com `/regions info`.
* `bigbangregions.bypass` - Permissão para ignorar todas as proteções territoriais.
* `bigbangregions.bypass.<flag>` - Permissão para ignorar a proteção de uma flag específica (ex: `bigbangregions.bypass.pvp`).

Se o LuckPerms ou outra API de permissões não estiver presente, o mod utiliza o nível do Operador (OP level 2) como fallback padrão.

## Estrutura de Configuração (`config/bigbangregions/config.json`)
```json
{
  "schemaVersion": 1,
  "defaultPriorities": {
    "systemRegion": 10000,
    "adminRegion": 1000,
    "playerRegion": 100
  },
  "permissions": {
    "operatorFallbackLevel": 2
  },
  "defaults": {
    "global": {
      "player-build": "ALLOW",
      "player-interact": "ALLOW",
      "container-access": "ALLOW",
      "door-use": "ALLOW",
      "redstone-use": "ALLOW",
      "entity-interact": "ALLOW",
      "pvp": "ALLOW",
      "item-pickup": "ALLOW",
      "item-drop": "ALLOW"
    },
    "adminRegion": {
      "player-build": "DENY",
      "player-interact": "DENY",
      "container-access": "DENY",
      "door-use": "DENY",
      "redstone-use": "DENY",
      "entity-interact": "DENY",
      "pvp": "DENY",
      "item-pickup": "ALLOW",
      "item-drop": "ALLOW"
    }
  }
}
```

## Comandos de Desenvolvimento
* Para compilar o mod: `./gradlew build`
* Para rodar os testes unitários: `./gradlew test`
* Para limpar a build: `./gradlew clean`

## Como Gerar Release
O jar final compilado e remapeado para distribuição estará disponível em:
`build/libs/bigbang-regions-<version>.jar`

## Como Abrir Issue Interna
Ao encontrar bugs ou propor novas funcionalidades, por favor envie as seguintes informações em nosso canal interno:
1. Log completo do erro (com stacktrace de `logs/latest.log`).
2. Versão do mod e do Fabric Loader.
3. Passos para reprodução (por exemplo: comandos digitados, ações do jogador).

## Limitações Conhecidas
1. Proteção de automações complexas (como robôs do mod Create, canos de Mekanism) requer adapters adicionais e não é tratada na fase base de proteção direta de jogadores.
2. Apenas formato geométrico Cubóide (3D) está disponível nesta versão.
