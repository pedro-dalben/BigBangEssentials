# Jobs Menu Refactor — Audit Report

## Problems Found in Old System

1. **No availability gating in commands**: `/jobs entrar` bypassed all eligibility checks — `JobCommandService.joinJob` was the sole gate, but it ran after command dispatch with no pre-validation layer for menu feedback.
2. **Hardcoded status strings**: Status text (`"Ativo"`, `"Bloqueado"`) was embedded in chat commands and menu templates without a central source of truth.
3. **No ViewModel layer**: Menu templates directly accessed domain objects (`JobProgress`, raw config records) creating tight coupling to internal data structures.
4. **Fragmented visibility logic**: Job visibility (hide until discovered, hide when locked) was scattered across 3+ classes with no unified evaluation point.
5. **Missing cooldown feedback**: The `/jobs` summary never showed slot cooldown information — players had to run `/jobs slot list` separately.
6. **No favorite integration**: The old system had no way to favorite/unfavorite jobs.
7. **No requirement breakdown**: Players saw only `"§c[BLOQUEADO]"` without knowing *why*.

## Files Created

| File | Purpose |
|------|---------|
| `JobsAvailabilityService.java` | Central eligibility evaluation producing rich `JobAvailabilityResult` |
| `JobAvailabilityResult.java` | Record + Builder for eligibility result with status, reasons, requirements |
| `JobAvailabilityStatus.java` | Enum of all availability states (11 values) |
| `JobRequirementResult.java` | Record for individual requirement check results |
| `JobRequirementType.java` | Enum of requirement categories (14 types) |
| `JobMenuViewModel.java` | Presentation record decoupling domain from menu |
| `JobMenuViewModelFactory.java` | Factory mapping domain data to ViewModel |
| `JobsMenuSupport.java` | Placeholder builder, sort/filter utilities bridging menu ↔ jobs |
| `ProgressBarComponent.java` | Reusable configurable progress bar rendering |
| `JobFavoriteService.java` | Per-player job favorite tracking |
| `JobDiscoveryService.java` | Tracks which jobs a player has discovered |
| `EarningsFeedbackMode.java` | Enum for feedback channel selection |
| `IntegrationHealthResult.java` | Record for integration health checks |
| `IntegrationHealthStatus.java` | Enum for integration availability states |
| `IntegrationHealthService.java` | Runtime health check for all optional integrations |

## Files Modified

| File | Change |
|------|--------|
| `JobsCommand.java` | `executeJoin` now calls `JobAvailabilityService.evaluate()` before `JobCommandService.joinJob()`; shows availability reason on rejection |
| `JobsCommand.java` | `executeSummary` tries menu first, falls back to text |
| `JobsConfig.java` | Added `VisibilityConfig` record, `VisibilityMode` enum, `visibility` field on `JobDefinition` |
| `JobsConfigLoader.java` | Parses `visibility` block from YAML, defaults to `ALWAYS_VISIBLE` |
| `JobCommandService.java` | Added `LICENSE_AVAILABLE`, `LICENSE_IN_PROGRESS`, `LICENSE_READY_TO_CLAIM` to `JoinResult` |
| `PlayerJobsData.java` | Added `getActiveJobsCount()`, `getTotalDailyEarnings()`, `getDailyEarnings()` |
| `JobsManager.java` | Added `getMaxActiveJobsForPlayer()`, `getGanhosPermissionMultiplier()` |

## Architectural Decisions

1. **Availability as pre-gate**: `JobAvailabilityService.evaluate()` runs *before* `JobCommandService.joinJob()`. The command switch on `JoinResult` remains authoritative — the availability check is informational/early-exit for menu feedback.

2. **Builder pattern on result**: `JobAvailabilityResult.Builder` allows progressive construction across multiple requirement evaluations, avoiding a constructor with 9 positional parameters.

3. **ViewModel over raw domain**: Menu templates never access `JobProgress` or `JobDefinition` directly — they consume `JobMenuViewModel` records, enabling the domain to change without breaking menus.

4. **Service stateless singletons**: All services (`JobAvailabilityService`, `JobFavoriteService`, etc.) are stateless singletons with `getInstance()`. Stateful data (favorites, progress) lives in injected repositories or in-memory maps.

5. **Visibility as runtime evaluation**: Visibility is not a stored property — `evaluateVisibility()` recomputes on every call based on player state, discovery, and status.

6. **Color mapping centralized**: Status-to-color mapping lives in `JobsMenuSupport.buildJobPlaceholders()`, not in templates — ensures consistent coloring.

## Tests Summary

| Test Class | Coverage |
|------------|----------|
| `JobsMenuSystemTest` | Availability status render flags, builder construction, VisibilityConfig defaults, favorite toggle, ProgressBarComponent 50% render, JobRequirementResult accessors, EarningsFeedbackMode defaults, IntegrationHealthStatus values, cooldown for no-slots |
| Existing `JobsSystemTest` | Permission multipliers, max jobs limit, XP progression, skill points, circular dependency detection, XP curve math |

Total test count: 10 new + 8 existing + existing pipeline/integration tests.

### Key Test Patterns
- **POJO model tests**: Assert record accessors, builder behavior, enum values.
- **Mock-free**: All new tests avoid Mockito static mocking — they test pure data transformations.
- **Bootstrap required**: Tests using `Component` or `ItemStack` require `Bootstrap.bootStrap()` in `@BeforeAll`.

## Known Limitations

1. **No permission-based visibility**: Visibility mode evaluates discovery and availability but does not check `job.permission` — a player without permission sees a job they can't join. This is by design (shows "why blocked").
2. **No cooldown display in menu**: The `JobAvailabilityResult` carries `cooldownRemaining` in milliseconds but the menu YAML templates don't expose a `{job_cooldown_remaining}` placeholder yet.
3. **Favorite persistence**: `JobFavoriteService` stores favorites in-memory only — they reset on server restart. A database-backed implementation is planned.
4. **Discovery persistence**: `JobDiscoveryService` similarly uses in-memory sets — no cross-restart persistence.
5. **Text fallback resilience**: If `MenuSystem` fails to open, `executeSummary` falls back to text output that duplicates logic from `JobsMenuSupport`. The two paths could drift if one is updated without the other.
6. **No integration health in menu**: Integration dependency failures show as `INTEGRATION_UNAVAILABLE` but the menu doesn't expose which integration is missing.
7. **`JobAvailabilityService.getRemainingCooldownForJob` is private**: Cannot be tested directly — covered indirectly through `evaluate()` return assertions.
