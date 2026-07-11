# RankUp System — `/rankup` & `/rankupadmin`

The RankUp module provides a visual progression ladder where players complete requirements (money, gems, tasks) to advance through ranks. Each rank is associated with a LuckPerms group, allowing automatic permission changes on promotion.

---

## 1. Overview

Players progress through a configurable ladder of ranks. Each rank after the initial one can require:

1. **Money** — A configurable amount from the server economy.
2. **Gems** — A configurable amount from the BigBangEssentials Gems system.
3. **Tasks** — One or more gameplay objectives (e.g., break 30 logs, kill 10 zombies).

When all requirements are met, the player can promote through the `/rankup` GUI. On promotion, the RankUp module:
- Withdraws the required money and gems with idempotency protection.
- Updates the player's LuckPerms groups (ladder groups are replaced, unrelated groups preserved).
- Resets completed task progress.
- Executes optional post-rank commands (e.g., give items, broadcast message).
- Records the promotion in the rank history.

---

## 2. Configuration — `rankup.json`

The RankUp config is stored in `config/bigbangessentials/rankup.json`.

### Ladder Settings

| Field | Type | Description |
|---|---|---|
| `id` | string | Unique ladder identifier (default: `"main"`) |
| `display-name` | string | Ladder display name with color codes |
| `initial-rank-id` | string | ID of the starting rank |
| `luckperms-mode` | enum | How LuckPerms groups are managed on promotion |
| `require-confirmation` | boolean | Whether promotion requires a confirmation click |

### LuckPerms Modes

| Mode | Behavior |
|---|---|
| `REPLACE_LADDER_INHERITANCE_AND_PRIMARY` | Remove all ladder groups, add target group, set as primary |
| `REPLACE_LADDER_INHERITANCE` | Remove all ladder groups, add target group |
| `ADD_INHERITANCE` | Add target group without removing existing |
| `SET_PRIMARY_GROUP` | Only set the primary group |

### Rank Definition

```json
{
  "id": "trainer",
  "order": 1,
  "display-name": "&aTrainer",
  "description": ["&7Prove your worth."],
  "enabled": true,
  "icon": {
    "item": "minecraft:iron_sword",
    "custom-model-data": 0
  },
  "luckperms": {
    "group": "trainer",
    "set-as-primary-group": true,
    "mode": "REPLACE_LADDER_INHERITANCE_AND_PRIMARY"
  },
  "requirements": {
    "money": 5000.0,
    "gems": 3,
    "task-mode": "ALL",
    "tasks": [...]
  },
  "actions": {
    "broadcast": "&a%player% became a &fTrainer&a!",
    "commands": ["give %player% minecraft:diamond 3"]
  }
}
```

### Task Definition

```json
{
  "id": "break_logs",
  "display-name": "&6Wood Collector",
  "description": ["&7Break 30 logs."],
  "type": "BREAK_BLOCK",
  "target": 30,
  "enabled": true,
  "filters": {
    "blocks": ["#minecraft:logs"]
  }
}
```

### Task Types

| Type | Description | Filters |
|---|---|---|---|
| `BREAK_BLOCK` | Break specific blocks | `blocks` (IDs or tags like `#minecraft:logs`) |
| `PLACE_BLOCK` | Place specific blocks | `blocks` |
| `KILL_ENTITY` | Kill specific entities | `entities` (IDs or tags) |
| `FISH` | Fish specific items | `items` |
| `CRAFT_ITEM` | Craft specific items | `items` |
| `SMELT_ITEM` | Smelt specific items | `items` |
| `ADVANCEMENT` | Earn specific advancements | `advancements` |
| `VISIT_BIOME` | Visit specific biomes | `biomes` |
| `PLAYTIME_MINUTES` | Accumulate playtime | None |

