# Economy operations runbook

## Before deployment

1. Back up the database and `bigbangessentials/` data directory.
2. Set `economy.backend` to `DATABASE` and configure MySQL credentials through `database.json`/environment variables.
3. Check that `database.required` is true for production.
4. Run the dry run for any legacy file and record its checksum and total.
5. Deploy during a maintenance window; migrations V025–V027 are applied before the database is marked ready.

## Health and pressure

Run `/bbe database status` and watch:

- `State`, `Connected`, and schema version;
- queued tasks and peak queue;
- active transactions and transaction retries;
- rejected tasks;
- average queue, connection, SQL, and commit times;
- pool active/idle/total connections.

An increasing queue or rejected-task count means backpressure is active. Do not raise the queue without measuring pool capacity and database latency. The queue is bounded by design; callers must handle the exceptional future rather than wait indefinitely.

## Money reconciliation

`/bbe economy reconcile` is read-only. Investigate any non-zero:

- pending/non-terminal operation;
- operation without an account;
- negative account;
- duplicate idempotency key;
- unexpected account count or total.

For a request timeout, query the durable operation using its exact idempotency key before retrying. If it exists, use its receipt. If it does not exist, retry the same key once the database is healthy. Never create a new key to “make it work.”

## Gems reconciliation

Run `/gems admin verify`. Expected output is zero negative accounts and zero pending operations. Compare each account's `held_minor` with the sum of active reservations. Use `/gems admin repair confirm` only for safe expiry cleanup; it does not guess or overwrite a balance.

For a stuck reservation, inspect it first. Capture/release with the original key. An expired reservation must not be captured; the expiry operation is the audit record.

## Legacy Gems import

```text
/gems admin migrate-json --dry-run
/gems admin migrate-json --execute confirm
```

The dry run reports accounts, reservations, invalid rows, total balance, and SHA-256. Execute creates a timestamped backup, imports valid rows in one transaction, records the checksum in `bbe_gem_data_migrations`, and keeps the source file. `COMPLETED` on a repeated checksum is safe and does not duplicate accounts/reservations/operations. `RECONCILIATION_REQUIRED` means stop and resolve the listed conflicts.

## Rollback

Rollback is a deployment rollback, not a live JSON restore:

1. stop writes / enter maintenance mode;
2. snapshot the current database and logs;
3. restore the database snapshot or apply a reviewed compensating operation with a new admin key;
4. keep the legacy JSON and migration backup untouched;
5. verify account totals, held reservations, operation status, and idempotency keys;
6. restart and run the read-only reconciliation commands.

Do not copy `balances.json` or `gems_state.json` over live database state. That bypasses receipts and can double-credit or lose reservations.

## Incident triage

| Symptom | First action | Safe response |
|---|---|---|
| Queue full | Capture status metrics and database latency | Let callers receive backpressure; repair capacity before retrying same keys |
| MySQL deadlocks | Check transaction retries and lock contention | Executor retries bounded deadlock/timeout cases; investigate if retries persist |
| Operation timeout | Look up idempotency key | Reuse the durable receipt or retry the same key |
| Negative/held mismatch | Stop automated repair and snapshot | Run verify/reconcile; use reviewed compensating operations |
| Database unavailable | Check `DatabaseManager` state and credentials | Production MySQL Gems/money fail closed; do not enable JSON fallback |
| Migration conflict | Preserve source/checksum/backup | Resolve account/reservation differences, then run the migration deliberately |
