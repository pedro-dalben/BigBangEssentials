# Changelog

All notable changes to the BigBangEssentials project will be documented in this file.

## [1.0.2.7] - 2026-06-28

### Added (P0 Remediation)
- **12/12 Crash failpoints:** Extended `GemsPersistenceFailpoint` to 12 constants covering the full mutation lifecycle (BEFORE_AFTER_WRITE_TEMP, BEFORE_AFTER_ATOMIC_MOVE, BEFORE_AFTER_CACHE_SWAP, BEFORE_AFTER_APPEND_LEDGER, BEFORE_AFTER_IDEMPOTENCY_REGISTRY_UPDATE, BEFORE_AFTER_EVENT_PUBLISH).
- **Comprehensive crash injection tests:** `GemCrashInjectionTest` now tests all 12 failpoints with full restart+recovery verification. No gem loss or duplication detected in any scenario.
- **All 12 concurrency scenarios:** `GemReservationConcurrencyTest` expanded from 4 to 12 scenarios, including concurrent reserve+debit, reserve+admin take, renew+expire, shutdown+reserve, shutdown+capture, and idempotency key collision in parallel.
- **IdempotencyRecords persisted in GemsState:** Every mutation now writes an `IdempotencyPersistedRecord` to `nextState.idempotencyRecords` before disk save. Idempotency survives crashes even without ledger.
- **PendingAuditEntries for ledger recovery:** Every mutation records a `PendingAuditEntry` in state before disk save. If ledger append fails, the next boot's `recover()` reconciles the pending entry.
- **`checkIdempotencyWithStateFallback()`:** New method checks in-memory registry first, then falls back to persisted `idempotencyRecords` in `currentState`. Release and renew now use it.
- **`GemReleaseRequest.idempotencyKey`:** Added `idempotencyKey`, `source`, `purpose`, `externalReference`, `metadata` fields.
- **`GemRenewRequest.idempotencyKey`:** Added `idempotencyKey`, `lease`, `source`, `purpose`, `externalReference`, `metadata` fields.
- **Admin reset uses config.startingBalance:** `executeAdminReset` now reads `getConfig().balances.startingBalance` instead of hardcoded `fallbackStarting = 0`.
- **Command execution tests:** `GemBalanceServiceTest` expanded with 10 new tests exercising real command logic via `GemsManager`.
- **Total tests:** 148 (17 new).

### Changed
- **Mutation order updated:** All operations now write `idempotencyRecords` and `pendingAuditEntries` to state before disk save. Ledger append is moved after cache swap.
- **Recovery protocol enhanced:** Boot recovery now reconciles `pendingAuditEntries`, rebuilds idempotency registry from 3 sources (state → ledger → active reservations).
- **Release and Renew are now fully idempotent by key** in addition to existing status-based idempotency.

### Fixed
- Idempotency loss on crash between state save and ledger append (A12 HIGH).
- Release without idempotencyKey preventing safe crash retry (A6 MEDIUM).
- Renew without idempotencyKey preventing safe crash retry (A8 MEDIUM → promoted to MEDIUM in P0).
- Insufficient crash injection coverage (A4, A5 HIGH).
- Incomplete concurrency test coverage (A7 MEDIUM).
- Admin reset ignoring config.startingBalance (A9 MEDIUM).

## [1.0.2.6] - 2026-06-27

### Added
- **Gems Wallet Crash Injection framework:** Injected `checkFailpoint(...)` hooks throughout transaction phases.
- **GemCrashInjectionTest:** Integrated a suite of JUnit tests utilizing failpoints to assert correct recovery behaviors.
- **Gems Concurrency validation:** Expanded `GemReservationConcurrencyTest` to verify concurrent capture/release, concurrent multiple captures, and concurrent multiple releases.

### Changed
- **Copy-on-Write (CoW) Persistence Pattern:** Refactored state mutations in `GemsManager` to utilize a Copy-on-Write clone-and-swap pattern, eliminating any possibility of memory/disk cache divergence during write failures.
- **Brigadier Admin Commands safety:** Required explicit literal `"confirm"` suffix on manual release commands to prevent accidental operator clicks.
- **Idempotency Key Conflict handling:** Strict payload validation check added for duplicate idempotency key requests, returning `IDEMPOTENCY_CONFLICT` on mismatched payloads.

### Fixed
- Cache/Disk state divergence in Gems Manager transactions.
- Lack of confirmation for manual reservation release.
- Missing parameter count on concurrent release requests.
