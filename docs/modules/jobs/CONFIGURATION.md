# Jobs Configuration Guide

## Canonical Path

```
config/bigbangessentials/jobs/
```

## File Tree

```
config/bigbangessentials/jobs/
├── global.json              # Global settings, daily limits, permissions
├── slots.json               # Slot definitions (COMMON_PRIMARY, etc.)
├── milestones.json          # RankUp milestones → slot unlocks
├── contracts.json           # Contract templates
├── crates.json              # Crate key drop rules
├── menus.json               # Menu layout overrides
└── professions/
    ├── miner.json
    ├── woodcutter.json
    ├── farmer.json
    ├── builder.json
    ├── blacksmith.json
    ├── crafter.json
    ├── explorer.json
    ├── ranger.json
    ├── culinarian.json
    ├── magician.json
    ├── fisherman.json
    ├── researcher.json
    ├── breeder.json
    ├── trainer.json
    ├── pasture_keeper.json
    ├── paleontologist.json
    └── raider.json
```

## Schema Version

Current: `2` (field: `schema-version` in `global.json`)

## global.json Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `schema-version` | int | `2` | Schema version for migration detection |
| `daily-limit.enabled` | bool | `true` | Enable global daily earnings cap |
| `daily-limit.global-limit` | double | `50000.0` | Max daily earnings across all jobs |
| `daily-limit.timezone` | string | `"America/Sao_Paulo"` | Timezone for daily reset |
| `daily-limit.reset-time` | string | `"00:00"` | HH:MM daily reset time |
| `max-active-jobs` | int | `2` | Max simultaneously active jobs per player |
| `max-in-progress-licenses` | int | `1` | Max concurrent license quests |
| `switch-cooldown-minutes` | int | `30` | Cooldown between job switches |
| `afk-prevention.prevent-earnings-while-afk` | bool | `true` | Block money while AFK |
| `afk-prevention.prevent-xp-while-afk` | bool | `true` | Block XP while AFK |
| `afk-prevention.continue-xp-after-limit` | bool | `false` | Allow XP gain after daily money cap |
| `permissions.prefix` | string | `"bigbangessentials.jobs"` | Permission node prefix |
| `permissions.legacy-aliases` | object | `{}` | Map of old → new permission nodes |

## Profession JSON Fields

Each file in `professions/` must be named `<id>.json` matching its `id` field.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | string | (required) | Unique profession ID, must match filename |
| `enabled` | bool | `true` | Whether the profession is active |
| `display-name` | string | (required) | Portuguese display name |
| `short-description` | string | `""` | Brief one-line description |
| `description` | string | `""` | Full profession description |
| `icon` | string | `"minecraft:book"` | Item ID for menu icon |
| `category` | string | (required) | `"COMMON"` or `"POKEMON_SPECIALIZATION"` |
| `sort-order` | int | `99` | Menu sort order (ascending) |
| `permission` | string | `"bigbangessentials.jobs.profession.<id>"` | Required permission node |
| `visible-without-permission` | bool | `true` | Show in menu even without permission |
| `unlocked-by-default` | bool | `true` | Available immediately (false = needs rank milestone) |
| `license-required` | bool | `false` | Must complete license quest before earning |
| `required-integration` | string\|null | `null` | Cobbleverse mod required (e.g., `"cobblemon"`) |
| `max-level` | int | `100` | Maximum profession level |
| `xp-curve.type` | string | `"polynomial"` | `"polynomial"` or `"linear"` |
| `xp-curve.base` | double | `100.0` | Base XP for level 1 |
| `xp-curve.multiplier` | double | `1.0` | XP multiplier |
| `xp-curve.exponent` | double | `1.5` | Exponent for polynomial curves |
| `max-daily-earnings` | double | `-1` | Per-job daily cap (-1 = unlimited) |
| `money-bonus-per-level` | double | `0.5` | Money multiplier increase per level |
| `max-level-money-bonus` | double | `50.0` | Cap for cumulative money multiplier |
| `skill-points-every` | int | `2` | Levels between skill point awards |
| `reset-progress-on-leave` | bool | `false` | Wipe level/XP when leaving job |
| `actions` | object | `{}` | Action → target → reward mappings |
| `license-objectives` | array | `[]` | License quest objectives |
| `skills` | object | `{}` | Skill tree definitions |
| `crate-rewards` | array | `[]` | Per-action crate key drop config (see below) |
| `unlock-requirements` | object | `{unlockedByDefault: true}` | Rank/permission requirements to access profession |
| `level-up-rewards` | object | `{}` | Level → command array rewards |
| `messages` | object | `{}` | Custom messages (join, leave, level-up) |
| `how-to-earn` | object | optional | Money/XP help text |

