# BigBangHolograms — Final Architecture

## Overview

```
BigBangHolograms (static entry)
├── HologramRegistry      — owns hologram definitions, CRUD
├── HologramIndex         — spatial index (per-dimension, per-chunk)
├── ViewerService         — viewer sessions, per-player state
├── RenderService         — packet building, fingerprint dedup, entity allocation
├── ContentService        — content type resolution (text, item, head, block)
├── LayoutEngine          — line positioning, origin calculation
├── AnimationEngine       — built-in animations (typewriter, scroll, rainbow, burn, wave)
├── ActionEngine          — click triggers, action execution pipeline
├── PlaceholderRegistry   — extensible placeholder resolvers with cache
├── SchedulerService      — task scheduling with per-tick budgets
├── PersistenceService    — per-file atomic storage with schema versioning
├── InteractionService    — virtual interaction entity management
├── MetricsService        — counters, timers, cache stats
├── EventBus              — public event dispatch
├── PermissionRegistry    — permission constants
├── CommandTree            — full Brigadier command suite
├── Config                 — comprehensive configuration
└── PlatformBridge         — Fabric/NeoForge renderer implementations
```

## Separation from Crates

Crates module communicates through the public API only:
- `BigBangHolograms.getApi().createOrUpdate(definition)`
- `BigBangHolograms.getApi().delete(id)`

No internal access. No exception.

## Data Flow

```
Player moves/changes dimension
  → ViewerService.invalidate(player)
    → HologramIndex.query(dimension, chunkX, chunkZ)
      → ViewerService.computeVisibility(player, candidates)
        → RenderService.show/hide/update per hologram
          → RenderService.buildFingerprint(hologram, viewer)
            → if changed: send packets
```

```
Tick
  → SchedulerService.process(server, tickCounter)
    → AnimationEngine.update(viewers, budget)
    → ContentService.update(dynamicHolograms, viewers, budget)
    → PersistenceService.flushIfNeeded()
    → MetricsService.collect()
```

## Entity ID Management

Virtual entity IDs allocated from pool starting at 1_500_000_000.
Per-hologram, per-viewer tracking.
Released on hide/logout/shutdown.
No collision with real entities (Minecraft uses IDs < INT_MAX but starts low).

## Packet Lifecycle

For each viewer-hologram pair:
1. SPAWN — `ClientboundAddEntityPacket` (TEXT_DISPLAY, ITEM_DISPLAY, etc.)
2. METADATA — `ClientboundSetEntityDataPacket` with render config
3. UPDATE — `ClientboundSetEntityDataPacket` only if fingerprint changed
4. TELEPORT — `ClientboundTeleportEntityPacket` only if moved
5. DESTROY — `ClientboundRemoveEntitiesPacket` on hide/logout
