# Kit Management

> **Version:** 1.0.2.6 · **Config:** `kits.json`, `config.json` → `kits` section

---

## Overview

Create item kits with cooldowns, permission gates, and command execution on claim. Players can preview kits before claiming. Staff can give kits to other players and reset cooldowns.

---

## Commands

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/kit` | `/kit` | `bigbangessentials.kits.use` | List available kits with cooldown status |
| `/kit` | `/kit <name> [player]` | `bigbangessentials.kits.use` / `bigbangessentials.kit.others` | Claim a kit (or give to another player) |
| `/kits` | alias | same | Alias |
| `/listkits` | alias | same | Alias |
| `/showkit` | `/showkit <name>` | `bigbangessentials.showkit` | Preview kit contents without claiming |
| `/createkit` | `/createkit <name> [cooldownHours]` | `bigbangessentials.kits.create` / `bigbangessentials.kits.admin.create` | Create kit from current inventory |
| `/delkit` | `/delkit <name>` | `bigbangessentials.kits.delete` / `bigbangessentials.kits.admin.delete` | Delete a kit |
| `/kitreset` | `/kitreset <kit> [player]` | `bigbangessentials.kitreset` / `bigbangessentials.kitreset.others` | Reset a kit cooldown |

> Compatibility note: the runtime also accepts the legacy singular kit nodes (`bigbangessentials.kit.*`) and the bundled admin nodes (`bigbangessentials.kits.admin.*`) so older permission setups keep working.

---

## Permissions

| Node | Default | Description |
|---|---|---|
| `bigbangessentials.kits` | ✅ | List and claim kits |
| `bigbangessentials.kits.others` | 🔒 | Give a kit to another player |
| `bigbangessentials.kits.create` | 🔒 | Create kits with `/createkit` |
| `bigbangessentials.kits.delete` | 🔒 | Delete kits with `/delkit` |
| `bigbangessentials.kits.override` | 🔒 | Bypass kit cooldowns |
| `bigbangessentials.kitreset` | 🔒 | Reset your own kit cooldown |
| `bigbangessentials.kitreset.others` | 🔒 | Reset another player's cooldown |
| `bigbangessentials.kit.<name>` | — | Restrict a specific kit to players with this node |

---

## Kit Data Format (`kits.json`)

```json
{
  "kits": [
    {
      "name": "starter",
      "cooldownHours": 24,
      "items": [
        { "item": "minecraft:stone_sword", "count": 1 },
        { "item": "minecraft:bread", "count": 16 }
      ],
      "commands": [
        "say Welcome {player}!"
      ]
    }
  ]
}
```

| Field | Description |
|---|---|
| `name` | Kit name (used in `/kit <name>`) |
| `cooldown` | Seconds between claims (`0` = no cooldown, `-1` = one-time) |
| `items` | List of items — `item` (registry ID), `count`, optional `nbt` |
| `commands` | Server commands run on claim; `{player}` replaced with claimer name |

---

## Config (`config.json` → `kits`)

| Key | Default | Description |
|---|---|---|
| `skipUsedOneTimeKitsFromKitList` | `true` | Hide one-time kits after claimed |
| `kitAutoEquip` | `true` | Auto-equip armour from kits into empty armour slots |
| `maxKitsPerPlayer` | `0` | Max simultaneous active cooldowns (0 = unlimited) |
| `allowKitOverride` | `true` | Allow `bigbangessentials.kits.override` bypass |
| `enableKitPreview` | `true` | Enable `/showkit` preview |
| `newPlayerKit` | `""` | Kit name to auto-give on first join (blank = disabled) |
| `logKitUsage` | `true` | Log kit claims to console |

---

## How Cooldowns Work

- Cooldown starts the moment a kit is successfully claimed
- Staff with `bigbangessentials.kits.override` bypass cooldowns entirely
- `/kitreset <kit>` clears a specific cooldown
- `/kitreset <kit> <player>` requires `bigbangessentials.kitreset.others`
- One-time kits (`cooldown: -1`) can never be re-claimed

---

*Back to [Wiki Home](Home)*
