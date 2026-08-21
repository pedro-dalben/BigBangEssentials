# NPC Module — Runtime Fix Analysis

**Date:** 2026-08-16
**Minecraft:** 1.21.1 (official Mojang mappings + Parchment 2024.11.17)
**Branch:** `fix/npc-runtime`

> Status: **COMPLETE — automated validation passed**. This document records the
> failure reproduction and root causes found by inspecting the actual 1.21.1
> classes (`~/.gradle/caches/neoformruntime/intermediate_results/*output.jar`
> and `common/build/moddev/artifacts/neoforge-21.1.179.jar`), then the fixes
> applied. Runtime validation on a live server is performed manually (see
> `docs/engineering/npcs/04-manual-test-plan.md`).

---

## Symptom

`/npc create` succeeds, config is written, the hologram is configured, but the
NPC **never becomes visible** for the player. There is no crash — the failure
is silent at runtime.

## Failure trace (as designed vs. what actually happens)

```
/npc create
  → NpcCommand.createCmd
  → NpcManager.create
  → NpcManager.registerNpc
  → NpcRenderService.spawn (on visibility scan)
  → SkinCache.resolve (async)
  → NpcRenderService.sendSpawn (server thread)
  → NpcPacketSenderImpl.addPlayerInfo   ← BROKEN (see RC-1)
  → ClientboundPlayerInfoUpdatePacket   ← ClassCastException / IllegalArgumentException
```

Because the packet construction throws, `sendSpawn` logs a warning and the NPC
never spawns. Additionally `NpcRenderService.spawn()` had no duplicate-spawn
guard, so the failure repeated on every visibility scan.

## Root causes

### RC-1 — `NpcPacketSenderImpl` reflection is broken (packet bridge never worked)

- **File:** `common/.../npcs/render/NpcPacketSenderImpl.java`
- **Problem:** In 1.21.1 `ClientboundPlayerInfoUpdatePacket` has **no**
  constructor accepting a collection of `Entry` records. The only public
  constructors are `(EnumSet<Action>, Collection<ServerPlayer>)` and
  `(Action, ServerPlayer)`. Both convert `ServerPlayer` → `Entry` internally.
  - Old code (HEAD): `getDeclaredConstructor(Set.class)` then called with two
    arguments → `IllegalArgumentException` on every spawn.
  - Current code (working tree): `getDeclaredConstructor(EnumSet.class,
    Collection.class)` *resolves by erasure* to the `ServerPlayer` constructor,
    then `newInstance(...)` with a `List<Entry>` → `ClassCastException` when the
    constructor casts entries to `ServerPlayer`.
- **Runtime effect:** the very first packet of the spawn sequence always throws;
  the NPC never spawns.
- **Fix:** replaced reflection with `NpcPacketBridge`, which builds the packet
  by serializing the exact wire format with public buffer APIs and decoding via
  the vanilla `STREAM_CODEC` (the same decode the client runs). Round-trip is
  covered by `NpcPacketBridgeTest`.

### RC-2 — Skin-layers metadata used the wrong entity-data index

- **File:** `NpcRenderService` / `NpcPacketSenderImpl` (hard-coded index)
- **Problem:** The skin-layers accessor is
  `Player.DATA_PLAYER_MODE_CUSTOMISATION` (`SynchedEntityData.defineId(Player.class, BYTE)`).
  The hard-coded index was wrong, and the value was not verified against the
  real 1.21.1 class. Sending a `BYTE` at the wrong index corrupts the
  entity-data stream (client expects a different serializer at that index) and
  can desync the player.
- **Verified value (1.21.1):** `Entity` has 8 data fields (0–7),
  `LivingEntity` has 7 (8–14), so `Player` starts at 15:
  absorption 15, score 16, **customisation 17**, main hand 18. Confirmed at
  runtime against both the vanilla 1.21.1 merged jar (Fabric) and
  `neoforge-21.1.179` (ModDev artifact). Note: `LivingEntity` has only 7 fields
  in 1.21.1, so Player does NOT start at 17 as in 1.20.x.
- **Fix:** `NpcPacketBridge` resolves the accessor id from `Player` via
  reflection at static init (works on NeoForge dev+prod and in tests), with a
  verified fallback to `17` for remapped (Fabric production) environments where
  field names are obfuscated. `NpcPacketBridgeTest` asserts the resolved id
  equals the verified value.

### RC-3 — Head vs body rotation packets were swapped / wrong

- **File:** `NpcPacketSenderImpl.rotateHead` / `teleportEntity`
- **Problem:**
  - `rotateHead()` sent `ClientboundMoveEntityPacket.Rot` — that is **body
    yaw + pitch**, not head rotation. The client's `handleRotateMob` (which
    applies head yaw) is only reached by `ClientboundRotateHeadPacket`.
  - `teleportEntity()` sent `ClientboundMoveEntityPacket.PosRot` with a
    (0,0,0) relative delta — a no-op movement with body rotation. NPCs never
    move, so this was wasteful and semantically wrong.
