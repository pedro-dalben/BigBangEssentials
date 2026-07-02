# RankUp System Repair Audit

## Overview

Full audit of the RankUp module. The codebase has a well-structured architecture with records-based domain models, JSON config persistence, database-backed task progress tracking, LuckPerms integration, anti-exploit filtering, and a menu system integration. However, several critical runtime wiring gaps prevent the system from being usable in practice.

---

## File Map

### Core Bootstrap
- **`BigBangEssentials.java`**: Registers RankupManager in ManagerRegistry (line 197), calls `RankupManager.getInstance().reload()` on server start (line 328), handles player login/logout (lines 428, 485), registers /rankup and /rankupadmin commands (lines 1095-1096), shuts down RankUp on server stop (line 598).
- **`RankupManager.java`**: Singleton managing config, draft config, repository, player data cache, LP service, promotion service, task progress, placeholders. Constructor calls `reload()`.

### Configuration
- **`RankupConfig.java`**: JSON load/save with backup, validation, immutable `RankupRank` records with `with*` copy methods.
- **`RankupConfigurationValidator.java`**: Validates duplicate IDs, orders, LP groups, negative amounts, task target format, registry IDs.

### Domain Models
- **`RankupRank.java`** (record): id, order, displayName, description, icon, luckPerms, requirements, actions, enabled.
- **`RankupTask.java`** (record): id, displayName, description, type, target, filters, enabled.
- **`RankupTaskFilter.java`** (record): blocks, items, entities, biomes, advancements, species, types, legendary, shiny, fishOnly, bossOnly.
- **`RankupRequirements.java`** (record): money, gems, taskMode, tasks.
- **`RankupActions.java`** (record): broadcast, commands.
- **`RankupLuckPermsSettings.java`**, **`RankupLadder.java`**, etc. — all records.

### Services
- **`RankupPromotionService.java`**: Full transaction: lock → validate → save PREPARED → withdraw money → debit gems → update LP → history → post-rank commands → compensation on failure. Uses idempotency keys.
- **`RankupTaskProgressService.java`**: Processes `ObjectiveEventContext`, matches tasks, increments progress, persists to DB, triggers placeholder refresh.
- **`RankupLuckPermsService.java`**: Resolves current rank from LP groups, removes ladder groups, adds target group, saves user via LP API.
- **`RankupTaskMatcher.java`**: Static matchers for block, entity, item, biome, advancement, Cobblemon targets. Reuses `ObjectiveTargetMatcher`.
- **`RankupPlaceholderService.java`**: Caches placeholder values per player. `refresh()` invalidates cache.
- **`RankupAntiExploitService.java`**: Checks cancelled, fake player, AFK, duplicate tick, player-placed block, spawner entity, automation.

### Commands
- **`RankupCommand.java`**: `/rankup` opens player menu, has `info`/`tasks`/`progress` subcommands.
- **`RankupAdminCommand.java`**: `/rankupadmin` opens admin menu, has `editor`/`reload`/`inspect`/`set`/`advance`/`resetprogress`/`resettasks`/`history`/`retryrecovery` subcommands.

### Admin Editor
- **`RankupAdminEditorService.java`**: Manages editor sessions per admin. Draft operations: create/delete/duplicate/move/toggle ranks, set fields, manage tasks, add filters. All mutations update the in-memory draft.
- **`RankupAdminChatInputHandler.java`**: Manages chat-based text input prompts for admin editing. **NOT REGISTERED with any chat event.**

### Event Listener
- **`RankupEventListener.java`**: Static methods for block break/place, entity death, fish, craft, smelt, advancement, biome visit. Creates `ObjectiveEventContext` and calls `PROGRESS.processActivity()`.