### actions Structure

```json
"actions": {
  "ACTION-TYPE": {
    "target_id": { "money": 5.0, "xp": 10.0, "chance": 1.0 },
    "#tag_name":   { "money": 3.0, "xp": 6.0 },
    "*":           { "money": 1.0, "xp": 2.0 }
  }
}
```

- **target_id**: Exact registry ID match (e.g., `minecraft:coal_ore`, `cobblemon:pikachu`)
- **#tag_name**: Tag group match (e.g., `#ores`, `#rare_pokemon`)
- **`*`**: Wildcard fallback for any unmatched target
- **chance**: 0.0–1.0 probability (default 1.0 = always)

Evaluation order: exact match → tag match → wildcard.

### license-objectives Structure

```json
[{
  "objective-id": "capture_50",
  "action-type": "POKEMON_CAPTURED",
  "required-amount": 50,
  "match-tags": [],
  "match-target-ids": [],
  "require-non-player-placed": false,
  "require-mature": false,
  "progress-message": "Capture 50 Pokemon"
}]
```

### skills Structure

```json
"skill_id": {
  "id": "skill_id",
  "name": "Display Name",
  "description": "Skill effect description",
  "max-level": 5,
  "max-rank": 1,
  "point-cost": 1,
  "required-level": 5,
  "prerequisites": ["other_skill_id:1"],
  "effects": {"xp-multiplier": 0.03}
}
```

### crate-rewards Structure

```json
"crate-rewards": [
  {
    "actions": ["POKEMON-CAPTURED"],
    "key-id": "researcher_key",
    "chance": 0.02,
    "amount": 1,
    "minimum-job-level": 5,
    "required-rank-id": "adept",
    "daily-limit": 5,
    "cooldown-seconds": 3600
  },
  {
    "actions": [],
    "key-id": "craft_key",
    "chance": 0.005,
    "amount": 1,
    "minimum-job-level": 1,
    "daily-limit": 3,
    "cooldown-seconds": 1800
  }
]
```

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------|
| `actions` | string[] | `[]` (qualquer ação) | Action types que ativam esta recompensa (case-insensitive, vazio = todas) |
| `key-id` | string | `"craft_key"` | ID da chave de crate a ser concedida |
| `chance` | double | `0.005` | Probabilidade por ação (0.0–1.0, 0.005 = 0.5%) |
| `amount` | int | `1` | Quantidade de chaves por concessão |
| `minimum-job-level` | int | `1` | Nível mínimo da profissão para ativar |
| `required-rank-id` | string\|null | `null` | Rank mínimo do RankUp (opcional) |
| `daily-limit` | int | `3` | Limite diário por jogador (0 = bloqueado) |
| `cooldown-seconds` | long | `1800` | Cooldown entre concessões (em segundos) |

**Notas**:
- Se a lista `crate-rewards` estiver vazia, o sistema usa o fallback legado `JobKeyDropRule` (0.5% base + 0.02%/level, sem limites diários/cooldown).
- A validação rejeita: `chance` fora de 0–1, `amount` < 1, `key-id` vazio, `minimum-job-level` < 1, `daily-limit` < 0.
- Action types desconhecidos geram warning, não erro.

### unlock-requirements Structure

```json
"unlock-requirements": {
  "unlocked-by-default": false,
  "required-rank-id": "adept",
  "required-rank-order": 3,
  "permission": "jobs.profession.raider"
}
```

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------|
| `unlocked-by-default` | bool | `true` | Se true, ignora todas as outras verificações |
| `required-rank-id` | string\|null | `null` | ID do rank específico necessário |
| `required-rank-order` | int | `0` | Ordem mínima do rank (0 = sem verificação) |
| `permission` | string\|null | `null` | Permissão LuckPerms necessária |

**Lógica de avaliação** (AND):
1. Se `unlockedByDefault == true` → liberado
2. Verifica milestones de carreira (slots)
3. Se `requiredRankId` presente → jogador deve ter rank ≥ aquele ID
4. Se `requiredRankOrder > 0` → ordem do rank atual deve ser ≥ requiredRankOrder
5. Se `permission` presente → jogador deve ter a permissão no LuckPerms

