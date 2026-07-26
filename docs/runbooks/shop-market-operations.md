# Shop and Market Operations Runbook

## Health

Use `/bbe database status` and the module health commands:

```text
/pokemarket admin health
/pokemarket admin health full
/adminshop audit inspect <transaction-id>
/chestshop list
```

Confirm database readiness, migration version, pending journal rows, recovery
required rows, failed compensations, queue saturation, and the oldest pending
operation before enabling purchases.

## Inspect and retry safely

1. Record the transaction/listing/claim ID and original idempotency key.
2. Inspect the durable row and the economy receipt.
3. Compare the item/Pokémon checkpoint with the money checkpoint.
4. Retry with the original key only after the database is healthy.
5. If the payload differs, stop: it is an idempotency conflict.

Do not run a manual second debit, credit, item delivery, or Pokémon delivery.

## MySQL outage

With `economy.backend=DATABASE` and `database.required=true`, ChestShop,
AdminShop money, and PokéMarket monetary purchase/claims fail closed. No JSON
balance fallback is permitted. Restore connectivity, verify migrations and
queue health, then retry the original durable operation.

## Failed compensation

Leave the operation in `RECOVERY_REQUIRED` or `COMPENSATION_FAILED`, preserve
the logs and SQL row, and inspect both financial keys. Use a reviewed
compensating operation with a new administrative key only after confirming the
original result. Arbitrary command-backed rewards cannot be automatically
reversed and require manual reconciliation.

## Backups and migrations

Back up the database, `world/serverconfig/bigbangessentials/`, and the data
directory before deployment. Apply migrations through the normal migration
manager; V028 adds `bbe_chestshop_operations` with indexes for pending and shop
lookups. Do not delete legacy JSON journals; inspect/import them explicitly.

## Rollback

Stop commerce writes, snapshot the current database and logs, deploy the prior
code, and keep all new journal rows. Do not overwrite database balances or
claims with JSON files. Reconcile balances, operation statuses, listings,
shops, and claims before reopening commerce.
