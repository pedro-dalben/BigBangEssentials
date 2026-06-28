# Gems Reservation Lifecycle and Leases

Gems reservations allow external systems (like `BigBang Regions`) to safely lock player balances before committing changes.

## Reservation Lifecycle

A reservation transitions through four states:

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

- **ACTIVE:** The gems are locked in `heldBalance`. The player cannot spend them.
- **CAPTURED:** The transaction succeeded. Gems are deducted from `totalBalance` and `heldBalance` is reduced.
- **RELEASED:** The transaction was aborted. Locked gems return to the player's available balance.
- **EXPIRED:** The lease duration elapsed without a capture/release. The cleanup task automatically releases the balance back to the player.

---

## Leases and Time Limits

Every reservation specifies an expiration lease:
- **Default Lease Duration:** 900 seconds (15 minutes).
- **Maximum Lease Duration:** 3600 seconds (1 hour).

If a lease is too long, the system caps it to the maximum duration configured in `gems.json`.

### Renewals
Before a reservation expires, a caller can invoke `renew(GemRenewRequest)` to extend the lease. This is useful for long-running operations such as region resizes that take longer than the default 15-minute lease.

The renewal operation now requires an `idempotencyKey` field in `GemRenewRequest`. If the caller retries a renewal with the same `idempotencyKey` (e.g., after a crash), the system detects the duplicate via `checkIdempotencyWithStateFallback()` and returns the original success result without modifying state. This prevents double-renewal and ensures the lease extension is applied exactly once.

### Lease Expiration and Cleanup
An asynchronous cleanup task runs every **60 seconds** (configurable via `gems.json`) and transitions `ACTIVE` reservations past their `expiresAt` timestamp to `EXPIRED`:

1. **Lock acquisition:** Acquires the write lock to serialize with other mutations.
2. **Scan:** Iterates all `ACTIVE` reservations and checks `expiresAt < currentTime`.
3. **Expire:** For each expired reservation, sets status to `EXPIRED`, records the release timestamp.
4. **State persistence:** The expiration is written to `gems_state.json` via the Copy-on-Write flow, with a `PendingAuditEntry` recorded for ledger recovery.
5. **Ledger append:** Appends `RESERVATION_EXPIRED` to `gems_transactions.jsonl`.
6. **Reconciliation:** Removes the pending audit entry if the ledger append succeeds.

If the server crashes during the cleanup task, the next `recover()` on boot will:
- Detect and expire any `ACTIVE` reservations past their expiration time.
- Append the expiration to the ledger.
- Reconcile any pending audit entries left from the interrupted cleanup.

### Idempotency of Release and Renew
Both `release()` and `renew()` now support full `idempotencyKey`-based idempotency:

- **Release:** `GemReleaseRequest` contains `idempotencyKey`, `source`, `purpose`, `externalReference`, and `metadata`. The first call releases the reservation and returns success. A retry with the same key and same `reservationId` returns `idempotent_success` without modifying state. A retry with the same key but a different `reservationId` returns `IDEMPOTENCY_CONFLICT`.
- **Renew:** `GemRenewRequest` contains `idempotencyKey`, `lease`, `source`, `purpose`, `externalReference`, and `metadata`. The first call extends the lease. A retry with the same key and same `reservationId` returns `idempotent_success` without re-extending.

Both operations persist their idempotency records in `gems_state.json` under `idempotencyRecords`, guaranteeing survival across restarts.
