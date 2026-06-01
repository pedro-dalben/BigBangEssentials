# Chat Channels

> **Version:** 1.0.2.6 · **Config:** `config.json` → `chat.channels` section

---

## Overview

Chat channels allow messages to be scoped to a specific audience — local proximity, a permission group, global, or staff only. Each channel is configured independently with its own command, aliases, radius, format, and Discord relay settings.

### How Players Speak in Channels

Players can send messages through specific channels in three ways:
1. **Default Channel:** Any message typed normally in chat goes to the local channel by default.
2. **Commands:** 
   - Type `/command <message>` (e.g., `/g Hello world!`) to send a one-off message to that channel.
   - `/g` does not switch your active channel; it sends a single global message tagged with `[g]`.
3. **Prefix Shortcuts:** If a channel has a `prefix` configured (e.g., `!` for global, `@` for staff), typing that prefix before a chat message will route it directly to that channel (e.g., typing `!hello` sends `hello` to the global channel).

---

## Built-in Channel Types

| Type | Description |
|---|---|
| `global` | All players on the server |
| `local` | Players within a configurable block radius |
| `staff` | Players with a specific permission node |
| `permission` | Players who hold a specified permission |

---

## Config (`config.json` → `chat.channels`)

Each channel entry supports:

| Key | Description |
|---|---|
| `enabled` | Enable/disable this channel |
| `command` | Primary command to switch to or speak in this channel |
| `aliases` | List of aliases for the command |
| `type` | Channel type: `global`, `local`, `staff`, `permission` |
| `radius` | Block radius for `local` type (ignored for others) |
| `permission` | Required permission to use this channel |
| `prefix` | Shortcut prefix to route messages directly from standard chat |
| `tag` | Visible tag prepended to the formatted chat line (`[g]` for global by default) |
| `format` | Message format (supports `{player}`, `{prefix}`, `{message}`) |
| `default` | If `true`, all normal chat goes through this channel |
| `noPlayersMessage` | Message shown when local chat has nobody nearby to hear it |
| `discord.enabled` | Relay this channel to Discord |
| `discord.channelId` | Discord channel ID to relay to |
| `discord.relayFromDiscord` | Pull Discord messages into this channel |

---

## Example Config

```json
"channels": {
  "enabled": true,
  "local": {
    "enabled": true,
    "radius": 100,
    "command": "l",
    "aliases": ["local", "lc"],
    "prefix": "",
    "tag": "",
    "default": true,
    "noPlayersMessage": "§7Nobody nearby heard your message. Use §a/g §7to speak globally.",
    "discord": {
      "enabled": false,
      "channelId": ""
    }
  },
  "global": {
    "enabled": true,
    "command": "g",
    "aliases": ["global", "gc"],
    "prefix": "!",
    "tag": "§7[§ag§7]",
    "default": false,
    "discord": {
      "enabled": true,
      "channelId": ""
    }
  },
  "staff": {
    "enabled": true,
    "command": "staff",
    "aliases": ["mod", "admin", "s"],
    "prefix": "@",
    "permission": "bigbangessentials.chat.staff",
    "default": false,
    "discord": {
      "enabled": true,
      "channelId": ""
    }
  }
}
```

---

## Permissions

| Node | Description |
|---|---|
| `bigbangessentials.chat.channel.<name>` | Access a specific channel |
| `bigbangessentials.chat.staff` | Access the staff channel |
| `bigbangessentials.chat.bypass` | Bypass channel restrictions |

---

*Back to [Wiki Home](Home)*