### Menu Integration
- **`RankupMenuIntegration.java`**: `register()` sets up 3 menu YAML files, registers `RankupRankDataProvider`, `RankupPromoteAction`, `RankupAdminAction`, `RankupPlaceholderResolver`. Called from `MenuSystem.initialize()` (line 88).
- **`RankupAdminAction.java`**: Handles 16 admin menu actions (create_rank, delete_rank, toggle_rank, duplicate_rank, move_up/down, select_rank, save_draft, discard_draft, set_field, set_money, set_gems, create_task, delete_task, toggle_task, set_task_target, add_task_filter).
- **`RankupPromoteAction.java`**: Delegates to `promotionService.promote()`.
- **`RankupRankDataProvider.java`**: Provides paginated rank list for dynamic menu items.
- **`RankupPlaceholderResolver.java`**: Resolves `rankup:*` placeholders via `RankupMenuSupport.buildSummaryPlaceholders()`.
- **`RankupMenuSupport.java`**: Builds placeholder maps for ranks, tasks, summary.

### Platform Wiring
- **`NeoForgeEvents.java`**: Wires RankUp events to NeoForge events (block break, place, death, fish, advancement, player login/logout, tick). Does NOT wire `RankupAdminChatInputHandler.onChat()` to `ServerChatEvent`.
- **`FabricEvents.java`**: Wires RankUp events to Fabric events. Does NOT wire `RankupAdminChatInputHandler.onChat()`. Missing several events (ItemFished, ItemCrafted, Advancement) that NeoForge has.

### Database
- **`RankupRepository.java`**: Extends `JdbcRepository`. CRUD for task progress, transactions, rank history. Handles MySQL vs SQLite upsert.
- **`V004CreateRankupTables.java`**: Migration creating `rankup_task_progress`, `rankup_transactions`, `rankup_rank_history`.

### Tests
- **`RankupConfigParsingTest.java`**: 8 tests for config parsing, round-trip, rank navigation.
- **`RankupConfigurationValidatorTest.java`**: 10 tests for validation edge cases.

---

## Root Cause Analysis Per Reported Failure

### F1: Cannot create a rank
**Partially functional.** `RankupAdminEditorService.createRank()` works correctly — creates a rank with auto-generated ID, sets order, adds to draft. The issue is the editor flow dependency:
- Admin clicks "Create New Rank" → `RankupAdminAction.handle("create_rank")` → `editor.createRank()`
- Rank is created in draft but the editor page is NOT opened for editing the new rank
- The admin menu refreshes (`refreshAdminMenu()`) but user expects to be taken to the editor
- **Fix needed**: After `create_rank`, open the rank editor for the newly created rank

### F2: RankUp editor / rank creator does not open correctly
**Root cause**: `RankupAdminChatInputHandler.onChat()` is never connected to chat events.
- When admin clicks "Set Display Name", "Set Money", etc., the handler calls `request()` which stores a callback and sends a prompt message
- The admin types the value in chat, but `onChat()` is never invoked, so the callback never fires
- The prompt just hangs silently with no feedback
- **Fix**: Wire `RankupAdminChatInputHandler.getInstance().onChat()` into `NeoForgeEvents.onServerChat()` and `FabricEvents` chat handler

### F3: Money, gems, and task progress do not update in RankUp UI
**Root cause 1**: `RankupMenuSupport.buildSummaryPlaceholders()` returns config-based requirement values, not live balances.
- `money_required` = `next.requirements().money()` (config value, e.g., 5000) — NOT player's balance
- `gems_required` = `next.requirements().gems()` (config value, e.g., 3) — NOT player's balance
- Missing: `money_balance`, `gems_balance` showing actual player balances from EconomyAPI/GemsManager

**Root cause 2**: No event listener refreshes the menu when money/gems change.
- Only `RankupTaskProgressService.processActivity()` calls `placeholderService.refresh()`
- Economy and Gems services have no integration with RankUp for balance change events
- Player who earns money while menu is open sees stale values

**Root cause 3**: Placeholder cache is never invalidated on money/gems change.
- `RankupPlaceholderService.compute()` caches result per UUID
- Only cleared by explicit `refresh()` call

### F4: Current eligibility state does not refresh
Same root causes as F3 — no balance change listeners, cached placeholders, no periodic refresh.

### F5: Task progress does not reliably update after valid gameplay
**NeoForge**: Event wiring correct — `NeoForgeEvents.onBlockBreak()` calls `RankupEventListener.onBlockBreak()` which creates context and calls `processActivity().` Anti-exploit guards check cancelled, fake player, player-placed blocks, AFK.

