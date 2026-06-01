# Chat System

> **Version:** 1.0.2.6 · **Config:** `config.json` → `chat` section

---

## Overview

Full-featured chat system with format templates, rich text (gradients/rainbow), channel routing, Discord relay, mute/ignore, social spy, and per-player time/weather. All chat is logged to the server console.

Channel routing is local by default. Normal chat stays local, `/g` sends a one-off global message tagged `[g]`, and the local no-nearby prompt comes from `chat.channels.local.noPlayersMessage`.

---

## Config (`config.json` → `chat`)

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | Enable BigBangEssentials chat handling |
| `enable-chat-formatting` | `true` | Apply format templates to messages |
| `chat-format` | `"<{prefix}{name}{suffix}> {message}"` | Default format. Supports placeholders |
| `logChatToConsole` | `true` | Print formatted messages to server console |
| `localChatRadius` | `0` | Block radius for local chat (0 = global) |
| `joinMessage` | `"§e{player} joined the server"` | Join broadcast (blank = disabled) |
| `quitMessage` | `"§e{player} left the server"` | Quit broadcast |
| `richText.enabled` | `true` | Enable gradient/rainbow MiniMessage tags |
| `richText.allowedRoles` | `[]` | Groups allowed to use rich text (empty = all) |

### Chat Format Placeholders

| Placeholder | Value |
|---|---|
| `{prefix}` | Player's permission group prefix |
| `{suffix}` | Player's permission group suffix |
| `{name}` | Player's real username |
| `{displayname}` | Player's nickname or real name |
| `{message}` | The chat message content |
| `{world}` | Current world/dimension name |

---

## Commands

### Private Messaging

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/msg` | `/msg <player> <message>` | `bigbangessentials.chat.msg` | Send a private message |
| `/tell`, `/whisper`, `/w`, `/m` | aliases | same | Aliases |
| `/reply` | `/reply <message>` | `bigbangessentials.chat.reply` | Reply to last private message |
| `/r` | alias | same | Alias |
| `/msgtoggle` | `/msgtoggle [on\|off]` | `bigbangessentials.msgtoggle` | Toggle receiving private messages |
| `/rtoggle` | `/rtoggle [on\|off]` | `bigbangessentials.rtoggle` | Toggle receiving replies |
| `/socialspy` | `/socialspy [on\|off]` | `bigbangessentials.chat.socialspy` | Spy on all private messages |

### Ignore System

| Command | Syntax | Permission | Description |
|---|---|---|---|
| `/ignore` | `/ignore <player>` | `bigbangessentials.chat.ignore` | Ignore a player's messages |
| `/unignore` | `/unignore <player>` | `bigbangessentials.chat.ignore` | Unignore a player |
| `/ignorelist` | `/ignorelist` | `bigbangessentials.chat.ignore` | List ignored players |

---

## Rich Text

When `richText.enabled` is `true`, players (or players in `allowedRoles`) can use MiniMessage tags in chat:

| Tag | Effect |
|---|---|
| `<gradient:#ff0000:#0000ff>text</gradient>` | Red-to-blue gradient |
| `<rainbow>text</rainbow>` | Rainbow cycling colours |
| `<bold>text</bold>` | Bold |
| `<italic>text</italic>` | Italic |
| `<color:#hexcode>text</color>` | Hex colour |
| `<aqua>text</aqua>` | Named colour |

---

## Discord Integration (Simple Discord Link)

When **Simple Discord Link** is installed, BigBangEssentials automatically:
- Relays Minecraft chat → Discord channel (configurable `channelId`)
- Relays Discord messages → Minecraft chat
- Formats messages using the configured Discord chat format

Config (`config.json` → `discord` section per channel):

| Key | Description |
|---|---|
| `channelId` | Discord channel ID to relay to/from |
| `relayToDiscord` | Send MC chat to Discord |
| `relayFromDiscord` | Send Discord messages to MC |
| `format` | Message format for Discord → MC relay |

Works standalone (no relay) if Simple Discord Link is not installed.

---

## Data Files

| File | Contents |
|---|---|
| `bigbangessentials/ignore_data.json` | Per-player ignore lists |
| `bigbangessentials/muted_players.json` | Active mutes |

---

*Back to [Wiki Home](Home)*
