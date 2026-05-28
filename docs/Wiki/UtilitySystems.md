# Utility Systems

> **Version:** 1.0.2.6

---

## Overview

Miscellaneous quality-of-life commands covering player info, server admin tools, world/environment manipulation, fun commands, and player state management.

---

## Player Info Commands

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/seen` | `/seen <player>` | `bigbangessentials.seen` | Show online/offline status with location or last-seen time |
| `/near` | `/near [radius]` | `bigbangessentials.near` | List nearby players and their distance (default 200 blocks) |
| `/ping` | `/ping [player]` | `bigbangessentials.ping` | Show connection latency (colour-coded) |
| `/playtime` | `/playtime [player]` | `bigbangessentials.playtime` | Show total play time in h/m/s |
| `/whois` | `/whois <player>` | `bigbangessentials.whois` | Show UUID, dimension, coords, gamemode, ping, health, food |
| `/realname` | `/realname <nick>` | `bigbangessentials.realname` | Look up real username from nickname |
| `/list` | `/list` | `bigbangessentials.list` | List online players with count |
| `/who` | alias | same | Alias |
| `/motd` | `/motd` | `bigbangessentials.motd` | Display server MOTD |
| `/rules` | `/rules` | `bigbangessentials.rules` | Display server rules |
| `/helpop` | `/helpop <message>` | `bigbangessentials.helpop` | Send message to online staff |
| `/suicide` | `/suicide` | `bigbangessentials.suicide` | Kill yourself |

---

## Nicknames

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/nick` | `/nick <name\|off> [player]` | `bigbangessentials.nick` | Set a nickname |
| `/nickname` | alias | same | Alias |

Colour codes in nicks require `bigbangessentials.nick.color`. Setting others' nicks requires `bigbangessentials.nick.others`.

---

## Player State Commands

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/fly` | `/fly [on\|off] [player]` | `bigbangessentials.fly` | Toggle flight |
| `/god` | `/god [on\|off] [player]` | `bigbangessentials.god` | Toggle god mode |
| `/heal` | `/heal [player]` | `bigbangessentials.heal` | Full health, hunger, and saturation |
| `/feed` | `/feed [player]` | `bigbangessentials.feed` | Full hunger and saturation |
| `/speed` | `/speed [walk\|fly] <0-10> [player]` | `bigbangessentials.speed` | Set walk or fly speed |
| `/ext` | `/ext [player]` | `bigbangessentials.ext` | Extinguish fire |
| `/extinguish` | alias | same | Alias |
| `/burn` | `/burn <player> [seconds]` | `bigbangessentials.burn` | Set fire ticks on a player |
| `/give` | `/give <player> <item> [amount]` | `bigbangessentials.give` | Give items to a player |
| `/more` | `/more [amount]` | `bigbangessentials.more` | Fill held stack to max |
| `/hat` | `/hat` | `bigbangessentials.hat` | Wear held item as helmet |
| `/exp` | `/exp [show\|set\|give] [amount] [player]` | `bigbangessentials.exp` | Show, set, or give XP |
| `/gamemode` | `/gamemode <mode\|0-3> [player]` | `bigbangessentials.gamemode` | Change gamemode |
| `/gms`, `/gmc`, `/gma`, `/gmsp` | shortcut | same | Gamemode shortcuts |

---

## Per-Player Time & Weather

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/ptime` | `/ptime [reset\|day\|noon\|night\|<ticks>] [player]` | `bigbangessentials.ptime` | Set client-side time (server time unaffected) |
| `/pweather` | `/pweather [reset\|sun\|storm\|clear\|rain] [player]` | `bigbangessentials.pweather` | Set client-side weather |

---

