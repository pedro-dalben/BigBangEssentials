# NPC Module Architecture Audit

**Date:** 2026-08-06
**Minecraft:** 1.21.1 (Parchment 2024.11.17)
**NeoForge:** 21.1.179+

## 1. Existing Components to Reuse

### 1.1 ModuleManager (`core/ModuleManager.java`)
- States: REGISTERED → DISABLED/BLOCKED → STARTING → RUNNING/FAILED
- `register(id, BooleanSupplier enabled, String... dependencies)` — used in BigBangEssentials.registerModules()
- `prepare(id)` — checks enabled + dependencies, transitions to STARTING
- `started(id, millis)` — transitions to RUNNING
- `failed(id, error)` — transitions to FAILED
- `isActive(id)` / `isRunning(id)` — query current state
- NPC module: `modules.register("npcs", () -> ConfigManager.isModuleEnabled("npcs"), "holograms")` with holograms as optional soft dependency

### 1.2 ManagerRegistry (`core/ManagerRegistry.java`)
- `registerManager(name, category, class, supplier)` — tracks manager initialization
- `markInitialized(name)` / `markFailed(name, message)` — diagnostics
- NPC: register NpcManager with category "npcs"

### 1.3 ConfigManager (`config/ConfigManager.java`)
- `isModuleEnabled(module)` → checks `modules.json` or `config.json` for `{module}Enabled` key
- `getConfig(name)` → returns JsonObject from world/serverconfig/bigbangessentials/{name}.json
- NPC toggle: Config file `modules.json` entry: `"npcsEnabled": true`

### 1.4 ResourceUtil (`util/ResourceUtil.java`)
- `CONFIG_DIR = "world/serverconfig/bigbangessentials/"` — for configs
- `DATA_DIR = "bigbangessentials/"` — for runtime data
- NPC config path: `world/serverconfig/bigbangessentials/npcs/npcs.json`
- NPC data path: `bigbangessentials/npcs/skin-cache.json`

### 1.5 Holograms Module
- `BigBangHolograms.getApi()` → `HologramService` interface
- Hologram id pattern: `bigbangessentials:<owner>/<id>` → NPC holograms: `bigbangessentials:npc/<npc-id>`
- `HologramDefinition.builder(id).location(...).pages(...).persistent(true).build()` for creation
- `createOrUpdate()` for idempotent create/update
- `delete(id)` for removal
- `deleteByOwner(ownerId)` for orphan cleanup (on shutdown)
- Hologram lifecycle: `HologramDefinition.withEnabled(true/false)` to toggle

### 1.6 Permission System (`api/permissions/PermissionAPI`)
- `PermissionAPI.hasPermission(uuid, permission)` — check perms
- Register permission nodes in permissions.md

### 1.7 Platform Bridge (`util/Platform.java`)
- `Platform.getCurrentServer()` → `MinecraftServer` instance
- `Platform.init(provider)` — FabricPlatformProvider / NeoForgePlatformProvider
- `Platform.isModLoaded(modId)` — optional dependency checks

### 1.8 Message System (`util/MessageUtil`)
- `MessageUtil.sendMessage(player, key, ...)` — localized messages
- `ChatComponentUtil.parseColorCodes(text)` — MiniMessage-esque formatting (`<color><bold>`)

### 1.9 Scheduler (`scheduler/TaskScheduler.java`)
- `TaskScheduler.onServerTick(server)` — periodic task execution
- Can use server's tick for budgeted operations

### 1.10 Command Pattern
- Fabric: `CommandRegistrationCallback.EVENT.register((dispatcher, ...) -> ...)`
- NeoForge: `@SubscribeEvent RegisterCommandsEvent`
- Common: `BigBangEssentials.GameEvents.onRegisterCommands(dispatcher)`
- Brigadier commands registered in `CommandRegistry`

## 2. New Components Required

