# Configuração e Estrutura de Dados

## Armazenamento

O módulo de crates usa dois sistemas de persistência:

| Tipo | Onde | O quê |
|------|------|-------|
| **JSON** | `config/crates.json`, `config/keys.json`, `config/crate_locations.json` | Definições de crates, chaves e localizações |
| **JDBC** | Banco de dados configurado (MySQL/SQLite/H2) | Estado de jogadores, saldos, logs |

---

## 1. Arquivo `crates.json`

Lista de objetos `CrateDefinition`. Localização: `config/crates.json`

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "key": "crate_vip",
    "displayName": "Crate VIP",
    "description": "Uma crate especial para jogadores VIP",
    "enabled": true,
    "openingType": "VIRTUAL",
    "cooldownMillis": 3600000,
    "cost": 100.0,
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-03-01T14:22:00Z",
    "lastEditedBy": "550e8400-e29b-41d4-a716-446655440001",
    "lastEditReason": "Ajuste de pesos",
    "displayItem": {
      "item": "minecraft:chest",
      "count": 1,
      "components": { }
    },
    "lore": ["§7Uma crate especial", "§7para membros VIP"],
    "previewConfig": {
      "enabled": true,
      "layout": "54",
      "showChance": true,
      "hideUnavailableRewards": true,
      "requirementsMessage": "§7Clique para ver os requisitos",
      "showOpenAllButton": true,
      "maxPreviewItems": 28,
      "rewardSlots": [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43]
    },
    "animationConfig": {
      "allowSkip": true,
      "durationTicks": 60,
      "startSound": "minecraft:block.chest.open",
      "tickSound": "",
      "endSound": "minecraft:entity.player.levelup",
      "rewardSound": "minecraft:entity.experience_orb.pickup",
      "showRollingItems": true,
      "rollingSpeed": 2,
      "highlightDurationTicks": 40,
      "particleConfig": {
        "particleType": "minecraft:enchant",
        "shape": "CIRCLE",
        "frequencyTicks": 1,
        "particleCount": 5,
        "radius": 1.0,
        "height": 1.5,
        "speed": 0.1,
        "maxDistance": 32,
        "onlyNearbyPlayers": true
      }
    },
    "requirements": {
      "acceptedKeyIds": ["chave_vip"],
      "requirePhysicalKey": false,
      "requireVirtualKey": true,
      "requiredPermission": "grupo.vip",
      "requiredCost": 100.0,
      "cooldownMillis": 3600000,
      "oneTimeUse": false,
      "logic": "AND",
      "alternativeCosts": [
        {
          "type": "KEY",
          "value": "chave_vip",
          "description": "Usar chave VIP"
        },
        {
          "type": "ECONOMY",
          "value": "500",
          "description": "Pagar 500 moedas"
        }
      ]
    },
    "rarities": [
      {
        "id": "comum",
        "name": "Comum",
        "color": "#AAAAAA",
        "priority": 0,
        "weight": 50.0,
        "icon": "minecraft:paper",
        "active": true,
        "displayOrder": 0,
        "lore": ["§7Uma recompensa comum"]
      },
      {
        "id": "raro",
        "name": "Raro",
        "color": "#FFD700",
        "priority": 1,
        "weight": 30.0,
        "icon": "minecraft:gold_ingot",
        "active": true,
        "displayOrder": 1,
        "lore": ["§7Uma recompensa rara"]
      },
      {
        "id": "lendario",
        "name": "Lendário",
        "color": "#FF0000",
        "priority": 2,
        "weight": 20.0,
        "icon": "minecraft:diamond",
        "active": true,
        "displayOrder": 2,
        "lore": ["§7Uma recompensa lendária"]
      }
    ],
    "rewards": [
      {
        "id": "pedra",
        "name": "Bloco de Pedra",
        "crateId": "crate_vip",
        "type": "ITEM",
        "rarityId": "comum",
        "weight": 10.0,
        "requiredPermission": "",
        "globalLimit": -1,
        "playerLimit": -1,
        "broadcast": false,
        "broadcastMessage": "",
        "playerMessage": "",
        "active": true,
        "visibleInPreview": true,
        "milestoneOnly": false,
        "displayOrder": 0,
        "icon": {
          "item": "minecraft:stone",
          "count": 1
        },
        "lore": ["§7Um bloco de pedra"],
        "blockingPermissions": [],
        "items": [
          {
            "item": "minecraft:stone",
            "count": 1
          }
        ],
        "commands": []
      },
      {
        "id": "vip_item",
        "name": "Item VIP",
        "crateId": "crate_vip",
        "type": "COMMAND",
        "rarityId": "lendario",
        "weight": 1.0,
        "requiredPermission": "",
        "globalLimit": 100,
        "playerLimit": 1,
        "broadcast": true,
        "broadcastMessage": "§6§l{jogador} §eacabou de ganhar um item VIP!",
        "playerMessage": "§aParabéns! Você ganhou um item VIP raro!",
        "active": true,
        "visibleInPreview": true,
        "milestoneOnly": false,
        "displayOrder": 5,
        "icon": {
          "item": "minecraft:diamond_sword",
          "count": 1,
          "components": {}
        },
        "lore": ["§7Um item VIP lendário"],
        "blockingPermissions": [],
        "items": [],
        "commands": [
          "give {player} minecraft:diamond_sword 1",
          "broadcast §6{jogador} ganhou uma espada de diamante!"
        ]
      }
    ],
    "milestones": [
      {
        "id": "10_aberturas",
        "name": "10 Aberturas",
        "description": "Abra a crate 10 vezes",
        "rewardId": "vip_item",
        "requiredOpenings": 10,
        "repeatable": false,
        "active": true,
        "displayOrder": 0
      },
      {
        "id": "50_aberturas",
        "name": "50 Aberturas",
        "description": "Abra a crate 50 vezes",
        "rewardId": "vip_item",
        "requiredOpenings": 50,
        "repeatable": true,
        "active": true,
        "displayOrder": 1
      }
    ],
    "visualConfig": {
      "hologramEnabled": true,
      "hologramTemplate": "",
      "hologramLines": [
        "§6§l{name}",
        "§7{description}",
        "§e§lChaves: §f{key_amount}",
        "§7Clique para abrir"
      ],
      "hologramOffsetY": 2.0,
      "hologramUpdateIntervalTicks": 20,
      "hologramViewDistance": 16,
      "approachSound": "minecraft:block.note_block.harp",
      "openSound": "minecraft:block.chest.open",
      "approachSoundRadius": 8,
      "idleParticleConfig": {
        "particleType": "minecraft:enchant",
        "shape": "AURA",
        "frequencyTicks": 10,
        "particleCount": 3,
        "radius": 1.0,
        "height": 1.5,
        "speed": 0.1,
        "maxDistance": 32,
        "onlyNearbyPlayers": true
      },
      "openParticleConfig": {
        "particleType": "minecraft:enchant",
        "shape": "SPIRAL",
        "frequencyTicks": 1,
        "particleCount": 5,
        "radius": 1.0,
        "height": 1.5,
        "speed": 0.1,
        "maxDistance": 32,
        "onlyNearbyPlayers": true
      }
    }
  }
]
```

### Campos da CrateDefinition

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | UUID | Identificador único |
| `key` | String | ID técnico (minúsculas, números, `_`, `-`) |
| `displayName` | String | Nome de exibição |
| `description` | String | Descrição |
| `displayItem` | ItemStack serializado | Ícone para menus |
| `lore` | String[] | Linhas de lore |
| `enabled` | boolean | Se a crate está ativa |
| `openingType` | enum | `NONE`, `VIRTUAL`, `PHYSICAL` |
| `previewConfig` | objeto | Configuração do preview |
| `animationConfig` | objeto | Configuração da animação |
| `requirements` | objeto | Requisitos para abrir |
| `cooldownMillis` | long | Cooldown entre aberturas (ms) |
| `cost` | double | Custo econômico |
| `rarities` | array | Lista de raridades |
| `rewards` | array | Lista de recompensas |
| `milestones` | array | Lista de milestones |
| `visualConfig` | objeto | Configuração visual (holograma/partículas) |
| `createdAt` | ISO instant | Data de criação |
| `updatedAt` | ISO instant | Data da última modificação |
| `lastEditedBy` | UUID (opcional) | Quem editou por último |
| `lastEditReason` | String (opcional) | Motivo da última edição |

### Campos de CrateRarity

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | String | ID técnico |
| `name` | String | Nome de exibição |
| `color` | String | Cor hex (ex: `#FFD700`) |
| `priority` | int | Prioridade visual |
| `weight` | double | Peso para seleção aleatória |
| `icon` | String | ID do item do Minecraft |
| `lore` | String[] | Linhas de descrição |
| `active` | boolean | Se está ativa |
| `displayOrder` | int | Ordem de exibição |