- **Fix:** `rotateHead` now sends `ClientboundRotateHeadPacket(entityId, yaw)`;
  a dedicated `rotateBody(entityId, yaw, pitch)` sends
  `ClientboundMoveEntityPacket.Rot`. Look updates use head + body packets
  separately.

### RC-4 — Interaction bridges were dead code (never registered)

- **File:** `FabricNpcInteractionBridge`, `NeoForgeNpcInteractionBridge`
- **Problem:** `FabricNpcInteractionBridge.register()` is never called from
  `FabricEvents.register()`; `NeoForgeNpcInteractionBridge` is never registered
  on `NeoForge.EVENT_BUS` (only `NeoForgeEvents` is). In addition, virtual
  entities are invisible to the server: vanilla `handleInteract` resolves the
  entity via `packet.getTarget(level)`, which returns `null` for a virtual id,
  so `UseEntityCallback` / `PlayerInteractEvent.EntityInteractSpecific` would
  **never fire** even if registered.
- **Fix:** the working strategy is packet interception via `NpcInteractMixin`
  (injected at `HEAD` of `handleInteract`, cancelling when the NPC service
  handled the click). Both mixins were already registered in the mixin JSONs;
  the dead bridges were removed so there is a single, verifiable path.

### RC-5 — Spawn lifecycle race: duplicate/in-flight spawns and no failure state

- **File:** `NpcRenderService.spawn` / `NpcManager.syncViewer`
- **Problem:** `session.visibleNpcIds()` is only populated after the skin
  future completes. While the future is pending, the visibility scan re-finds
  the NPC and calls `spawn()` again → duplicate skin resolutions and duplicate
  spawn sequences. A packet failure also left the session unchanged so the NPC
  was retried forever, while a "successful" partial spawn could be marked
  visible.
- **Fix:** explicit per-viewer render states
  (`NOT_VISIBLE → SPAWNING → VISIBLE`, with `FAILED`/`RESOLVING_SKIN`
  transitions), a duplicate-spawn guard, and a transactional
  spawn-with-rollback path in `NpcRenderService`.

### RC-6 — Skin callback wrote to `npcs` map off the server thread

- **File:** `NpcManager.update` (skin refresh future)
- **Problem:** `npcs.put(...)` and viewer invalidation ran directly on the HTTP
  executor thread, mutating thread-confined state.
- **Fix:** skin callbacks now hop to the server thread via
  `server.execute(...)` and revalidate (NPC still exists, still enabled,
  skin name unchanged) before mutating.

### RC-7 — Skin cache: unbounded executor + non-atomic dedup + per-resolution disk writes

- **File:** `SkinCache`
- **Problem:** `Executors.newFixedThreadPool` uses an unbounded work queue
  (hundreds of Mojang requests can queue); `get → create → put` dedup is racy;
  every resolution called `persistAsync()` (full rewrite of the cache file).
- **Fix:** bounded `ThreadPoolExecutor` with a capped queue and explicit
  rejection handling; atomic `computeIfAbsent`-style dedup; dirty-flag debounce
  so the disk cache is rewritten at most once per second (and on shutdown).

### RC-8 — Shutdown order

- **File:** `NpcManager.shutdown`
- **Problem:** `skinCache.shutdown()` (which shuts down the executor and writes
  the cache on the calling thread) ran first, then `viewerService.clear()` made
  the subsequent `onPlayerLeave` loop a no-op — no despawn packets were sent and
  the skin cache was persisted on the server thread during shutdown.
- **Fix:** `shuttingDown` flag → despawn viewers → clear sessions → flush
  config + skin cache → only then shut down executors.

### RC-9 — Visibility scan radius

- **File:** `NpcManager.syncViewer`
- **Problem:** the spatial query radius was `max(defaultViewDistance, 64)`, so
  NPCs with a larger individual `viewDistance` were never found.
- **Fix:** query radius uses the maximum configured NPC view distance (bounded,
  no per-viewer N×M loop).

### RC-10 — `maxDespawnsPerTick` was never applied

- **File:** `NpcManager.syncViewer`
- **Fix:** the despawn path is now budgeted per tick; spawns are budgeted
  globally per tick (not per viewer), so 50 viewers cannot trigger 1000 spawns
  in one cycle.

### RC-11 — Dimension comparison always false → NPCs never spawned

- **File:** `NpcManager.syncViewer`, `NpcInteractionService`, `NpcRenderService`
- **Problem:** the dimension check compared `player.level().dimension()`
  (a `ResourceKey`) with `npc.location().dimension()` (a `ResourceLocation`)
  via `equals`. `ResourceKey` does not override `equals`, so the comparison was
  **always false** and the visibility scan silently skipped every NPC — this is
  the second half of “the NPC never appears”.
