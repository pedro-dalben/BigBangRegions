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

### Comandos de Administração
* `/regions pos1` - Salva a primeira posição (bloco atual) do jogador.
* `/regions pos2` - Salva a segunda posição (bloco atual) do jogador.
* `/regions create admin <id> [priority]` - Cria uma região administrativa a partir da seleção (pos1/pos2).
* `/regions admin create <sizeX> <sizeZ> [id] [priority]` - Cria uma região administrativa centrada no jogador (sem seleção).
* `/regions create player <id> <owner> [priority]` - Cria um terreno de jogador manual atribuindo o proprietário.
* `/regions delete <id>` - Deleta uma região.
* `/regions list [page]` - Lista todas as regiões registradas de forma paginada.
* `/regions player owner <id> [newOwner]` - Mostra ou altera o dono de uma região de jogador.
* `/regions player members <id>` - Lista membros de uma região administrativamente.
* `/regions player addmember <id> <player>` - Adiciona membro administrativamente.
* `/regions player removemember <id> <player>` - Remove membro administrativamente.
* `/regions player setrole <id> <player> <leader|member>` - Define papel administrativamente.
* `/regions reload` - Recarrega a configuração, limpa e recarrega os caches e banco de dados.

### Comandos de Jogador
* `/regiao info` - Mostra detalhes da região no bloco atual (papel, membros, e flags).
* `/regiao membros listar` - Lista membros do terreno pertencente ao jogador.
* `/regiao membros adicionar <player>` - Adiciona um membro (cargo MEMBER) no terreno.
* `/regiao membros remover <player>` - Remove um membro do terreno.
* `/regiao membros promover <player>` - Promove um membro a LEADER (apenas dono).
* `/regiao membros rebaixar <player>` - Rebaixa um LEADER para MEMBER (apenas dono).
* `/regiao sair` - Sai voluntariamente de uma região em que é membro.
* `/regiao flags listar` - Lista todas as flags do seu terreno.
* `/regiao flags ver <flag>` - Mostra o valor de uma flag específica.
* `/regiao flags definir <flag> <allow|deny|inherit>` - Altera o valor de uma flag no seu terreno.

## Permissões
* `bigbangregions.admin.create` - Permissão para selecionar posições e criar regiões.
* `bigbangregions.admin.player.create` - Permissão para criar regiões de jogador.
* `bigbangregions.admin.player.owner` - Permissão para gerenciar/visualizar proprietários de terrenos.
* `bigbangregions.admin.player.members` - Permissão para gerenciar membros administrativamente.
* `bigbangregions.admin.delete` - Permissão para deletar regiões.
* `bigbangregions.admin.edit` - Permissão para editar propriedades de regiões.
* `bigbangregions.admin.flags` - Permissão para visualizar e configurar flags de forma administrativa.
* `bigbangregions.admin.list` - Permissão para listar regiões.
* `bigbangregions.admin.reload` - Permissão para recarregar o mod.
* `bigbangregions.inspect` - Permissão para inspecionar regiões com `/regions info`.
* `bigbangregions.bypass` - Permissão para ignorar todas as proteções territoriais.
* `bigbangregions.bypass.<flag>` - Permissão para ignorar a proteção de uma flag específica.

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
  "playerRegions": {
    "maxRegionsPerOwner": 1,
    "rejectOverlapWithAdminRegions": true,
    "rejectOverlapWithSystemRegions": true,
    "rejectOverlapWithPlayerRegions": true
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
    },
    "playerRegion": {
      "player-build": "ALLOW",
      "player-interact": "ALLOW",
      "container-access": "ALLOW",
      "door-use": "ALLOW",
      "redstone-use": "ALLOW",
      "entity-interact": "ALLOW",
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