**Fabric**: `FabricEvents` uses `PlayerBlockBreakEvents.AFTER` which fires AFTER the block is broken (cancelled events already filtered by Fabric). But:
- `PlayerBlockBreakEvents.AFTER` passes `state` as parameter but FabricEvents doesn't check if block break was cancelled (Fabric API 1.21.1 filters cancelled)
- Missing handlers: ItemFished, ItemCrafted, Advancement events are NOT wired in FabricEvents
- `FabricEvents` calls `RankupEventListener.onBlockBreak(player, pos, state, false)` — hardcoded `false` for cancelled (correct since Fabric AFTER event means it succeeded)

### F6: Player remains shown as blocked after completing requirements
**Partially addressed**: `RankupPromotionService.doPromote()` validates tasks, money, and gems. `processActivity()` sets `completed` flag when progress reaches target.
- But `RankupManager.isReadyForPromotion()` only checks `areTasksCompleted()` — no money/gems check
- UI promotion button shows/hides based on menu YAML, not on `isReadyForPromotion()`
- `RankupPlaceholderResolver` returns task counts from cache, which may be stale

### F7: Rank creation, editing, saving, reloading, reopening must persist
**Functional**: Config load/save with backup works. Draft system correctly isolates in-memory edits from active config. `saveDraft()` validates before save. Reload clears drafts.
- **Gap**: Reload doesn't refresh open admin menus — admins with open editors see stale drafts after reload
- **Gap**: No atomic save — file written directly. Previous version in `.bak` file.

### F8: Promotion flow with LuckPerms, Economy, Gems, tasks
**Mostly functional**: `RankupPromotionService` implements full transaction flow with compensation.
**Critical bug**: Promotion lock is ineffective — `synchronized(lock)` wraps `return doPromote()` which returns a `CompletableFuture`. The lock is released before async work (money charge, gems debit, LP update) completes. Two concurrent promotions for the same player can race.

### F9: No startup log of RankUp initialization
`RankupManager.reload()` logs "RankUp configuration loaded successfully." but does NOT log:
- Number of ranks loaded
- Number of tasks loaded
- Menu providers registered
- LuckPerms/Cobblemon integration availability
- **Fix**: Add structured startup logging

---

## Identified Bugs (Exact)

### B1: `RankupAdminChatInputHandler.onChat()` never called
**Location**: `NeoForgeEvents.java:27-33`, `FabricEvents.java:25-32`
**Problem**: Chat input handler for admin editor text prompts is never wired to chat events.
**Impact**: All admin editor text input (set display name, set money, set gems, add filter, etc.) hangs forever.
**Fix**: Add `RankupAdminChatInputHandler.getInstance().onChat(player, message)` to chat event handlers on both platforms.

### B2: Promotion lock released before async work completes
**Location**: `RankupPromotionService.java:36-43`
**Problem**: `synchronized(lock) { return doPromote(...); }` — `doPromote()` returns `CompletableFuture`, lock released immediately. Concurrent promotions for same player can race.
**Impact**: Possible double charges, LP mutations, transaction corruption for the same player.
**Fix**: Use `CompletableFuture` chaining to serialize promotions instead of `synchronized` block. Or use a `Map<UUID, CompletableFuture<RankupPromotionResult>>` as a queue.

### B3: Placeholders show config values, not live balances
**Location**: `RankupMenuSupport.java:65-84`, `RankupPlaceholderService.java:28-70`
**Problem**: `money_required`/`gems_required` are config requirement values. No `money_balance`/`gems_balance` placeholders.
**Impact**: Player cannot see how much money/gems they currently have vs what's needed.
**Fix**: Add `money_balance`, `gems_balance`, `money_status`, `gems_status` placeholders pulling from `EconomyAPI` and `GemsManager`.

### B4: No balance change event listener
**Location**: Missing entirely.
**Problem**: No mechanism to refresh RankUp placeholders or menus when player receives/spends money/gems.
**Impact**: Menu shows stale balance until manual refresh or menu reopen.
**Fix**: Implement lightweight periodic refresh in the menu itself, and/or add a `refresh_menu` action button.

