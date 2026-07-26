# Economy foundation audit

Date: 2026-07-26
Branch: `audit/economy-foundation`

## Scope

This audit covers the money and Gems foundations: public APIs, managers, database executor, migrations, persistence, idempotency, transfers, reservations, recovery, metrics, and operational commands. Shop, ChestShop, AdminShop, Market, PokeMarket, jobs, rankup, crates, prices, items, and GUIs were intentionally left outside the implementation scope.

## Findings and fixes

| Finding | Root cause | Resolution |
|---|---|---|
| Money could be read as `double` before validation | Configuration and compatibility methods converted too early | Decimal config access is authoritative; `Money` converts to checked minor units only at the persistence boundary. |
| Concurrent credit/debit could use a stale balance | Read, calculate, and write were not one locked transaction | Account row lock, exact minor-unit arithmetic, versioned update, and operation journal now commit together. |
| A retried request could be applied twice or with changed arguments | Idempotency lookup was not protected by a durable fingerprint | Unique operation key plus SHA-256 payload fingerprint; identical retries return the original receipt and changed payloads return `IDEMPOTENCY_CONFLICT`. |
| MySQL same-key account creation could deadlock | Every mutation attempted an insert-ignore against an existing account before locking it | Existing accounts are checked first; the executor also retries MySQL deadlocks/lock timeouts with a bounded three-attempt policy. |
| Crossed transfers could deadlock | Sender and receiver locks had no global order | Transfers lock UUIDs in deterministic order and update both accounts in one transaction. |
| Gems reservations oversubscribed under concurrency | Availability was calculated from an aggregate reservation query | Migration V026 adds `held_minor`; reservation uses an atomic conditional increment, and capture/release/expiry decrement it in the same transaction. |
| Gems expiry had state transition without a durable receipt | Cleanup changed reservation status only | Expiry now writes `RESERVATION_EXPIRED`, held-balance adjustment, and operation receipt atomically, then publishes the event after commit. |
| Gems JSON was still authoritative in the old path | File state and SQL state had no explicit cutover | SQL tables V025/V026 are the database source of truth; JSON is migration input only. Migration is checksum-keyed, backed up, repeatable, and never deletes the source. |
| Queue shutdown could leave callers waiting | Queued futures were not all tracked | Executor tracks in-flight futures, rejects after shutdown, exposes bounded queue rejection, and completes remaining futures exceptionally. |
| Operational latency was opaque | Only total execution time was recorded | Metrics now include queue wait, connection wait, SQL time, commit time, peak queue, and transaction retries; `/bbe database status` displays them. |
| Journal precision was fixed at two decimals | V018 used `DECIMAL(19,2)` while currency scale allows 0–18 | V027 expands MySQL journal amount/before/after columns to `DECIMAL(38,18)`; account arithmetic remains checked `BIGINT` minor units. |

## Durable model

Money uses `bbe_economy_accounts` as the balance source of truth and `bbe_economy_operations` as the durable operation journal. A successful mutation is only reported after the account update and receipt transition from `PENDING` to `COMPLETED` commit. Rejected requests also receive a durable receipt where a transaction reached the database.

Gems uses:

- `bbe_gem_accounts`: total balance, held balance, and version;
- `bbe_gem_reservations`: active/captured/released/expired leases;
- `bbe_gem_operations`: durable idempotency keys, fingerprints, before/after values, and history;
- `bbe_gem_data_migrations`: checksum and reconciliation record for the legacy JSON import.

Every balance mutation is bounded by `Math.addExact`/`Math.subtractExact`; database constraints reject negative balances and non-positive reservation amounts. Events are published only after commit, so listeners cannot observe an operation that later rolls back.

## API compatibility

Existing boolean and synchronous methods remain available for old modules. New money integrations should use `EconomyAPI.depositAsync`, `withdrawAsync`, and `payPlayerAsync`, or the structured `DatabaseEconomyService` methods. The structured receipt contains operation id, status, currency, player, amount, before/after balances, key, fingerprint, error, replay marker, timestamp, source, and external reference.

Gems retains the synchronous `GemsService` methods and adds asynchronous integration methods. Database-backed Gems never falls back to JSON when MySQL is configured but unavailable; callers receive the database failure instead.

## Verification

The controlled MySQL test uses Testcontainers or the configured `BBE_TEST_MYSQL_*` connection and verifies:

- 100 concurrent money credits, same-key replay, fingerprint conflict, and final balance;
- 20 crossed atomic transfers with deterministic lock order;
- 100 concurrent Gems debits with no negative balance;
- 20 concurrent 50-gem reservations against a 900-gem available balance, with exactly 18 successes;
- capture replay, expiry history, restart persistence, and JSON migration dry-run/execute/repeat;
- a 100-operation money benchmark printed by the test.

Executed results on 2026-07-26:

```text
./gradlew :common:test --no-daemon       887 tests, 0 failures
./gradlew :common:mysqlIntegrationTest  1 test, 0 failures
mysql-economy-benchmark                 100 ops, 444 ms (≈225 ops/s)
```

`./gradlew build --no-daemon` also passed, including the common, Fabric, and NeoForge packaging tasks.

## Known boundaries

The legacy synchronous methods still call `join()` for source compatibility; they must not be called from a server-thread hot path when the database backend is active. The async boundaries are the supported integration route. Multi-resource flows that also change items or external plugin state remain sagas and are not silently claimed to be one database transaction.
