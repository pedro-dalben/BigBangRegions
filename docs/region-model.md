# Modelo de Regiões

O modelo de regiões define as propriedades e o comportamento das subdivisões territoriais no mod.

## Campos do Modelo

A entidade `Region` possui os seguintes campos mínimos mapeados no domínio e no SQLite:

| Campo | Tipo | Descrição |
|---|---|---|
| `id` | String | Identificador único alfanumérico da região (Primary Key). |
| `name` | String | Nome amigável de exibição da região. |
| `type` | Enum | Tipo da região: `ADMIN_REGION`, `PLAYER_REGION`, `SYSTEM_REGION`. |
| `dimensionKey` | String | Identificador do mundo/dimensão (ex: `minecraft:overworld`). |
| `minX`, `minY`, `minZ` | Integer | Coordenada mínima tridimensional da bounding box. |
| `maxX`, `maxY`, `maxZ` | Integer | Coordenada máxima tridimensional da bounding box. |
| `priority` | Integer | Prioridade de resolução de sobreposição. |
| `ownerUuid` | UUID | UUID do proprietário (nulo para regiões administrativas). |
| `createdByUuid` | UUID | UUID do criador da região. |
| `createdAt` | Long | Timestamp Unix da criação da região. |
| `updatedAt` | Long | Timestamp Unix da última modificação. |
| `status` | String | Estado da região (ex: `ACTIVE`, `SUSPENDED`). |

## Papéis de Membro (RegionRole)

* **OWNER (Dono)**:
  * Proprietário do terreno. Possui controle total. Pode gerenciar líderes e membros, editar flags e expandir o terreno (em fases futuras).
* **LEADER (Líder)**:
  * Administrador delegado da região. Pode gerenciar membros e alterar flags permitidas, mas não pode deletar ou transferir a região.
* **MEMBER (Membro)**:
  * Pode interagir com a região (construir, usar baús, etc.) conforme permissões internas concedidas.
* **VISITOR (Visitante)**:
  * Sem acesso especial. Suas ações são governadas estritamente pelas flags definidas na região.
