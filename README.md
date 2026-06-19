# BigBangEssentials

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://www.minecraft.net/) [![NeoForge](https://img.shields.io/badge/NeoForge-orange.svg)](https://neoforged.net/) [![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](https://opensource.org/licenses/MIT) [![Version](https://img.shields.io/github/v/release/pedro-dalben/BigBangEssentials?label=Version)](https://github.com/pedro-dalben/BigBangEssentials/releases) [![Discord](https://img.shields.io/discord/709351422088708196?color=7289da&label=Discord&logo=discord&logoColor=white)](https://discord.gg/dUGAQF2Mga)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-Support%20Me-F16061?logo=ko-fi&logoColor=white)](https://ko-fi.com/mrwhiteflamesyt)

> BigBangEssentials is a comprehensive, config-driven essentials mod for Minecraft NeoForge 1.21.1 - 1.21.11 servers. It provides 50+ commands, GUI tools, advanced administration, and a real-time web dashboard—all managed by modular JSON config files and standardized documentation.
>
> ⚠️ **Credits & Origin Notice**: This project is a complete recreation/rewrite based on the original **[NeoEssentials](https://github.com/pedro-dalben/BigBangEssentials)** mod created by **[MrWhiteFlamesYT](https://github.com/MrWhiteFlamesYT)**. Full credit for the original design, concepts, and features belongs to the original author.

## 🌟 Overview

BigBangEssentials brings essential server management, player utilities, and advanced admin features to NeoForge servers. All features are strictly documented and driven by config files for reliability and transparency.

**Server-Side Only**: No client install required—works with vanilla clients.
**50+ Commands**: Covers all major server functions, utilities, and moderation.
**Modern UI**: GUI commands, color code support, and web dashboard.

## ✨ Core Systems & Features

- **Economy System**: Player balances, payments, kits, and shop support.
- **Chat & Messaging**: Private messages, mail, ignore/socialspy, AFK system.
- **Moderation**: Ban, kick, mute, jail, vanish, freeze, sudo, player data.
- **Teleportation**: Homes, warps, spawn, teleport requests, back system.
- **Kit Management**: Configurable item kits with cooldowns and preview.
- **Web Dashboard**: Real-time server monitoring, config editing, API endpoints.
- **Permission System**: LuckPerms, FTB Ranks, and built-in support.
- **Item Management**: Item spawning, repair, enchant, clearinventory, powertool.
- **Utility Systems**: Nicknames, MOTD, near, ping, depth, helpop, rules, suicide, etc.
- **Database Infrastructure**: SQLite/MySQL support, HikariCP pool, async executor, versioned migrations.
- **APIs & Placeholder System**: PlaceholderAPI integration, custom placeholders, REST API endpoints.

## 📖 Documentation

Start at [Home](https://github.com/pedro-dalben/BigBangEssentials/wiki/Home) for a complete, config-driven documentation hub. All major systems are documented and standardized to match the codebase and config files:
  - [DatabaseInfrastructure](docs/database.md)
  - [EconomySystem](https://github.com/pedro-dalben/BigBangEssentials/wiki/EconomySystem)
  - [ChatSystem](https://github.com/pedro-dalben/BigBangEssentials/wiki/ChatSystem)
  - [ModerationSystem](https://github.com/pedro-dalben/BigBangEssentials/wiki/ModerationSystem)
  - [TeleportationSystem](https://github.com/pedro-dalben/BigBangEssentials/wiki/TeleportationSystem)
  - [KitManagement](https://github.com/pedro-dalben/BigBangEssentials/wiki/KitManagement)
  - [WebDashboard](https://github.com/pedro-dalben/BigBangEssentials/wiki/WebDashboard)
  - [PermissionSystem](https://github.com/pedro-dalben/BigBangEssentials/wiki/PermissionSystem)
  - [ItemManagement](https://github.com/pedro-dalben/BigBangEssentials/wiki/ItemManagement)
  - [UtilitySystems](https://github.com/pedro-dalben/BigBangEssentials/wiki/UtilitySystems)
  - [APISystem](https://github.com/pedro-dalben/BigBangEssentials/wiki/APISystem)
  - [AFKSystem](https://github.com/pedro-dalben/BigBangEssentials/wiki/AFKSystem)

See [APISystem](https://github.com/pedro-dalben/BigBangEssentials/wiki/APISystem) for API & Placeholder System details, including:
  - PlaceholderAPI integration for dynamic text
  - Custom and expansion placeholders
  - Web Dashboard REST API endpoints for server status, player info, logs, config, events, and statistics
  - Permissions and config options for API features

## 🚀 Quick Start

### Installation
1. Download the latest release [![Version](https://img.shields.io/github/v/release/pedro-dalben/BigBangEssentials?label=Version)](https://github.com/pedro-dalben/BigBangEssentials/releases)
2. Place the JAR file in your server's `mods` folder
3. Start your server to generate configuration files in `config/bigbangessentials/`
4. Configure permissions and features as needed
5. Restart the server to apply changes

### Essential Configuration Files
```
config/bigbangessentials/
├── config.json           # Main configuration settings
├── permissions.json      # Permission system setup
├── language/            # Language files directory
├── shops.json           # Shop system configuration
└── settings.json        # Additional mod settings
```

### Quick Permission Setup
For LuckPerms users:
```
/lp group admin permission set bigbangessentials.admin true
/lp group moderator permission set bigbangessentials.moderator true
/lp group default permission set bigbangessentials.player true
```

## 🎮 Command Reference

See [Home](https://github.com/pedro-dalben/BigBangEssentials/wiki/Home) and individual system docs above for full command lists and config options.

## 🔧 Configuration Examples

All features are managed by modular JSON config files. See [Home](https://github.com/pedro-dalben/BigBangEssentials/wiki/Home) and system docs above for details.

## 🔗 API Integration for Modders

See [APISystem](https://github.com/pedro-dalben/BigBangEssentials/wiki/APISystem) for full API and PlaceholderAPI documentation, including:
- Registering custom placeholders
- Using REST API endpoints for server data
- Economy API for mod integration

## 🔗 Integration & Compatibility

- **LuckPerms** and **FTB Ranks** supported
- **Server-Side Only** (no client mods required)
- **Vanilla Client Support**
- **Performance Optimized**

## 🛠️ Development

### IDE Setup
This project uses **IntelliJ IDEA Community Edition** with Gradle.

**Setup Instructions:**
1. Clone the repository
2. Open the project in IntelliJ IDEA
3. Import as a Gradle project
4. Wait for Gradle to sync and download dependencies
5. The IDE is configured to automatically download sources and javadocs

**Running the Mod:**
- Use Gradle run configurations provided by NeoForge ModDev
- `runClient` - Test in client mode
- `runServer` - Test in server mode
- `runData` - Generate data resources

**Building:**
```bash
./gradlew build
```
Output JAR: `build/libs/bigbangessentials-VERSION+build.XXX.jar`

### Project Structure
- **Java 21** - Target JDK version
- **Gradle** - Build system with auto-incrementing build numbers
- **NeoForge ModDev 2.0.107** - Modern mod development plugin
- **Parchment Mappings** - Better parameter names and javadocs

## 🤝 Support & Community

- **Discord**: [Join our Discord server](https://discord.gg/dUGAQF2Mga) for support and community discussion
- **Bug Reports**: Report issues and bugs through GitHub or Discord
- **Feature Requests**: Suggest new features and improvements
- **Documentation**: See [Home](https://github.com/pedro-dalben/BigBangEssentials/wiki/Home) and system docs above

## 👥 Credits & Acknowledgments

This project is a custom recreation of the original **[NeoEssentials](https://github.com/pedro-dalben/BigBangEssentials)** mod. 
- **Original Creator:** [MrWhiteFlamesYT](https://github.com/MrWhiteFlamesYT) / [pedrodalben](https://github.com/pedrodalben)
- **Ko-fi Support:** If you want to support the original creator's hard work, visit their [Ko-fi](https://ko-fi.com/mrwhiteflamesyt).

We are deeply grateful to MrWhiteFlamesYT for the original features, conceptual design, and documentation which made this project possible.

## 📄 License

BigBangEssentials is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

**🌟 Ready to enhance your server? Download BigBangEssentials and give your players the essential tools they need!**

*Made with ❤️ for the Minecraft community*
