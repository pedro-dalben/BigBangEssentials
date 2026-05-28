# BigBangEssentials — Permission System

> **Last updated:** 2026-03-06 · **Version:** 1.0.2.6  
> **Source of truth:** `PermissionRegistry.registerAllPermissions()` in the mod source.  
> All nodes listed here are **actively registered** and recognised by the permission engine.  
> Nodes marked `✅ default` are granted to every player automatically (including non-OP).  
> Nodes marked `🔒 op-only` require explicit grant or OP level 2+ unless overridden.

---

## Table of Contents
1. [Configuration](#configuration)
2. [How Permissions Work](#how-permissions-work)
3. [Wildcards & Inheritance](#wildcards--inheritance)
4. [Dynamic Nodes](#dynamic-nodes)
5. [Permission Nodes — Full Reference](#permission-nodes--full-reference)
   - [Core](#core)
   - [Economy](#economy)
   - [Teleportation](#teleportation)
   - [Kits](#kits)
   - [Items](#items)
   - [Chat & Messaging](#chat--messaging)
   - [Moderation](#moderation)
   - [Miscellaneous Utilities](#miscellaneous-utilities)
   - [Admin & Config](#admin--config)
   - [Permission System Commands](#permission-system-commands)
   - [Web Dashboard](#web-dashboard)
6. [Example groups.json](#example-groupsjson)
7. [External Permission Mods](#external-permission-mods)

---

## Configuration

**`config.json` → `permissions` section:**

| Key | Default | Description |
|---|---|---|
| `useExternalPermissions` | `false` | Use LuckPerms / FTB Ranks instead of built-in engine |
| `defaultGroup` | `"default"` | Group assigned to new players |
| `opsBypassPermissions` | `true` | OPs (level 2+) bypass all permission checks |
| `cachePermissions` | `true` | Cache permission lookups for performance |
| `permissionCacheExpiryMinutes` | `5` | How long cached results are valid |

**Permission data file:** `bigbangessentials/permissions.json`

---

## How Permissions Work

1. When a player runs a command, `PermissionValidator.validatePermission()` is called.
2. It checks `PermissionAPI.hasPermission(uuid, node)`.
3. `PermissionAPI` checks (in order):
   - Player's explicit node grants/denials
   - Player's group (and inherited groups, highest priority first)
   - Wildcard nodes (`bigbangessentials.*`, `bigbangessentials.teleport.*`, etc.)
   - `opsBypassPermissions` — OPs skip the check entirely if enabled
4. If denied, the player sees: `§cYou don't have permission to use this command. §7Required: §f<node>`

---

## Wildcards & Inheritance

| Wildcard | Grants access to |
|---|---|
| `bigbangessentials.*` | Every permission in the mod |
| `bigbangessentials.economy.*` | All economy nodes |
| `bigbangessentials.teleport.*` | All teleport nodes |
| `bigbangessentials.teleport.admin.*` | All admin-teleport nodes |
| `bigbangessentials.teleport.home.*` | All home nodes |
| `bigbangessentials.teleport.request.*` | All TPA request nodes |
| `bigbangessentials.teleport.spawn.*` | All spawn nodes |
| `bigbangessentials.teleport.warp.*` | All warp nodes |
| `bigbangessentials.kits.*` | All kit nodes |
| `bigbangessentials.item.*` | All item management nodes |
| `bigbangessentials.chat.*` | All chat nodes |
| `bigbangessentials.moderation.*` | All moderation nodes |
| `bigbangessentials.permissions.*` | All permissions-command nodes |

> **Negative permissions** — prefix a node with `-` to explicitly deny it even if a wildcard grants it.  
> Example: give `bigbangessentials.*` then add `-bigbangessentials.item.enchant.unsafe` to deny unsafe enchanting.

---

## Dynamic Nodes

These are **not pre-registered** but are checked at runtime:

### Home limit
Pattern: `bigbangessentials.home.<number>` (1–100)  
The **highest number** the player has is used as their home limit.  
Example: `bigbangessentials.home.5` → player can set 5 homes.  
If no home-limit node is found, the config default is used.

### Warp limit
Pattern: `bigbangessentials.warp.limit.<number>` (1–100)  
Example: `bigbangessentials.warp.limit.10` → player can create 10 player-warps.  
Special: `bigbangessentials.warp.limit.unlimited` → no limit.

### Per-kit nodes
Pattern: `bigbangessentials.kits.<kitname>` — grants access to that specific kit.  
Pattern: `bigbangessentials.kits.<kitname>.nocooldown` — bypasses the cooldown for that kit.  
These are **registered automatically** when a kit is created via `/createkit`.

---

## Permission Nodes — Full Reference

### Core

| Node | Default | Description |
|---|---|---|
| `bigbangessentials.use` | ✅ default | Basic mod usage — required for all commands |
| `bigbangessentials.info` | ✅ default | View mod information (`/neoe`) |

---

### Economy

| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.economy.balance` | ✅ default | Check own balance | `/balance` |
| `bigbangessentials.economy.balance.others` | 🔒 op-only | Check another player's balance | `/balance <player>` |
| `bigbangessentials.economy.pay` | ✅ default | Send money to online players | `/pay` |
| `bigbangessentials.economy.pay.offline` | 🔒 op-only | Send money to offline players | `/pay` |
| `bigbangessentials.economy.pay.toggle` | ✅ default | Toggle receiving payments | `/paytoggle` |
| `bigbangessentials.economy.baltop` | ✅ default | View balance leaderboard | `/baltop [page]` |
| `bigbangessentials.economy.baltop.exempt` | 🔒 op-only | Exclude self from baltop ranking | |
| `bigbangessentials.economy.eco` | 🔒 op-only | Run eco admin commands | `/eco` |
| `bigbangessentials.economy.admin` | 🔒 op-only | Economy administration (parent node) | `/eco` |
| `bigbangessentials.economy.admin.give` | 🔒 op-only | Give money to a player | `/eco give` |
| `bigbangessentials.economy.admin.take` | 🔒 op-only | Take money from a player | `/eco take` |
| `bigbangessentials.economy.admin.set` | 🔒 op-only | Set a player's balance | `/eco set` |
| `bigbangessentials.economy.admin.reset` | 🔒 op-only | Reset a player's balance to starting balance | `/eco reset` |
| `bigbangessentials.worth` | ✅ default | Check sell value of item | `/worth [item] [amount]` |
| `bigbangessentials.sell` | ✅ default | Use the sell command | `/sell` |
| `bigbangessentials.sell.hand` | ✅ default | Sell item in hand | `/sell hand [amount]` |
| `bigbangessentials.sell.bulk` | ✅ default | Sell entire inventory | `/sell inventory\|all` |
| `bigbangessentials.setworth` | 🔒 op-only | Set item sell prices | `/setworth <item\|hand> <price\|remove>` |

---

### Teleportation

#### Admin Teleport
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.teleport.admin` | 🔒 op-only | Admin teleport (parent node) | |
| `bigbangessentials.teleport.admin.tp` | 🔒 op-only | Teleport a player to another | `/tp <player> <target>` |
| `bigbangessentials.teleport.tp` | 🔒 op-only | Teleport self (alias) | `/tp <player>` |
| `bigbangessentials.teleport.admin.tphere` | 🔒 op-only | Bring a player to you | `/tphere` |
| `bigbangessentials.teleport.tphere` | 🔒 op-only | Bring a player to you (alias) | `/tphere` |
| `bigbangessentials.teleport.admin.tpall` | 🔒 op-only | Teleport all players to a target | `/tpall` |
| `bigbangessentials.teleport.admin.tppos` | 🔒 op-only | Teleport to coordinates | `/tppos` |
| `bigbangessentials.teleport.tppos` | 🔒 op-only | Teleport to coordinates (alias) | `/tppos` |
| `bigbangessentials.teleport.admin.tpo` | 🔒 op-only | Teleport to offline player's last location | `/tpo` |

#### Teleport Requests (TPA)
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.teleport.request.tpa` | ✅ default | Send a teleport request | `/tpa <player>` |
| `bigbangessentials.teleport.request.tpahere` | ✅ default | Request a player teleport to you | `/tpahere <player>` |
| `bigbangessentials.teleport.request.accept` | ✅ default | Accept a teleport request | `/tpaccept` |
| `bigbangessentials.teleport.request.deny` | ✅ default | Deny a teleport request | `/tpdeny` |
| `bigbangessentials.teleport.request.cancel` | ✅ default | Cancel a sent request | `/tpcancel` |

#### Home System
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.teleport.home` | ✅ default | Use the home system | `/home` |
| `bigbangessentials.teleport.home.set` | ✅ default | Set a home location | `/sethome` |
| `bigbangessentials.teleport.home.delete` | ✅ default | Delete a home | `/delhome` |
| `bigbangessentials.teleport.home.list` | ✅ default | List homes | `/homes` |
| `bigbangessentials.teleport.home.others` | 🔒 op-only | Access other players' homes | `/home <player>:<name>` |
| `bigbangessentials.home.<number>` | — | **Dynamic** — sets home limit (see above) | |

#### Warp System
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.teleport.warp` | ✅ default | Use warps | `/warp <name>` |
| `bigbangessentials.teleport.warp.list` | ✅ default | List available warps | `/warps [page]`, `/warp` |
| `bigbangessentials.teleport.warp.others` | 🔒 op-only | Warp another player to a warp | `/warp <name> <player>` |
| `bigbangessentials.teleport.warp.create` | 🔒 op-only | Create a warp | `/setwarp` |
| `bigbangessentials.teleport.warp.delete` | 🔒 op-only | Delete a warp | `/delwarp` |
| `bigbangessentials.warps.<name>` | — | **Per-warp** — access to specific warp (when `perWarpPermission: true` in config) | |
| `bigbangessentials.warps.*` | 🔒 op-only | Access ALL warps regardless of per-warp permissions | |
| `bigbangessentials.teleport.pwarp` | ✅ default | Use player warps | `/pwarp` |
| `bigbangessentials.teleport.pwarp.create` | ✅ default | Create a player warp | `/pwarp create` |
| `bigbangessentials.teleport.pwarp.delete` | ✅ default | Delete a player warp | `/pwarp delete` |
| `bigbangessentials.teleport.pwarp.list` | ✅ default | List player warps | `/pwarp list` |
| `bigbangessentials.warp.limit.<number>` | — | **Dynamic** — sets player-warp limit | |
| `bigbangessentials.warp.limit.unlimited` | 🔒 op-only | Unlimited player warps | |

#### Spawn System
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.teleport.spawn` | ✅ default | Teleport to spawn | `/spawn` |
| `bigbangessentials.teleport.spawn.set` | 🔒 op-only | Set the server spawn | `/setspawn` |
| `bigbangessentials.teleport.spawn.info` | 🔒 op-only | View spawn info | `/spawninfo` |
| `bigbangessentials.teleport.spawn.clear` | 🔒 op-only | Clear spawn location | `/clearspawn` |

#### Misc Teleport
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.teleport.back` | ✅ default | Return to previous location | `/back` |
| `bigbangessentials.teleport.death` | ✅ default | Teleport to death location | `/back` (on death) |
| `bigbangessentials.teleport.top` | ✅ default | Teleport to highest block | `/top` |
| `bigbangessentials.teleport.jump` | ✅ default | Teleport through walls | `/jump` |
| `bigbangessentials.teleport.jumpto` | ✅ default | Teleport to block you're looking at | `/jumpto` |
| `bigbangessentials.teleport.tpr` | ✅ default | Random teleport | `/tpr` |
| `bigbangessentials.teleport.settpr` | 🔒 op-only | Set random teleport centre | `/settpr` |

---

### Kits

| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.kits.use` | ✅ default | Use the kit system | `/kit` |
| `bigbangessentials.kits.list` | ✅ default | List available kits | `/kit`, `/listkits` |
| `bigbangessentials.kits.nocooldown` | 🔒 op-only | Bypass all kit cooldowns | |
| `bigbangessentials.kit.others` | 🔒 op-only | Give a kit to another player | `/kit <name> <player>` |
| `bigbangessentials.kitreset` | 🔒 op-only | Reset own kit cooldown | `/kitreset <kit>` |
| `bigbangessentials.kitreset.others` | 🔒 op-only | Reset another player's kit cooldown | `/kitreset <kit> <player>` |
| `bigbangessentials.kits.create` | 🔒 op-only | Create a kit from inventory | `/createkit` |
| `bigbangessentials.kits.delete` | 🔒 op-only | Delete a kit | `/delkit` |
| `bigbangessentials.kits.override` | 🔒 op-only | Override all kit restrictions | |
| `bigbangessentials.kits.admin` | 🔒 op-only | Kit administration (parent) | `/kit admin` |
| `bigbangessentials.kits.admin.create` | 🔒 op-only | Admin kit creation | |
| `bigbangessentials.kits.admin.delete` | 🔒 op-only | Admin kit deletion | |
| `bigbangessentials.kits.admin.list` | 🔒 op-only | List all kits (admin) | |
| `bigbangessentials.kits.<kitname>` | — | **Dynamic** — access to specific kit | |
| `bigbangessentials.kits.<kitname>.nocooldown` | — | **Dynamic** — bypass cooldown for specific kit | |

---

### Player State & Admin Tools

| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.fly` | 🔒 op-only | Toggle flight mode | `/fly [on\|off]` |
| `bigbangessentials.fly.others` | 🔒 op-only | Toggle flight for another player | `/fly <player> [on\|off]` |
| `bigbangessentials.god` | 🔒 op-only | Toggle god mode (invincibility) | `/god [on\|off]` |
| `bigbangessentials.god.others` | 🔒 op-only | Toggle god mode for another player | `/god <player> [on\|off]` |
| `bigbangessentials.heal` | 🔒 op-only | Restore own health and hunger | `/heal` |
| `bigbangessentials.heal.others` | 🔒 op-only | Restore another player's health | `/heal <player>` |
| `bigbangessentials.feed` | 🔒 op-only | Restore own hunger | `/feed` |
| `bigbangessentials.feed.others` | 🔒 op-only | Restore another player's hunger | `/feed <player>` |
| `bigbangessentials.speed` | 🔒 op-only | Set own walk or fly speed (0–10) | `/speed [walk\|fly] <0-10>` |
| `bigbangessentials.speed.others` | 🔒 op-only | Set another player's speed | `/speed [walk\|fly] <0-10> <player>` |
| `bigbangessentials.ext` | ✅ default | Extinguish own fire | `/ext` |
| `bigbangessentials.ext.others` | 🔒 op-only | Extinguish another player | `/ext <player>` |
| `bigbangessentials.burn` | 🔒 op-only | Set a player on fire | `/burn <player> [seconds]` |
| `bigbangessentials.give` | 🔒 op-only | Give items to players | `/give <player> <item> [amount]` |
| `bigbangessentials.more` | 🔒 op-only | Fill held stack to max | `/more [amount]` |
| `bigbangessentials.hat` | 🔒 op-only | Wear held item as helmet | `/hat` |
| `bigbangessentials.exp` | ✅ default | View own XP info | `/exp [show]` |
| `bigbangessentials.exp.set` | 🔒 op-only | Set own XP | `/exp set <amount>` |
| `bigbangessentials.exp.set.others` | 🔒 op-only | Set another player's XP | `/exp set <amount> <player>` |
| `bigbangessentials.exp.give` | 🔒 op-only | Give XP to self | `/exp give <amount>` |
| `bigbangessentials.exp.give.others` | 🔒 op-only | Give XP to another player | `/exp give <amount> <player>` |
| `bigbangessentials.sudo` | 🔒 op-only | Run a command as another player | `/sudo <player> <command>` |
| `bigbangessentials.sudo.exempt` | 🔒 op-only | Cannot be sudo'd by non-console | |
| `bigbangessentials.playtime` | ✅ default | View own playtime | `/playtime` |
| `bigbangessentials.playtime.others` | 🔒 op-only | View another player's playtime | `/playtime <player>` |

---

### Items

### Server Admin Commands

| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.broadcast` | 🔒 op-only | Broadcast a message to all players | `/broadcast <msg>`, `/bc`, `/announce` |
| `bigbangessentials.time` | 🔒 op-only | View current world time | `/time` |
| `bigbangessentials.time.set` | 🔒 op-only | Set or add world time | `/time set\|add <value>`, `/day`, `/night` |
| `bigbangessentials.weather` | 🔒 op-only | Set world weather | `/weather <sun\|storm\|thunder> [dur]`, `/sun`, `/storm`, `/thunder` |
| `bigbangessentials.kill` | 🔒 op-only | Kill a player | `/kill <player>` |
| `bigbangessentials.kill.exempt` | 🔒 op-only | Exempt from being killed by /kill | |
| `bigbangessentials.kill.force` | 🔒 op-only | Force kill even exempt players | |
| `bigbangessentials.gamemode` | 🔒 op-only | Change own gamemode | `/gamemode <mode>` |
| `bigbangessentials.gamemode.others` | 🔒 op-only | Change another player's gamemode | `/gamemode <mode> <player>` |
| `bigbangessentials.teleport.tpo` | 🔒 op-only | Teleport to player (bypass tptoggle) | `/tpo <player>` |
| `bigbangessentials.teleport.tpohere` | 🔒 op-only | Bring player to you (bypass tptoggle) | `/tpohere <player>` |
| `bigbangessentials.teleport.tpoffline` | 🔒 op-only | Teleport to offline player's last position | `/tpoffline <player>` |

---

### Utility Commands

| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.ptime` | 🔒 op-only | Set own per-player time override | `/ptime [reset\|day\|night\|<ticks>]` |
| `bigbangessentials.ptime.others` | 🔒 op-only | Set another player's time override | `/ptime <value> <player>` |
| `bigbangessentials.pweather` | 🔒 op-only | Set own per-player weather override | `/pweather [reset\|sun\|storm]` |
| `bigbangessentials.pweather.others` | 🔒 op-only | Set another player's weather override | `/pweather <type> <player>` |
| `bigbangessentials.effect` | 🔒 op-only | Apply potion effects to players | `/effect <player> <effect\|clear> [dur] [amp]` |
| `bigbangessentials.spawnmob` | 🔒 op-only | Spawn entities at own location | `/spawnmob <mob> [amount]`, `/mob` |
| `bigbangessentials.spawnmob.others` | 🔒 op-only | Spawn entities at another player | `/spawnmob <mob> [amount] <player>` |
| `bigbangessentials.unlimited` | 🔒 op-only | Toggle unlimited item use | `/unlimited [list\|clear\|<item>]` |
| `bigbangessentials.unlimited.others` | 🔒 op-only | Toggle unlimited items for another player | `/unlimited <item> <player>` |
| `bigbangessentials.condense` | 🔒 op-only | Condense items to storage blocks | `/condense [item]` |

---

### Item Customisation & Miscellaneous

| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.me` | ✅ default | Broadcast action messages | `/me <action>` |
| `bigbangessentials.tptoggle` | ✅ default | Toggle own teleport request acceptance | `/tptoggle [on\|off]` |
| `bigbangessentials.tptoggle.others` | 🔒 op-only | Toggle tptoggle for another player | `/tptoggle <player> [on\|off]` |
| `bigbangessentials.gc` | 🔒 op-only | View server TPS, memory, uptime, chunk info | `/gc`, `/mem` |
| `bigbangessentials.lightning` | 🔒 op-only | Strike lightning at look target | `/lightning`, `/smite` |
| `bigbangessentials.lightning.others` | 🔒 op-only | Strike lightning at a named player | `/lightning <player>` |
| `bigbangessentials.skull` | 🔒 op-only | Get a player head item | `/skull [player]` |
| `bigbangessentials.itemname` | 🔒 op-only | Rename the held item | `/itemname [name\|-]`, `/rename` |
| `bigbangessentials.itemlore` | 🔒 op-only | Edit held item lore lines | `/itemlore add\|set\|remove\|clear` |
| `bigbangessentials.remove` | 🔒 op-only | Remove entities in radius | `/remove <type> [radius]` |
| `bigbangessentials.loom` | 🔒 op-only | Open portable loom | `/loom` |
| `bigbangessentials.cartography` | 🔒 op-only | Open portable cartography table | `/cartography`, `/cartographytable` |

---

### Home & Warp Enhancements

| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.renamehome` | ✅ default | Rename own home | `/renamehome <old> <new>` |
| `bigbangessentials.renamehome.others` | 🔒 op-only | Rename another player's home | `/renamehome <player:old> <new>` |
| `bigbangessentials.warpinfo` | ✅ default | Show warp coordinates and world | `/warpinfo <name>` |
| `bigbangessentials.world` | 🔒 op-only | Teleport to a world/dimension | `/world [name]` |
| `bigbangessentials.world.others` | 🔒 op-only | Teleport another player to a world | `/world <name> <player>` |
| `bigbangessentials.spawner` | 🔒 op-only | Change a mob spawner type | `/spawner <mob>` |
| `bigbangessentials.spawner.*` | 🔒 op-only | Change spawner to any mob | wildcard — grants all mob types |
| `bigbangessentials.spawner.<mob>` | 🔒 op-only | Change spawner to a specific mob | e.g. `bigbangessentials.spawner.zombie` |
| `bigbangessentials.recipe` | ✅ default | Show/unlock crafting recipe for an item | `/recipe [item]` |
| `bigbangessentials.tpauto` | ✅ default | Auto-accept all incoming teleport requests | `/tpauto [on\|off]` |
| `bigbangessentials.tpauto.others` | 🔒 op-only | Toggle tpauto for another player | `/tpauto <player> [on\|off]` |

---

### World Interaction & Fun Commands

| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.fireball` | 🔒 op-only | Shoot a projectile | `/fireball [type] [speed]` |
| `bigbangessentials.fireball.*` | 🔒 op-only | Shoot any projectile type | wildcard |
| `bigbangessentials.fireball.<type>` | 🔒 op-only | Shoot specific type (fireball/small/large/arrow/skull/egg/snowball/expbottle/dragon/trident/windcharge) | e.g. `bigbangessentials.fireball.arrow` |
| `bigbangessentials.fireball.ride` | 🔒 op-only | Ride the shot projectile | `/fireball <type> <speed> ride` |
| `bigbangessentials.tree` | 🔒 op-only | Grow a tree at look target | `/tree <type>`, `/bigtree` |
| `bigbangessentials.break` | 🔒 op-only | Instantly break the looked-at block (no drops) | `/break` |
| `bigbangessentials.break.bedrock` | 🔒 op-only | Break bedrock blocks | permission bypass |
| `bigbangessentials.ice` | 🔒 op-only | Freeze self solid | `/ice` |
| `bigbangessentials.ice.others` | 🔒 op-only | Freeze another player | `/ice <player>` |
| `bigbangessentials.bottom` | 🔒 op-only | Teleport to world bottom at current XZ | `/bottom` |
| `bigbangessentials.tpaall` | 🔒 op-only | Send tpa-here to all online players | `/tpaall [player]` |
| `bigbangessentials.tpaall.others` | 🔒 op-only | Send tpaall on behalf of another player | `/tpaall <player>` |
| `bigbangessentials.broadcastworld` | 🔒 op-only | Broadcast to players in your current world | `/broadcastworld`, `/bcastworld` |

---

### Player Info & Admin Tools

| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.seen` | ✅ default | View when a player was last online | `/seen <player>` |
| `bigbangessentials.near` | ✅ default | List players within a radius | `/near [radius]` |
| `bigbangessentials.ping` | ✅ default | View your own ping | `/ping` |
| `bigbangessentials.ping.others` | ✅ default | View another player's ping | `/ping <player>` |
| `bigbangessentials.playtime` | ✅ default | View your total play time | `/playtime` |
| `bigbangessentials.playtime.others` | ✅ default | View another player's play time | `/playtime <player>` |
| `bigbangessentials.whois` | 🔒 op-only | View detailed player info (UUID, pos, gamemode, health) | `/whois <player>` |
| `bigbangessentials.realname` | ✅ default | Look up real name from nickname | `/realname <nickname>` |
| `bigbangessentials.sudo` | 🔒 op-only | Force a player to run a command | `/sudo <player> <command>` |
| `bigbangessentials.sudo.exempt` | 🔒 op-only | Be immune to /sudo | permission node |
| `bigbangessentials.suicide` | ✅ default | Kill yourself | `/suicide` |
| `bigbangessentials.msgtoggle` | ✅ default | Toggle your incoming private messages on/off | `/msgtoggle [on\|off]` |
| `bigbangessentials.msgtoggle.others` | 🔒 op-only | Toggle another player's messages | `/msgtoggle <player> [on\|off]` |
| `bigbangessentials.rtoggle` | ✅ default | Toggle reply-to-last-sender for `/r` | `/rtoggle [on\|off]` |
| `bigbangessentials.rtoggle.others` | 🔒 op-only | Toggle rtoggle for another player | `/rtoggle <player> [on\|off]` |
| `bigbangessentials.motd` | ✅ default | View the message of the day | `/motd` |
| `bigbangessentials.rules` | ✅ default | View server rules | `/rules` |

---

| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.item.repair` | 🔒 op-only | Repair held item | `/repair` |
| `bigbangessentials.item.enchant` | 🔒 op-only | Enchant held item | `/enchant` |
| `bigbangessentials.item.enchant.unsafe` | 🔒 op-only | Apply enchants beyond vanilla limits | `/enchant` |
| `bigbangessentials.item.enchant.others` | 🔒 op-only | Enchant another player's item | `/enchant <player>` |
| `bigbangessentials.item.enchant.any` | 🔒 op-only | Enchant any item (ignore type restrictions) | `/enchant` |
| `bigbangessentials.item.powertool` | 🔒 op-only | Use the powertool system | `/powertool` |
| `bigbangessentials.item.powertool.toggle` | 🔒 op-only | Toggle powertool on/off | `/pttoggle` |
| `bigbangessentials.item.dispose` | ✅ default | Use the item disposal chest | `/dispose` |
| `bigbangessentials.item.clearinventory` | 🔒 op-only | Clear own inventory | `/clearinv` |
| `bigbangessentials.item.clearinventory.others` | 🔒 op-only | Clear another player's inventory | `/clearinv <player>` |
| `bigbangessentials.item.spawn` | 🔒 op-only | Spawn items | `/spawnitem` |
| `bigbangessentials.invsee` | 🔒 op-only | View another player's inventory | `/invsee` |
| `bigbangessentials.invsee.edit` | 🔒 op-only | Edit another player's inventory | `/invsee` |
| `bigbangessentials.enderchest` | 🔒 op-only | View another player's ender chest | `/ec <player>` |
| `bigbangessentials.enderchest.edit` | 🔒 op-only | Edit another player's ender chest | `/ec <player>` |

---

### Chat & Messaging

#### Private Messaging
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.chat.msg` | ✅ default | Send private messages | `/msg` |
| `bigbangessentials.chat.reply` | ✅ default | Reply to messages | `/reply` |
| `bigbangessentials.chat.ignore` | ✅ default | Ignore a player | `/ignore` |
| `bigbangessentials.chat.unignore` | ✅ default | Unignore a player | `/unignore` |
| `bigbangessentials.chat.msgtoggle` | ✅ default | Toggle receiving messages | `/msgtoggle` |
| `bigbangessentials.chat.socialspy` | 🔒 op-only | See all private messages | `/socialspy` |
| `bigbangessentials.chat.socialspy.exempt` | 🔒 op-only | Private messages not visible to socialspy | |
| `bigbangessentials.chat.msgtoggle.bypass` | 🔒 op-only | Message players who have toggled off | |

#### Mail
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.mail` | ✅ default | Use the mail system | `/mail` |
| `bigbangessentials.mail.send` | ✅ default | Send mail to a player | `/mail send` |
| `bigbangessentials.mail.clear` | ✅ default | Clear own mailbox | `/mail clear` |

#### Moderation Chat
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.chat.mute` | 🔒 op-only | Mute a player | `/mute` |
| `bigbangessentials.chat.unmute` | 🔒 op-only | Unmute a player | `/unmute` |
| `bigbangessentials.chat.mutelist` | 🔒 op-only | View muted players | `/mutelist` |
| `bigbangessentials.chat.exempt` | 🔒 op-only | Exempt from being muted | |

#### Formatting & Colours
| Node | Default | Description |
|---|---|---|
| `bigbangessentials.chat.color` | 🔒 op-only | Use `&0-9`, `&a-f` colour codes in chat |
| `bigbangessentials.chat.color.hex` | 🔒 op-only | Use `&#RRGGBB` hex colours in chat |
| `bigbangessentials.chat.format` | 🔒 op-only | Use `&k-o`, `&r` formatting codes in chat |
| `bigbangessentials.chat.richtext` | 🔒 op-only | Use gradient/rainbow rich text effects |
| `bigbangessentials.chat.gradient` | 🔒 op-only | Use gradient text effects |
| `bigbangessentials.chat.rainbow` | 🔒 op-only | Use rainbow text effects |

#### Chat Channels
| Node | Default | Description |
|---|---|---|
| `bigbangessentials.chat.channel.local` | ✅ default | Use local chat channel |
| `bigbangessentials.chat.channel.global` | ✅ default | Use global chat channel |
| `bigbangessentials.chat.staff` | 🔒 op-only | Access staff chat channel |
| `bigbangessentials.chat.mention` | ✅ default | Mention players with `@name` |
| `bigbangessentials.chat.mention.all` | 🔒 op-only | Mention everyone with `@everyone` |
| `bigbangessentials.chat.itemlink` | ✅ default | Show held item in chat with `[item]` |

#### Anti-Spam Bypasses
| Node | Default | Description |
|---|---|---|
| `bigbangessentials.chat.caps.bypass` | 🔒 op-only | Bypass caps filter |
| `bigbangessentials.chat.repeat.bypass` | 🔒 op-only | Bypass repeat-message filter |
| `bigbangessentials.chat.links.bypass` | 🔒 op-only | Bypass link filter |
| `bigbangessentials.chat.spam.bypass` | 🔒 op-only | Bypass spam rate limit |

---

### Moderation

| Node | Default | Description | Command |
|---|---|---|---|
| **Banning** | | | |
| `bigbangessentials.moderation.ban` | 🔒 op-only | Ban a player | `/ban` |
| `bigbangessentials.moderation.banip` | 🔒 op-only | Ban an IP address | `/banip` |
| `bigbangessentials.moderation.banlist` | 🔒 op-only | View the ban list | `/banlist` |
| `bigbangessentials.moderation.tempban` | 🔒 op-only | Temporarily ban a player | `/tempban` |
| `bigbangessentials.moderation.unban` | 🔒 op-only | Unban a player | `/unban` |
| `bigbangessentials.moderation.unbanip` | 🔒 op-only | Unban an IP address | `/unbanip` |
| **Kicking** | | | |
| `bigbangessentials.moderation.kick` | 🔒 op-only | Kick a player | `/kick` |
| `bigbangessentials.moderation.kickall` | 🔒 op-only | Kick all players | `/kickall` |
| **Freezing** | | | |
| `bigbangessentials.moderation.freeze` | 🔒 op-only | Freeze a player | `/freeze` |
| `bigbangessentials.moderation.unfreeze` | 🔒 op-only | Unfreeze a player | `/unfreeze` |
| `bigbangessentials.moderation.freezeall` | 🔒 op-only | Freeze all players | `/freezeall` |
| `bigbangessentials.moderation.unfreezeall` | 🔒 op-only | Unfreeze all players | `/unfreezeall` |
| `bigbangessentials.moderation.freezelist` | 🔒 op-only | List frozen players | `/freezelist` |
| **Jailing** | | | |
| `bigbangessentials.moderation.jail` | 🔒 op-only | Jail a player | `/jail` |
| `bigbangessentials.moderation.unjail` | 🔒 op-only | Unjail a player | `/unjail` |
| `bigbangessentials.moderation.setjail` | 🔒 op-only | Create a jail location | `/setjail` |
| `bigbangessentials.moderation.jaillist` | 🔒 op-only | List jailed players | `/jaillist` |
| `bigbangessentials.moderation.jailinfo` | 🔒 op-only | View jail info | `/jailinfo` |
| **Vanish** | | | |
| `bigbangessentials.moderation.vanish` | 🔒 op-only | Vanish yourself | `/vanish` |
| `bigbangessentials.moderation.vanish.others` | 🔒 op-only | Vanish another player | `/vanish <player>` |
| `bigbangessentials.moderation.seevanished` | 🔒 op-only | See vanished players | |
| `bigbangessentials.vanish.see` | 🔒 op-only | See vanished players (alias) | |
| `bigbangessentials.moderation.vanishlist` | 🔒 op-only | List vanished players | `/vanishlist` |
| **Notifications** | | | |
| `bigbangessentials.moderation.notify` | 🔒 op-only | Receive moderation action notifications | |
| `bigbangessentials.moderation.notifications` | 🔒 op-only | Receive moderation event broadcasts | |

---

### Miscellaneous Utilities

#### Player Info
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.list` | ✅ default | View online player list | `/list`, `/who` |
| `bigbangessentials.near` | ✅ default | View nearby players | `/near` |
| `bigbangessentials.seen` | ✅ default | Check when a player was last seen | `/seen` |
| `bigbangessentials.whois` | ✅ default | View player info | `/whois` |
| `bigbangessentials.whois.detailed` | 🔒 op-only | View detailed player info | `/whois` |
| `bigbangessentials.ping` | ✅ default | Check own ping | `/ping` |
| `bigbangessentials.ping.others` | 🔒 op-only | Check another player's ping | `/ping <player>` |
| `bigbangessentials.realname` | ✅ default | Look up a player's real name from nickname | `/realname` |
| `bigbangessentials.depth` | ✅ default | View depth/Y-level info | `/depth` |
| `bigbangessentials.depth.others` | 🔒 op-only | View another player's depth info | `/depth <player>` |
| `bigbangessentials.compass` | ✅ default | View compass/direction info | `/compass` |
| `bigbangessentials.compass.others` | 🔒 op-only | View compass info for another player | `/compass <player>` |
| `bigbangessentials.getpos` | ✅ default | View own position | `/getpos` |
| `bigbangessentials.getpos.others` | 🔒 op-only | View another player's position | `/getpos <player>` |

#### Nicknames
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.nick` | ✅ default | Change own nickname | `/nick` |
| `bigbangessentials.nick.color` | 🔒 op-only | Use colour codes in nickname | `/nick` |
| `bigbangessentials.nick.others` | 🔒 op-only | Change another player's nickname | `/setnick` |

#### Server Info
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.motd` | ✅ default | View the message of the day | `/motd` |
| `bigbangessentials.rules` | ✅ default | View server rules | `/rules` |
| `bigbangessentials.helpop` | ✅ default | Send a help request to staff | `/helpop` |
| `bigbangessentials.helpop.receive` | 🔒 op-only | Receive help-op requests | |
| `bigbangessentials.staff` | 🔒 op-only | Access staff chat and features | |

#### Portable Workstations
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.anvil` | ✅ default | Open portable anvil | `/anvil` |
| `bigbangessentials.crafting` | ✅ default | Open portable crafting table | `/craft` |
| `bigbangessentials.grindstone` | ✅ default | Open portable grindstone | `/grindstone` |
| `bigbangessentials.smithing` | ✅ default | Open portable smithing table | `/smithing` |
| `bigbangessentials.stonecutting` | ✅ default | Open portable stonecutter | `/stonecutter` |

#### Book & Sign
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.book` | ✅ default | Give yourself a writable book | `/book` |
| `bigbangessentials.book.unlock` | 🔒 op-only | Unlock a written book for editing | `/book unlock` |
| `bigbangessentials.book.title` | 🔒 op-only | Set a book's title | `/book title` |
| `bigbangessentials.book.author` | 🔒 op-only | Set a book's author | `/book author` |
| `bigbangessentials.sign` | ✅ default | Edit sign text | `/sign` |
| `bigbangessentials.sign.colors` | 🔒 op-only | Use colours on signs | `/sign` |

#### AFK, Gamemode & Other
| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.afk` | ✅ default | Use the AFK system | `/afk` |
| `bigbangessentials.afk.exempt` | 🔒 op-only | Exempt from AFK kick | |
| `bigbangessentials.suicide` | ✅ default | Use the suicide command | `/suicide` |
| `bigbangessentials.gamemode` | 🔒 op-only | Change own gamemode | `/gm`, `/gmc`, `/gms` |
| `bigbangessentials.gamemode.others` | 🔒 op-only | Change another player's gamemode | `/gm <player>` |

---

### Admin & Config

| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.admin` | 🔒 op-only | General admin access | |
| `bigbangessentials.reload` | 🔒 op-only | Reload the mod configuration | `/neoe reload` |
| `bigbangessentials.debug` | 🔒 op-only | Enable debug logging | |
| `bigbangessentials.rules.admin` | 🔒 op-only | Create/edit/delete server rules | `/rules add` etc. |
| `bigbangessentials.motd.set` | 🔒 op-only | Set the message of the day | `/motd set` |
| `bigbangessentials.motd.broadcast` | 🔒 op-only | Broadcast the MOTD to all players | `/motd broadcast` |
| `bigbangessentials.motd.reload` | 🔒 op-only | Reload MOTD from file | `/motd reload` |

---

### Permission System Commands

| Node | Default | Description | Command |
|---|---|---|---|
| `bigbangessentials.permissions.admin` | 🔒 op-only | Full permissions system access | `/permissions` |
| `bigbangessentials.permissions.reload` | 🔒 op-only | Reload the permissions system | `/permissions reload` |
| `bigbangessentials.permissions.list` | 🔒 op-only | List registered permission nodes | `/permissions list` |
| `bigbangessentials.permissions.check` | 🔒 op-only | Check a player's effective permissions | `/permissions check` |
| `bigbangessentials.permissions.search` | 🔒 op-only | Search permission nodes | `/permissions search` |
| `bigbangessentials.permissions.user` | 🔒 op-only | User management (parent) | `/permissions user` |
| `bigbangessentials.permissions.user.permissions` | 🔒 op-only | Add/remove user permission nodes | |
| `bigbangessentials.permissions.user.groups` | 🔒 op-only | Add/remove user from groups | |
| `bigbangessentials.permissions.user.clear` | 🔒 op-only | Clear all user permissions | |
| `bigbangessentials.permissions.list.users` | 🔒 op-only | List all permission users | |
| `bigbangessentials.permissions.info.user` | 🔒 op-only | View a user's permission info | |
| `bigbangessentials.permissions.group` | 🔒 op-only | Group management (parent) | `/permissions group` |
| `bigbangessentials.permissions.group.create` | 🔒 op-only | Create a new group | |
| `bigbangessentials.permissions.group.delete` | 🔒 op-only | Delete a group | |
| `bigbangessentials.permissions.group.rename` | 🔒 op-only | Rename a group | |
| `bigbangessentials.permissions.group.clone` | 🔒 op-only | Clone a group | |
| `bigbangessentials.permissions.group.inherit` | 🔒 op-only | Set group inheritance | |
| `bigbangessentials.permissions.group.permissions` | 🔒 op-only | Manage group permission nodes | |
| `bigbangessentials.permissions.group.modify` | 🔒 op-only | Modify group settings (prefix/suffix) | |
| `bigbangessentials.permissions.group.clear` | 🔒 op-only | Clear all group permissions | |
| `bigbangessentials.permissions.list.groups` | 🔒 op-only | List all groups | |
| `bigbangessentials.permissions.info.group` | 🔒 op-only | View a group's info | |

---

### Web Dashboard

| Node | Default | Description |
|---|---|---|
| `bigbangessentials.admin.dashboard` | 🔒 op-only | Access the admin dashboard command |
| `bigbangessentials.dashboard.access` | 🔒 op-only | Register an account and log in to the dashboard |
| `bigbangessentials.dashboard.view` | 🔒 op-only | View-only dashboard access |
| `bigbangessentials.dashboard.manage` | 🔒 op-only | Manage dashboard settings |
| `bigbangessentials.dashboard.moderator` | 🔒 op-only | Moderator-level dashboard access |
| `bigbangessentials.dashboard.admin` | 🔒 op-only | Full admin dashboard access |

---

## Example groups.json

```json
{
  "defaultGroup": "default",
  "groups": [
    {
      "name": "default",
      "prefix": "§7",
      "suffix": "",
      "permissions": [
        "bigbangessentials.use",
        "bigbangessentials.economy.balance",
        "bigbangessentials.economy.pay",
        "bigbangessentials.economy.pay.toggle",
        "bigbangessentials.economy.baltop",
        "bigbangessentials.teleport.request.tpa",
        "bigbangessentials.teleport.request.tpahere",
        "bigbangessentials.teleport.request.accept",
        "bigbangessentials.teleport.request.deny",
        "bigbangessentials.teleport.request.cancel",
        "bigbangessentials.teleport.home",
        "bigbangessentials.teleport.home.set",
        "bigbangessentials.teleport.home.delete",
        "bigbangessentials.teleport.home.list",
        "bigbangessentials.home.3",
        "bigbangessentials.teleport.warp",
        "bigbangessentials.teleport.warp.list",
        "bigbangessentials.teleport.spawn",
        "bigbangessentials.teleport.back",
        "bigbangessentials.teleport.death",
        "bigbangessentials.teleport.tpr",
        "bigbangessentials.kits.use",
        "bigbangessentials.kits.list",
        "bigbangessentials.item.dispose",
        "bigbangessentials.chat.msg",
        "bigbangessentials.chat.reply",
        "bigbangessentials.chat.ignore",
        "bigbangessentials.chat.unignore",
        "bigbangessentials.chat.msgtoggle",
        "bigbangessentials.chat.channel.local",
        "bigbangessentials.chat.channel.global",
        "bigbangessentials.chat.mention",
        "bigbangessentials.chat.itemlink",
        "bigbangessentials.mail",
        "bigbangessentials.mail.send",
        "bigbangessentials.mail.clear",
        "bigbangessentials.list",
        "bigbangessentials.near",
        "bigbangessentials.seen",
        "bigbangessentials.whois",
        "bigbangessentials.ping",
        "bigbangessentials.realname",
        "bigbangessentials.motd",
        "bigbangessentials.rules",
        "bigbangessentials.helpop",
        "bigbangessentials.afk",
        "bigbangessentials.anvil",
        "bigbangessentials.crafting",
        "bigbangessentials.grindstone",
        "bigbangessentials.smithing",
        "bigbangessentials.stonecutting",
        "bigbangessentials.book",
        "bigbangessentials.sign",
        "bigbangessentials.nick",
        "bigbangessentials.suicide",
        "bigbangessentials.depth",
        "bigbangessentials.compass",
        "bigbangessentials.getpos",
        "bigbangessentials.info"
      ],
      "inherits": []
    },
    {
      "name": "vip",
      "prefix": "§6[VIP] §f",
      "suffix": "",
      "permissions": [
        "bigbangessentials.home.10",
        "bigbangessentials.teleport.top",
        "bigbangessentials.teleport.jump",
        "bigbangessentials.teleport.jumpto",
        "bigbangessentials.nick.color",
        "bigbangessentials.chat.color",
        "bigbangessentials.chat.format",
        "bigbangessentials.chat.richtext",
        "bigbangessentials.teleport.warp.create",
        "bigbangessentials.warp.limit.5",
        "bigbangessentials.item.repair",
        "bigbangessentials.sign.colors"
      ],
      "inherits": ["default"]
    },
    {
      "name": "moderator",
      "prefix": "§2[Mod] §f",
      "suffix": "",
      "permissions": [
        "bigbangessentials.moderation.ban",
        "bigbangessentials.moderation.banip",
        "bigbangessentials.moderation.banlist",
        "bigbangessentials.moderation.tempban",
        "bigbangessentials.moderation.unban",
        "bigbangessentials.moderation.unbanip",
        "bigbangessentials.moderation.kick",
        "bigbangessentials.moderation.kickall",
        "bigbangessentials.moderation.freeze",
        "bigbangessentials.moderation.unfreeze",
        "bigbangessentials.moderation.freezeall",
        "bigbangessentials.moderation.unfreezeall",
        "bigbangessentials.moderation.freezelist",
        "bigbangessentials.moderation.jail",
        "bigbangessentials.moderation.unjail",
        "bigbangessentials.moderation.setjail",
        "bigbangessentials.moderation.jaillist",
        "bigbangessentials.moderation.jailinfo",
        "bigbangessentials.moderation.vanish",
        "bigbangessentials.moderation.seevanished",
        "bigbangessentials.moderation.vanishlist",
        "bigbangessentials.moderation.notify",
        "bigbangessentials.chat.mute",
        "bigbangessentials.chat.unmute",
        "bigbangessentials.chat.mutelist",
        "bigbangessentials.chat.socialspy",
        "bigbangessentials.chat.staff",
        "bigbangessentials.staff",
        "bigbangessentials.helpop.receive",
        "bigbangessentials.whois.detailed",
        "bigbangessentials.nick.others",
        "bigbangessentials.teleport.admin.tp",
        "bigbangessentials.teleport.admin.tphere",
        "bigbangessentials.teleport.admin.tpo",
        "bigbangessentials.home.20",
        "bigbangessentials.warp.limit.unlimited",
        "bigbangessentials.dashboard.moderator"
      ],
      "inherits": ["vip"]
    },
    {
      "name": "admin",
      "prefix": "§c[Admin] §f",
      "suffix": "",
      "permissions": [
        "bigbangessentials.*"
      ],
      "inherits": ["moderator"]
    }
  ]
}
```

---

## External Permission Mods

BigBangEssentials supports the following external permission systems when `useExternalPermissions: true`:

| Mod | Notes |
|---|---|
| **FTB Ranks** | Full support — ranks map to groups, all nodes respected |
| **LuckPerms** | Full support via the LuckPerms API adapter |
| **YAWP** | Basic support |

When an external system is active, the built-in `permissions.json` groups are **not used** for permission checks, but the registry still provides node metadata (descriptions, defaults) for export via `/permissions export`.

> **Tip:** Run `/permissions export luckperms` or `/permissions export ftbranks` to generate a ready-to-import config file for your preferred permission mod.