### level-up-rewards Structure

```json
"10": ["give %player% cobblemon:poke_ball 16"],
"50": ["give %player% cobblemon:ultra_ball 32"]
```

### how-to-earn Structure

```json
{
  "money-header": "Como ganhar dinheiro",
  "xp-header": "Como ganhar XP",
  "money-lines": ["Do X to earn money."],
  "xp-lines": ["Every action grants XP."],
  "example-targets": ["target_id_1", "target_id_2"]
}
```

## XP Curves

### Polynomial (default)

`xpRequired = base × level^exponent × multiplier`

Example (base=100, multiplier=1.0, exponent=1.5):
- Level 1: 100 XP
- Level 10: 3,162 XP
- Level 50: 35,355 XP
- Level 100: 100,000 XP

### Linear

`xpRequired = base + multiplier × (level - 1)`

Example (base=100, multiplier=50):
- Level 1: 100 XP
- Level 10: 550 XP
- Level 50: 2,550 XP
- Level 100: 5,050 XP

## Validation Rules

**On load, the system validates:**

1. `schema-version` must be ≥ 1
2. At least one profession must be loaded
3. At least one slot must be configured
4. `display-name` cannot be empty
5. `category` must be `"COMMON"` or `"POKEMON_SPECIALIZATION"`
6. `max-level` must be ≥ 1
7. `money-bonus-per-level` cannot be negative
8. `max-level-money-bonus` cannot be negative
9. Profession `id` must match filename
10. **No duplicate profession IDs**
11. All action reward `money` values must be ≥ 0
12. All action reward `xp` values must be ≥ 0
13. Action type strings must be known (warn on unknown, don't reject)
14. Target IDs (non-tag) must be valid ResourceLocations
15. Skill `max-rank` must be ≥ 1
16. Skill `point-cost` cannot be negative
17. Skill `required-level` must be ≥ 1
18. Skill prerequisites must reference existing skills
19. **No circular dependencies in skill trees**
20. Milestone `eligible-jobs` must reference existing professions
21. Milestone `unlocked-slots` must reference existing slots
22. **Crate reward `chance` must be 0.0–1.0**
23. **Crate reward `amount` must be ≥ 1**
24. **Crate reward `key-id` cannot be empty**
25. **Crate reward `minimum-job-level` must be ≥ 1**
26. **Crate reward `daily-limit` must be ≥ 0**
27. **Crate reward action types: unknown values generate warning only**

## Reload Behavior

Triggered by `/jobsadmin reload` or `/jobs admin reload`.

1. **Atomic swap**: Full config is loaded into a new `JobsConfig` object before the old one is replaced
2. **Rollback on failure**: If `loadAndValidate()` returns null or throws, old config stays active
3. **Shutdown old integrations**: All bridge listeners are unsubscribed
4. **Reprobe integrations**: Each bridge re-probes for mod presence and re-subscribes events
5. **Player caches preserved**: `playerDataCache` is not cleared on reload
6. **Daily cycle check**: Current daily cycle is recalculated against new timezone/reset-time

**NOTE**: Rollback covers config loader failure only. If config loads but an integration fails to subscribe, the config still updates. Integration enters `DEGRADED` state.

## Backup (.bak on Migration)

When configs exist at the canonical path and legacy configs are detected during migration:

1. Existing canonical configs are copied to `config/bigbangessentials/jobs_backup_<yyyyMMdd_HHmmss>/`
2. Legacy configs (`world/serverconfig/bigbangessentials/jobs/`) are copied to canonical path
3. A `.migrated` marker file is written in the legacy directory

Manual backup: copy the entire `config/bigbangessentials/jobs/` directory.

## Migration from `world/serverconfig/`

Automatic on server start (in `JobsConfigLoader.migrateIfNeeded()`):

1. **Detection**: If `world/serverconfig/bigbangessentials/jobs/` exists
2. **Backup canonical**: Create timestamped backup of `config/bigbangessentials/jobs/`
3. **Copy**: Move files from legacy to canonical
4. **Mark**: Write `.migrated` file in legacy dir
5. **Proceed**: Load from canonical path

After migration, the legacy directory is left intact with the `.migrated` marker. Future restarts skip migration.
