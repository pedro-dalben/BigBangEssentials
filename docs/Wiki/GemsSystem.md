# Gems Wallet System Design

This document details the system design, architectural decisions, and recovery protocols for the Gems wallet system in `BigBangEssentials`.

## Architecture & Decoupling
The Gems system is a completely independent secondary economy, separate from the primary Coins economy.
- **Separate Storage:** Gems does not share files with Coins. It uses `gems.json` for configuration, `gems_state.json` for balances/reservations, and `gems_transactions.jsonl` for its transaction log.
- **No Vault Integration:** Vault is only used for Coins. Gems are not exposed as a Vault currency.
- **Decimal-Free:** All operations use `long` (integers). There are no fractional values, preventing floating-point errors.
- **Decoupled API:** The API provides rich result objects (`GemOperationResult`, `GemReservationResult`) instead of simple booleans.

---

## State & Data Structures

### Balance Model
A player's balance consists of:
- `totalBalance`: The absolute persistent balance of the player.
- `heldBalance`: The sum of all active reservations (Gems reserved for external pending actions).
- `availableBalance`: Calculated as `totalBalance - heldBalance`.

The available balance can never drop below zero.

### Idempotency Persistence
The Gems system persists idempotency records directly in `gems_state.json` under `idempotencyRecords`. Each record contains:
- `transactionId`: UUID of the original transaction.
- `operationType`: The operation performed (CREDIT, DEBIT, RESERVE, CAPTURE, RELEASE, RENEW).
- `requestFingerprint`: SHA-256 hash of the full request payload for conflict detection.
- `playerUuid`, `amount`, `reservationId`: Key operation parameters.
- `resultStatus`: `"SUCCESS"` if the operation completed.
- `createdAt`: Timestamp.

This guarantees that idempotency survives server crashes — even if the ledger append fails after state persistence, the next request with the same `idempotencyKey` will find the persisted record in `currentState.idempotencyRecords` via `checkIdempotencyWithStateFallback()`.

### Pending Audit Entries for Ledger Recovery
The system uses `pendingAuditEntries` in `gems_state.json` to handle the case where state has been persisted but the ledger append failed:
- Before every mutation, a `PendingAuditEntry` is added to `nextState.pendingAuditEntries` with the transaction type, player UUID, revision, and reservation ID.
- After the state is saved and the reference swapped, the ledger append is attempted.
- If the ledger append succeeds, `reconcilePendingAuditEntry()` removes the pending entry from state and saves the clean state.
- If the ledger append fails, the pending entry remains in the state. On next boot, `recover()` scans `pendingAuditEntries` and appends each unreconciled entry to the ledger, ensuring no audit trail is lost.

### Reservation States
A reservation goes through the following lifecycle:
```
       [Created]
           ↓
       +---+---+
       | ACTIVE|
       +---+---+
      /    |    \
     /     |     \
[Capture] [Release] [Expire]
   ↓       ↓         ↓
CAPTURED  RELEASED  EXPIRED
```
- **ACTIVE:** The Gems are reserved (held) from the player's balance.
- **CAPTURED:** The transaction is completed, and the reserved Gems are permanently deducted from `totalBalance` and removed from `heldBalance`.
- **RELEASED:** The transaction is aborted; reserved Gems are returned to the available balance.
- **EXPIRED:** The reservation duration (lease) has passed without capture/release. The cleanup task expires it and returns the Gems to the available balance.

---

## Durability & Copy-on-Write Transaction Model

The Gems system utilizes a **State authoritative + ledger reconciliável** approach.
- **Authoritative State:** `gems_state.json` is the sole source of truth.
- **Audit Log:** `gems_transactions.jsonl` is a chronological append-only log of transactions, used for auditing (it is not a WAL journal).
- **Concurrency Isolation:** Operations utilize a `ReentrantReadWriteLock` to isolate read/write access.

### Persistence Mutation Order (Copy-on-Write)
All modifying operations (credit, debit, setBalance, reserve, capture, release, renew) follow a Copy-on-Write (CoW) workflow to prevent runtime state divergence in the case of disk or process failures:

1. Clone the current state deeply (`currentState.cloneState()`), ensuring reservations, balances, idempotencyRecords, and pendingAuditEntries maps are duplicated.
2. Apply mutations exclusively to the cloned state snapshot (nextState).
3. Add an `IdempotencyPersistedRecord` to `nextState.idempotencyRecords` with the operation fingerprint (SHA-256 of all request fields including idempotencyKey). This guarantees idempotency survives crashes — even if the ledger append fails, the idempotency record is already on disk in the state.
4. Add a `PendingAuditEntry` to `nextState.pendingAuditEntries` recording the transaction for later ledger reconciliation.
5. Save the cloned state synchronously to the temp file `gems_state.json.tmp`.
6. Perform an atomic move (`Files.move`) renaming `gems_state.json.tmp` to `gems_state.json`.
7. Only after the disk write succeeds:
   - Swap the in-memory reference: `currentState = nextState`.
   - Append the transaction to the audit log (`gems_transactions.jsonl`).
   - If the ledger append succeeds: reconcile the pending audit entry (remove it from state and save the clean state).
   - If the ledger append fails: the `PendingAuditEntry` remains in the state; the next `recover()` will reconcile it.
   - Add the record to the in-memory idempotency registry.
   - Fire domain life-cycle events.

---

## Recovery Protocol on Server Boot
During system boot, `BigBangEssentials` executes the recovery protocol:

1. Load `gems_state.json`.
2. Validate `schemaVersion == 1` and file integrity.
3. Scan all reservations: if `ACTIVE` and `expiresAt < currentTime`, transition to `EXPIRED`, append `RESERVATION_EXPIRED` to the transaction log, and restore the available balance.
4. Recalculate `heldBalance` for each player from non-expired `ACTIVE` reservations only.
5. Validate invariants: no negative balances, `heldBalance <= totalBalance` for every player.
6. If any invariant is violated, set `dataIntegrityError = true` (blocks all mutations until repair).
7. **Reconcile pending audit entries:** For each `PendingAuditEntry` in `state.pendingAuditEntries` that is not yet reconciled, append the corresponding transaction to the ledger. This ensures no audit entries are lost when the ledger append failed after state persistence.
8. If the state was modified (expired reservations or reconciled entries), save the cleaned-up state to `gems_state.json`.
9. **Rebuild the idempotency registry** (`loadIdempotencyFromLedger`):
   - First, load persisted `IdempotencyPersistedRecord` entries from `currentState.idempotencyRecords`.
   - Then, scan `gems_transactions.jsonl` for `idempotencyKey` fields (state records take precedence).
   - Finally, register all `ACTIVE` reservations that have an `idempotencyKey` from `currentState.reservations`.
10. Log a recovery report detailing loaded balances, active reservations, expired cleanups, reconciled entries, and idempotency keys loaded.