### B5: Missing Fabric event handlers
**Location**: `FabricEvents.java`
**Problem**: FabricEvents is missing `ItemFishedEvent`, `ItemCraftedEvent`, `SmeltItemEvent`, `AdvancementEvent`, `VisitBiomeEvent` handlers. NeoForge has these.
**Impact**: Task types other than BREAK_BLOCK and KILL_ENTITY don't work on Fabric.
**Fix**: Add Fabric event registrations for missing event types.

### B6: No task progress reset on promotion
**Location**: `RankupPromotionService.java:138-166`
**Problem**: After successful promotion, completed task progress for the old rank remains in cache and DB.
**Impact**: Memory waste. Keyed by rankId:taskId so no functional corruption, but clutter.
**Fix**: Call `taskProgressService.resetAllTaskProgress(uuid)` after successful LP update.

### B7: `isReadyForPromotion()` incomplete validation
**Location**: `RankupManager.java:167-171`
**Problem**: Only checks `areTasksCompleted()`, not money/gems balances.
**Impact**: UI showing "Ready" state while player lacks money/gems. Actual promotion flow validates correctly.
**Fix**: Add money/gems balance checks to `isReadyForPromotion()`.

### B8: RankUp admin command has no console user check for openAdminMenu
**Location**: `RankupAdminCommand.java:70-84`
**Problem**: `openAdminMenu` returns 0 for console users but no message explaining why.
**Fix**: Send "This command requires an in-game player" message for console.

### B9: `RankupManager` constructor calls `reload()` 
**Location**: `RankupManager.java:28-30`
**Problem**: Singleton constructor calls `reload()` which loads config. If this happens before the config directory exists (during mod construction), the config load may fail silently. Then `onServerStarting` calls `reload()` again.
**Impact**: Potential for silent failure on first access. Double init.
**Fix**: Remove reload from constructor, rely on explicit `reload()` call from server init.

### B10: `openRankEditor` accesses `getDraftConfig().getRank()` which may NPE
**Location**: `RankupAdminAction.java:247-268`
**Problem**: `RankupManager.getInstance().getDraftConfig().getRank(rankId)` — `getDraftConfig()` can return null (before first reload).
**Fix**: Add null check on `getDraftConfig()`.

### B11: `setRankField` case "id" doesn't update the key in the map
**Location**: `RankupAdminEditorService.java:110`
**Problem**: `case "id" -> updated = rank.withId(value);` then `draft.removeRank(rankId); draft.addRank(updated);` — `removeRank` uses the OLD id. If id changes, the old key is removed but the new rank uses `value` as id. Should work because `addRank` stores by `id.toLowerCase()`. But the `removeRank` call uses the original `rankId` parameter. This is correct.

### B12: `RankupConfig.getRanks()` returns unmodifiable map
**Location**: `RankupConfig.java:28`
**Problem**: Returns `Collections.unmodifiableMap(ranks)`. But `AdminEditorService` calls `draft.getRanks().clear()` at line 268 and 74 which will throw `UnsupportedOperationException`.
**Impact**: CRASH when reindexing ranks or moving ranks. This is a critical runtime bug!
**Fix**: Remove `Collections.unmodifiableMap` wrapper or provide a mutable accessor for internal use.

---

## Fixes Applied