## Server Admin Commands

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/broadcast` | `/broadcast <message>` | `bigbangessentials.broadcast` | Server-wide announcement |
| `/bc`, `/announce` | aliases | same | Aliases |
| `/broadcastworld` | `/broadcastworld <message>` | `bigbangessentials.broadcastworld` | Broadcast to current world only |
| `/bcastworld` | alias | same | Alias |
| `/time` | `/time [set\|add] <value\|day\|night…>` | `bigbangessentials.time` | Get/set server time |
| `/day`, `/night` | shortcuts | same | Shortcuts |
| `/weather` | `/weather <sun\|storm\|thunder> [dur]` | `bigbangessentials.weather` | Set server weather |
| `/sun`, `/storm`, `/thunder` | shortcuts | same | Shortcuts |
| `/sudo` | `/sudo <player> <command>` | `bigbangessentials.sudo` | Run a command as another player |
| `/gc` | `/gc` | `bigbangessentials.gc` | Show TPS, memory, uptime, chunks |
| `/mem` | alias | same | Alias |
| `/backup` | `/backup` | `bigbangessentials.backup` | Trigger a server backup |
| `/kill` | `/kill <player>` | `bigbangessentials.kill` | Kill a player |
| `/spawner` | `/spawner <mob>` | `bigbangessentials.spawner` | Set spawner type at looked block |
| `/spawnmob` | `/spawnmob <mob> [amount] [player]` | `bigbangessentials.spawnmob` | Spawn entities at a player |
| `/mob` | alias | same | Alias |
| `/effect` | `/effect <player> <effect\|clear> [dur] [amp]` | `bigbangessentials.effect` | Apply/clear potion effects |
| `/unlimited` | `/unlimited [list\|clear\|<item\|hand>] [player]` | `bigbangessentials.unlimited` | Unlimited item mode (never depleted) |
| `/recipe` | `/recipe [item]` | `bigbangessentials.recipe` | Unlock crafting recipes for an item |

---

## World Interaction

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/fireball` | `/fireball [type] [speed] [ride]` | `bigbangessentials.fireball.<type>` | Shoot a projectile (11 types) |
| `/tree` | `/tree <type>` | `bigbangessentials.tree` | Grow a tree at your feet |
| `/bigtree` | `/bigtree` | `bigbangessentials.tree` | Grow a big tree |
| `/break` | `/break` | `bigbangessentials.break` | Instantly break looked-at block |
| `/ice` | `/ice [player]` | `bigbangessentials.ice` | Fully freeze a player (powder snow mechanic) |
| `/lightning` | `/lightning [player]` | `bigbangessentials.lightning` | Strike lightning |
| `/smite` | alias | same | Alias |
| `/remove` | `/remove <type> [radius]` | `bigbangessentials.remove` | Remove entities by type in radius |
| `/nuke` | `/nuke` | `bigbangessentials.nuke` | Remove all nearby entities |
| `/tptoggle` | `/tptoggle [on\|off]` | `bigbangessentials.tptoggle` | Toggle receiving TP requests |
| `/msgtoggle` | `/msgtoggle [on\|off]` | `bigbangessentials.msgtoggle` | Toggle receiving private messages |
| `/rtoggle` | `/rtoggle [on\|off]` | `bigbangessentials.rtoggle` | Toggle receiving `/reply` messages |

---

## Fun Commands

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/me` | `/me <action>` | `bigbangessentials.me` | Broadcast a third-person action message |
| `/firework` | `/firework` | `bigbangessentials.firework` | Launch a firework |
| `/antioch` | `/antioch` | `bigbangessentials.antioch` | Launch a Holy Hand Grenade 🐇 |
| `/kittycannon` | `/kittycannon` | `bigbangessentials.kittycannon` | Launch a kitten |
| `/beezooka` | `/beezooka` | `bigbangessentials.beezooka` | Launch bees |
| `/rest` | `/rest` | `bigbangessentials.rest` | Skip the night (vote) |
| `/info` | `/info` | `bigbangessentials.info` | Show server/mod info |
| `/itemdb` | `/itemdb [item]` | `bigbangessentials.itemdb` | Show registry info for held/named item |

---

## Mail

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/mail read` | `/mail read [page]` | `bigbangessentials.mail` | Read your mail |
| `/mail send` | `/mail send <player> <message>` | `bigbangessentials.mail.send` | Send mail |
| `/mail sendtemp` | `/mail sendtemp <player> <duration> <message>` | `bigbangessentials.mail.sendtemp` | Send expiring mail |
| `/mail sendall` | `/mail sendall <message>` | `bigbangessentials.mail.sendall` | Broadcast mail to all players |
| `/mail clear` | `/mail clear [index]` | `bigbangessentials.mail` | Clear your mailbox or specific message |

---

*Back to [Wiki Home](Home)*
