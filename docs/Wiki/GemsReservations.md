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
Before a reservation expires, a caller can invoke `renew(GemRenewRequest)` to extend the lease. This is useful for long-running operations.

---

## Automatic Cleanup Task

An asynchronous scheduler runs every **60 seconds** to scan and expire `ACTIVE` reservations.
- Expired reservations are transitioned to `EXPIRED`.
- The corresponding balance is released in memory.
- An authoritative state update is persisted síncronamente (Copy-on-Write).
- An audit log entry (`RESERVATION_EXPIRED`) is appended to `gems_transactions.jsonl`.
