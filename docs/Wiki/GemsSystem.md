# Gems System

Gems are a separate integer currency. They are not Vault money and do not share money accounts, prices, item data, or shop state.

## Backend and model

When the database is ready, SQL is the only active source of truth:

- `bbe_gem_accounts`: `balance_minor`, atomic `held_minor`, version, and timestamps;
- `bbe_gem_reservations`: lease rows with `ACTIVE`, `CAPTURED`, `RELEASED`, or `EXPIRED` status;
- `bbe_gem_operations`: operation receipts, fingerprints, idempotency keys, and history;
- `bbe_gem_data_migrations`: repeatable legacy import records.

`gems.json` remains configuration. `gems_state.json` and `gems_transactions.jsonl` are legacy migration input/audit artifacts, not a silent fallback when MySQL is configured. If configured MySQL is unavailable, Gems operations fail instead of writing JSON.

## Balance invariants

```text
availableBalance = totalBalance - heldBalance
0 <= heldBalance <= totalBalance
totalBalance >= 0
```

Credits/debits use checked integer arithmetic. A reservation uses an atomic SQL conditional increment of `held_minor`, so concurrent requests cannot reserve the same available Gems. Capture decrements both total and held; release and expiry decrement held only. All state changes and operation receipts commit in one transaction.

## Reservation lifecycle

```text
ACTIVE -> CAPTURED
       -> RELEASED
       -> EXPIRED
```

Use reservations for a multi-step action:

1. `reserve` with a durable key;
2. perform the technical action;
3. `capture` on success or `release` on failure;
4. retry every uncertain call with the exact same key and payload.

Expiry is handled by the cleanup task and on balance reads. It writes `RESERVATION_EXPIRED`, adjusts `held_minor`, and publishes `GemReservationExpiredEvent` after commit.

## API and concurrency

`BigBangEssentialsApi.gems()` exposes `GemsService`. Existing synchronous methods remain compatible. The async methods (`getBalanceAsync`, `creditAsync`, `debitAsync`, `reserveAsync`, `captureAsync`, and `releaseAsync`) are the integration boundary for server-thread code; database JDBC work is dispatched away from the caller.

Every request with an idempotency key has a SHA-256 fingerprint. Same key plus same payload returns the original transaction/reservation. Same key plus changed payload returns `IDEMPOTENCY_CONFLICT` and does not mutate the wallet.

## Configuration

| Key | Default | Meaning |
|---|---:|---|
| `enabled` | `true` | Enable Gems |
| `balances.startingBalance` | `0` | New wallet balance |
| `balances.maxBalance` | `Long.MAX_VALUE` | Upper balance bound |
| `reservations.enabled` | `true` | Enable leases |
| `reservations.defaultLeaseSeconds` | `3600` | Default lease |
| `reservations.maxLeaseSeconds` | `86400` | Maximum lease |
| `reservations.cleanupIntervalSeconds` | `300` | Expiry scan interval |

## Administration

| Command | Purpose |
|---|---|
| `/gems admin verify` | Counts accounts, held Gems, pending operations, and negatives |
| `/gems admin repair confirm` | Runs safe expiry repair; it does not invent balances |
| `/gems admin migrate-json --dry-run` | Validates legacy state, checksum, totals, and conflicts |
| `/gems admin migrate-json --execute confirm` | Creates a backup and imports JSON into SQL |

The migration is repeatable by source checksum. The source and backup remain available for rollback/reconciliation.

## Events

`GemBalanceChangedEvent`, `GemReservationCreatedEvent`, `GemReservationCapturedEvent`, `GemReservationReleasedEvent`, and `GemReservationExpiredEvent` are published after the database commit. A listener must treat the transaction id as the deduplication key.
# Cross-mod API

Other mods must use `BigBangEssentialsApi.gemsIntegration()` and the public `api.gems` records. The provider can be `WAITING_FOR_DATABASE` while Fabric server startup is still initializing MySQL; this is normal and must not be converted to a permanent disabled state.

All balance and reservation operations exposed for integrations are asynchronous. Persist the caller's idempotency key before sending a request and reuse it after restart. `ALREADY_CAPTURED` and `ALREADY_RELEASED` are safe replays; `IDEMPOTENCY_CONFLICT` is a manual-reconciliation failure.
