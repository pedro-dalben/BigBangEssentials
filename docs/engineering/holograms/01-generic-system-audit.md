# BigBangHolograms — Generic System Audit

## 1. What Exists

### API Layer (`holograms/api/`)
14 files defining public contract:
- `BigBangHolograms` — static singleton entry point
- `HologramService` — core CRUD interface
- `HologramDefinition` — immutable hologram descriptor (id, owner, location, pages, visual config)
- `HologramDefinitionBuilder` — fluent builder
- `HologramHandle` — return object after create/update
- `HologramLocation` — dimension + xyz record
- `HologramPage` — one page with list of `HologramLine`
- `HologramLine` — text or pre-built Component
- `HologramUpdatePolicy` — STATIC / DYNAMIC enum
- `HologramVisibilityPolicy` — NEARBY_PLAYERS / GLOBAL / MANUAL enum
- `HologramRendererType` — CLIENT_ONLY_TEXT_DISPLAY enum
- `HologramStats` — runtime statistics record
- `HologramPlaceholderResolver` — interface for placeholder resolution
- `HologramLifecycleListener` — lifecycle hooks

### Service Layer
- `BigBangHologramsManager` — monolithic singleton (723 lines). Handles: CRUD, viewer sync, scheduled updates, spatial queries, placeholder resolution, persistence, legacy cleanup delegation.

### Renderer
- `HologramRenderer` interface — show/update/hide
- `ClientOnlyTextDisplayRenderer` — spawns virtual TEXT_DISPLAY via packets
- `NoopHologramRenderer` — fallback when renderer init fails
- `RenderSnapshot` — data record for one hologram + player
- `TextDisplayMetadata` — static bridge; delegates to platform-specific `VirtualTextDisplayMetadataFactory`
- Fabric: `FabricTextDisplayMetadataFactory` (uses @Accessor mixin)
- NeoForge: `NeoForgeTextDisplayMetadataFactory` (uses reflection on Mojang names)

### Config
- `HologramConfig` — record with default values
- `HologramConfigStore` — loads/saves `holograms.json`

### Storage
- `HologramRepository` — interface
- `JsonHologramRepository` — single-file `persistent_holograms.json` with atomic write

### Command
- `HologramCommand` — 18 subcommands: list, inspect, create, remove, move, setline, addline, removeline, setdistance, setoffset, page add/remove/setinterval, visibility, reload, stats, cleanup legacy
- `HologramPermissions` — 7 permission constants

### Visibility
- `ChunkSpatialIndex` — maps hologram IDs to chunk keys, O(1) add/remove, O(chunkRadius²) query

### Placeholder Engine
- `PlaceholderEngine` — resolves `{placeholder}` tokens, pluggable resolvers, metadata fallback

### Legacy Migration
- `LegacyCrateHologramCleaner` — removes old ArmorStand crate holograms identified by entity tags

### Crates → Hologram Bridge
- `CrateHologramManager` — creates/manages hologram definitions for crate locations

---

## 2. What Works

- Virtual TEXT_DISPLAY renderer (no persistent entities)
- Atomic persistence (tmp + rename)
- Chunk-based spatial queries (no cartesian product per tick)
- Legacy ArmorStand cleanup
- Basic placeholder resolution with player name
- Page rotation support
- Static/dynamic update policies
- Visibility policies (nearby, global, manual)
- Viewer session tracking (visibleIds, forcedShown, forcedHidden)
- Round-robin viewer sync with configurable budget
- Scheduled content updates via priority queue
- Lazy renderer init for crash resilience
- Text styling (shadow, seeThrough, background, lineWidth, opacity, billboard, scale)

---

## 3. What Is Basic or Incomplete

### Rendering
- **Only TEXT_DISPLAY**. No ItemDisplay, BlockDisplay, PlayerHead support.
- **No multi-entity holograms**. Each hologram is a single TextDisplay entity.
- **No per-line rendering**. All lines merged into one Component with `\n`.
- **No render fingerprint**. Every update re-sends the full `ClientboundSetEntityDataPacket`.

### Content
- **No line types beyond text**. No item, head, block, entity preview.
- **No per-line visual config**. Line offset, scale, facing exist only at hologram level.
- **No alignment engine**. No top/bottom origin, no line-to-line alignment.
- **No rich content**. No gradients, no pixel-width calculation, no multiline awareness.

### Pages
- **Flat page model**. Pages exist but no per-player page tracking in ViewerSession.
- **No page duration or page-specific intervals**.
- **No page-level actions, permissions, or flags**.
- **No page clone, swap, insert** operations.

### Animations
- **None**. No animation engine, no typewriter, scroll, rainbow, burn, wave.

### Actions/Interactions
- **None**. No click handling, no action engine, no interaction entities.

### Scheduler
- **Single priority queue** with basic tick-budget. No timing wheel, no round-robin across holograms, no priority for near players.

### Commands
- **Basic set only**. Missing: clone, rename, enable/disable, teleport, movehere, center, align, facing, permission, line insert/swap/clone/clear, line height/offset/scale/facing, page insert/clone/swap/switch/default, page interval/duration/rotation, actions, visibility per-player, diagnostics, validate, save, export/import, benchmark.

### Permissions
- **7 coarse permissions**. Missing granular per-command permissions.