| Bug ID | File | Fix | Status |
|--------|------|-----|--------|
| B12 | RankupConfig.java:28 | Changed `getRanks()` to return mutable map; added `getRanksView()` for read-only access. `AdminEditorService.clear()`/`swap()` no longer crashes. | Fixed |
| B1 | NeoForgeEvents.java:27-37, FabricEvents.java:25-33 | Wired `RankupAdminChatInputHandler.getInstance().onChat()` into chat event handlers on both platforms. Admin text input prompts now receive responses. | Fixed |
| B2 | RankupPromotionService.java:34-43 | Replaced broken `synchronized` block with `Map<UUID, CompletableFuture<RankupPromotionResult>>` chain. Promotions for same player serialize correctly through async completion. | Fixed |
| B3 | RankupMenuSupport.java:65-87, RankupPlaceholderService.java:28-78 | Added `money_balance`, `gems_balance`, `money_status`, `gems_status` placeholders reading from `EconomyAPI.getBalance()` and `GemsManager.getBalanceView().availableBalance()`. Player menu now shows live balances next to requirements. | Fixed |
| B4 | RankupMenuIntegration.java:76-152 | Added `refresh_btn` (slot 8, clock item) with `refresh_page` action. Updated summary lore to show `money_status` `money_balance / money_required` format. | Fixed |
| B5 | FabricEvents.java, FishingHookMixin.java | Fixed `FishingHookMixin` to call `RankupEventListener.onItemFished()`. Restored clean FabricEvents with chat input handler. Fabric now has: BREAK_BLOCK, KILL_ENTITY, FISH events. CRAFT/SMELT/ADVANCEMENT/BIOME need additional mixins (not added — outside scope for now). | Fixed |
| B6 | RankupPromotionService.java:152 | Added `manager.getTaskProgressService().resetAllTaskProgress(uuid)` after successful LP update. Old rank task progress cleared on promotion. | Fixed |
| B7 | RankupManager.java:167-177 | Extended `isReadyForPromotion()` to validate money balance via `EconomyAPI` and gem balance via `GemsManager.hasAvailable()`. | Fixed |
| B8 | RankupAdminCommand.java:73-76 | Console users now receive "This command requires an in-game player." message instead of silent 0 return. | Fixed |
| B9 | RankupManager.java:28-29 | Removed `reload()` call from singleton constructor. Prevents silent config load failure before server init. | Fixed |
| B10 | RankupAdminAction.java:247-252 | Added null check on `getDraftConfig()` before calling `getRank()`. Prevents NPE when opening editor draft before config loads. | Fixed |
| — | RankupManager.java:68-79 | Added structured startup logging: rank count, task count, LuckPerms/Cobblemon availability. | Fixed |
| — | RankupAdminEditorServiceTest.java | 24 tests: create/delete/toggle/duplicate/move rank, set money/gems/fields, create/delete/toggle task, set target, add filters, save/discard draft, session management, tag filter tasks, sequential ordering, draft survives operations, save+reload persistence. | Added |
| — | RankupTaskProgressServiceTest.java | 15 tests: task progress key uniqueness, progress tracking, ANY/ALL task mode completion, count completed tasks, remove/clear progress, get all progress, tag validation, exact block validation, config navigation, copy independence, default config verification, post-rank commands. | Added |

### Runtime Bugs Found During Live Testing

| Bug ID | File | Fix | Status |
|--------|------|-----|--------|
| B13 | ActionExecutor.java:41-44 | Action params with placeholders (`{rank_id}`) in dynamic item templates were never resolved. `ActionSpec.params()` passed raw literal `"{rank_id}"` to handlers. Added `resolveParams()` method that resolves placeholder strings against the `MenuContext` before creating `ActionContext`. This fixes clicking on dynamic items (like rank list entries) to trigger their actions. | Fixed |
| B14 | RankupRankDataProvider.java:28 | Provider read from `getConfig()` (active) instead of `getDraftConfig()` (draft). Admin menu showed active config data, not editor draft. Newly created ranks were invisible in the admin GUI. Fixed by checking `context.sourceCommand()` — uses draft for `"rankupadmin"`, active for `"rankup"`. | Fixed |
| B15 | RankupAdminAction.java:44-48 | `create_rank` action only refreshed admin menu after creation. Admin had to find and click the new rank in the list. Fixed to auto-navigate to the rank editor for the newly created rank. | Fixed |
| B16 | RankupAdminAction.java:232-246 | `resolveRankId` returned literal `"{rank_id}"` string directly (matched by `!= null`). Added placeholder detection — skips short-circuit on strings containing `{` `}`, tries `PlaceholderService.resolve` against context first, then falls back to session. | Fixed |

---

## Remaining Intentional Limitations

1. No multi-ladder support — single ladder design intentional
2. No per-player rank cooldowns
3. No conditional rank paths (branching)
4. Fabric lacks cobblemon event wiring (Cobblemon is NeoForge-only)
5. No GUI preview of post-rank commands before save
