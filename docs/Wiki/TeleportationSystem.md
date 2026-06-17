# Teleportation System

> **Version:** 1.0.2.6 · **Config:** `config.json` → `teleportation` section

---

## Overview

Full teleportation suite — homes, warps, player warps, spawn, TPA requests, random teleport, direct TP commands, and utility teleports. All with safe-location detection, delay/warmup, and `/back` support.

Compatibility note: the runtime accepts both the canonical teleport permission nodes from `PermissionSystem.md` and the older aliases used in legacy configs and older docs.
Legacy child aliases are only honored when assigned explicitly; parent nodes like `bigbangessentials.spawn` do not imply admin subcommands such as `setspawn`.

---

## Safe Location Detection

All teleport destinations are checked for safety:
- **Feet block** must have a solid collision shape (correctly handles slabs, stairs, glass, trapdoors)
- **Head block** must be passable (air, non-solid)
- **Dangerous blocks** are rejected: lava, fire, soul fire, magma, cactus, sweet berry bush, wither rose, nether portal, campfire, soul campfire, powder snow
- A top-down column scan finds the surface first, then an expanding XZ radius search as fallback
- Safety can be disabled per feature in config

---

## Homes

### Commands

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/home` | `/home [name]` | `bigbangessentials.home` | Teleport to a home |
| `/sethome` | `/sethome [name]` | `bigbangessentials.home.set` | Set home at current location |
| `/delhome` | `/delhome [name]` | `bigbangessentials.home.delete` | Delete a home |
| `/deletehome` | alias | same | Alias |
| `/homes` | `/homes` | `bigbangessentials.home.list` | List all homes |
| `/renamehome` | `/renamehome <old> <new>` | `bigbangessentials.renamehome` | Rename a home |

### Config (`teleportation.homeSettings`)

| Key | Default | Description |
|---|---|---|
| `maxHomes` | `5` | Max homes per player |
| `allowCrossDimensionHomes` | `true` | Allow homes in other dimensions |
| `enableHomeTeleportSafety` | `true` | Check safe location on home TP |
| `teleportDelay` | `3` | Seconds before teleport completes |
| `cancelOnMovement` | `true` | Cancel if player moves during delay |

---

## Warps

### Commands

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/warp` | `/warp [name] [player]` | `bigbangessentials.teleport.warp` | Teleport to a warp (or warp another player) |
| `/warp` | `/warp` or `/warp <page>` | same | List all warps (paginated) |
| `/setwarp` | `/setwarp <name>` | `bigbangessentials.teleport.warp.create` | Create a warp |
| `/delwarp` | `/delwarp <name>` | `bigbangessentials.teleport.warp.delete` | Delete a warp |
| `/warps` | `/warps [page]` | `bigbangessentials.teleport.warp.list` | List warps (20 per page) |
| `/warpinfo` | `/warpinfo <name>` | `bigbangessentials.warpinfo` | Show warp coordinates and world |
| `/pwarp` | `/pwarp [name]` | `bigbangessentials.teleport.pwarp` | Teleport to your player warp |
| `/setpwarp` | `/setpwarp <name>` | `bigbangessentials.teleport.pwarp.create` | Create a player warp |
| `/delpwarp` | `/delpwarp <name>` | `bigbangessentials.teleport.pwarp.delete` | Delete a player warp |
| `/pwarps` | `/pwarps` | `bigbangessentials.teleport.pwarp.list` | List your player warps |

Set `perWarpPermission: true` in config to require `bigbangessentials.warps.<name>` per warp.

---

## Spawn

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/spawn` | `/spawn [player]` | `bigbangessentials.teleport.spawn` | Teleport to server spawn |
| `/setspawn` | `/setspawn` | `bigbangessentials.teleport.spawn.set` | Set spawn at current location |
| `/spawninfo` | `/spawninfo` | `bigbangessentials.teleport.spawn.info` | Show spawn info and admin stats |

---

## Teleport Requests (TPA)

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/tpa` | `/tpa <player>` | `bigbangessentials.teleport.tpa` | Request to TP to a player |
| `/tpahere` | `/tpahere <player>` | `bigbangessentials.teleport.tpahere` | Request player TP to you |
| `/tpaccept` | `/tpaccept` | `bigbangessentials.teleport.tpaccept` | Accept incoming request |
| `/tpdeny` | `/tpdeny` | `bigbangessentials.teleport.tpdeny` | Deny incoming request |
| `/tpacancel` | `/tpacancel` | `bigbangessentials.teleport.tpacancel` | Cancel your outgoing request |
| `/tptoggle` | `/tptoggle [on\|off]` | `bigbangessentials.tptoggle` | Toggle accepting TP requests |
| `/tpauto` | `/tpauto [on\|off]` | `bigbangessentials.tpauto` | Auto-accept all incoming TPA requests |
| `/tpaall` | `/tpaall [player]` | `bigbangessentials.tpaall` | Send TPA-here to all online players |

---

## Random Teleport

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/tpr` | `/tpr [location]` | `bigbangessentials.teleport.tpr` | Teleport to a random location |
| `/rtp` | alias | same | Alias |
| `/randomtp` | alias | same | Alias |
| `/settpr` | `/settpr <name>` | `bigbangessentials.teleport.settpr` | Set a named RTP centre point |

### Config (`teleportation.randomTeleportSettings`)

| Key | Default | Description |
|---|---|---|
| `defaultMinRange` | `500` | Minimum distance from centre |
| `defaultMaxRange` | `10000` | Maximum distance from centre |
| `findAttempts` | `10` | Attempts to find a safe spot |
| `cooldown` | `300` | Seconds between uses per player |
| `cacheThreshold` | `10` | Pre-computed location cache size |
| `excludedBiomes` | `[ocean, deep_ocean, void]` | Biomes to avoid |

---

## Admin Teleport Commands

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/tp` | `/tp <player>` or `/tp <x y z>` | `bigbangessentials.teleport.admin.tp` | Teleport to player or coords |
| `/tphere` | `/tphere <player>` | `bigbangessentials.teleport.admin.tphere` | Bring player to you |
| `/tpall` | `/tpall` | `bigbangessentials.teleport.admin.tpall` | Bring all players to you |
| `/tppos` | `/tppos <x> <y> <z>` | `bigbangessentials.teleport.admin.tppos` | Teleport to exact coordinates |
| `/tpo` | `/tpo <player>` | `bigbangessentials.teleport.tpo` | TP to player, bypasses tptoggle |
| `/tpohere` | `/tpohere <player>` | `bigbangessentials.teleport.tpohere` | Bring player, bypasses tptoggle |
| `/tpoffline` | `/tpoffline <player>` | `bigbangessentials.teleport.tpoffline` | TP to offline player's last position |
| `/world` | `/world [dimension] [player]` | `bigbangessentials.world` | Teleport to a world/dimension |

---

## Utility Teleports

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/back` | `/back` | `bigbangessentials.teleport.back` | Return to previous location |
| `/top` | `/top` | `bigbangessentials.teleport.top` | Teleport to highest block above you |
| `/jump` | `/jump` | `bigbangessentials.teleport.jump` | Teleport to block you're looking at |
| `/bottom` | `/bottom` | `bigbangessentials.bottom` | Teleport to bottom of world at your X/Z |

---

## Data Files

| File | Contents |
|---|---|
| `bigbangessentials/homes.json` | Player UUID → named home locations |
| `bigbangessentials/warps.json` | Server warp locations |
| `bigbangessentials/player_warps.json` | Player-created warp locations |

---

*Back to [Wiki Home](Home)*