### Config
- **Minimal**. Missing: mode, text-display/item-display toggles, defaults for lineHeight/origin/billboard, limits for maxHolograms/pages/lines/chars/actions/animations, scheduler budgets per operation, placeholder cache TTL, animation config, interaction cooldown/max-distance, persistence auto-save/debounce, diagnostics toggles.

### Persistence
- **Single monolithic file** (`persistent_holograms.json`). No per-hologram files, no schema versioning, no backup before migration, no quarantine for invalid files.

### Metrics/Diagnostics
- **Basic stats only**. Missing: per-hologram diagnostics, packet counters by type, cache hit/miss rates, memory estimates, scheduler task counts, renderer error tracking.

### Spatial Index
- **No per-dimension tracking**. `clearDimension` not implemented.
- **Only dimension-aware via passed dimension key**. Works but not feature-complete.

### Viewer Sessions
- **Minimal state**. Only visibleIds, forcedShown, forcedHidden. Missing: currentPage, lastFingerprints, lastPlaceholderValues, renderedParts, entityIds, dimension tracking.

### Platform Bridges
- **TEXT_DISPLAY metadata only**. No ItemDisplay, BlockDisplay, Interaction entity support in common.

---

## 4. What Must Be Preserved

- `BigBangHolograms.getApi()` entry point
- `HologramService` interface (may add methods, must keep signature of existing ones)
- `HologramDefinition.builder(id)` API
- `createOrUpdate(definition)` — idempotent upsert
- `delete(hologramId)` — deletes virtual entities from all viewers
- `CrateHologramManager` — uses `BigBangHolograms.getApi().createOrUpdate(def)` and `.delete(id)`
- Crate hologram IDs format: `bigbangessentials:crate/<uuid>`
- Crate hologram owner: `bigbangessentials:crate`
- Crate visual config: template, lines, offsetY, viewDistance, updateInterval, toggle
- Legacy cleanup integration
- All existing tests must continue to pass

---

## 5. What Needs to Be Replaced

- `BigBangHologramsManager` → decompose into: HologramRegistry, ViewerService, RenderService, UpdateScheduler, PersistenceService, etc.
- `HologramConfig` → expand with all new settings
- `HologramCommand` → completely rewrite with full subcommand tree
- `JsonHologramRepository` → per-file storage with schema versioning

---

## 6. What Can Be Reused

- `HologramDefinition` / `HologramDefinitionBuilder` — extend, don't replace
- `HologramPage` / `HologramLine` — add fields, don't remove
- `HologramLocation` — fine as-is
- `HologramStats` — extend with new metrics
- `HologramService` interface — add methods, keep existing
- `HologramHandle` — extend
- `ClientOnlyTextDisplayRenderer` — keep, add ItemDisplay renderer
- `TextDisplayMetadata` / `VirtualTextDisplayMetadataFactory` — keep, add ItemDisplay equivalent
- `ChunkSpatialIndex` — reuse, add clearDimension
- `LegacyCrateHologramCleaner` — keep as-is
- `CrateHologramManager` — keep as-is
- All Fabric/NeoForge platform implementations — keep, extend

---

## 7. Consumer Impact Analysis

| Consumer | API used | Risk |
|----------|----------|------|
| `CrateHologramManager` | `createOrUpdate`, `delete`, `HologramDefinition.builder()` | LOW — if these stay same |
| `CrateManager` | `removeAll`, `reconcileAll` | LOW |
| `CratePlayerListener` | `syncPlayerNow`, `onPlayerStateInvalidated`, `onPlayerDisconnect` | LOW |
| `BigBangEssentials` | `initialize`, `shutdown`, `tick` | LOW |
| `CrateService` | `synchronizeLocation`, `synchronizeCrate`, `removeByCrate` | LOW |

---

## 8. Risk Areas

### Packet Spam Risk (MODERATE now, HIGH after adding features)
- **Now**: `update` re-sends full `ClientboundSetEntityDataPacket` for every scheduled update. No change detection.
- **Fix needed**: render fingerprint + cache.

### Memory Leak Risk (MODERATE)
- **Now**: `viewerSessions` map grows with each disconnect? No — `onPlayerDisconnect` removes entries. Safe.
- **Now**: `CachedComponent` map in `ManagedHologram.viewerCache` could grow with many players. Cleared on update cycle. Acceptable.
- **Risk**: adding animation frames per viewer without cleanup.

### Global Loop Risk (LOW now)
- **Now**: Round-robin sync with configurable budget. No cartesian product.
- **Risk**: if new scheduler iterates all holograms every tick.

### Unsafe Persistence Risk (LOW)
- **Now**: Atomic write via tmp + rename. Good.
- **Risk**: per-file persistence must maintain same safety.

### Missing Commands Risk (HIGH)
- Many commands in spec not implemented. This is the largest gap.

### Missing Tests Risk (HIGH)
- Only 6 test classes exist. Crate regression tests needed before refactor.

---

## 9. Migration Strategy

1. Create crate regression tests (characterization tests)
2. Run existing tests
3. Extend domain model (HologramDefinition, HologramPage, HologramLine)
4. Decompose manager into services (keep HologramService interface unchanged on existing methods)
5. Add new features one at a time
6. Verify crates after each major step
7. Full regression test suite
