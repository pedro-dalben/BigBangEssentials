---
phase: crates-module
reviewed: 2026-06-29T12:00:00Z
depth: deep
files_reviewed: 25
files_reviewed_list:
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/CrateManager.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/command/CrateCommand.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/command/KeyGiveCommand.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/command/GiveKeyCommand.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateKeyService.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateService.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateOpeningService.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/RewardService.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateAuditService.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateMetricsService.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/persistence/JdbcPlayerCrateStateRepository.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/persistence/JdbcCrateMetricsRepository.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/persistence/JdbcCrateIdempotencyRepository.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/persistence/JdbcRewardRollStateRepository.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/persistence/JdbcCrateAuditRepository.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/persistence/JdbcPlayerVirtualKeyRepository.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/persistence/JsonCrateRepository.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/persistence/JsonKeyRepository.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/persistence/JsonCrateLocationRepository.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/integration/CrateEconomyIntegration.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/listener/CrateBlockListener.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/listener/CratePlayerListener.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/placeholder/CratePlaceholderResolver.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/animation/CrateAnimationHandler.java
  - common/src/main/java/com/pedrodalben/bigbangessentials/crates/domain/CrateOpenAudit.java
findings:
  critical: 6
  warning: 5
  info: 4
  total: 15
status: fixes_applied
---

# Crates Module: Code Review Report

**Reviewed:** 2026-06-29T12:00:00Z
**Depth:** deep
**Files Reviewed:** 25
**Status:** issues_found

## Summary

Reviewed 25 source files across the crates module covering persistence (JDBC/JSON), service layer, commands, listeners, and animation. Found **6 CRITICAL** issues including an authorization bypass, duplicate reward delivery, command injection vulnerability, and HMAC anti-forgery weaknesses. Also identified **5 WARNING** and **4 INFO** findings. The most severe issue is a permission-check logic flaw that completely defeats crate permission requirements, granting access to any player when a permission node is merely configured (not checked).

---

## Critical Issues

### CR-01: Authorization Bypass in Permission Validation — CrateOpeningService.validateRequirements
**✅ FIXED** — Replaced broken `hasPerm = true` logic with actual `PermissionAPI.hasPermission()` call.

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateOpeningService.java:196-211`
**Severity:** CRITICAL

**Issue:** The permission check in `validateRequirements` has a logic error that grants access whenever a permission node is configured, without actually verifying the player possesses it.

```java
if (requirements.hasPermissionRequirement()) {
    if (!player.hasPermissions(4)) {
        boolean hasPerm = false;
        var permNode = requirements.getRequiredPermission();
        if (permNode != null && !permNode.isBlank()) {
            hasPerm = true;  // <-- BUG: sets true just because node is non-null
        }
        if (!hasPerm) {
            return new ValidationResult(false, "You don't have permission to open this crate");
        }
    }
}
```

If `hasPermissionRequirement()` returns true (node is non-null + non-blank), execution enters the block. For non-OP players, `permNode` is guaranteed non-null/blank (we already checked), so `hasPerm = true` unconditionally. The denial at line 208-210 is never reached. **Any player can open any crate with a configured permission requirement.**

**Fix:** Replace the check with an actual permission verification:
```java
if (requirements.hasPermissionRequirement()) {
    if (!player.hasPermissions(4)) {
        var permNode = requirements.getRequiredPermission();
        if (permNode == null || permNode.isBlank() ||
            !com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(
                player.getUUID(), permNode)) {
            return new ValidationResult(false, "You don't have permission to open this crate");
        }
    }
}
```

---

### CR-02: Duplicate Reward Delivery in Virtual Crate Openings
**✅ FIXED** — Removed `deliverReward` from `CrateAnimationHandler.completeAnimation()`. Reward is delivered once in `openCrateInternal`.

**File:** Multiple files — `CrateBlockListener.java:80-94`, `CrateOpeningService.java:124`, `CrateAnimationHandler.java:196-199`
**Severity:** CRITICAL

**Issue:** When a virtual crate is opened, the reward is delivered **twice**: once inside `openCrateInternal` (line 124), and again when the animation completes (`completeAnimation` line 196-199). Additionally, the animation delivers `displayReward` (the first active reward in the crate's list) rather than the actual rolled reward, potentially giving the player a different item than what was won.

**Flow:**
1. `CrateBlockListener.onBlockInteract` → `openingService.openCrate(...)` → `openCrateInternal` → **delivers rolled reward** (line 124)
2. After `openCrate` returns, `startVirtualAnimation(player, crate, displayReward)` starts animation with `displayReward = crate.getRewards().stream().filter(CrateReward::isActive).toList().get(0)` (NOT the rolled reward)
3. Animation completes → `completeAnimation` → **delivers `displayReward` again** (wrong reward, double delivery)

The player receives both the correctly rolled reward AND the first active reward from the crate list as a bonus.

**Fix:** Remove the reward delivery from `completeAnimation` — the animation should only play visuals/sound:
```java
// CrateAnimationHandler.java completeAnimation method — remove lines 195-200:
// if (state.reward != null) {
//     playSound(state.player, "minecraft:entity.player.levelup");
//     RewardService.getInstance().deliverReward(state.player, state.reward);
//     ...
// }
```
Or alternatively, pass the actually rolled reward to the animation and only deliver it there, removing the delivery from `openCrateInternal`.

---

### CR-03: Command Injection via Player Name in Reward Commands
**✅ FIXED** — Changed `player.getName().getString()` to `player.getGameProfile().getName()` which returns the real Minecraft username, not a modifiable display name.

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/RewardService.java:148-155`
**Severity:** CRITICAL

