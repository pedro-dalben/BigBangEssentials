# NPC Module (npcs)

Virtual NPC objects with player appearance — skins, holograms, player tracking, and click commands.

## Installation

1. Ensure `modules.json` contains:
```json
{ "npcsEnabled": true }
```

2. Restart or `/bigbangessentials reload`

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/npc create <id> <skin>` | `bigbangessentials.npcs.create` | Create NPC at your position |
| `/npc remove <id>` | `bigbangessentials.npcs.remove` | Delete an NPC |
| `/npc movehere <id>` | `bigbangessentials.npcs.move` | Move NPC to your position |
| `/npc skin <id> <name>` | `bigbangessentials.npcs.edit` | Change NPC skin |
| `/npc name <id> <text>` | `bigbangessentials.npcs.edit` | Set display name |
| `/npc command <id> <cmd>` | `bigbangessentials.npcs.edit` | Set click command (player) |
| `/npc consolecommand <id> <cmd>` | `bigbangessentials.npcs.consolecommand` | Set click command (console) |
| `/npc hologram <id> on` | `bigbangessentials.npcs.edit` | Enable hologram |
| `/npc hologram <id> off` | `bigbangessentials.npcs.edit` | Disable hologram |
| `/npc hologram <id> addline <text>` | `bigbangessentials.npcs.edit` | Add hologram line |
| `/npc hologram <id> setline <n> <text>` | `bigbangessentials.npcs.edit` | Set hologram line |
| `/npc hologram <id> removeline <n>` | `bigbangessentials.npcs.edit` | Remove hologram line |
| `/npc look <id> on` | `bigbangessentials.npcs.edit` | Enable look-at-player |
| `/npc look <id> off` | `bigbangessentials.npcs.edit` | Disable look-at-player |
| `/npc enable <id>` | `bigbangessentials.npcs.edit` | Enable NPC |
| `/npc disable <id>` | `bigbangessentials.npcs.edit` | Disable NPC |
| `/npc teleport <id>` | `bigbangessentials.npcs.admin` | Teleport to NPC |
| `/npc info <id>` | `bigbangessentials.npcs.admin` | Show NPC details |
| `/npc list` | `bigbangessentials.npcs.admin` | List all NPCs |
| `/npc reload` | `bigbangessentials.npcs.reload` | Reload from config |
| `/npc save` | `bigbangessentials.npcs.save` | Force save to disk |
| `/npc stats` | `bigbangessentials.npcs.stats` | Show metrics |
| `/npcs` | (same) | Alias for `/npc` |

## Permissions

```
bigbangessentials.npcs.admin - All NPC permissions
bigbangessentials.npcs.create - Create NPCs
bigbangessentials.npcs.remove - Remove NPCs
bigbangessentials.npcs.edit - Edit NPC properties
bigbangessentials.npcs.move - Move NPCs
bigbangessentials.npcs.reload - Reload configuration
bigbangessentials.npcs.save - Force save
bigbangessentials.npcs.stats - View metrics
bigbangessentials.npcs.consolecommand - Set console commands
bigbangessentials.npcs.use - Interact with NPCs
bigbangessentials.npcs.use.<npc-id> - Interact with specific NPC
```

## Configuration

File: `world/serverconfig/bigbangessentials/npcs/npcs.json`

```json
{
  "schemaVersion": 1,
  "defaults": {
    "viewDistance": 48.0,
    "despawnDistance": 56.0,
    "interactionDistance": 4.5,
    "interactionCooldownMillis": 750
  },
  "performance": {
    "visibilityScanIntervalTicks": 10,
    "maxViewerSyncsPerTick": 50,
    "maxSpawnsPerTick": 20,
    "maxDespawnsPerTick": 50,
    "maxLookUpdatesPerTick": 200
  },
  "skinCache": {
    "freshTtlHours": 24,
    "staleTtlDays": 30,
    "negativeCacheMinutes": 10,
    "maxConcurrentRequests": 2,
    "connectTimeoutMillis": 3000,
    "requestTimeoutMillis": 5000
  },
  "npcs": {
    "warp_end": {
      "enabled": true,
      "displayName": "<light_purple><bold>Explorar o End</bold>",
      "location": {
        "dimension": "minecraft:overworld",
        "x": 125.5,
        "y": 72.0,
        "z": -34.5,
        "yaw": 180.0,
        "pitch": 0.0
      },
      "skin": {
        "playerName": "Dalbesmr"
      },
      "action": {
        "type": "PLAYER_COMMAND",
        "command": "warp end"
      },
      "hologram": {
        "enabled": true,
        "lines": [
          "<light_purple><bold>Explorar o End</bold>",
          "<gray>Clique para viajar"
        ],
        "offsetY": 2.25,
        "viewDistance": 32.0
      },
      "lookAtPlayers": {
        "enabled": true,
        "range": 10.0,
        "updateIntervalTicks": 4,
        "minimumAngleChange": 2.0,
        "maxYawFromBase": 100.0,
        "maxPitchUp": 45.0,
        "maxPitchDown": 35.0,
        "rotateBody": true,
        "resetWhenOutOfRange": true
      },
      "interaction": {
        "distance": 4.5,
        "cooldownMillis": 750,
        "permission": ""
      }
    }
  }
}
```

## Action Types

| Type | Behavior |
|------|----------|
| `PLAYER_COMMAND` | Executes command as the clicking player |
| `CONSOLE_COMMAND` | Executes command as console (`{player}` placeholder supported) |
| `NONE` | No action on click |

## Skin Cache

- **Persistent**: `bigbangessentials/npcs/skin-cache.json`
- **In-memory**: ConcurrentHashMap with dedup of in-flight requests
- **Fresh TTL**: 24 hours — use cached skin immediately
- **Stale TTL**: 30 days — use cached skin while refreshing in background
- **Negative cache**: 10 minutes — remember that a name doesn't exist
- **Mojang offline**: Use last valid cached skin; fallback to Steve

## Architecture

NPCs are **virtual entities** sent individually to each viewer via packets:
- Never persisted in chunk NBT
- Never loaded as real `ServerPlayer`
- Never processed by AI/pathfinding
- Each viewer sees independent rotation (look-at-player)

Reuses:
- ModuleManager lifecycle
- BigBangHolograms API for holograms
- PermissionAPI for access control
- ConfigManager for module toggle
- Atomic JSON persistence pattern
- ChunkSpatialIndex pattern

## Troubleshooting

| Issue | Diagnostic |
|-------|-----------|
| NPC not visible | `/npc info <id>` — check enabled, dimension, location |
| Skin not loading | `/npc stats` — check skin request failures |
| Hologram missing | Check holograms module is enabled |
| Permission denied | Check `interaction.permission` on NPC config |
| NPC not spawning after restart | Check `npcs.json` for validity |