### 2.1 Domain Model (`api/`)
- `NpcDefinition` — immutable record/class with id, location, skin, action, hologram config, look settings
- `NpcLocation` — dimension, x, y, z, yaw, pitch
- `NpcSkin` — playerName (Mojang account), resolved texture value, signature, model
- `NpcAction` — type (PLAYER_COMMAND, CONSOLE_COMMAND, NONE), command string
- `NpcHologramConfig` — enabled, lines, offsetY, viewDistance, shadow, seeThrough
- `NpcLookSettings` — enabled, range, updateIntervalTicks, minimumAngleChange, rotateBody, etc.
- `NpcInteractionConfig` — distance, cooldownMillis, permission
- `NpcService` — API interface (find, list, create, update, delete, reload, save, stats)

### 2.2 Persistence (`config/`, `persistence/`)
- `NpcConfig` — top-level config with defaults, performance settings, npcs map
- `NpcConfigStore` — reads/writes npcs.json atomically (temp + backup + ATOMIC_MOVE)
- `NpcConfigValidator` — validates individual NPC definitions, normalizes IDs

### 2.3 Skin System (`skin/`)
- `SkinService` — orchestrator: check cache → resolve → cache
- `SkinCache` — in-memory ConcurrentHashMap with deduplication of inflight requests
- `SkinCacheEntry` — texture value, signature, uuid, model, fetchedAt, expiresAt
- `MojangSkinResolver` — HTTP calls to Mojang API (async, limited executor)
- Persistent skin cache: `bigbangessentials/npcs/skin-cache.json`

### 2.4 Render System (`render/`)
- `NpcRenderService` — manages spawning/despawning per viewer
- `NpcRenderSnapshot` — entityId, UUID, position, skin data, rotation, metadata
- `NpcViewerService` — viewer sessions (matching hologram ViewerService pattern)
- `NpcViewerSession` — visible NPCs, entity IDs, fingerprints, last rotation
- `NpcEntityIdAllocator` — AtomicInteger starting at 2_000_000_000 (separate range from holograms 1_500_000_000)

### 2.5 Spatial Index (`spatial/`)
- `NpcSpatialIndex` — adapted from `ChunkSpatialIndex`, queries NPCs near player chunk

### 2.6 Look System (`look/`)
- `NpcLookService` — calculates yaw/pitch for each viewer, budgeted per tick
- Uses `Math.atan2` for yaw, clamped within maxYawFromBase/maxPitch
- Sends `ClientboundRotateHeadPacket` + `ClientboundTeleportEntityPacket` for rotation

### 2.7 Interaction System (`interaction/`)
- `NpcInteractionService` — validates click, checks distance/permission/cooldown, dispatches
- `NpcActionExecutor` — executes PLAYER_COMMAND / CONSOLE_COMMAND
- Fabric bridge: `UseEntityCallback.EVENT` (already used by holograms/crates)
- NeoForge bridge: `PlayerInteractEvent.EntityInteractSpecific` (already used by holograms)

### 2.8 Hologram Integration (`hologram/`)
- `NpcHologramService` — creates/updates/removes holograms via `BigBangHolograms.getApi()`
- Orphan cleanup on shutdown: `deleteByOwner("bigbangessentials:npc")`

### 2.9 Service/Orchestrator (`service/`)
- `NpcManager` — singleton, implements `NpcService`, orchestrates all subsystems
- Lifecycle: initialize, tick, reload, shutdown

## 3. Fabric vs NeoForge Differences

| Concern | Fabric | NeoForge |
|---------|--------|----------|
| Entrypoint | `ModInitializer.onInitialize()` | `@Mod` constructor + `IEventBus` |
| Server events | `ServerLifecycleEvents` callbacks | `@SubscribeEvent` static methods |
| Tick | `ServerTickEvents.END_SERVER_TICK` | `ServerTickEvent.Post` |
| Player join | `ServerPlayConnectionEvents.JOIN` | `PlayerEvent.PlayerLoggedInEvent` |
| Player leave | `ServerPlayConnectionEvents.DISCONNECT` | `PlayerEvent.PlayerLoggedOutEvent` |
| Entity interact | `UseEntityCallback.EVENT` | `PlayerInteractEvent.EntityInteractSpecific` |
| Dimension change | `ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD` | `PlayerChangedDimensionEvent` |
| Command register | `CommandRegistrationCallback.EVENT` | `RegisterCommandsEvent` |
| Packet send | `player.connection.send(packet)` — same | `player.connection.send(packet)` — same |