**Issue:** Reward commands interpolate the player's display name via simple string replacement without sanitization:

```java
String resolved = command
    .replace("{player}", player.getName().getString())
    .replace("{uuid}", player.getUUID().toString());
try {
    server.getCommands().performPrefixedCommand(source, resolved);
} catch (Exception e) {
    LOGGER.error("Failed to execute reward command: {}", resolved, e);
}
```

If a player's display name (or nickname set by another plugin) contains Minecraft command syntax like `@a`, `@p`, `[` `]`, command separators, or other special characters, this could result in command injection. For example, a crate with reward command `give {player} diamond 1` when the player's name is `@a[type=player]` would execute `give @a[type=player] diamond 1`, granting diamonds to all players instead of just the intended recipient.

**Fix:** Sanitize the player name and/or use UUID-based selectors instead:
```java
// Use the player's UUID directly in target selectors
String resolved = command
    .replace("{player}", player.getGameProfile().getName()) // Use profile name, not display name
    .replace("{uuid}", player.getUUID().toString());

// Or use a UUID-based target selector for commands that support it
// e.g., "give @s diamond 1" is always safe when run as the player
```

---

### CR-04: HMAC Anti-Forgery Secret Stored in World-Readable Plaintext File
**✅ FIXED** — Added `setReadable(true, true)`, `setWritable(false, false)`, `setExecutable(false, false)` after writing the secret file.

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateKeyService.java:41, 296-317`
**Severity:** CRITICAL

**Issue:** The HMAC secret used to sign physical key items (preventing forged keys) is stored in `config/.crate_hmac_secret` as a plaintext hex string with no file permission hardening:

```java
private static final String SECRET_FILE = "config/.crate_hmac_secret";
// ...
Path path = Paths.get(SECRET_FILE);
if (Files.exists(path)) {
    serverSecret = Files.readString(path).trim();
} else {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    serverSecret = HexFormat.of().formatHex(key);
    Files.createDirectories(path.getParent());
    Files.writeString(path, serverSecret);  // No permission setting
}
```

The file inherits the server process's umask (typically 022), making it world-readable. Any malicious plugin, compromised player with file access, or filesystem-level attacker can read the secret and forge valid HMAC signatures for any key ID, bypassing the anti-forgery system entirely.

**Additional concern:** The path is relative (`config/.crate_hmac_secret`), so if the working directory changes between restarts (e.g., different launcher configurations), the secret is regenerated, invalidating all existing physical keys.

**Fix:** Set restrictive file permissions on creation and use an absolute path:
```java
Files.writeString(path, serverSecret);
path.toFile().setReadable(true, true);   // owner read only
path.toFile().setWritable(false, false); // no write access
path.toFile().setExecutable(false, false);
```

---

### CR-05: Idempotency `markProcessed` Returns `true` on Database Failure
**✅ FIXED** — Changed `return true` to `return false` in the catch block, so DB failures are treated as "not processed" (fail-closed).

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/persistence/JdbcCrateIdempotencyRepository.java:53-54`
**Severity:** CRITICAL

