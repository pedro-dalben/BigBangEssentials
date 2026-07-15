# Cobbleverse Integrations

## Overview

6 integration bridges connect the Jobs module to Cobblemon and its ecosystem mods. Each bridge implements `OptionalJobsIntegration` with a full health state machine and reflective event subscription.

## Integration IDs

| ID | Bridge Class | Purpose |
|----|-------------|---------|
| `cobblemon_base` | `CobblemonJobsBridge` | Pokemon capture and Dex entry events |
| `cobblemon_breeding` | `BreedingJobsBridge` | Egg creation and hatching events |
| `cobblemon_trainers` | `TrainerJobsBridge` | NPC trainer battle victories |
| `cobblemon_pasture` | `PastureJobsBridge` | Pasture task completion events |
| `cobblemon_fossils` | `FossilJobsBridge` | Fossil revival events |
| `cobblemon_raids` | `RaidDensJobsBridge` | Raid completion events |

## Status Summary

| Integration | Status | Mod Detection | Event Handler |
|-------------|--------|---------------|---------------|
| `cobblemon_base` | ACTIVE | Cobblemon detected | `PokemonCapturedEvent` via `CobblemonEvents` |
| `cobblemon_breeding` | ACTIVE | Cobblemon/Cobbreeding | `EggHatchEvent` (multi-candidate probe) |
| `cobblemon_trainers` | ACTIVE | Cobblemon/RCTMod | `BattleVictoryEvent` via `CobblemonEvents` |
| `cobblemon_pasture` | MOD_NOT_INSTALLED | N/A | None (contracts only) |
| `cobblemon_fossils` | MOD_NOT_INSTALLED | N/A | None (contracts only) |
| `cobblemon_raids` | MOD_NOT_INSTALLED | N/A | None (contracts only) |

---

## REAL Integration: cobblemon_base (Pesquisador Pokémon)

**Mod ID**: `cobblemon`
**Expected Version Range**: 1.5.x – 1.6.x (Cobblemon for 1.21.1)
**Bridge Class**: `CobblemonJobsBridge.java`

### Event Class
`com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent`

### Event Bus
`com.cobblemon.mod.common.api.events.CobblemonEvents` — static fields accessed via reflection.

### Adapter Strategy
**REFLECTIVE** — subscribes via `subscribe(Class, Consumer)` and unsubscribes via `unsubscribe(Class, Consumer)`, both called reflectively. Event bus type and subscribe/unsubscribe methods are detected at probe time.

### State Lifecycle
```
NOT_PROBED → probeApi()
  ├── MOD_NOT_INSTALLED (Cobblemon JAR not in mods folder)
  ├── MOD_INSTALLED_API_NOT_FOUND (no CobblemonEvents class found)
  └── API_FOUND (CobblemonEvents.CAPTURE event found)
      → subscribeEvents()
        ├── SUBSCRIPTION_FAILED (unsubscribe method missing or reflection error)
        └── SUBSCRIPTION_SUCCEEDED → ACTIVE
```

### Data Extraction (fail-closed)
| Data Point | Method | Fallback |
|-----------|--------|----------|
| Pokemon UUID | `getUuid()` | Reject if null (fail-closed) |
| Species name | `getSpecies().getName()` | "unknown" |
| Player | `getPlayer()` | Reject if null |
| Trade history | `getTradeHistory()` | Reject if non-empty |
| Persistent data | `getPersistentData()` | Check for `admin_spawned` flag |
| Shiny | `isShiny()` | false |
| Caught ball | `getCaughtBall()` | null |

### Anti-Exploit Rules
- Pokemon without UUID → rejected (logged in audit)
- Trade history non-empty → rejected (prevents trade farming)
- `admin_spawned` flag present → rejected
- Same species captured within 3 seconds → blocked (anti-macro)
- Dex entry already registered → only first registration triggers `DEX_ENTRY_ADDED`

### Deduplication
Action ID format: `UUID.nameUUIDFromBytes("cap_" + playerId + "_" + pokemonUuid)`
Receipt stored in `JobActionReceiptRepository.reserveAction()`.

---

## REAL Integration: cobblemon_breeding (Criador Pokémon)

