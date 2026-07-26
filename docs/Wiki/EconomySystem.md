# Economy System

## Source of truth

The configured default is `backend: DATABASE`. Money is represented as exact `BigDecimal` at the API boundary and checked `long` minor units in `bbe_economy_accounts.balance_minor`. The durable journal is `bbe_economy_operations`; `balances.json` is legacy migration input only when the database backend is selected.

`currency.scale` is centralized in `economy.json` and supports 0–18 decimal places. `currency.rounding-mode` is applied once at the boundary. Existing Vault/double methods remain compatibility wrappers; new integrations should use structured async calls.

## Configuration

| Key | Default | Meaning |
|---|---:|---|
| `backend` | `DATABASE` | `DATABASE` or explicit legacy `JSON` mode |
| `startingBalance` | `100.0` | Balance for a newly created database account |
| `currency.scale` | `2` | Minor-unit scale |
| `currency.rounding-mode` | `HALF_UP` | Boundary rounding mode |
| `maxBalance` | `999999999.99` | Maximum account balance |
| `allowNegativeBalances` | `false` | Must remain false for normal economy safety |
| `taxPercentage` | `0.0` | `/pay` tax percentage |

Database connection, pool, executor, queue, migration, and debug settings live in `database.json`. SQLite is deliberately forced to one connection and one executor thread; MySQL uses the configured pool and bounded executor.

## Transaction contract

Every database credit, debit, set, and transfer follows:

1. validate exact amount and key;
2. lock account rows;
3. check the durable operation key and fingerprint;
4. insert a `PENDING` journal row;
5. update account(s) using checked minor-unit arithmetic;
6. mark the receipt `COMPLETED` or `REJECTED`;
7. commit once and only then return success.

The same idempotency key with the same payload returns the original operation id and marks the receipt as `idempotentReplay`. Reusing a key with different player, amount, type, source, reference, or metadata returns `IDEMPOTENCY_CONFLICT` and changes no balance.

Transfers lock both UUIDs in lexical order and update sender and receiver in one transaction. The fee is removed from the transfer; the structured receipt describes the sender-side balance transition and includes receiver/fee metadata.

## Public API

```java
EconomyAPI.depositAsync(player, amount, key, reason, metadata);
EconomyAPI.withdrawAsync(player, amount, key, reason, metadata);
EconomyAPI.payPlayerAsync(sender, receiver, amount, key);
```

Each returns `CompletableFuture<EconomyOperationReceipt>`. The receipt includes operation id, status, currency, player, amount, balances before/after, idempotency key, fingerprint, error, replay flag, timestamp, source module, and external reference.

The old `deposit`, `withdraw`, boolean Vault methods, and synchronous manager methods remain for compatibility. They may block on JDBC and should not be used from a server-thread hot path.

## Administration

| Command | Purpose |
|---|---|
| `/bbe economy status` | Shows configured backend and database state |
| `/bbe economy migrate-json --dry-run` | Validates legacy balances and prints checksum/total/conflicts |
| `/bbe economy migrate-json --execute --confirm` | Backs up and imports valid rows transactionally |
| `/bbe economy reconcile` | Read-only pending/orphan/negative/idempotency checks |
| `/bbe database status` | Pool, queue, transaction, SQL, commit, and retry metrics |

Migration is keyed by the source SHA-256. The original JSON is retained, the backup is never auto-deleted, and a conflicting or partial import reports `RECONCILIATION_REQUIRED` instead of guessing.

## Files and tables

| Resource | Role |
|---|---|
| `bigbangessentials/balances.json` | Legacy import/rollback artifact, not active in `DATABASE` mode |
| `bbe_economy_accounts` | Current minor-unit balances |
| `bbe_economy_operations` | Durable receipts, idempotency, fingerprints, before/after values |
| `bbe_economy_data_migrations` | Legacy money migration records |

Migrations V018–V024 establish the money journal and fingerprint. V027 expands MySQL journal decimals to `DECIMAL(38,18)` so the journal does not truncate a configured scale.