**Issue:** When the database operation in `markProcessed` throws an exception, the method returns `true`, signaling to callers that the idempotency key was successfully marked as processed:

```java
try {
    int inserted = getDatabase().executeUpdate(INSERT, ...).join();
    if (inserted == 0) {
        return false; // Already processed
    }
    return true; // First time processed
} catch (Exception e) {
    LOGGER.error("Failed to mark idempotency key '{}': {}", idempotencyKey, e.getMessage(), e);
    return true; // BUG: Returns "processed" on failure
}
```

This means if the database is temporarily unavailable, operations that should be rejected as duplicates will proceed, potentially causing duplicate key grants, duplicate crate openings, and double economy deductions. Conversely, if the operation that was meant to be prevented by idempotency goes through, it could lead to item/currency duplication.

**Fix:** Return `false` (or rethrow) on database failure to err on the side of caution:
```java
} catch (Exception e) {
    LOGGER.error("Failed to mark idempotency key '{}': {}", idempotencyKey, e.getMessage(), e);
    return false; // Fail closed — assume not processed
}
```

---

### CR-06: Player Lock Entries Never Removed — Memory Leak
**✅ FIXED** — Added `playerLocks.remove(playerId)` in the `finally` block after `lock.unlock()`.

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateOpeningService.java:33, 51-63`
**Severity:** CRITICAL

**Issue:** Per-player `ReentrantLock` instances are created and stored in `playerLocks` via `computeIfAbsent` but **never removed**. Every unique player who ever opens a crate leaves a lock entry in this `ConcurrentHashMap` for the lifetime of the server:

```java
private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks;

public CrateOpeningResult openCrate(ServerPlayer player, ...) {
    UUID playerId = player.getUUID();
    ReentrantLock lock = playerLocks.computeIfAbsent(playerId, k -> new ReentrantLock());
    // lock acquired and released, but not removed from map
}
```

On a server with thousands of unique players over its lifetime, this grows unbounded. Each `ReentrantLock` object stays reachable from the map, preventing garbage collection.

**Fix:** Add cleanup after lock release:
```java
try {
    return openCrateInternal(player, crate, source, idempotencyKey);
} finally {
    lock.unlock();
    // Optional: remove lock if no contention to keep map small
    // (Simple approach: just remove it — contention is rare per player session)
}
```

However, removing on every unlock could cause churn. A better approach: use a bounded cache (e.g., Guava Cache with weak references) or remove after a period of inactivity.

---

## Warnings

### WR-01: Rollback Restores Wrong Key Type for Physical Key Consumption

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateOpeningService.java:155-156`
**Severity:** WARNING

**Issue:** The rollback method always restores a virtual key, even when the consumed key was physical:

```java
if (keyConsumed) {
    keyService.giveVirtualKey(playerId, crate.getRequirements().getAcceptedKeyIds().get(0), 1, GrantSource.ROLLBACK, null);
}
```

If the crate accepts both virtual and physical keys, the code can't determine which type was consumed. Furthermore, it only restores the first accepted key ID, not the specific key used.

**Fix:** Track which key was actually consumed (key type and ID) in the `CrateOpeningResult` or a state variable, and restore the exact same type.

---

### WR-02: Physical Key Deduction is Single-Item Only

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateKeyService.java:169-181`
**Severity:** WARNING

**Issue:** `takePhysicalKeyFromInventory` only removes one physical key item at a time, but `consumeKeyForOpening` calls it for each opening. The balance of the physical key stack isn't checked before calling `openCrate` — only `countPhysicalKeysInInventory` is called. This is a minor inconsistency — if a physical key stack is consumed mid-operation by an external plugin, `takePhysicalKeyFromInventory` could silently fail.

Additionally, the function signature and `consumeKeyForOpening` only take 1 key, but `givePhysicalKey` can give `amount` keys via `stack.setCount(amount)`. There's no batch `takePhysicalKeyFromInventory(player, keyId, amount)` method.

---

### WR-03: Economy `deposit` Returns `false` When `amount <= 0` (Inconsistent Contract)

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/integration/CrateEconomyIntegration.java:59-63`
**Severity:** WARNING