> **Playtime tracking note**: `PLAYTIME_MINUTES` only counts playtime accumulated **after** the mod is installed (tracked via `RankupPlaytimeTracker`). Pre-existing playtime is not counted. The tracker relies on tick registration (`FabricEvents` / `NeoForgeEvents`) and may show 0 if tick events are not properly registered on the platform.

### Task Mode

| Mode | Behavior |
|---|---|
| `ALL` | All enabled tasks must be completed |
| `ANY` | At least one enabled task must be completed |

### Filter Format

- **Exact ID**: `"minecraft:oak_log"` — matches exactly that block/item/entity.
- **Tag**: `"#minecraft:logs"` — matches any entry in the tag group.
- **Modded**: `"create:zinc_ore"` — matches modded items by registry ID.

### Color Codes (`&` → `§` Translation)

All text fields in the RankUp config support `&` color codes (e.g., `&a`, `&6`, `&l`). The system automatically translates `&` to the Minecraft section sign `§` at config load time via `RankupConfig.translateColors()`.

**Affected fields**:
- `ladder.display-name`
- `rank.display-name` and `rank.description[]`
- `task.display-name` and `task.description[]`
- `actions.broadcast`

**Preserved in UI**: The menu system (`RankupMenuSupport`), promotion messages (`RankupPromotionService`), and task formatters (`RankupFormatter`) no longer strip color codes after translation.

---

## 3. Player Commands

### `/rankup`
Opens the RankUp progression GUI showing all ranks and your progress.

### `/rankup info [rank]`
Shows details about the current rank or a specific rank.

### `/rankup tasks`
Lists all tasks for the next rank with progress.

### `/rankup progress`
Shows summary of current rank and progress toward next rank.

**Permission**: `bigbangessentials.rankup.use`

---

## 4. Admin Commands

### `/rankupadmin`
Opens the RankUp administration GUI.

### `/rankupadmin editor`
Same as above — opens the admin home menu.

### `/rankupadmin reload`
Reloads RankUp configuration from disk.

### `/rankupadmin inspect <player>`
Shows a player's current and next rank.

### `/rankupadmin set <player> <rank>`
Force-sets a player to a specific rank.

### `/rankupadmin advance <player>`
Advances a player to the next rank (bypasses requirements).

### `/rankupadmin resetprogress <player>`
Resets all task progress for a player.

### `/rankupadmin resettasks <player>`
Resets all task progress (alias for resetprogress).

### `/rankupadmin history <player>`
Shows the rank promotion history for a player.

### `/rankupadmin retryrecovery <transaction>`
Retries a failed promotion transaction recovery (not yet implemented).

### Admin Permissions

| Permission | Description |
|---|---|
| `bigbangessentials.rankup.admin` | Base admin access |
| `bigbangessentials.rankup.admin.editor` | Access rank editor |
| `bigbangessentials.rankup.admin.reload` | Reload configuration |
| `bigbangessentials.rankup.admin.inspect` | Inspect player progress |
| `bigbangessentials.rankup.admin.set` | Force-set player rank |
| `bigbangessentials.rankup.admin.advance` | Advance player |
| `bigbangessentials.rankup.admin.reset` | Reset task progress |
| `bigbangessentials.rankup.admin.history` | View rank history |
| `bigbangessentials.rankup.admin.recovery` | Retry failed transactions |

---

## 5. Admin GUI Workflow

### Creating a Rank

1. Run `/rankupadmin`.
2. Click **Create New Rank** (green wool).
3. Click the new rank in the paginated list to open the **rank editor**.
4. Configure:
   - **Set Display Name** — Chat input for the display name.
   - **Set Icon** — Chat input for the icon item ID.
   - **Set LP Group** — Chat input for the LuckPerms group.
   - **Set Money Requirement** — Chat input for the money amount.
   - **Set Gems Requirement** — Chat input for the gems amount.
   - **Toggle Rank** — Enable/disable.
   - **Duplicate Rank** — Create a copy.
   - **Move Up/Down** — Reorder the rank in the ladder.
   - **Delete Rank** — Remove permanently.
