<p align="right">
  🌍 Read in: <a href="README.md">Português</a>
</p>

# BigBangEssentials

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-blue.svg)](https://minecraft.net)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.16.9+-blueviolet.svg)](https://fabricmc.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.179+-green.svg)](https://neoforged.net)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.2.6+build.366-blue)](https://github.com/pedro-dalben/BigBangEssentials)

A modular Minecraft server management platform for Fabric and NeoForge. Provides economy, jobs, administration, teleportation, player utilities, chat, crates, holograms, tablist, rankup, PokeMarket, and a web dashboard — all persisted in SQLite or MySQL with a configurable module system.

---

## Table of Contents

- [Overview](#overview)
- [Compatibility](#compatibility)
- [Features](#features)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Installation](#installation)
- [First-time setup](#first-time-setup)
- [Configuration files](#configuration-files)
- [Database](#database)
- [Permissions](#permissions)
- [Commands](#commands)
- [Integrations](#integrations)
- [Developer API](#developer-api)
- [Development](#development)
- [Testing](#testing)
- [Build & artifacts](#build--artifacts)
- [Releases](#releases)
- [Troubleshooting](#troubleshooting)
- [Security](#security)
- [Contributing](#contributing)
- [Credits](#credits)
- [License](#license)

---

## Overview

BigBangEssentials is a server-side mod for Minecraft servers running **Fabric** or **NeoForge**. It replaces vanilla commands (like `/msg`, `/tell`, `/tag`) and adds dozens of ready-to-use server systems:

- Economy with payments, balances, and anti-exploit protection
- Job system with professions, XP, levels, contracts, and Cobblemon integration
- Full moderation (ban, kick, mute, jail, freeze, vanish)
- Teleportation (homes, warps, spawn, TPA, /back, random TP)
- Chat with private messages, channels, tags, AFK, socialspy
- Crates with keys, rewards, animations, and holograms
- Kits, rankup, admin shops
- PokeMarket — player-to-player Pokemon marketplace (Cobblemon)
- Web dashboard with REST API and WebSocket
- Built-in PlaceholderAPI, LuckPerms and FTB Ranks support

The mod runs exclusively on the server. Vanilla clients can connect without installing anything.

---

## Compatibility

| Component | Version | Status |
|---|---|---|
| Minecraft | 1.21.1 (range: 1.21.1 – 1.21.10) | Stable |
| Java | 21 (Corretto recommended) | Required |
| Fabric Loader | 0.16.9+ | Stable |
| Fabric API | 0.102.0+ for 1.21.1 | Required (Fabric) |
| NeoForge | 21.1.179+ | Stable |
| Cobblemon | 1.7.3+ for 1.21.1 | Optional |
| Environment | Server-side (vanilla clients) | Confirmed |
| Primary DB | SQLite (default) | Supported |
| Alternative DB | MySQL 8+ | Supported |

> **Note:** NeoForge support covers up to 1.21.10. Version 1.21.11 introduces breaking API changes.

---

## Features

### Economy & progression

- Currency system (`/balance`, `/pay`, `/baltop`)
- Admin commands (`/eco`, `/setworth`)
- Gems — secondary currency with reservations, capture, and expiry
- Vault API for integration with other mods
- Daily earning limits for jobs
- Anti-exploit: cooldowns, rate limiting, block provenance
- Rankup with JSON-configured ranks
- Rankup + jobs integration (shared milestones)

### Administration

- Ban, tempban, banip, unban, kick, kickall
- Mute, mutelist, jail (multiple configurable cells)
- Freeze, freezeall, vanish, sudo
- Inventory inspection (`/invsee`, `/enderchest`)
- Player management (`/whois`, `/seen`, `/playtime`)
- Server commands (`/broadcast`, `/time`, `/weather`, `/kill`)
- Root admin command: `/bigbangessentials` (alias: `/bbe`)

### Teleportation & utilities

- Homes (multiple, permission-limited)
- Warps (global and personal)
- Configurable spawn
- TPA, TPA here, TP accept/deny/cancel
- `/back` — return to last location
- TPR (random), TPPOS, TP all, TP override
- `/top`, `/jump`, `/jumpto`, `/bottom`
- Kits (with cooldowns and menu preview)
- Portable utilities: `/anvil`, `/craft`, `/grindstone`, `/smithing`, `/stonecutter`, `/loom`, `/cartography`, `/book`, `/enchantingtable`
- YAML-configurable teleport menus

### Chat & communication

- Private messages (`/msg`, `/reply`, `/tell`, `/whisper`, `/w`)
- Dynamic chat channels (JSON-configured)
- Customizable chat tags
- AFK with automatic detection
- SocialSpy, mail, ignore
- Discord integration via DiscordSRV, DCIntegration, SDLink (reflection-based)
- Custom placeholders in chat and tablist

### Cobblemon

- **PokeMarket** — player-to-player Pokemon marketplace
  - Fixed-term or sell-until-gone listings
  - Direct player trades
  - Claim and notification system
  - Automatic listing expiry
  - Suggested pricing by rarity/IVs
- Cobblemon job integration:
  - Trainer, Breeder, Collector, Paleontologist, Shepherd, Raids
  - Breeding jobs for incubation
  - Boss/raid den jobs
  - Fossil processing jobs
  - Pasture collection and diversity scoring
  - Researcher (Pokedex completion)
- Rankup with Cobblemon requirements

### Infrastructure

- SQLite (default) or MySQL/HikariCP database
- 22 versioned migrations with checksum verification
- Connection pool with async execution
- Auto-generated configuration on first startup
- Web dashboard with REST API and WebSocket
- Discord OAuth and local registration authentication
- Live player map in dashboard
- Player session analytics
- 17 configurable modules
- Built-in PlaceholderAPI (30+ native placeholders)

---

## Architecture

```
BigBangEssentials/
├── common/          # Shared code (core logic, commands, DB, UI)
├── fabric/          # Fabric entrypoint + mixins + event bus shim
├── neoforge/        # NeoForge entrypoint + native event bus integration
├── docs/            # Technical documentation
└── gradle/          # Wrapper and conventions
```

Three-layer separation:

1. **common** — Contains all mod logic: commands, economy, jobs, chat, database, dashboard, placeholders, permissions, integrations. Loader-independent.
2. **fabric** — `FabricModEntrypoint` + mixins + NeoForge event bus shim for listener reuse. Built with Fabric Loom.
3. **neoforge** — `NeoForgeModEntrypoint` with `@Mod` + native NeoForge listeners. Built with NeoForge ModDev.

`PlatformProvider` interface abstracts server access, directories, event bus, and mod detection across loaders.

### Modules

| Module | Dependencies | Default enabled |
|---|---|---|
| database | — | Yes |
| economy | database | Yes |
| chat | — | Yes |
| moderation | — | Yes |
| teleportation | — | Yes |
| kits | — | Yes |
| customcommands | — | Yes |
| webdashboard | — | Yes |
| jobs | economy, database | Yes |
| rankup | economy, database | Yes |
| crates | database | Yes |
| holograms | — | Yes |
| shop | economy, database | Yes |
| adminshop | economy, database | Yes |
| cobblemon | — | Auto (if Cobblemon present) |
| pokemarket | database, economy, cobblemon | Yes |
| tablist | — | Yes |

Each module can be disabled via config. Unsatisfied dependencies block activation.

---

## Requirements

- **Java 21** (Corretto 21 recommended)
- **Minecraft 1.21.1**
- **Fabric:** Fabric Loader 0.16.9+ and Fabric API
- **NeoForge:** NeoForge 21.1.179+
- **Cobblemon (optional):** 1.7.3+ for 1.21.1
- **LuckPerms (optional):** For permission management
- **FTB Ranks (optional):** For advanced ranks (NeoForge)
- **MySQL (optional):** For external production database

---

## Installation

### Fabric

1. Install **Fabric Loader** for Minecraft 1.21.1.
2. Place **Fabric API** (0.102.0+ for 1.21.1) in `mods/`.
3. Place `bigbangessentials-fabric-*.jar` in `mods/`.
4. (Optional) Place **Cobblemon** (Fabric) in `mods/` for PokeMarket or Cobblemon jobs.
5. Start the server. Configuration files will be auto-generated.
6. Configure permissions and `database.json` as needed.
7. Restart the server to apply changes.

### NeoForge

1. Install **NeoForge** 21.1.179+ for Minecraft 1.21.1.
2. Place `bigbangessentials-*.jar` in `mods/`.
3. (Optional) Place **Cobblemon** (NeoForge) in `mods/` for PokeMarket or Cobblemon jobs.
4. Start the server. Configuration files will be auto-generated.
5. Configure permissions and `database.json` as needed.
6. Restart the server to apply changes.

> ⚠️ Fabric and NeoForge JARs are **not interchangeable**. Use the correct artifact for your loader.

---

## First-time setup

1. After starting the server, configs are located at:
   ```
   world/serverconfig/bigbangessentials/
   ```
   (legacy migration from `config/bigbangessentials/` is automatic)

2. Configure the database in `database.json`:
   ```json
   {
     "enabled": true,
     "required": true,
     "type": "SQLITE",
     "sqlite": {
       "file": "bigbangessentials/database/bigbangessentials.db"
     }
   }
   ```

3. Set up permissions (example with LuckPerms):
   ```
   /lp group default permission set bigbangessentials.player true
   /lp group moderator permission set bigbangessentials.moderation.* true
   /lp group admin permission set bigbangessentials.admin true
   ```

4. Verify the mod loaded:
   ```
   /bigbangessentials version
   ```

5. (Optional) Split `config.json` into smaller files:
   ```
   /bigbangessentials config split
   ```

---

## Configuration files

```
world/serverconfig/bigbangessentials/
├── config.json              # Main configuration (monolithic)
├── database.json            # Database connection settings
├── economy.json             # Economy configuration
├── permissions.json         # Internal permissions
├── kits.json                # Kit definitions
├── modules.json             # Module enable/disable toggles
├── tablist.json             # Tab list configuration
├── discord_auth.json        # Discord authentication for dashboard
├── custom_commands.json     # Custom command aliases
├── rankup.json              # Rank definitions
├── adminshop.json           # Admin shop catalog
├── tags.json                # Chat tag definitions
├── jobs/                    # Jobs configuration
│   ├── global.json
│   ├── slots.json
│   ├── milestones.json
│   └── professions/*.json
├── menus/*.yml              # Customizable menus (YAML)
├── holograms/*.json         # Hologram definitions
├── badges/                  # Chat badge images
└── text/*.txt               # Custom text pages
```

When the monolithic `config.json` grows too large, run `/bigbangessentials config split` to divide it into system-specific files (`main.json`, `commands.json`, `chat.json`, `teleportation.json`, `moderation.json`, `webdashboard.json`, `items.json`, `afk.json`, `security.json`).

> All missing config files are auto-generated with defaults on first startup.

---

## Database

### SQLite (default)

- Local file: `bigbangessentials/database/bigbangessentials.db`
- Pool: `maximumPoolSize=1` (WAL contention prevention)
- Ideal for small to medium servers
- No external configuration needed

### MySQL (production)

Configure in `database.json`:

```json
{
  "type": "MYSQL",
  "mysql": {
    "host": "localhost",
    "port": 3306,
    "database": "bigbangessentials",
    "user": "bbe_user",
    "password": "secure_password"
  },
  "pool": {
    "maximumPoolSize": 10,
    "minimumIdle": 2,
    "connectionTimeoutMs": 5000
  }
}
```

Recommended for servers with high concurrent player counts.

### Migrations

22 versioned migrations run automatically on startup. Fresh servers get the full schema. Existing servers only run pending migrations. Each migration has a checksum to detect tampering.

### Recommendations

- Regularly back up the database (especially before updating the mod)
- MySQL is recommended for production performance and reliability
- Never share database credentials publicly

---

## Permissions

The permission system supports LuckPerms, FTB Ranks, PEX, or the built-in system (which defaults to OP for admin commands).

All permissions follow `bigbangessentials.<module>.<action>` format.

### Core permissions

| Permission | Description | Default |
|---|---|---|
| `bigbangessentials.player.*` | Basic player commands | true |
| `bigbangessentials.chat.*` | Chat commands (msg, reply, mail) | true |
| `bigbangessentials.economy.*` | Economy commands (balance, pay) | true |
| `bigbangessentials.teleport.*` | Teleport commands (home, warp, tpa) | true |
| `bigbangessentials.kit.*` | Kit access | true |
| `bigbangessentials.item.*` | Item commands (hat, repair) | true |
| `bigbangessentials.moderation.*` | Moderation commands | false |
| `bigbangessentials.admin.*` | Admin commands | false |
| `bigbangessentials.jobs.*` | Jobs system | true |
| `bigbangessentials.crates.*` | Crate management | true |
| `bigbangessentials.holograms.*` | Hologram management | false |
| `bigbangessentials.rankup.*` | Rankup system | true |
| `bigbangessentials.pokemarket.*` | PokeMarket | true |
| `bigbangessentials.tablist.*` | Tablist configuration | false |
| `bigbangessentials.webdashboard.*` | Dashboard access | false |

Full permission reference: [`docs/Wiki/PermissionSystem.md`](docs/Wiki/PermissionSystem.md) and [`permissions.md`](permissions.md)

---

## Commands

The mod registers approximately **110 unique commands** (excluding aliases and subcommands).

### Administrative

| Command | Description | Permission |
|---|---|---|
| `/bigbangessentials` `/bbe` | Root admin command | admin |
| `/eco` | Manage economy | admin |
| `/ban` `/unban` `/banip` `/tempban` | Ban players | moderation |
| `/kick` `/kickall` | Kick players | moderation |
| `/mute` `/unmute` `/mutelist` | Mute players | moderation |
| `/jail` `/unjail` `/setjail` `/deljail` | Jail system | moderation |
| `/freeze` `/unfreeze` `/freezeall` | Freeze players | moderation |
| `/vanish` `/v` | Toggle vanish | moderation |
| `/sudo` | Run command as another | moderation |
| `/broadcast` `/bc` | Broadcast to all | admin |
| `/gamemode` `/gms` `/gmc` `/gmsp` `/gma` | Change gamemode | admin |
| `/kill` | Kill player | admin |
| `/invsee` `/enderchest` | Inspect inventory | moderation |
| `/dashboard` | Manage web dashboard | admin |

### Teleportation

| Command | Description | Permission |
|---|---|---|
| `/home` `/sethome` `/delhome` `/homes` | Homes | teleport |
| `/warp` `/setwarp` `/delwarp` `/warps` | Warps | teleport |
| `/spawn` `/setspawn` | Spawn | teleport |
| `/tpa` `/tpahere` `/tpaccept` `/tpdeny` | Teleport requests | teleport |
| `/tp` `/tphere` `/tpall` `/tppos` | Admin teleport | admin |
| `/tpr` | Random teleport | teleport |
| `/back` | Return to last position | teleport |
| `/top` `/jump` `/bottom` | Quick teleport | teleport |

### Economy

| Command | Description | Permission |
|---|---|---|
| `/balance` `/bal` | Check balance | economy |
| `/pay` | Pay player | economy |
| `/baltop` | Balance leaderboard | economy |
| `/worth` `/sell` | Item value and selling | economy |
| `/gems` | Gem wallet | economy |

### Chat

| Command | Description | Permission |
|---|---|---|
| `/msg` `/tell` `/w` `/whisper` | Private message | chat |
| `/reply` `/r` | Reply to message | chat |
| `/mail` | Mail system | chat |
| `/ignore` `/unignore` | Ignore player | chat |
| `/socialspy` | Spy on messages | moderation |
| `/afk` `/away` | Away from keyboard | chat |
| `/tags` | Manage chat tags | chat |

### Jobs

| Command | Description | Permission |
|---|---|---|
| `/jobs` | View and manage jobs | jobs |
| `/jobsadmin` | Administer jobs | admin |

### Crates

| Command | Description | Permission |
|---|---|---|
| `/crates` `/crate` | Manage crates | crates |
| `/givekey` `/keygive` | Give key to player | admin |

### Cobblemon

| Command | Description | Permission |
|---|---|---|
| `/pokemarket` `/gts` `/pm` | Pokemon marketplace | pokemarket |

`/shop` is AdminShop. `/chestshop` and `/cshop` are ChestShop commands;
`/gts` and `/pm` are aliases of the same PokéMarket command tree.

Full command reference: [`docs/Wiki/CommandsReference.md`](docs/Wiki/CommandsReference.md)

---

## Integrations

| Integration | Required | Loader | Version | Behavior without |
|---|---|---|---|---|
| **Cobblemon** | No | Both | 1.7.3+ | Cobblemon jobs and PokeMarket disabled |
| **LuckPerms** | No | Both | API 5.4 | Uses built-in permission system (OP) |
| **FTB Ranks** | No | NeoForge | — | Falls back to LuckPerms or built-in |
| **Fabric API** | Yes (Fabric) | Fabric | 0.102.0+ | Mod won't load |
| **DiscordSRV** | No | Both | — | Chat works without Discord bridge |
| **PlaceholderAPI** | No | Both | — | Built-in PlaceholderManager always active |

---

## Developer API

### Public API

Package: `com.pedrodalben.bigbangessentials.api`

| Interface | Purpose |
|---|---|
| `EconomyAPI` | Financial operations (deposit, withdraw, balance, transfer) |
| `BigBangEssentialsAPI` | Central mod access |
| `PlaceholderAPI` | Placeholder registration and resolution |
| `ChatAPI` | Message sending |
| `PermissionAPI` | Permission checking |
| `RankupAPI` | Rank query and promotion |

### Events

| Event | Description |
|---|---|
| `EconomyDepositEvent` | Fired when money is deposited |
| `EconomyWithdrawEvent` | Fired when money is withdrawn |
| `GemBalanceChangedEvent` | Fired when gem balance changes |
| `RankTransitionCompletedEvent` | Fired when rank transition completes |

### Placeholders

30+ built-in placeholders: `{player}`, `{online}`, `{max}`, `{balance}`, `{job}`, `{job_level}`, `{rank}`, `{gems}`, `{ping}`, `{world}`, `{prefix}`, `{suffix}`, and more.

Developers can register custom placeholders via `PlaceholderAPI.registerPlaceholder()`.

### Dashboard REST API

The web dashboard exposes a REST API with endpoints for server status, players, logs, configuration, and statistics. Authentication via Discord OAuth or local registration.

---

## Development

### Prerequisites

- JDK 21 (Corretto 21 recommended)
- IntelliJ IDEA (Community Edition recommended)
- Git

### Setup

```bash
git clone https://github.com/pedro-dalben/BigBangEssentials.git
cd BigBangEssentials
./gradlew idea
```

Open the directory in IntelliJ IDEA as a Gradle project. Sync will download all dependencies automatically.

### Build

```bash
./gradlew build
```

### Special tasks

```bash
./gradlew verifyCobblemonDependencies    # Verify Cobblemon dependencies
./gradlew verifyNoBundledCobblemon       # Ensure Cobblemon is not bundled in JAR
./gradlew test                           # All tests (requires Docker for MySQL)
./gradlew mysqlIntegrationTest           # MySQL integration tests
./gradlew pokeMarketConcurrencyTest      # PokeMarket concurrency tests
./gradlew pokeMarketFaultInjectionTest   # PokeMarket fault injection tests
./gradlew runWithoutCobblemonTest        # Test startup without Cobblemon
```

### Build artifacts

| Loader | Path | Filename |
|---|---|---|
| Fabric | `fabric/build/libs/` | `bigbangessentials-fabric-<version>+build.<N>.jar` |
| NeoForge | `neoforge/build/libs/` | `bigbangessentials-<version>+build.<N>.jar` |

---

## Testing

The project has **90+ tests** organized by module:

- **Unit:** Domain objects, validation, parsing, formatting
- **Integration:** Database (SQLite + MySQL via Testcontainers), jobs, economy, crates
- **Concurrency:** PokeMarket, gem reservations
- **Fault injection:** PokeMarket, economy, database
- **Mocking:** Permissions, placeholders, commands

MySQL tests require Docker or `BBE_TEST_MYSQL_*` environment variables.

---

## Releases

Currently there are no official releases published on GitHub. To use the mod:

1. Build from source: `./gradlew build`
2. JARs are at `fabric/build/libs/` and `neoforge/build/libs/`
3. See `CHANGELOG.md` for version history

---

## Troubleshooting

| Issue | Likely cause | Solution |
|---|---|---|
| Mod won't load | Java < 21 | Check `java -version`, install JDK 21 |
| `fabric.mod.json` not found | Wrong loader | Use the Fabric JAR with Fabric Loader |
| "Cobblemon class not found" | Cobblemon missing | Install Cobblemon or disable Cobblemon modules |
| MySQL connection fails | Invalid credentials | Check `database.json`, access, and firewall |
| Commands not showing | Module disabled | Check `modules.json` or `config.json > modules` |
| Config not applying | Game cache | Stop server, edit, restart |
| Permission denied for player command | Permission system | Configure LuckPerms or grant OP |
| `configuration-cache` error | Corrupted cache | `rm -rf .gradle/configuration-cache` |

---

## Security

- **Never publish** database credentials
- Web dashboard requires authentication (Discord OAuth or local registration)
- Admin endpoints are protected by permissions
- Configure restrictive permissions for admin commands
- Regularly back up the database and configuration
- Report vulnerabilities via GitHub Issues

---

## Contributing

1. Open an issue describing the bug or enhancement
2. Fork the repository
3. Create a descriptive branch
4. Keep commits organized
5. Run `./gradlew test` before opening a PR
6. Document relevant changes
7. Open a pull request

---

## Credits

- **Author:** [pedrodalben](https://github.com/pedrodalben)
- **Repository:** [github.com/pedro-dalben/BigBangEssentials](https://github.com/pedro-dalben/BigBangEssentials)
- **Inspiration:** The project was inspired by NeoEssentials (originally by MrWhiteFlamesYT), but constitutes a complete, independent rewrite.

---

## License

**MIT License**. Copyright (c) 2025 ZeroG Network. See [`LICENSE`](LICENSE).

---

**BigBangEssentials** — A modular platform for Minecraft servers.