### Campos de CrateReward

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | String | ID técnico |
| `name` | String | Nome de exibição |
| `crateId` | String | ID da crate pai |
| `type` | enum | `ITEM` ou `COMMAND` |
| `rarityId` | String | ID da raridade |
| `weight` | double | Peso dentro da raridade |
| `icon` | ItemStack | Ícone para preview |
| `lore` | String[] | Descrição no preview |
| `items` | ItemStack[] | Itens a dar (tipo ITEM) |
| `commands` | String[] | Comandos a executar (tipo COMMAND) |
| `requiredPermission` | String | Permissão necessária |
| `blockingPermissions` | String[] | Permissões que bloqueiam |
| `globalLimit` | int | Limite global (-1 = ilimitado) |
| `playerLimit` | int | Limite por jogador (-1 = ilimitado) |
| `broadcast` | boolean | Anunciar no servidor |
| `broadcastMessage` | String | Mensagem de anúncio |
| `playerMessage` | String | Mensagem para o jogador |
| `active` | boolean | Se está ativa |
| `visibleInPreview` | boolean | Se aparece no preview |
| `milestoneOnly` | boolean | Disponível apenas como milestone |
| `displayOrder` | int | Ordem de exibição |

### Campos de CrateMilestone

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | String | ID técnico |
| `name` | String | Nome |
| `description` | String | Descrição |
| `rewardId` | String | ID da recompensa a dar |
| `requiredOpenings` | int | Número de aberturas necessárias |
| `repeatable` | boolean | Se repete a cada N aberturas |
| `active` | boolean | Se está ativo |
| `displayOrder` | int | Ordem de exibição |