**Issue:** The `deposit` method returns `false` when `amount <= 0`, while `withdraw` and `hasBalance` return `true` when `amount <= 0`:

```java
public boolean deposit(UUID playerId, double amount, String reason) {
    if (!enabled || economyService == null) return false;
    if (amount <= 0) return false;  // Inconsistent with withdraw/hasBalance
    return economyService.deposit(playerId, amount);
}
```

Callers (like the rollback at `CrateOpeningService.java:164`) expect `deposit` to return `true` on success and `false` on failure. If `amount` is 0, the rollback interprets `false` as a failure, even though there was nothing to deposit. This is inconsistent with `withdraw` which returns `true` for `amount <= 0`.

**Fix:** `deposit` should return `true` for `amount <= 0` (nothing to do = success):
```java
public boolean deposit(UUID playerId, double amount, String reason) {
    if (!enabled || economyService == null) return amount <= 0;
    if (amount <= 0) return true;
    return economyService.deposit(playerId, amount);
}
```

---

### WR-04: Negative Amount Could Increment Virtual Key Balance (Defense in Depth)

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/persistence/JdbcPlayerVirtualKeyRepository.java:175-193`
**Severity:** WARNING

**Issue:** The `decrementBalance` SQL does `SET amount = amount - ?` with `WHERE amount >= ?`. The `CrateKeyService.takeVirtualKey` validates `amount <= 0`, but the repository method itself has no guard. A caller that bypasses the service layer (e.g., direct repository access from another module) or has a bug in the `amount` parameter could inadvertently *increase* the key balance by passing a negative amount.

**Fix:** Add a guard at the repository level:
```java
public boolean decrementBalance(UUID playerId, String keyId, int amount) {
    if (amount <= 0) return false;
    // ... rest of method
}
```

---

### WR-05: No Validation That Reward Rolled is Actually Delivered

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateOpeningService.java:124`
**Severity:** WARNING

**Issue:** After `rewardService.deliverReward(player, selectedReward)` is called, the return value is never checked. If delivery fails (e.g., inventory full for ITEM type and drop fails, or command execution throws), the crate opening is still marked as COMPLETED, the key is consumed, and the cost is deducted. The player loses their key and money but may not receive the reward.

The `deliverReward` method can fail silently — command execution exceptions are caught and logged inside `deliverReward`, and the inventory overflow just drops items on the ground. While item overflow has graceful handling, command execution failures are logged but the opening is still considered successful.

---

## Info

### IN-01: Hardcoded Audit Retention and Cleanup Intervals

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/CrateManager.java:25-26`
**Severity:** INFO

**Issue:** `AUDIT_RETENTION_DAYS = 30` and `CLEANUP_INTERVAL_HOURS = 6` are hardcoded constants. A reasonable default is fine, but the cleanup interval of 6 hours means audit data could accumulate significantly between cleanups. The `getAuditRetentionDays()` method is accessible via `crateManager`, suggesting config-driven values might be intended.

---

### IN-02: HMAC `reload()` Only Reloads Key Repo

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateKeyService.java:319-323`
**Severity:** INFO

**Issue:** The `reload()` method only reloads the key definitions from JSON, but does NOT reload the HMAC secret. If the secret file is manually updated by an administrator, the change won't take effect until server restart. This may be intentional (affects all physical keys), but should be documented.

---

### IN-03: Empty `reload()` Methods Across Multiple Services

**File:** `CrateOpeningService.java:263`, `RewardService.java:256`, `CrateMetricsService.java:83`, `CrateAuditService.java:153`
**Severity:** INFO

**Issue:** Several service classes have empty `reload()` methods that do nothing but are called during the reload cycle. These should either be removed, or the underlying repositories should actually be reloaded.

---

### IN-04: `logOpening` Method in CrateAuditService Called But Never Used

**File:** `common/src/main/java/com/pedrodalben/bigbangessentials/crates/service/CrateAuditService.java:56-75`
**Severity:** INFO

**Issue:** The `logOpening` method is defined but never called anywhere in the reviewed code. The crate opening flow uses `createPendingAudit` + `completeAudit` instead. This is dead code that should be removed to avoid confusion.

---

_Reviewed: 2026-06-29T12:00:00Z_
_Reviewer: gsd-code-reviewer (deep analysis)_
_Depth: deep_