5. Use **Back to Admin Home** to return.
6. Click **Save Draft** (slime ball) to validate and save.
7. Run `/rankupadmin reload` to apply changes.

### Creating a Task

1. Open a rank in the editor.
2. (Task creation is currently done via rank editor menu actions. Full task editor GUI with visual block/item selection is a future enhancement.)

### Saving Changes

- Click **Save Draft** in the admin home menu.
- The entire configuration is validated before saving.
- A backup (`rankup.json.bak`) is created before overwriting.
- Click **Discard Draft** to revert all unsaved changes.

---

## 6. Promotion Transaction Flow

When a player promotes, the following ordered steps execute:

1. **Acquire promotion lock** — Per-player serialization prevents concurrent promotions.
2. **Validate requirements** — Current rank, next rank, tasks, money, gems.
3. **Save PREPARED transaction** — Persistent record with idempotency key.
4. **Withdraw money** — Via `EconomyAPI.withdraw()`.
5. **Debit gems** — Via `GemsManager.debit()` with idempotency key.
6. **Update LuckPerms** — Remove ladder groups, add target group, save user.
7. **Reset task progress** — Clear completed tasks for old rank.
8. **Record history** — Entry in `rankup_rank_history` table.
9. **Execute post-rank commands** — Broadcast message and console commands.
10. **Refresh menus** — Update player UI.

On failure at any step:
- **Before LP update**: Money and gems are compensated (refunded).
- **After LP update failure**: Transaction marked `RECOVERY_REQUIRED`.
- **Unexpected errors**: Compensation attempted, transaction logged.

---

## 7. Database Tables

### `rankup_task_progress`
Stores per-player, per-task completion progress.

### `rankup_transactions`
Stores every promotion attempt with status tracking for audit and recovery.

### `rankup_rank_history`
Stores rank change history for player lookup.

---

## 8. Placeholders

| Placeholder | Description |
|---|---|
| `{rankup:current_id}` | Current rank ID |
| `{rankup:current_name}` | Current rank display name |
| `{rankup:next_id}` | Next rank ID |
| `{rankup:next_name}` | Next rank display name |
| `{rankup:money_required}` | Money needed for next rank |
| `{rankup:gems_required}` | Gems needed for next rank |
| `{rankup:money_balance}` | Player's current money balance |
| `{rankup:gems_balance}` | Player's current gem balance |
| `{rankup:money_status}` | ✔ or ✘ indicator for money |
| `{rankup:gems_status}` | ✔ or ✘ indicator for gems |
| `{rankup:tasks_completed}` | Number of tasks completed |
| `{rankup:tasks_total}` | Total number of tasks |
| `{rankup:progress_percent}` | Overall progress percentage |

---

## 9. Anti-Exploit Protections

The RankUp task engine applies the same anti-exploit rules as the Jobs module:

- **Cancelled events** — Ignored (blocked by protection plugins).
- **Fake players** — Ignored (mod/plugin simulated players).
- **AFK players** — Ignored (no progress while AFK).
- **Player-placed blocks** — Breaking self-placed blocks does not count.
- **Spawner entities** — Killing spawner mobs does not count (configurable).
- **Position cooldown** — Same block position has a 5-minute cooldown.
- **Duplicate per tick** — Only one progress per player-action per tick.
- **Automation** — Machine/automation actions detected and blocked.

---

## 10. Limitations

1. **Single ladder only** — Multi-ladder support is not implemented.
2. **No rank cooldowns** — Players can promote immediately when requirements are met.
3. **No branching paths** — Ranks form a linear progression.
4. **Cobblemon** — Requires NeoForge; Fabric does not support Cobblemon integration.
5. **Task editor** — Full visual task editor with held-item selection is pending.
6. **Transaction recovery** — `/rankupadmin retryrecovery` is not yet implemented.
7. **Playtime tracking** — `PLAYTIME_MINUTES` counts only post-install playtime. Verify tick registration if counter stays at 0.