---

## 2. Arquivo `keys.json`

Lista de objetos `KeyDefinition`.

```json
[
  {
    "id": "chave_vip",
    "name": "Chave VIP",
    "active": true,
    "virtual": false,
    "requiredPermission": "",
    "giveSound": "minecraft:entity.player.levelup",
    "takeSound": "",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-03-01T14:22:00Z",
    "physicalItem": {
      "item": "minecraft:tripwire_hook",
      "count": 1,
      "components": {
        "minecraft:custom_name": "{\"text\":\"§6Chave VIP\"}"
      }
    },
    "lore": [
      "§7Use para abrir a Crate VIP",
      "§7Clique com botão direito no bloco"
    ],
    "compatibleCrateIds": ["crate_vip"],
    "giveCommands": ["broadcast §6{jogador} ganhou uma chave VIP!"]
  }
]
```

### Campos de KeyDefinition

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | String | ID técnico |
| `name` | String | Nome de exibição |
| `active` | boolean | Se a chave está ativa |
| `virtual` | boolean | Se é apenas virtual (sem item físico) |
| `physicalItem` | ItemStack | Item físico (para chaves não-virtuais) |
| `lore` | String[] | Lore do item físico |
| `compatibleCrateIds` | String[] | IDs das crates que aceitam esta chave |
| `requiredPermission` | String | Permissão necessária para usar |
| `giveSound` | String | Som ao dar a chave |
| `takeSound` | String | Som ao remover a chave |
| `giveCommands` | String[] | Comandos executados ao dar a chave |
| `createdAt` | ISO instant | Data de criação |
| `updatedAt` | ISO instant | Data da última modificação |

---

## 3. Arquivo `crate_locations.json`