**Key insight: Packet sending is identical on both platforms.** The Minecraft protocol classes (`ClientboundAddPlayerPacket`, `ClientboundPlayerInfoUpdatePacket`, etc.) are on the Mojang-mapped classpath for both loaders. This means NPC rendering code can live entirely in `common/` — no separate Fabric/NeoForge renderers needed.

Platform-specific code needed only for:
- Event registration (FabricEvents.java / NeoForgeEvents.java hooks)
- Entity interaction click detection (use existing bridge pattern)

## 4. Rendering Strategy

### 4.1 Packets Per Viewer (Spawn Sequence)
1. `ClientboundPlayerInfoUpdatePacket(ADD_PLAYER, [entry])` — add player info temporarily
2. `ClientboundAddPlayerPacket(...)` — spawn fake player entity (already existed since 1.20.5)
3. `ClientboundSetEntityDataPacket(id, metadata)` — skin layers, pose
4. Wait ~1 tick (optional, for skin load). Remove from tab:
   `ClientboundPlayerInfoUpdatePacket(UPDATE_LISTED, [entry])` with listed=false — hide from tab without removing entity

MC 1.21.1 uses `ClientboundPlayerInfoUpdatePacket` with Action enums. The `createPlayerInitializing()` static method creates ADD_PLAYER entries. After skin loads, `UPDATE_LISTED` action with `listed = false` hides from tab while keeping the entity visible.

Alternative: Use `ClientboundPlayerInfoRemovePacket` after the entity spawn — but this might also remove the rendered entity on some client versions. Better to use the UPDATE_LISTED approach.

### 4.2 Per-Viewer Rotation
- `ClientboundRotateHeadPacket(entityId, headYaw)` — head rotation
- `ClientboundTeleportEntityPacket(entityId, pos, bodyYaw, pitch, onGround)` — body + pitch + position

### 4.3 Despawn
- `ClientboundRemoveEntitiesPacket(entityId)` — standard entity removal

### 4.4 Fingerprint Strategy
- Track last sent state (position, rotation, skin signature) per viewer
- Skip packet when nothing changed
- Separate fingerprints for: position/rotation, metadata, skin

## 5. Interaction Strategy

### 5.1 Validation Chain
```
entity ID received → lookup in session.entityIdToNpc → 
check NPC exists → check NPC enabled → 
check same dimension → check distance → 
check permission → check cooldown → execute
```

### 5.2 Platform Registration
- Fabric: `UseEntityCallback.EVENT.register(...)` in FabricCrateEvents-style
- NeoForge: `PlayerInteractEvent.EntityInteractSpecific @SubscribeEvent` in NeoForgeEvents-style

### 5.3 Duplicate Prevention
- `interactionCooldownMillis` minimum 250ms
- Track `lastClickTimestamp` per (player, npc) pair in ConcurrentHashMap

## 6. Persistence Strategy

### 6.1 NPC Configuration
- Path: `world/serverconfig/bigbangessentials/npcs/npcs.json`
- Format: JSON with schemaVersion
- Atomic write: serialize to .tmp → validate → backup old → ATOMIC_MOVE / rename
- Backup files: `npcs.json.bak`, `npcs.json.tmp`
- Debounced save on command changes (500-1000ms)

### 6.2 Skin Cache
- Path: `bigbangessentials/npcs/skin-cache.json`
- Persisted on shutdown and periodically
- Each entry: normalizedName, originalName, uuid, textureValue, textureSignature, model, fetchedAt, expiresAt

### 6.3 GSON Usage
- Project already uses GSON (`GsonBuilder().setPrettyPrinting().create()`)
- Same pattern for NPC config

## 7. Skin Cache Strategy

### 7.1 In-Memory
- `ConcurrentHashMap<String, SkinCacheEntry>` keyed by normalized name
- `ConcurrentHashMap<String, CompletableFuture<SkinCacheEntry>>` for inflight dedup
- Fresh TTL: 24h, Stale TTL: 30d, Negative: 10min