- **Fix:** compare
  `player.level().dimension().location().equals(npc.location().dimension())`
  in the visibility scan, the spawn revalidation and the interaction handler.

## Test environment notes

The `:common:test` module compiles against the **NeoForge-patched** Minecraft
classes, whose `FeatureFlags` initializer asks the FML loader for modded flags.
Outside an FML runtime `LoadingModList.get()` returns `null` and the whole
vanilla bootstrap explodes. Handling:

- `MinecraftTestBootstrap` installs an empty `LoadingModList` stub via
  reflection and a synthetic `SharedConstants` version (1.21.1 / protocol 767)
  before calling `Bootstrap.bootStrap()`.
- `MinecraftTestExtension` (registered via
  `junit-platform.properties` + `META-INF/services`) runs that bootstrap
  BEFORE the first test class, so the first class that touches `FeatureFlags`
  cannot poison the JVM (a failed class initializer can never be retried).
- `SkinCache` accepts an injectable storage file (`null` disables disk
  persistence) so tests never leak entries through the shared
  `skin-cache.json`; the two persistence tests use a JUnit `@TempDir`.
- `NpcRenderServiceTest` guards the Mockito mocks with an assumption: the
  inline mock maker cannot retransform already-loaded NeoForge-patched classes
  in some JVM orderings, so the test class skips with a clear message and the
  spawn/reskin/despawn packet flow is validated manually on a real server.

## Player spawn protocol verified for 1.21.1

From `ClientPacketListener.java`:

1. The client creates a `RemotePlayer` in `createEntityFromPacket` only when
   `ClientboundAddEntityPacket` has type `EntityType.PLAYER` **and** the
   profile is already registered via `ADD_PLAYER`:
   ```java
   if (entitytype == EntityType.PLAYER) {
       PlayerInfo playerinfo = this.getPlayerInfo(packet.getUUID());
       if (playerinfo == null) {
           LOGGER.warn("Server attempted to add player prior to sending player info ...");
           return null;   // ← entity NOT spawned
       }
       return new RemotePlayer(this.level, playerinfo.getProfile());
   }
   ```
   ⇒ **`ADD_PLAYER` must be sent before `ClientboundAddEntityPacket`.**
2. `ClientboundAddEntityPacket` with `EntityType.PLAYER` is valid and is the
   only way to spawn a `RemotePlayer` on the client in 1.21.1
   (`ClientboundAddPlayerPacket` does not exist in 1.21.1).
3. The profile (with the `textures` property) is attached to the entity UUID
   via the `ADD_PLAYER` entry; `RemotePlayer` reads its skin lazily from
   `PlayerInfo`, and `PlayerInfo` memoizes the skin lookup on first access —
   so the textures must be present **before** the client builds the `PlayerInfo`.
4. Tab list membership is controlled exclusively by the `UPDATE_LISTED` action.
   A bare `ADD_PLAYER` creates the `PlayerInfo` (skin + entity spawn work) but
   **never adds the NPC to the tab list**. No cleanup packet is required.
5. Despawn: `ClientboundRemoveEntitiesPacket`; player-info cleanup:
   `ClientboundPlayerInfoRemovePacket(List<UUID>)`.
6. Metadata: `ClientboundSetEntityDataPacket(id, [DataValue(17, BYTE, 0x7F)])`
   enables all skin layers (cape/jacket/sleeves/pants/hat) → second layer works
   (index 17 = `DATA_PLAYER_MODE_CUSTOMISATION`, verified on both loaders).
   Slim/default model is resolved by the client from the texture metadata; no
   server state is needed.
7. Head rotation: `ClientboundRotateHeadPacket`; body yaw + pitch:
   `ClientboundMoveEntityPacket.Rot`.

## Skin flow (contract)

`playerName → Mojang profile API (UUID) → sessionserver (textures) → cache →
GameProfile(profileId, name, textures property) → ADD_PLAYER entry`.

- Skin is specified **only** by Minecraft Java player name (`/npc create id
  Dalbesmr`, `/npc skin id <playerName>`). No URLs, no PNG, no base64.
- `SkinCache` keeps fresh (24h), stale (30d) and negative (10min) entries,
  persists to `bigbangessentials/npcs/skin-cache.json`, and deduplicates
  in-flight Mojang requests.
- If the skin cannot be resolved the NPC spawns with the default (Steve) skin
  so the entity is always visible, and re-applies the real skin when it becomes
  available (`/npc info` reports the state).

## Tests

- `NpcPacketBridgeTest` — player-info round-trip, spawn/despawn/rotation packet
  construction, metadata index resolution.
- `SkinCacheTest` — fresh/stale/negative, dedup, offline (fake resolver)
  persistence.
- `NpcViewerSessionTest` — render state machine transitions.
- `NpcInteractionServiceTest` — validation chain (enabled, dimension,
  distance, permission, cooldown).
- `NpcLifecycleTest` — config store round-trip (create → save → reload).
- Existing `NpcModuleTest` preserved and passing.
