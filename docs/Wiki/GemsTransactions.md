# Gems Ledger and Idempotency Reference

> **Current database backend:** The runtime source of truth is `bbe_gem_operations` plus `bbe_gem_accounts`/`bbe_gem_reservations`. The JSON state/ledger details below describe the explicit legacy file backend and migration input only; they are not a fallback when MySQL is configured.

This page describes the transaction logging schema and the idempotency mechanisms that guarantee safety across restarts and crash recoveries.

## Transaction Log Schema (`gems_transactions.jsonl`)

The ledger log at `bigbangessentials/gems_transactions.jsonl` is written in JSON Lines format. Each line is an independent JSON object:

```json
{
  "transactionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "timestamp": 1782782400000,
  "type": "RESERVATION_CREATED",
  "playerUuid": "069a7d2b-aa7b-4bcf-90f7-0e6fb1c49a56",
  "amount": 30,
  "totalBefore": 100,
  "totalAfter": 100,
  "heldBefore": 0,
  "heldAfter": 30,
  "availableBefore": 100,
  "availableAfter": 70,
  "actorUuid": null,
  "source": "bigbangregions",
  "purpose": "PLAYER_REGION_RESIZE",
  "reservationId": "8fa85f64-5717-4562-b3fc-2c963f66afa6",
  "idempotencyKey": "bigbangregions:resize:12:3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "externalReference": "region-ref-12",
  "metadata": {}
}
```

### Transaction Types (`GemTransactionType`)
- **CREDIT:** Direct wallet credit.
- **DEBIT:** Direct wallet debit.
- **SET_BALANCE:** Administrator command forced balance reset.
- **RESERVATION_CREATED:** A new balance reservation was locked.
- **RESERVATION_CAPTURED:** Reserved balance was successfully deducted.
- **RESERVATION_RELEASED:** Reserved balance was cancelled and restored.
- **RESERVATION_EXPIRED:** A reservation lease timed out and funds were automatically restored.
- **RESERVATION_RENEWED:** A reservation lease was extended via a renewal request.

---

## Idempotency Engine

Idempotency guarantees that duplicate requests (due to retries, network glitches, or crashes) do not cause duplicate charges.

- **Idempotency Keys:** Clients supply an arbitrary string key (e.g. `bigbangregions:resize:<regionId>:<operationId>`).
- **Conflict Checking:** If a key is repeated, the manager inspects the registered payload:
  - If the player UUID, amount, operation type, and reservation ID match, the original result is returned directly without repeating the mutation.
  - If any payload field differs, the operation fails immediately with `IDEMPOTENCY_CONFLICT`.
- **In-Memory Registry:** An in-memory cache (`ConcurrentHashMap`) tracks active/recent keys to provide sub-millisecond response times. On boot, the registry is re-populated from three sources.

### Persisted Idempotency (`idempotencyRecords`)

Idempotency records are **persisted in `gems_state.json`** under the `idempotencyRecords` key. This is the critical guarantee that makes idempotency survive server crashes:

```json
{
  "idempotencyRecords": {
    "bigbangregions:resize:region-123:op-456:capture": {
      "transactionId": "uuid",
      "operationType": "CAPTURE",
      "requestFingerprint": "sha256-hex",
      "playerUuid": "uuid",
      "amount": 50,
      "reservationId": "uuid",
      "resultStatus": "SUCCESS",
      "createdAt": 0
    }
  }
}
```

Before every mutation (credit, debit, reserve, capture, **release**, **renew**), the system:
1. Computes a SHA-256 `requestFingerprint` of the full request payload (including idempotencyKey, source, purpose, externalReference, metadata, etc.).
2. Adds an `IdempotencyPersistedRecord` to `nextState.idempotencyRecords`.
3. Saves the state to disk.

This means the idempotency record is on disk **before** the ledger append. Even if the server crashes between `saveState` and `appendTransaction`, the next request with the same key will find the persisted record via `checkIdempotencyWithStateFallback()`.

### Idempotency for Release and Renew

Both `release()` and `renew()` now support `idempotencyKey`-based idempotency:

- **Release (`GemReleaseRequest`):** Includes `idempotencyKey`, `source`, `purpose`, `externalReference`, `metadata`. The first call releases the reservation. A retry with the same key and same `reservationId` returns `idempotent_success`. A retry with the same key but different `reservationId` returns `IDEMPOTENCY_CONFLICT`. The record is persisted in `gems_state.json`.
- **Renew (`GemRenewRequest`):** Includes `idempotencyKey`, `lease`, `source`, `purpose`, `externalReference`, `metadata`. The first call extends the lease. A retry with the same key returns `idempotent_success` without double-extending.

### Boot Recovery — Idempotency Registry Reconstruction

On server boot, `loadIdempotencyFromLedger()` reconstructs the in-memory registry from three sources in order:

1. **State records (highest priority):** Loads all entries from `currentState.idempotencyRecords`. These are the most authoritative because they were persisted atomically with the state.
2. **Ledger records:** Scans `gems_transactions.jsonl` for lines with an `idempotencyKey` field. Skips keys already loaded from state. Maps ledger types (`RESERVATION_CREATED` → `RESERVE`, `RESERVATION_RELEASED` → `RELEASE`, `RESERVATION_RENEWED` → `RENEW`, etc.).
3. **Active reservations:** Registers any `ACTIVE` reservation that has an `idempotencyKey` not already in the registry.

---

## Pending Audit Entries for Ledger Recovery

The `pendingAuditEntries` mechanism in `gems_state.json` ensures no audit trail is lost when the ledger append fails.

### Mutation Flow with Pending Audit

```
1. Clone state (Copy-on-Write)
2. Apply mutation to nextState (balance + reservation changes)
3. Add IdempotencyPersistedRecord to nextState.idempotencyRecords
4. Add PendingAuditEntry to nextState.pendingAuditEntries
5. saveState(nextState) — atomic write (tmp + atomic move)
6. currentState = nextState (reference swap)
7. Append transaction to gems_transactions.jsonl
8. If append succeeds:
   → reconcilePendingAuditEntry (removes from state, saves clean state)
9. If append fails:
   → PendingAuditEntry remains in state
   → Error is logged
   → Next recover() reconciles the pending entry
```

### Pending Audit Entry Schema

```json
{
  "pendingAuditEntries": [
    {
      "transactionId": "uuid",
      "revision": 182,
      "type": "RESERVATION_CAPTURED",
      "playerUuid": "uuid",
      "reservationId": "uuid",
      "createdAt": 0,
      "reconciled": false
    }
  ]
}
```

### Recovery Reconciliation

During `recover()` on boot:
1. Scan `state.pendingAuditEntries` for entries where `reconciled == false`.
2. For each unreconciled entry, append a `RECONCILIATION` transaction to the ledger with the original type, player UUID, and reservation ID.
3. Mark the entry as `reconciled = true`.
4. Any remaining unreconciled entries stay in `state.pendingAuditEntries` for the next boot.

This guarantees that every mutation eventually produces a corresponding ledger entry, even after crashes.
