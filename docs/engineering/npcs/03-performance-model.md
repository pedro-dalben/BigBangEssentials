# NPC Module — Performance Model

## Design Constraints — 100 NPCs, 50 Players

| Operation | Bound | Implementation |
|-----------|-------|----------------|
| Visibility sync | maxViewerSyncsPerTick = 50 | Round-robin, step = 10 ticks |
| Spawn rate | maxSpawnsPerTick = 20 | Per-viewer, budgeted |
| Despawn rate | maxDespawnsPerTick = 50 | Per-viewer, budgeted |
| Look updates | maxLookUpdatesPerTick = 200 | Single loop over visible NPCs |
| Spatial query | O(chunkRadius²) per player | HashMap-based ChunkSpatialIndex |
| Skin resolution | 2 concurrent HTTP max | Deduplicated async executor |

## Memory Budget (100 NPCs, 50 Players)

| Component | Per Unit | Total |
|-----------|----------|-------|
| NpcDefinition (100) | ~2 KB | 200 KB |
| NpcRenderState (100) | ~100 B | 10 KB |
| NpcViewerSession (50) | ~1 KB each | 50 KB |
| SkinCacheEntry (100) | ~2 KB each | 200 KB |
| HologramDefinition (100) | ~1 KB each | 100 KB |
| **Total** | | **~560 KB** |

## Network Budget

| Operation | Packets | ~Bytes |
|-----------|---------|--------|
| Spawn (per NPC per player) | 5 | ~2 KB |
| Despawn | 1 | ~50 B |
| Look update | 1-2 | ~100 B |
| **50 players, 5 NPCs each** | | |
| Initial spawn burst | 250 × 2 KB | ~500 KB (spread over ticks) |
| Per-tick look | 50 × 100 B | ~5 KB |

## What We DON'T Do

- No N×M loop (never: `for each player: for each NPC: check distance`)
- No HTTP on server thread
- No file IO per tick
- No individual scheduled task per NPC
- No real entity ticking (no AI, no pathfinding, no chunk loading)
- No duplicate packets (fingerprint comparison before sending)
- No global skin re-resolution (per-viewer skin sharing via async dedup)
