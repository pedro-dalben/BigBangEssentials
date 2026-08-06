# NPC Module — Protocol and Rendering

**Minecraft 1.21.1**

## Rendering Pipeline

NPCs are rendered as **virtual player entities** — they don't exist as `ServerPlayer` or `Entity` instances on the server. Each viewer receives independent packets.

### Spawn Sequence (Per Viewer)

1. **ClientboundPlayerInfoUpdatePacket (ADD_PLAYER)** — Adds fake player info to the tab list, enabling skin download
2. **ClientboundAddEntityPacket** — Spawns entity with `EntityType.PLAYER` at NPC location
3. **ClientboundSetEntityDataPacket** — Sets skin layer metadata (byte 0x7F = all layers)
4. **ClientboundMoveEntityPacket.Rot** — Sets initial head/body rotation
5. **ClientboundPlayerInfoUpdatePacket (UPDATE_LISTED, listed=false)** — Hides from tab while keeping entity visible

### Look Update (Per Viewer, Per Tick)

- **ClientboundMoveEntityPacket.Rot** — Updates body yaw + pitch for look-at-player
- Rotation calculated using `Math.atan2(dx, dz)` for yaw, `Math.atan2(dy, distance)` for pitch
- Clamped to `maxYawFromBase`, `maxPitchUp`, `maxPitchDown` settings
- Only sent when delta exceeds `minimumAngleChange` threshold

### Despawn (Per Viewer)

- **ClientboundRemoveEntitiesPacket** — Removes entity by ID

## Packet Sender Architecture

Due to 1.21.1 `ClientboundPlayerInfoUpdatePacket` constructor restrictions (takes only `ServerPlayer` objects, not raw `Entry` objects), a reflection-based `NpcPacketSenderImpl` bridges virtual profiles into the packet system:

- Constructs `Entry` records via reflection
- Constructs the packet via its package-private constructor (`Set<Entry>`)
- Common code with no Fabric/NeoForge-specific dependencies

## Entity ID Allocation

- NPC virtual entity IDs: `2_000_000_000` onward (via `AtomicInteger`)
- Hologram virtual entity IDs: `1_500_000_000` onward (existing)
- No collision risk between modules

## Skin Flow

1. Admin sets `skin.playerName: "Dalbesmr"` in config
2. `NpcRenderService.spawn()` calls `SkinCache.resolve("Dalbesmr")` asynchronously
3. Result is posted to server thread via `server.execute()`
4. Packet sequence sent with resolved texture value and signature
5. UUID derived deterministically from NPC id: `UUID.nameUUIDFromBytes("bigbang-npc:" + npcId)`