**Mod ID**: `cobblemon` (or `cobbreeding`)
**Expected Version Range**: Cobblemon 1.5.x+ or Cobbreeding 1.x
**Bridge Class**: `BreedingJobsBridge.java`

### Event Candidates (multi-probe)
| Event Class | Purpose | Priority |
|-------------|---------|----------|
| `EggHatchEvent` | Pokemon hatched from egg | 1st |
| `PokemonHatchedEvent` | Alternative hatch event | 2nd |
| `EggCreatedEvent` | Egg produced via breeding | 1st |
| `PokemonEggCreatedEvent` | Alternative create event | 2nd |

### Adapter Strategy
**REFLECTIVE** with multi-candidate probe. Bridge tests each candidate class name via `Class.forName()` and subscribes to all found classes. If no event class is found, bridge enters `API_CLASS_NOT_FOUND`.

### State Lifecycle
```
NOT_PROBED → probeApi()
  ├── MOD_NOT_INSTALLED
  └── API_FOUND (at least one event class found)
      → subscribeEvents() → ACTIVE (for each found event)
        └── On handler exception: DEGRADED
```

### Deduplication
`EggLifecycleService` maintains `processedEggs` map by `eggUuid`. Each egg UUID is recorded on first encounter; subsequent events for the same egg are silently dropped.

---

## REAL Integration: cobblemon_trainers (Treinador Pokémon)

**Mod ID**: `cobblemon` (or `rctmod`)
**Expected Version Range**: Cobblemon 1.5.x+ (with `BattleVictoryEvent`)
**Bridge Class**: `TrainerJobsBridge.java`

### Event Class
`com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent`

### Event Bus
`com.cobblemon.mod.common.api.events.CobblemonEvents` — static field accessed via reflection.

### PvP Differentiation
Bridge calls `isPvP()` on the battle object. If `true`, the event is silently skipped (no reward for PvP).

### Trainer Tier Mapping
`TrainerMappingService` maps NPC trainer types to tiers:
- `GYM_LEADER` — gym leader NPCs
- `ELITE_FOUR` — Elite Four members
- `CHAMPION` — champion NPCs
- `TRAINER_COMMON` — standard NPC trainers

### Cooldowns
- Gym leaders, Elite Four, Champion: **24 hours** cooldown
- Common trainers: **1 hour** cooldown

Cooldown key is `playerUUID + trainerId + battleId` in a `ConcurrentHashMap.newKeySet()`. Not persisted across restarts.

### Deduplication
Action ID: `UUID.nameUUIDFromBytes("battle_" + playerId + "_" + trainerId + "_" + battleId)` — deterministic and repeatable.

### State Lifecycle
```
NOT_PROBED → probeApi()
  ├── MOD_NOT_INSTALLED
  └── API_FOUND → subscribeEvents() → ACTIVE
```

---

## STUB Integration: cobblemon_pasture (Cuidador de Pasto)

**Mod ID**: `cobblemon` (pasture addon)
**Status**: MOD_NOT_INSTALLED
**Bridge Class**: `PastureJobsBridge.java`

### Why STUB
No pasture mod detected in modpack. Bridge probes for known pasture mod classes, finds none, enters `MOD_NOT_INSTALLED`.

### Adapter Strategy
**NONE** — bridge does not register any event listeners. The `PastureCollectionService.processManualCollection()` accepts only `eventSource = "manual"` or `"contract_delivery"`.

### Contract-Only Mode
The `pasture_keeper` job can still earn via contracts. The `JobContractGenerator` includes `PASTURE_TASK_COMPLETED` objectives in contract templates. Manual task delivery through the contract UI is accepted.

### What Happens When Mod Is Installed
1. Bridge probes → finds pasture event classes → enters `API_FOUND`
2. Bridge subscribes to pasture events → enters `ACTIVE`
3. Real-time pasture task completion starts awarding money/XP
4. Contracts continue to work alongside real-time rewards

---

## STUB Integration: cobblemon_fossils (Paleontólogo)

**Mod ID**: `cobblemon` (fossil addon)
**Status**: MOD_NOT_INSTALLED
**Bridge Class**: `FossilJobsBridge.java`

### Why STUB
No fossil mod detected. Bridge probes for fossil event classes, finds none.

### Adapter Strategy
**NONE** — no event listeners. `FossilProcessDeduplicationService.processFossilRevived()` is available for external calls when the mod is installed.

