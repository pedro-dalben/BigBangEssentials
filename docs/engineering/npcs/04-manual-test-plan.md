# NPC Module — Manual Test Plan

## Setup

1. Ensure `modules.json` has `"npcsEnabled": true`
2. Ensure holograms module is enabled

## Test Scenarios

### TC-01: Create NPC
```
1. Enter server
2. /npc create warp_end Dalbesmr
3. Expected: NPC created at player position with skin Dalbesmr
```

### TC-02: Configure NPC
```
1. /npc name warp_end <light_purple><bold>Explorar o End</bold>
2. /npc command warp_end warp end
3. /npc hologram warp_end setline 1 <light_purple><bold>Explorar o End</bold>
4. /npc hologram warp_end addline <gray>Clique para viajar
5. Expected: All commands succeed
```

### TC-03: Visibility
```
1. Walk away from NPC beyond view distance (48 blocks)
2. Expected: NPC disappears
3. Walk back within view distance
4. Expected: NPC reappears
```

### TC-04: Look-at-player
```
1. Circle around the NPC
2. Expected: NPC's head follows you
```

### TC-05: Click interaction
```
1. Right-click NPC
2. Expected: /warp end executes
3. Click again immediately (within 750ms)
4. Expected: Cooldown prevents double execution
```

### TC-06: Persistence after restart
```
1. Restart server completely
2. Enter world
3. Expected: NPC still exists at same position, with same skin, hologram, command
```

### TC-07: Skin cache persistence
```
1. Disconnect internet
2. Restart server
3. Expected: NPC still shows correct skin from cache
4. /npc stats — skinStaleHits > 0
```

### TC-08: Reload
```
1. /npc reload
2. Expected: No duplication, NPCs remain
3. Expected: Reload summary message
```

### TC-09: Dimension change
```
1. Teleport to nether
2. Expected: NPCs from overworld are not visible
3. Return to overworld
4. Expected: NPCs reappear
```

### TC-10: Disconnect
```
1. Log out completely
2. Log back in
3. Expected: NPC state clean, no duplicate entities
```

### TC-11: /npc info
```
1. /npc info warp_end
2. Expected: Shows name, status, location, skin, action, hologram, look, permission
```

### TC-12: /npc stats
```
1. /npc stats
2. Expected: Shows definitions, sessions, spatial index, skin cache stats, holograms
```

### TC-13: /npc list
```
1. /npc list
2. Expected: Lists all NPCs with status indicators
```

### TC-14: Invalid NPC doesn't crash others
```
1. Manually edit npcs.json, add NPC with invalid ID
2. /npc reload
3. Expected: Invalid NPC disabled, others still functional
```

### TC-15: Corrupted config
```
1. Delete npcs.json
2. Restart server
3. Expected: Default config created automatically
```
