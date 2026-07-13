# BigBangHolograms — Feature Matrix

| Area | Feature | Reference (DecentHolograms) | Current State | Decision | Status |
|------|---------|------------------------------|---------------|----------|--------|
| **Core** | Hologram CRUD | Create/delete/clone/rename | Fully functional | MAINTAIN | ✓ |
| **Core** | Namespaced IDs | `dh:spawn` | Yes (`bigbangessentials:admin/spawn`) | MAINTAIN | ✓ |
| **Core** | Owner system | Admin/system owners | Yes (`bigbangessentials:admin`, `:crate`) | MAINTAIN | ✓ |
| **Core** | Persistence modes | Save to file | RUNTIME/PERSISTENT implicit | IMPLEMENT | |
| **Core** | SYSTEM_MANAGED mode | N/A | Not present | IMPLEMENT | |
| **Core** | Enable/disable | Toggle hologram visibility | Not exposed to commands | IMPLEMENT | |
| **API** | HologramService interface | DHAPI | Present, complete | MAINTAIN + EXTEND | |
| **API** | HologramDefinition builder | Builder pattern | Present | MAINTAIN + EXTEND | |
| **API** | HologramHandle | Handle pattern | Present | MAINTAIN | |
| **API** | Editor APIs | PageEditor, LineEditor | Not present | IMPLEMENT | |
| **API** | Events | HologramClickEvent etc | Not present | IMPLEMENT | |
| **Render** | Virtual TEXT_DISPLAY | Packet-based | Fully functional | MAINTAIN | ✓ |
| **Render** | Virtual ITEM_DISPLAY | Item display | Not present | IMPLEMENT | |
| **Render** | Virtual BLOCK_DISPLAY | Block display | Not present | ADAPT (if supported) | |
| **Render** | Player heads | Texture-based heads | Not present | IMPLEMENT | |
| **Render** | Render fingerprint | No redundant packets | Not present | IMPLEMENT | |
| **Render** | Platform bridge | Fabric vs NeoForge | TEXT_DISPLAY done | MAINTAIN + EXTEND | ✓ |
| **Render** | Fail-safe renderer | HEALTHY/DEGRADED/DISABLED | Present (lazy init) | MAINTAIN | ✓ |
| **Pages** | Multiple pages | Per-hologram pages | Present | MAINTAIN | ✓ |
| **Pages** | Page add/insert/remove | Operations | Basic add/remove only | IMPLEMENT | |
| **Pages** | Page clone | Clone page content | Not present | IMPLEMENT | |
| **Pages** | Page swap | Swap two pages | Not present | IMPLEMENT | |
| **Pages** | Page per viewer | Per-player current page | Not present | IMPLEMENT | |
| **Pages** | Page rotation | Auto-switch interval | Present | MAINTAIN | ✓ |
| **Pages** | Page duration | Per-page duration | Not present | IMPLEMENT | |
| **Lines** | Line add/insert/remove | Operations | Basic add/set/remove | IMPLEMENT | |
| **Lines** | Line clone | Clone line content | Not present | IMPLEMENT | |
| **Lines** | Line swap | Swap two lines | Not present | IMPLEMENT | |
| **Lines** | Line move | Move line position | Not present | IMPLEMENT | |
| **Lines** | Line height | Custom height | Not present | IMPLEMENT | |
| **Lines** | Line offsetX/Z | Per-line x/z offset | Not present | IMPLEMENT | |
| **Lines** | Line scale | Per-line scale | Not present | IMPLEMENT | |
| **Lines** | Line facing | Per-line billboard | Not present | IMPLEMENT | |
| **Lines** | Line permission | Per-line visibility | Not present | IMPLEMENT | |
| **Lines** | Line flags | Per-line feature flags | Not present | IMPLEMENT | |
| **Content** | Item content | ItemDisplay | Not present | IMPLEMENT | |
| **Content** | Head content | Player/texture head | Not present | IMPLEMENT | |
| **Content** | Block content | BlockDisplay | Not present | ADIAR (verify support) | |
| **Content** | Entity preview | Entity render | Not present | ADIAR (backlog) | |
| **Placeholders** | Pluggable resolver | Registry pattern | Present | MAINTAIN | ✓ |
| **Placeholders** | {player} placeholder | Player name | Present | MAINTAIN | ✓ |
| **Placeholders** | {online} placeholder | Online players | Not present | IMPLEMENT | |
| **Placeholders** | {max_players} | Max players | Not present | IMPLEMENT | |
| **Placeholders** | {world}/{dimension} | World name | Not present | IMPLEMENT | |
| **Placeholders** | {x}/{y}/{z} | Coordinates | Not present | IMPLEMENT | |
| **Placeholders** | {server_tps} | Server TPS | Not present | IMPLEMENT | |
| **Placeholders** | {time}/{date} | Time/date | Not present | IMPLEMENT | |
| **Placeholders** | {page}/{pages} | Page indicators | Not present | IMPLEMENT | |
| **Placeholders** | Cache (global/viewer) | TTL-based cache | Basic CachedComponent | IMPLEMENT | |
| **Animations** | Typewriter | Character-by-character | Not present | IMPLEMENT | |
| **Animations** | Scroll | Text scrolling | Not present | IMPLEMENT | |
| **Animations** | Rainbow | Color cycling | Not present | IMPLEMENT | |
| **Animations** | Burn | Color transition | Not present | IMPLEMENT | |
| **Animations** | Wave | Color wave | Not present | IMPLEMENT | |
| **Animations** | Animation engine | Budget-scheduled | Not present | IMPLEMENT | |
| **Animations** | Custom animations | Extensible format | Not present | ADIAR (backlog) | |
| **Actions** | Click actions | Left/right click | Not present | IMPLEMENT | |
| **Actions** | MESSAGE action | Send message | Not present | IMPLEMENT | |
| **Actions** | COMMAND action | Player command | Not present | IMPLEMENT | |
| **Actions** | CONSOLE action | Console command | Not present | IMPLEMENT | |
| **Actions** | TELEPORT action | Teleport player | Not present | IMPLEMENT | |
| **Actions** | SOUND action | Play sound | Not present | IMPLEMENT | |
| **Actions** | NEXT_PAGE/PREV_PAGE | Page control | Not present | IMPLEMENT | |
| **Actions** | Interaction entity | Virtual interaction | Not present | IMPLEMENT | |
| **Visibility** | display-distance | Render range | Single viewDistance | IMPLEMENT | |
| **Visibility** | update-distance | Update range | Same as viewDistance | IMPLEMENT | |
| **Visibility** | Show/hide per player | Manual control | Present | MAINTAIN | ✓ |
| **Visibility** | Spectator handling | Hide from spectators | Present | MAINTAIN | ✓ |
| **Visibility** | Permission-based | Require permission | Present | MAINTAIN | ✓ |
| **Persistence** | Per-file storage | One file per hologram | Single monolithic file | IMPLEMENT | |
| **Persistence** | Atomic writes | temp + rename | Present | MAINTAIN | ✓ |
| **Persistence** | Schema versioning | Version field | Not present | IMPLEMENT | |
| **Persistence** | Backup before migration | Auto backup | Not present | IMPLEMENT | |
| **Persistence** | Debounced save | Delay coalescing | Not present | IMPLEMENT | |
| **Config** | Renderer toggles | text/item/block display | Not present | IMPLEMENT | |
| **Config** | Default values | origin/height/billboard etc | Missing many | IMPLEMENT | |
| **Config** | Limits | max holograms/lines/chars | Missing many | IMPLEMENT | |
| **Config** | Scheduler budgets | per-operation limits | Missing many | IMPLEMENT | |
| **Config** | Animation config | intervals/global toggle | Not present | IMPLEMENT | |
| **Config** | Interaction config | cooldown/distance | Not present | IMPLEMENT | |
| **Commands** | /hologram create | Create at player pos | Present | MAINTAIN | ✓ |
| **Commands** | /hologram clone | Clone with new ID | Not present | IMPLEMENT | |
| **Commands** | /hologram rename | Rename hologram | Not present | IMPLEMENT | |
| **Commands** | /hologram delete | Remove hologram | Present | MAINTAIN | ✓ |
| **Commands** | /hologram enable/disable | Toggle state | Not present | IMPLEMENT | |
| **Commands** | /hologram info | Detailed info | Basic inspect | IMPLEMENT | |
| **Commands** | /hologram list | List all | Present | MAINTAIN | ✓ |
| **Commands** | /hologram near | Nearby holograms | Not present | IMPLEMENT | |
| **Commands** | /hologram movehere | Move to player | Present (as move) | MAINTAIN | ✓ |
| **Commands** | /hologram teleport | Teleport to hologram | Not present | IMPLEMENT | |
| **Commands** | /hologram align | Align to another | Not present | IMPLEMENT | |
| **Commands** | /hologram center | Center hologram | Not present | IMPLEMENT | |
| **Commands** | /hologram facing | Billboard mode | Not present | IMPLEMENT | |
| **Commands** | /hologram permission | Set permission | Not present | IMPLEMENT | |
| **Commands** | /hologram displayrange | Show range | Present (setdistance) | MAINTAIN | ✓ |
| **Commands** | /hologram updaterange | Update range | Not present | IMPLEMENT | |
| **Commands** | /hologram line add/set/remove | Line management | Present | MAINTAIN | ✓ |
| **Commands** | /hologram line insert | Insert at index | Not present | IMPLEMENT | |
| **Commands** | /hologram line move/swap | Move/swap lines | Not present | IMPLEMENT | |
| **Commands** | /hologram line clone | Clone line | Not present | IMPLEMENT | |
| **Commands** | /hologram line clear | Clear page lines | Not present | IMPLEMENT | |
| **Commands** | /hologram line height/offset/scale | Visual config | Not present | IMPLEMENT | |
| **Commands** | /hologram line facing | Per-line facing | Not present | IMPLEMENT | |
| **Commands** | /hologram line permission | Per-line permission | Not present | IMPLEMENT | |
| **Commands** | /hologram line flag | Per-line flags | Not present | IMPLEMENT | |
| **Commands** | /hologram page add/remove | Page CRUD | Present | MAINTAIN | ✓ |
| **Commands** | /hologram page insert | Insert page | Not present | IMPLEMENT | |
| **Commands** | /hologram page clone | Clone page | Not present | IMPLEMENT | |
| **Commands** | /hologram page swap | Swap pages | Not present | IMPLEMENT | |
| **Commands** | /hologram page switch | Switch to page | Not present | IMPLEMENT | |
| **Commands** | /hologram page rotation | Toggle rotation | Partial (setinterval) | IMPLEMENT | |
| **Commands** | /hologram action add/remove | Action management | Not present | IMPLEMENT | |
| **Commands** | /hologram visibility show/hide | Show/hide per player | Not present | IMPLEMENT | |
| **Commands** | /hologram save/reload | Persistence control | Partial (reload only) | IMPLEMENT | |
| **Commands** | /hologram diagnostics | Diagnostic info | Not present | IMPLEMENT | |
| **Commands** | /hologram stats | Statistics | Present | MAINTAIN | ✓ |
| **Commands** | /hologram flag | Flag management | Not present | IMPLEMENT | |
| **Permissions** | Granular permissions | Per-command permissions | 7 coarse permissions | IMPLEMENT | |
| **Permissions** | system-managed | Access system holograms | Not present | IMPLEMENT | |
| **Flags** | DISABLE_UPDATING | Disable updates | Not present | IMPLEMENT | |
| **Flags** | DISABLE_PLACEHOLDERS | Disable placeholders | Not present | IMPLEMENT | |
| **Flags** | DISABLE_ANIMATIONS | Disable animations | Not present | IMPLEMENT | |
| **Flags** | DISABLE_ACTIONS | Disable actions | Not present | IMPLEMENT | |
| **Flags** | DISABLE_SHADOW | Disable text shadow | Not present | IMPLEMENT | |
| **Flags** | STATIC_CONTENT | Never refresh | Not present | IMPLEMENT | |
| **Flags** | MANUAL_VISIBILITY | Manual only | Not present | IMPLEMENT | |
| **Flags** | IGNORE_SPECTATORS | Ignore spec | Not present | IMPLEMENT | |
| **Metrics** | Packet counters | Spawn/update/destroy | Present | MAINTAIN | ✓ |
| **Metrics** | Cache hit/miss | Placeholder cache stats | Not present | IMPLEMENT | |
| **Metrics** | Scheduler stats | Task counts | Not present | IMPLEMENT | |
| **Metrics** | Per-hologram diag | Detailed per-item | Not present | IMPLEMENT | |
| **Events** | Create/Delete events | Lifecycle events | Internal listener | IMPLEMENT | |
| **Events** | Show/Hide events | Visibility events | Internal listener | IMPLEMENT | |
| **Events** | ClickEvent | Click handling | Not present | IMPLEMENT | |
| **Events** | MoveEvent | Movement | Not present | IMPLEMENT | |
| **Events** | PageChangeEvent | Page switch | Not present | IMPLEMENT | |
| **Tests** | Unit tests | Core logic | 6 test classes | MAINTAIN + EXTEND | |
| **Tests** | Crate regression | Crates untouched | 2 crate-specific | MAINTAIN + EXTEND | |

## Decision Legend
- **IMPLEMENT**: Build original implementation
- **ADAPT**: Implement with adjustments for Minecraft 1.21.1 / Fabric+NeoForge
- **MAINTAIN**: Keep existing implementation
- **MAINTAIN + EXTEND**: Keep but add to it
- **ADIAR**: Defer with justification
- **REJECT**: Exclude with reason