### Contract-Only Mode
`FOSSIL_REVIVED` objectives available in contracts. Admin can manually trigger fossil rewards via admin commands.

### What Happens When Mod Is Installed
1. Bridge probes → finds fossil event classes → enters `API_FOUND`
2. Bridge subscribes → enters `ACTIVE`
3. Real-time fossil revival rewards activate

---

## STUB Integration: cobblemon_raids (Incursionista)

**Mod ID**: `cobblemon` (raid dens addon)
**Status**: MOD_NOT_INSTALLED
**Bridge Class**: `RaidDensJobsBridge.java`

### Why STUB
No raid dens mod detected. Bridge probes for raid event classes, finds none.

### Adapter Strategy
**NONE** — no event listeners. `RaidDeduplicationService.processRaidCleared()` is available for external calls.

### Contract-Only Mode
`RAID_CLEARED` objectives available in contracts.

### What Happens When Mod Is Installed
1. Bridge probes → finds raid event classes → enters `API_FOUND`
2. Bridge subscribes → enters `ACTIVE`
3. Real-time raid completion rewards activate

---

## Health State Machine

All bridges share the same state machine:

```
NOT_PROBED
    ↓ probeApi()
    ├── MOD_NOT_INSTALLED         (mod JAR not present)
    ├── MOD_INSTALLED_API_NOT_FOUND  (mod present, no event class)
    ├── API_CLASS_NOT_FOUND       (event class candidate missing)
    └── API_FOUND                 (event class found)
          ↓ subscribeEvents()
          ├── SUBSCRIPTION_FAILED
          └── SUBSCRIPTION_SUCCEEDED
                └── ACTIVE
                      ├── DEGRADED  (handler threw exception)
                      ├── ERROR     (fatal integration error)
                      └── SHUTDOWN  (server stopping or reload)
```

**Invariants**:
1. `ACTIVE` only set after confirmed subscription to real event
2. Handler exceptions update status to `DEGRADED` and record `lastError`
3. Initialization is idempotent (AtomicBoolean guard)
4. Reload executes shutdown (clear listeners) before re-probe

---

## Diagnostics

### `/jobsadmin integrations probe`

Executes safe re-probe of all 6 integrations without duplicating listeners. Output per integration:

- Current state (color-coded)
- Detected mod ID and version
- Adapter type (REFLECTIVE / NONE)
- Event class name
- Event bus identifier
- Subscription status (SUBSCRIBED / FAILED / NOT_SUBSCRIBED)
- Event counters: total received / accepted / rejected
- Last event timestamp
- Last success timestamp
- Last error message (if any)

### `/jobsadmin audit <player>`

Shows audit logs for a specific player's Pokemon-related actions — captures, battles, eggs — including rejections with reasons.

---

## Troubleshooting

### Integration Stuck on API_FOUND

**Symptom**: Integration shows `API_FOUND` but not `ACTIVE`.
**Cause**: Event class found but subscription failed (missing unsubscribe method, reflection error).
**Fix**:
1. Run `/jobsadmin integrations probe` to see subscription error
2. Check server logs for `ReflectiveOperationException` on subscribe
3. Verify Cobblemon version compatibility
4. Reload: `/jobsadmin reload`

### Events Not Received (State ACTIVE but counters = 0)

**Symptom**: Integration is `ACTIVE` but shows 0 events received.
**Causes**:
- Event class found but the actual event bus field moved in newer Cobblemon version
- Generic type mismatch on subscription (subscribed to wrong event subtype)
- Player hasn't triggered any qualifying events
**Fix**:
1. Check Cobblemon changelog for event bus API changes
2. Verify with `/jobsadmin pokemon status <player>` — shows player cooldowns/timers
3. Check `lastError` field in integration probe output

### STUB Integration: "Mod Not Installed"

**Symptom**: Profession shows as available but earns nothing from actions.
**Cause**: The required mod is not in the modpack. Integration is in `MOD_NOT_INSTALLED`.
**Workaround**: Those jobs work via contracts. Accept contracts from `/jobs contratos` to earn money/XP for those professions.
**Fix**: Install the required addon mod. After installation, run `/jobsadmin reload` — bridges will re-probe and subscribe if the event classes are found.