Lista de objetos `CrateLocation`.

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440002",
    "crateId": "crate_vip",
    "world": "minecraft:overworld",
    "x": 100,
    "y": 64,
    "z": 200,
    "hologramTemplate": "",
    "hologramOffsetY": 2.0,
    "hologramEnabled": true,
    "particleEnabled": true,
    "active": true,
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-03-01T14:22:00Z"
  }
]
```

### Campos de CrateLocation

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | UUID | Identificador único |
| `crateId` | String | ID da crate vinculada |
| `world` | String | Namespace da dimensão (ex: `minecraft:overworld`) |
| `x` | int | Coordenada X |
| `y` | int | Coordenada Y |
| `z` | int | Coordenada Z |
| `hologramTemplate` | String | Template personalizado de holograma |
| `hologramOffsetY` | double | Offset vertical do holograma |
| `hologramEnabled` | boolean | Se holograma está ativo |
| `particleEnabled` | boolean | Se partículas estão ativas |
| `active` | boolean | Se a localização está ativa |
| `createdAt` | ISO instant | Data de criação |
| `updatedAt` | ISO instant | Data da última modificação |

---

## 4. Tabelas JDBC

### `crate_player_keys`

Saldos de chaves virtuais por jogador.

```sql
CREATE TABLE IF NOT EXISTS crate_player_keys (
    player_uuid VARCHAR(36) NOT NULL,
    key_id VARCHAR(64) NOT NULL,
    amount INT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, key_id)
);
```

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `player_uuid` | VARCHAR(36) | UUID do jogador |
| `key_id` | VARCHAR(64) | ID da chave |
| `amount` | INT | Quantidade de chaves |
| `updated_at` | BIGINT | Timestamp da última atualização |

### `crate_player_state`

Estado do jogador por crate (cooldown, aberturas, milestones).

```sql
CREATE TABLE IF NOT EXISTS crate_player_state (
    player_uuid VARCHAR(36) NOT NULL,
    crate_id VARCHAR(64) NOT NULL,
    cooldown_until BIGINT NOT NULL DEFAULT 0,
    total_opened INT NOT NULL DEFAULT 0,
    milestone_progress INT NOT NULL DEFAULT 0,
    latest_opened_at BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, crate_id)
);
```

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `player_uuid` | VARCHAR(36) | UUID do jogador |
| `crate_id` | VARCHAR(64) | ID da crate |
| `cooldown_until` | BIGINT | Timestamp até quando está em cooldown |
| `total_opened` | INT | Total de aberturas |
| `milestone_progress` | INT | Progresso de milestone |
| `latest_opened_at` | BIGINT | Timestamp da última abertura |

### `crate_reward_roll_state`

Estado de rolagem de recompensas (limites globais e por jogador).

```sql
CREATE TABLE IF NOT EXISTS crate_reward_roll_state (
    reward_id VARCHAR(64) NOT NULL,
    global_count INT NOT NULL DEFAULT 0,
    player_counts TEXT NOT NULL DEFAULT '{}',
    PRIMARY KEY (reward_id)
);
```

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `reward_id` | VARCHAR(64) | ID da recompensa |
| `global_count` | INT | Total de vezes que foi distribuída globalmente |
| `player_counts` | TEXT | JSON com mapa de UUID do jogador → contagem |

### `crate_audit_log`

Logs de auditoria de aberturas de crates.

```sql
CREATE TABLE IF NOT EXISTS crate_audit_log (
    id VARCHAR(36) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    crate_id VARCHAR(64) NOT NULL,
    key_id VARCHAR(64),
    source VARCHAR(32) NOT NULL,
    reward_ids TEXT,
    reward_names TEXT,
    status VARCHAR(16) NOT NULL,
    cost_consumed DOUBLE NOT NULL DEFAULT 0.0,
    timestamp BIGINT NOT NULL,
    idempotency_key VARCHAR(64),
    server_id VARCHAR(64),
    error_detail TEXT,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_crate_audit_player ON crate_audit_log (player_uuid);
CREATE INDEX IF NOT EXISTS idx_crate_audit_crate ON crate_audit_log (crate_id);
CREATE INDEX IF NOT EXISTS idx_crate_audit_idempotency ON crate_audit_log (idempotency_key);
CREATE INDEX IF NOT EXISTS idx_crate_audit_timestamp ON crate_audit_log (timestamp);
```

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | VARCHAR(36) | UUID do log |
| `player_uuid` | VARCHAR(36) | UUID do jogador |
| `crate_id` | VARCHAR(64) | ID da crate |
| `key_id` | VARCHAR(64) | ID da chave usada (pode ser null) |
| `source` | VARCHAR(32) | Fonte da abertura (enum `GrantSource`) |
| `reward_ids` | TEXT | JSON array de IDs das recompensas |
| `reward_names` | TEXT | JSON array de nomes das recompensas |
| `status` | VARCHAR(16) | Status: `PENDING`, `COMPLETED`, `FAILED`, `ROLLED_BACK`, `CANCELLED` |
| `cost_consumed` | DOUBLE | Custo consumido |
| `timestamp` | BIGINT | Timestamp da abertura |
| `idempotency_key` | VARCHAR(64) | Chave de idempotência |
| `server_id` | VARCHAR(64) | ID do servidor |
| `error_detail` | TEXT | Detalhe do erro (se houver) |

---

## 5. Migrações

As tabelas JDBC são criadas automaticamente na inicialização via `CREATE TABLE IF NOT EXISTS`. Não há scripts de migração manuais — o esquema é gerenciado pelo código.

Para migrar dados entre servidores:

1. **Definições (JSON)**: Copie os arquivos `crates.json`, `keys.json` e `crate_locations.json` para o novo servidor
2. **Estado (JDBC)**: Exporte as tabelas `crate_player_keys`, `crate_player_state`, `crate_reward_roll_state` e `crate_audit_log` via ferramenta de banco de dados

## 6. Serialização de Items

Itens são serializados usando `ItemSerializer` que utiliza o sistema de componentes vanilla do Minecraft 1.21+:

```json
{
  "item": "minecraft:diamond_sword",
  "count": 1,
  "components": {
    "minecraft:custom_name": "{\"text\":\"Espada Especial\"}",
    "minecraft:enchantments": {
      "levels": {
        "minecraft:sharpness": 5
      }
    }
  }
}
```
