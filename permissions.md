# BigBangEssentials — Complete Commands Reference

> **Last Updated:** 2026-03-06 · **Version:** 1.0.2.6  
> All commands are prefixed with `/`. Permission nodes follow `bigbangessentials.<node>` pattern.  
> `🔒` = op-only by default · `✅` = available to all players by default  
> Square brackets `[x]` = optional · Angle brackets `<x>` = required · `|` = or
>
> Compatibility note: some rows below still mention legacy permission aliases used by older configs. The runtime accepts both the canonical nodes from `PermissionSystem.md` and the older aliases documented here, but legacy child aliases are only honored when assigned explicitly. Parent nodes like `bigbangessentials.spawn` no longer imply `setspawn`.

---

## 📋 Table of Contents

1. [Economy](#economy)
2. [ChestShop](#chestshop)
3. [Teleportation](#teleportation)
4. [Homes](#homes)
5. [Warps](#warps)
6. [Spawn](#spawn)
7. [Player State & Admin Tools](#player-state--admin-tools)
8. [Server Admin](#server-admin)
9. [Moderation](#moderation)
10. [Chat & Messaging](#chat--messaging)
11. [Kits](#kits)
12. [Items](#items)
13. [Worth & Sell](#worth--sell)
14. [Utility](#utility)
15. [AFK](#afk)
16. [Web Dashboard](#web-dashboard)
17. [Permissions Management](#permissions-management)
18. [Miscellaneous](#miscellaneous)
19. [Jobs & Professions](#jobs--professions)

---

## Economy

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/balance` | `/balance [player]` | `bigbangessentials.economy.balance` / `.balance.others` | ✅ | Check own or another player's balance |
| `/bal` | alias for `/balance` | same | ✅ | Alias |
| `/pay` | `/pay <player> <amount>` | `bigbangessentials.economy.pay` | ✅ | Send money to an online player |
| `/paytoggle` | `/paytoggle` | `bigbangessentials.economy.pay.toggle` | ✅ | Toggle receiving payments |
| `/pt` | alias for `/paytoggle` | same | ✅ | Alias |
| `/baltop` | `/baltop [page]` | `bigbangessentials.economy.baltop` | ✅ | View top player balances |
| `/balancetop` | alias for `/baltop` | same | ✅ | Alias |
| `/eco` | `/eco give\|take\|set\|reset <player> <amount>` | `bigbangessentials.economy.eco` | 🔒 | Admin economy management |

---

## ChestShop

Sign-based chest shops for automated buy/sell trading.

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/chestshop` | `/chestshop` | none | ✅ | Show ChestShop help and usage |
| `/chestshop list` | `/chestshop list [player]` | none / `bigbangessentials.shop.list.others` | ✅ | List your shops; listing another player's shops needs the `list.others` node |
| `/chestshop info` | `/chestshop info` | `bigbangessentials.shop.use` | ✅ | Show info about the shop you're looking at |
| `/chestshop convert` | `/chestshop convert` | `bigbangessentials.shop.create` | ✅ | Register the looked-at sign as a shop |
| `/chestshop remove` | `/chestshop remove <x> <y> <z>` | `bigbangessentials.shop.admin.remove` | 🔒 | Remove a shop by coordinates |
| `/chestshop reload` | `/chestshop reload` | `bigbangessentials.shop.admin.reload` | 🔒 | Reload shop data from disk |
| `/cshop` | alias for `/chestshop` | same | ✅ | Alias |

### Permissions

| Node | Description |
|---|---|
| `bigbangessentials.shop.create` | Create player shops and convert signs into shops |
| `bigbangessentials.shop.create.admin` | Create admin shops (line 1: `Admin Shop`) |
| `bigbangessentials.shop.use` | Buy/sell at shops |
| `bigbangessentials.shop.list.others` | List other players' shops |
| `bigbangessentials.shop.admin.remove` | Remove any shop |
| `bigbangessentials.shop.admin.reload` | Reload shop data |

---

## Teleportation

### Player Teleport
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/tp` | `/tp <player>` or `/tp <x> <y> <z>` | `bigbangessentials.teleport.admin.tp` | 🔒 | Teleport to a player or coordinates |
| `/tphere` | `/tphere <player>` | `bigbangessentials.teleport.admin.tphere` | 🔒 | Teleport a player to you |
| `/tpall` | `/tpall` | `bigbangessentials.teleport.admin.tpall` | 🔒 | Teleport all players to you |
| `/tppos` | `/tppos <x> <y> <z>` | `bigbangessentials.teleport.admin.tppos` | 🔒 | Teleport to exact coordinates |
| `/tpo` | `/tpo <player>` | `bigbangessentials.teleport.tpo` | 🔒 | Teleport to player, bypassing their tptoggle |
| `/tpohere` | `/tpohere <player>` | `bigbangessentials.teleport.tpohere` | 🔒 | Bring player here, bypassing tptoggle |
| `/tpoffline` | `/tpoffline <player>` | `bigbangessentials.teleport.tpoffline` | 🔒 | Teleport to an offline player's last position |
| `/back` | `/back` | `bigbangessentials.teleport.back` | ✅ | Return to previous location |
| `/top` | `/top` | `bigbangessentials.teleport.top` | 🔒 | Teleport to the highest block above you |
| `/jump` | `/jump` | `bigbangessentials.teleport.jump` | 🔒 | Teleport to the block you are looking at |
| `/jumpto` | alias for `/jump` | same | 🔒 | Alias |

### Teleport Requests
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/tpa` | `/tpa <player>` | `bigbangessentials.teleport.tpa` | ✅ | Request to teleport to a player |
| `/tpahere` | `/tpahere <player>` | `bigbangessentials.teleport.tpahere` | ✅ | Request a player teleport to you |
| `/tpaccept` | `/tpaccept` | `bigbangessentials.teleport.tpaccept` | ✅ | Accept a pending teleport request |
| `/tpdeny` | `/tpdeny` | `bigbangessentials.teleport.tpdeny` | ✅ | Deny a pending teleport request |
| `/tpacancel` | `/tpacancel` | `bigbangessentials.teleport.tpacancel` | ✅ | Cancel your outgoing teleport request |

### Random Teleport
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/tpr` | `/tpr [location]` | `bigbangessentials.teleport.tpr` | ✅ | Teleport to a random location |
| `/rtp` | alias for `/tpr` | same | ✅ | Alias |
| `/randomtp` | alias for `/tpr` | same | ✅ | Alias |
| `/randomteleport` | alias for `/tpr` | same | ✅ | Alias |
| `/settpr` | `/settpr <name>` | `bigbangessentials.teleport.settpr` | 🔒 | Set a named RTP centre location |

---

## Homes

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/home` | `/home [name]` | `bigbangessentials.home` | ✅ | Teleport to your home (or named home) |
| `/sethome` | `/sethome [name]` | `bigbangessentials.home.set` | ✅ | Set your home at current location |
| `/delhome` | `/delhome [name]` | `bigbangessentials.home.delete` | ✅ | Delete a home |
| `/deletehome` | alias for `/delhome` | same | ✅ | Alias |
| `/homes` | `/homes` | `bigbangessentials.home.list` | ✅ | List all your homes |

---

## Warps

### Server Warps
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/warp` | `/warp <name>` | `bigbangessentials.teleport.warp` | ✅ | Teleport to a named warp |
| `/setwarp` | `/setwarp <name>` | `bigbangessentials.teleport.warp.create` | 🔒 | Create a warp at current location |
| `/delwarp` | `/delwarp <name>` | `bigbangessentials.teleport.warp.delete` | 🔒 | Delete a warp |
| `/warps` | `/warps [page]` | `bigbangessentials.teleport.warp.list` | ✅ | List all available warps |

### Player Warps
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/pwarp` | `/pwarp <name>` | `bigbangessentials.teleport.pwarp` | ✅ | Teleport to a player warp |
| `/setpwarp` | `/setpwarp <name>` | `bigbangessentials.teleport.pwarp.create` | ✅ | Create your own player warp |
| `/delpwarp` | `/delpwarp <name>` | `bigbangessentials.teleport.pwarp.delete` | ✅ | Delete one of your player warps |
| `/pwarps` | `/pwarps` | `bigbangessentials.teleport.pwarp.list` | ✅ | List your player warps |

---

## Spawn

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/spawn` | `/spawn` | `bigbangessentials.teleport.spawn` | ✅ | Teleport to server spawn |
| `/setspawn` | `/setspawn` | `bigbangessentials.teleport.spawn.set` | 🔒 | Set the server spawn at your location |
| `/spawninfo` | `/spawninfo` | `bigbangessentials.teleport.spawn.info` | 🔒 | Show spawn info and admin stats |

---

## Player State & Admin Tools

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/fly` | `/fly [on\|off]` or `/fly <player> [on\|off]` | `bigbangessentials.fly` / `.fly.others` | 🔒 | Toggle flight mode |
| `/god` | `/god [on\|off]` or `/god <player> [on\|off]` | `bigbangessentials.god` / `.god.others` | 🔒 | Toggle invincibility (god mode) |
| `/heal` | `/heal [player]` | `bigbangessentials.heal` / `.heal.others` | 🔒 | Restore full health, hunger, saturation, clear effects |
| `/feed` | `/feed [player]` | `bigbangessentials.feed` / `.feed.others` | 🔒 | Restore full hunger and saturation |
| `/speed` | `/speed [walk\|fly] <0-10> [player]` | `bigbangessentials.speed` / `.speed.others` | 🔒 | Set walk or fly speed (0–10 scale) |
| `/ext` | `/ext [player]` | `bigbangessentials.ext` / `.ext.others` | ✅ (self) 🔒 (others) | Extinguish fire on a player |
| `/extinguish` | alias for `/ext` | same | ✅ | Alias |
| `/burn` | `/burn <player> [seconds]` | `bigbangessentials.burn` | 🔒 | Set a player on fire (default 10s) |
| `/give` | `/give <player> <item> [amount]` | `bigbangessentials.give` | 🔒 | Give items to a player |
| `/more` | `/more [amount]` | `bigbangessentials.more` | 🔒 | Fill held item stack to max (or set amount) |
| `/hat` | `/hat` | `bigbangessentials.hat` | 🔒 | Wear held item as helmet |
| `/exp` | `/exp [show\|set\|give] [amount] [player]` | `bigbangessentials.exp` + sub-nodes | ✅ (show) 🔒 (set/give) | Manage player experience |
| `/xp` | alias for `/exp` | same | ✅ | Alias |
| `/sudo` | `/sudo <player> <command>` | `bigbangessentials.sudo` | 🔒 | Execute a command as another player |
| `/playtime` | `/playtime [player]` | `bigbangessentials.playtime` / `.playtime.others` | ✅ | View how long a player has played |

---

## Server Admin

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/broadcast` | `/broadcast <message>` | `bigbangessentials.broadcast` | 🔒 | Broadcast a coloured message to all players |
| `/bc` | alias for `/broadcast` | same | 🔒 | Alias |
| `/announce` | alias for `/broadcast` | same | 🔒 | Alias |
| `/time` | `/time [set\|add] <value>` | `bigbangessentials.time` / `.time.set` | 🔒 | Get or set world time (names: day/noon/night/midnight etc.) |
| `/day` | `/day` | `bigbangessentials.time.set` | 🔒 | Set time to day (1000 ticks) |
| `/night` | `/night` | `bigbangessentials.time.set` | 🔒 | Set time to night (13000 ticks) |
| `/weather` | `/weather <sun\|storm\|thunder> [seconds]` | `bigbangessentials.weather` | 🔒 | Set world weather |
| `/sun` | `/sun` | `bigbangessentials.weather` | 🔒 | Set weather to clear |
| `/storm` | `/storm` | `bigbangessentials.weather` | 🔒 | Set weather to rain/storm |
| `/thunder` | `/thunder` | `bigbangessentials.weather` | 🔒 | Set weather to thunderstorm |
| `/kill` | `/kill <player>` | `bigbangessentials.kill` | 🔒 | Kill a player (respects kill.exempt) |
| `/gamemode` | `/gamemode <survival\|creative\|adventure\|spectator\|0-3> [player]` | `bigbangessentials.gamemode` / `.gamemode.others` | 🔒 | Change player gamemode |
| `/gms` | `/gms [player]` | `bigbangessentials.gamemode` | 🔒 | Switch to Survival mode |
| `/gmc` | `/gmc [player]` | `bigbangessentials.gamemode` | 🔒 | Switch to Creative mode |
| `/gma` | `/gma [player]` | `bigbangessentials.gamemode` | 🔒 | Switch to Adventure mode |
| `/gmsp` | `/gmsp [player]` | `bigbangessentials.gamemode` | 🔒 | Switch to Spectator mode |

---

## Moderation

### Banning
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/ban` | `/ban <player> [reason]` | `bigbangessentials.moderation.ban` | 🔒 | Permanently ban a player |
| `/tempban` | `/tempban <player> <duration> [reason]` | `bigbangessentials.moderation.tempban` | 🔒 | Temporarily ban a player (e.g. `1h`, `7d`) |
| `/unban` | `/unban <player>` | `bigbangessentials.moderation.unban` | 🔒 | Unban a player |
| `/banip` | `/banip <player\|ip>` | `bigbangessentials.moderation.banip` | 🔒 | Ban a player's IP address |
| `/unbanip` | `/unbanip <ip>` | `bigbangessentials.moderation.unbanip` | 🔒 | Unban an IP address |
| `/banlist` | `/banlist [page]` | `bigbangessentials.moderation.banlist` | 🔒 | View all banned players |

### Kicking & Muting
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/kick` | `/kick <player> [reason]` | `bigbangessentials.moderation.kick` | 🔒 | Kick a player from the server |
| `/kickall` | `/kickall [reason]` | `bigbangessentials.moderation.kickall` | 🔒 | Kick all online players |
| `/mute` | `/mute <player> [duration] [reason]` | `bigbangessentials.moderation.mute` | 🔒 | Mute a player |
| `/unmute` | `/unmute <player>` | `bigbangessentials.moderation.unmute` | 🔒 | Unmute a player |
| `/mutelist` | `/mutelist` | `bigbangessentials.moderation.mutelist` | 🔒 | List all muted players |

### Jail
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/jail` | `/jail <player> [jail] [reason]` | `bigbangessentials.moderation.jail` | 🔒 | Jail a player indefinitely |
| `/jailfor` | `/jailfor <player> <duration> [jail] [reason]` | `bigbangessentials.moderation.jail` | 🔒 | Jail a player for a duration |
| `/unjail` | `/unjail <player>` | `bigbangessentials.moderation.unjail` | 🔒 | Release a player from jail |
| `/setjail` | `/setjail <name>` | `bigbangessentials.moderation.setjail` | 🔒 | Create a jail at current location |
| `/deljail` | `/deljail <name>` | `bigbangessentials.moderation.deljail` | 🔒 | Delete a jail location |
| `/jaillist` | `/jaillist` | `bigbangessentials.moderation.jaillist` | 🔒 | List all jail locations and jailed players |

### Freeze & Vanish
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/freeze` | `/freeze <player>` | `bigbangessentials.moderation.freeze` | 🔒 | Freeze a player in place |
| `/unfreeze` | `/unfreeze <player>` | `bigbangessentials.moderation.unfreeze` | 🔒 | Unfreeze a player |
| `/freezeall` | `/freezeall` | `bigbangessentials.moderation.freeze` | 🔒 | Freeze all online players |
| `/unfreezeall` | `/unfreezeall` | `bigbangessentials.moderation.unfreeze` | 🔒 | Unfreeze all players |
| `/freezelist` | `/freezelist` | `bigbangessentials.moderation.freezelist` | 🔒 | List all frozen players |
| `/vanish` | `/vanish [on\|off]` | `bigbangessentials.moderation.vanish` | 🔒 | Toggle vanish mode (invisible to other players) |
| `/v` | alias for `/vanish` | same | 🔒 | Alias |
| `/unvanish` | `/unvanish` | `bigbangessentials.moderation.vanish` | 🔒 | Disable vanish mode |
| `/vanishlist` | `/vanishlist` | `bigbangessentials.moderation.vanish` | 🔒 | List all vanished players |

---

## Chat & Messaging

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/msg` | `/msg <player> <message>` | `bigbangessentials.chat.msg` | ✅ | Send a private message to a player |
| `/tell` | alias for `/msg` | same | ✅ | Alias |
| `/whisper` | alias for `/msg` | same | ✅ | Alias |
| `/message` | alias for `/msg` | same | ✅ | Alias |
| `/w` | alias for `/msg` | same | ✅ | Alias |
| `/reply` | `/reply <message>` | `bigbangessentials.chat.reply` | ✅ | Reply to the last private message received |
| `/r` | alias for `/reply` | same | ✅ | Alias |
| `/msgtoggle` | `/msgtoggle` | `bigbangessentials.chat.msgtoggle` | ✅ | Toggle receiving private messages |
| `/socialspy` | `/socialspy [on\|off]` | `bigbangessentials.chat.socialspy` | 🔒 | See all private messages between players |
| `/ignore` | `/ignore <player>` | `bigbangessentials.chat.ignore` | ✅ | Ignore a player's messages |
| `/unignore` | `/unignore <player>` | `bigbangessentials.chat.unignore` | ✅ | Stop ignoring a player |
| `/mail` | `/mail send\|read\|clear\|sendall [args]` | `bigbangessentials.mail` | ✅ | In-game mail system |
| `/helpop` | `/helpop <message>` | `bigbangessentials.helpop` | ✅ | Send a help request to all online staff |
| `/ac` | alias for `/helpop` | same | ✅ | Alias |
| `/amsg` | alias for `/helpop` | same | ✅ | Alias |

### Chat Tags

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/createtag` | `/createtag <name> <format>` | `bigbangessentials.tag.create` / `bigbangessentials.tag.admin` | 🔒 | Create or update a chat tag |
| `/deltag` | `/deltag <name>` | `bigbangessentials.tag.remove` / `bigbangessentials.tag.admin` | 🔒 | Delete a chat tag |
| `/tags` | `/tags` or `/tag list` | none | ✅ | List the tags you can use and select one |
| `/tag` | alias for `/tags` | same | ✅ | Alias; supports `/tag select <name>`, `/tag clear`, and `/tag remove <name>` |

Each tag is protected by `bigbangessentials.tag.<name>` and can be granted individually or through the `bigbangessentials.tag.*` wildcard. The management nodes are `bigbangessentials.tag.create`, `bigbangessentials.tag.remove`, and `bigbangessentials.tag.admin`.

---

## Kits

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/kit` | `/kit [name] [player]` | `bigbangessentials.kit` | ✅ | Claim a kit (respects cooldown) |
| `/kits` | `/kits [page]` | `bigbangessentials.kit.list` | ✅ | List all available kits |
| `/listkits` | alias for `/kits` | same | ✅ | Alias |
| `/createkit` | `/createkit <name> [cooldown]` | `bigbangessentials.kit.create` | 🔒 | Create a kit from current inventory |
| `/delkit` | `/delkit <name>` | `bigbangessentials.kit.delete` | 🔒 | Delete a kit |
| `/kitreset` | `/kitreset <kit> [player]` | `bigbangessentials.kit.reset` | 🔒 | Reset a player's kit cooldown |

---

## Items

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/repair` | `/repair [all]` | `bigbangessentials.item.repair` | 🔒 | Repair held item (or all items with `all`) |
| `/fix` | alias for `/repair` | same | 🔒 | Alias |
| `/enchant` | `/enchant <enchantment> [level]` | `bigbangessentials.item.enchant` | 🔒 | Enchant held item |
| `/dispose` | `/dispose` | `bigbangessentials.item.dispose` | ✅ | Open an item disposal chest |
| `/trash` | alias for `/dispose` | same | ✅ | Alias |
| `/clearinventory` | `/clearinventory [player]` | `bigbangessentials.item.clearinventory` | 🔒 | Clear a player's inventory |
| `/ci` | alias for `/clearinventory` | same | 🔒 | Alias |
| `/clear` | alias for `/clearinventory` | same | 🔒 | Alias |
| `/powertool` | `/powertool <command>` or `/powertool clear` | `bigbangessentials.item.powertool` | 🔒 | Bind a command to held item |
| `/pt` | alias for `/powertool` | same | 🔒 | Alias (also alias for paytoggle — use with care) |
| `/invsee` | `/invsee <player>` | `bigbangessentials.item.invsee` | 🔒 | View another player's inventory (read-only) |
| `/inv` | alias for `/invsee` | same | 🔒 | Alias |
| `/invseeedit` | `/invseeedit <player>` | `bigbangessentials.item.invsee.edit` | 🔒 | View and edit another player's inventory |
| `/enderchest` | `/enderchest <player>` | `bigbangessentials.item.enderchest` | 🔒 | View another player's ender chest |
| `/ec` | alias for `/enderchest` | same | 🔒 | Alias |
| `/enderchestedit` | `/enderchestedit <player>` | `bigbangessentials.item.enderchest.edit` | 🔒 | View and edit another player's ender chest |
| `/ecedit` | alias for `/enderchestedit` | same | 🔒 | Alias |

### Portable Workstations
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/anvil` | `/anvil` | `bigbangessentials.item.anvil` | 🔒 | Open portable anvil |
| `/workbench` | `/workbench` | `bigbangessentials.item.workbench` | 🔒 | Open portable crafting table |
| `/crafting` | alias for `/workbench` | same | 🔒 | Alias |
| `/craft` | alias for `/workbench` | same | 🔒 | Alias |
| `/grindstone` | `/grindstone` | `bigbangessentials.item.grindstone` | 🔒 | Open portable grindstone |
| `/smithing` | `/smithing` | `bigbangessentials.item.smithing` | 🔒 | Open portable smithing table |
| `/loom` | `/loom` | `bigbangessentials.loom` | 🔒 | Open portable loom |
| `/cartography` | `/cartography` | `bigbangessentials.cartography` | 🔒 | Open portable cartography table |
| `/cartographytable` | alias for `/cartography` | same | 🔒 | Alias |

---

## Worth & Sell

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/worth` | `/worth [item\|hand] [amount]` | `bigbangessentials.worth` | ✅ | Check the sell value of an item |
| `/sell` | `/sell hand\|inventory\|all\|<item> [amount]` | `bigbangessentials.sell` | ✅ | Sell items for money |
| `/setworth` | `/setworth <item\|hand> <price\|remove>` | `bigbangessentials.setworth` | 🔒 | Set or remove an item's sell price |

---

## Utility

### Per-Player Time & Weather
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/ptime` | `/ptime [reset\|day\|noon\|night\|midnight\|<ticks>] [player]` | `bigbangessentials.ptime` / `.ptime.others` | 🔒 | Set a client-side time override for a player |
| `/pweather` | `/pweather [reset\|sun\|clear\|storm\|rain] [player]` | `bigbangessentials.pweather` / `.pweather.others` | 🔒 | Set a client-side weather override for a player |

### Effects & Entities
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/effect` | `/effect <player> <effect\|clear> [duration] [amplifier]` | `bigbangessentials.effect` | 🔒 | Apply or clear potion effects on a player |
| `/spawnmob` | `/spawnmob <mob> [amount] [player]` | `bigbangessentials.spawnmob` / `.spawnmob.others` | 🔒 | Spawn entities at a player's location |
| `/mob` | alias for `/spawnmob` | same | 🔒 | Alias |

### Item Utilities
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/unlimited` | `/unlimited [list\|clear\|<item\|hand>] [player]` | `bigbangessentials.unlimited` / `.unlimited.others` | 🔒 | Toggle infinite item use for a player |
| `/condense` | `/condense [item]` | `bigbangessentials.condense` | 🔒 | Compress loose items into storage blocks |

---

## Item Customisation & Miscellaneous Commands

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/me` | `/me <action>` | `bigbangessentials.me` | ✅ | Broadcast an action message to all players |
| `/tptoggle` | `/tptoggle [on\|off] [player]` | `bigbangessentials.tptoggle` / `.tptoggle.others` | ✅ | Toggle teleport request acceptance |
| `/gc` | `/gc` | `bigbangessentials.gc` | 🔒 | Show server TPS, memory, uptime, loaded chunks |
| `/mem` | alias for `/gc` | same | 🔒 | Alias |
| `/lightning` | `/lightning [player]` | `bigbangessentials.lightning` / `.lightning.others` | 🔒 | Strike lightning at look target or player |
| `/smite` | alias for `/lightning` | same | 🔒 | Alias |
| `/skull` | `/skull [player]` | `bigbangessentials.skull` | 🔒 | Get a player head item |
| `/itemname` | `/itemname [name\|-]` | `bigbangessentials.itemname` | 🔒 | Rename held item (omit or use `-` to clear) |
| `/rename` | alias for `/itemname` | same | 🔒 | Alias |
| `/itemlore` | `/itemlore add\|set\|remove\|clear [args]` | `bigbangessentials.itemlore` | 🔒 | Add/set/remove/clear held item lore lines |
| `/remove` | `/remove <type> [radius]` | `bigbangessentials.remove` | 🔒 | Remove entities in radius (types: all, items, mobs, animals, monsters, arrows, xp, boats, minecarts, tnt, paintings) |

---

## Player Info & Admin Tools

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/seen` | `/seen <player>` | `bigbangessentials.seen` | ✅ | Show if a player is online with location/ping, or offline |
| `/near` | `/near [radius]` | `bigbangessentials.near` | ✅ | List players within radius (default 200 blocks) with distance |
| `/ping` | `/ping [player]` | `bigbangessentials.ping` / `.ping.others` | ✅ | Show network latency in ms. Green <80ms, yellow <200ms, red otherwise |
| `/playtime` | `/playtime [player]` | `bigbangessentials.playtime` / `.playtime.others` | ✅ | Show total server play time (hours/minutes/seconds) from vanilla stats |
| `/whois` | `/whois <player>` | `bigbangessentials.whois` | 🔒 | Show UUID, world, coordinates, gamemode, ping, health and food level |
| `/realname` | `/realname <nickname>` | `bigbangessentials.realname` | ✅ | Find the real username of a player by their display name/nickname |
| `/sudo` | `/sudo <player> <command>` | `bigbangessentials.sudo` | 🔒 | Force a player to run a command. Prefix `c:` to send chat. Respects `bigbangessentials.sudo.exempt` |
| `/suicide` | `/suicide` | `bigbangessentials.suicide` | ✅ | Kill yourself. Broadcasts death message to all online players |
| `/msgtoggle` | `/msgtoggle [on\|off] [player]` | `bigbangessentials.msgtoggle` / `.msgtoggle.others` | ✅ | Block or allow incoming private messages. Synced with `MsgToggleManager` |
| `/rtoggle` | `/rtoggle [on\|off] [player]` | `bigbangessentials.rtoggle` / `.rtoggle.others` | ✅ | Toggle whether `/r` replies to the last sender (default on) |
| `/motd` | `/motd` | `bigbangessentials.motd` | ✅ | Show the message of the day (configured in `config.json` → `general.motd`) |
| `/rules` | `/rules` | `bigbangessentials.rules` | ✅ | Show server rules (configured in `config.json` → `general.rules`) |

---

## World Interaction & Fun Commands

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/fireball` | `/fireball [type] [speed] [ride]` | `bigbangessentials.fireball.<type>` | 🔒 | Shoot a projectile. Types: fireball, small, large, arrow, skull, egg, snowball, expbottle, dragon, trident, windcharge |
| `/tree` | `/tree <type>` | `bigbangessentials.tree` | 🔒 | Grow a tree at look target. Types: oak, birch, spruce, jungle, acacia, darkoak, mangrove, cherry, azalea, bigoak, mega_spruce, mega_jungle |
| `/bigtree` | `/bigtree` | `bigbangessentials.tree` | 🔒 | Grow a large oak tree (alias for `/tree bigoak`) |
| `/break` | `/break` | `bigbangessentials.break` | 🔒 | Instantly break the looked-at block (no drops). Bedrock requires `bigbangessentials.break.bedrock` |
| `/ice` | `/ice [player]` | `bigbangessentials.ice` / `.ice.others` | 🔒 | Freeze a player solid using powder snow freeze ticks |
| `/bottom` | `/bottom` | `bigbangessentials.bottom` | 🔒 | Teleport to the lowest safe position at your current XZ coordinates |
| `/tpaall` | `/tpaall [player]` | `bigbangessentials.tpaall` / `.tpaall.others` | 🔒 | Send a tpa-here request to every online player (respects tptoggle) |
| `/broadcastworld` | `/broadcastworld <message>` | `bigbangessentials.broadcastworld` | 🔒 | Broadcast a coloured message to all players in the sender's current world |
| `/bcastworld` | alias for `/broadcastworld` | same | 🔒 | Alias |

---

## AFK

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/afk` | `/afk [message]` | `bigbangessentials.afk` | ✅ | Toggle AFK status with optional message |
| `/away` | alias for `/afk` | same | ✅ | Alias |

---

## Web Dashboard

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/dashboard` | `/dashboard start\|stop\|restart\|status\|info` | `bigbangessentials.dashboard` | 🔒 | Manage the web dashboard server |
| `/dashboardregister` | `/dashboardregister [username] [password]` | `bigbangessentials.dashboard.register` | ✅ (if permitted) | Register a web dashboard account in-game |

---

## Permissions Management

| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/permissions` | `/permissions <user\|group> <action> [args]` | `bigbangessentials.permissions` | 🔒 | Manage user and group permissions |
| `/pex` | alias for `/permissions` | same | 🔒 | Alias |

---

## Miscellaneous

### Information
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/whois` | `/whois <player>` | `bigbangessentials.whois` | 🔒 | View detailed info about a player |
| `/info` | alias for `/whois` | same | 🔒 | Alias |
| `/seen` | `/seen <player>` | `bigbangessentials.seen` | ✅ | Check when a player was last online |
| `/list` | `/list` | `bigbangessentials.list` | ✅ | List all online players |
| `/who` | alias for `/list` | same | ✅ | Alias |
| `/online` | alias for `/list` | same | ✅ | Alias |
| `/near` | `/near [radius]` | `bigbangessentials.near` | ✅ | Show nearby players |
| `/nearby` | alias for `/near` | same | ✅ | Alias |
| `/ping` | `/ping [player]` | `bigbangessentials.ping` | ✅ | Check your ping (or another player's) |
| `/pong` | alias for `/ping` | same | ✅ | Alias |
| `/playtime` | `/playtime [player]` | `bigbangessentials.playtime` | ✅ | Check a player's total play time |
| `/getpos` | `/getpos [player]` | `bigbangessentials.getpos` | ✅ | Show your current coordinates |
| `/coords` | alias for `/getpos` | same | ✅ | Alias |
| `/whereami` | alias for `/getpos` | same | ✅ | Alias |
| `/compass` | `/compass` | `bigbangessentials.compass` | ✅ | Show your current facing direction |
| `/direction` | alias for `/compass` | same | ✅ | Alias |
| `/depth` | `/depth` | `bigbangessentials.depth` | ✅ | Show your current depth (Y level relative to sea level) |
| `/motd` | `/motd` | `bigbangessentials.motd` | ✅ | View the server message of the day |
| `/rules` | `/rules` | `bigbangessentials.rules` | ✅ | View the server rules |

### Player Actions
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/nick` | `/nick <nickname\|off>` | `bigbangessentials.nick` | 🔒 | Set your display nickname |
| `/nickname` | alias for `/nick` | same | 🔒 | Alias |
| `/realname` | `/realname <nickname>` | `bigbangessentials.realname` | ✅ | Find a player's real name from their nickname |
| `/suicide` | `/suicide` | `bigbangessentials.suicide` | ✅ | Kill yourself |
| `/killme` | alias for `/suicide` | same | ✅ | Alias |
| `/sign` | `/sign <line> <text>` | `bigbangessentials.sign` | 🔒 | Edit sign text |
| `/book` | `/book` | `bigbangessentials.book` | 🔒 | Edit or unsign a written book |
| `/language` | `/language [code]` | `bigbangessentials.language` | 🔒 | View or switch the server language |
| `/world` | `/world [name] [player]` | `bigbangessentials.world` / `.world.others` | 🔒 | Teleport to a world/dimension (lists worlds if no arg) |
| `/spawner` | `/spawner <mob>` | `bigbangessentials.spawner` | 🔒 | Change the looked-at mob spawner type |
| `/recipe` | `/recipe [item]` | `bigbangessentials.recipe` | ✅ | Unlock and show crafting recipe for held or named item |
| `/tpauto` | `/tpauto [on\|off] [player]` | `bigbangessentials.tpauto` / `.tpauto.others` | ✅ | Auto-accept all incoming teleport requests |


---

## Jobs & Professions

Jobs and Professions system with levels, skills, daily limit tracking, and admin tools.

### Player Commands
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/jobs` | `/jobs` | `jobs.command.jobs` | ✅ | View your jobs summary profile and daily earnings |
| `/jobs list` | `/jobs list` | `jobs.command.list` | ✅ | List all available jobs, their statuses and commands |
| `/jobs entrar` | `/jobs entrar <job>` | `jobs.command.entrar` | ✅ | Enter/Join a profession |
| `/jobs join` | alias for `/jobs entrar` | same | ✅ | Alias |
| `/jobs sair` | `/jobs sair <job>` | `jobs.command.sair` | ✅ | Leave a profession |
| `/jobs leave` | alias for `/jobs sair` | same | ✅ | Alias |
| `/jobs info` | `/jobs info [job]` | `jobs.command.info` | ✅ | Show detailed information on actions/rewards for a job |
| `/jobs progresso` | `/jobs progresso [job]` | `jobs.command.info` | ✅ | View detailed XP progression bar for a job |
| `/jobs habilidades` | `/jobs habilidades <job>` | `jobs.command.habilidades` | ✅ | View passive skill trees for a job |
| `/jobs skills` | alias for `/jobs habilidades` | same | ✅ | Alias |
| `/jobs habilidade` | `/jobs habilidade <job> desbloquear <skill>` | `jobs.command.habilidades` | ✅ | Upgrade a passive skill using skill points |
| `/jobs ganhos` | `/jobs ganhos` | `jobs.command.ganhos` | ✅ | Show daily earnings, limits, multipliers, and reset time |
| `/jobs top` | `/jobs top <job>` | `jobs.command.top` | ✅ | View leaderboard for the highest levels in a job |
| `/jobs notificacoes` | `/jobs notificacoes <on\|off>` | `jobs.command.jobs` | ✅ | Toggle actionbar notifications on/off |
| `/jobs ajuda` | `/jobs ajuda` | none | ✅ | Show jobs help menu |

### Admin Commands
| Command | Syntax | Permission | Default | Description |
|---|---|---|---|---|
| `/jobsadmin reload` | `/jobsadmin reload` | `jobs.admin.reload` | 🔒 | Reload jobs and professions configurations |
| `/jobsadmin info` | `/jobsadmin info <player> [job]` | `jobs.admin.info` | 🔒 | View another player's jobs profile or detailed job stats |
| `/jobsadmin entrar` | `/jobsadmin entrar <player> <job>` | `jobs.admin.modify` | 🔒 | Force a player to join a profession |
| `/jobsadmin sair` | `/jobsadmin sair <player> <job>` | `jobs.admin.modify` | 🔒 | Force a player to leave a profession |
| `/jobsadmin setlevel` | `/jobsadmin setlevel <player> <job> <level>` | `jobs.admin.modify` | 🔒 | Set a player's job level |
| `/jobsadmin addxp` | `/jobsadmin addxp <player> <job> <xp>` | `jobs.admin.modify` | 🔒 | Add XP to a player's job (triggers level-ups) |
| `/jobsadmin removexp` | `/jobsadmin removexp <player> <job> <xp>` | `jobs.admin.modify` | 🔒 | Remove XP from a player's job |
| `/jobsadmin reset` | `/jobsadmin reset <player> [job]` | `jobs.admin.reset` | 🔒 | Reset progress of one or all of a player's jobs |
| `/jobsadmin resetganhos` | `/jobsadmin resetganhos <player>` | `jobs.admin.reset` | 🔒 | Reset a player's daily earnings |
| `/jobsadmin pontos` | `/jobsadmin pontos <player> <job> <adicionar\|remover> <qty>` | `jobs.admin.modify` | 🔒 | Add or remove skill points for a player's job |
| `/jobsadmin desbloquear` | `/jobsadmin desbloquear <player> <job>` | `jobs.admin.modify` | 🔒 | Grant permission/unlock access to a job for a player |
| `/jobsadmin bloquear` | `/jobsadmin bloquear <player> <job>` | `jobs.admin.modify` | 🔒 | Revoke permission/lock access to a job for a player |
| `/jobsadmin debug` | `/jobsadmin debug <on\|off>` | `jobs.admin.debug` | 🔒 | Toggle global administrative debug mode |

---

## 📊 Command Count Summary

| System | Commands (incl. aliases) |
|---|---|
| Economy | 8 |
| Teleportation | 17 |
| Homes | 5 |
| Warps | 8 |
| Spawn | 2 |
| Player State & Admin Tools | 15 |
| Server Admin | 16 |
| Moderation | 23 |
| Chat & Messaging | 13 |
| Kits | 6 |
| Items (incl. workstations) | 21 |
| Worth & Sell | 3 |
| Utility | 7 |
| AFK | 2 |
| Web Dashboard | 2 |
| Permissions Management | 2 |
| Miscellaneous | 22 |
| Jobs & Professions | 27 |
| **Total** | **~199** |

---

## ⚙️ Configuration

All commands can be individually enabled or disabled in `config.json` under the `commands` section:

```json
{
  "commands": {
    "fly": true,
    "god": true,
    "heal": true,
    "sell": true
  }
}
```

Economy-related settings (currency symbol, sell multiplier, etc.) are under the `economy` section.  
Teleportation settings (delays, safe teleport, random teleport) are under `teleportation`.  
Web dashboard settings are under `webDashboard`.

---

*See [PermissionSystem.md](PermissionSystem.md) for the full permissions reference.*  
*See [EconomySystem.md](EconomySystem.md), [TeleportationSystem.md](TeleportationSystem.md), etc. for system-specific documentation.*