### 7.2 Persistent
- JSON file loaded at startup, saved on shutdown
- Provides survival across restarts when Mojang API is down

### 7.3 Executor
- Named, limited-thread pool: `Executors.newFixedThreadPool(2, factory)`
- Named "BigBangEssentials-NpcSkin"
- Shutdown in NpcManager.shutdown()
- All HTTP on background threads; results posted back via `server.execute(() -> ...)` or `server.tell(new TickTask(...))`

## 8. Performance Impact Assessment

### 8.1 Hot Path (every tick)
- Visibility sync: round-robin, `maxViewerSyncsPerTick = 50`, step interval 10 ticks
- Look updates: budgeted `maxLookUpdatesPerTick = 200`
- Spatial index queries: O(chunkRadius²) per synced player — very cheap (index is HashMap)
- No global N×M loops

### 8.2 Cold Path (spawn/despawn)
- Spawn: `maxSpawnsPerTick = 20` (packets per tick cap)
- Despawn: `maxDespawnsPerTick = 50`
- Skin resolution: async, deduplicated, never on main thread

### 8.3 Memory
- 100 NPCs: ~100 NpcDefinition objects + ~100 HologramDefinitions
- 50 players: 50 NpcViewerSessions (each ~5KB for visible NPC tracking)
- Skin cache: ~100 entries in memory (~1KB each)
- Total: < 1MB for NPC data + sessions

### 8.4 Network
- Per NPC spawn: ~3 packets (player info + add entity + metadata)
- Per NPC despawn: 1 packet
- Per look update: 1-2 packets (head + body rotation)
- Spawn/despawn budgeted per tick to avoid spikes

## 9. Risks

### 9.1 High Risk
- **Packet version compatibility**: MC 1.21.1 packet classes may change across minor versions. Mitigation: use Mojang mappings, test on target version.
- **Skin removal from tab**: `UPDATE_LISTED` with `listed=false` behavior depends on client version. If broken, fallback to `ClientboundPlayerInfoRemovePacket` accepting possible visual artifact.
- **Entity ID collision with holograms**: Separate allocator ranges (NPCs: 2_000_000_000+, Holograms: 1_500_000_000-1_999_999_999).

### 9.2 Medium Risk
- **Mojang API rate limiting**: Two concurrent requests max. If Moajng returns 429, all skin requests fail until rate limit resets.
- **Hologram module dependency**: NPC module depends on holograms being active. If holograms fail, NPCs degrade gracefully (no holograms, NPCs still work).

### 9.3 Low Risk
- **Large skin cache file**: Thousands of entries → larger save times. Mitigation: limit cache entries, expire stale entries.
- **Entity metadata differences**: Slim vs default model metadata may differ across versions. Mitigation: test, use version-aware metadata.

## 10. Integration Points Summary

| Point | Where | How |
|-------|-------|-----|
| Module registration | BigBangEssentials.registerModules() | `modules.register("npcs", () -> ConfigManager.isModuleEnabled("npcs"), "holograms")` |
| Module startup | BigBangEssentials.GameEvents.onServerStarting() | `NpcManager.getInstance().initialize()` |
| Server tick | FabricEvents/NeoForgeEvents tick handler | `NpcManager.getInstance().tick()` |
| Player join | onPlayerLoggedIn | `NpcManager.getInstance().onPlayerJoin(player)` |
| Player leave | onPlayerLoggedOut | `NpcManager.getInstance().onPlayerLeave(player)` |
| Dimension change | FabricEvents/NeoForgeEvents | `NpcManager.getInstance().onPlayerDimensionChange(player)` |
| Entity interact | FabricEvents/NeoForgeEvents | `NpcManager.getInstance().getInteractionService().handleClick(...)` |
| Command registration | BigBangEssentials.GameEvents.onRegisterCommands() | Add NpcCommand registration |
| Server shutdown | onServerStopping | `NpcManager.getInstance().shutdown()` |
| Reload | NpcCommand /npc reload | `NpcManager.getInstance().reload()` |
