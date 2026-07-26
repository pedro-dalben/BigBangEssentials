# Shop and Market Production Hardening Audit

Date: 2026-07-26
Repository: `https://github.com/pedro-dalben/BigBangEssentials`
Baseline: `6decb2490f2d1c6a050fb506e8b14478974f705f` (`origin/master`, unchanged before work)

## Scope and baseline

This audit covers AdminShop (`/shop`), ChestShop (`/chestshop`, `/cshop`),
PokéMarket (`/pokemarket`, `/gts`, `/pm`), their economy calls, SQL journals,
claims, recovery, menus, commands, and permissions. Jobs, Rankup, and Crates
production code are outside the scope and were not changed.

Baseline toolchain: Java 21.0.10, Gradle 8.14.3. The focused Common tests and
the complete Common/Fabric/NeoForge build passed before the feature branch was
created. The working branch is `audit/shop-market-hardening`.

## Entry points and state maps

| Module | Entry points | Financial boundary | Durable state |
|---|---|---|---|
| AdminShop | `/shop`, `/cash`, `/gemas shop`, menu actions | `EconomyManager.debitAsync`/`creditAsync`; Gems reservation/capture | AdminShop audit/state tables |
| ChestShop | sign right-click/left-click, `/chestshop`, `/cshop` | `commercialTransferAsync` for player shops; debit/credit for admin shops | `bbe_chestshop_operations` |
| PokéMarket | commands, aliases, menu integration | Existing JDBC purchase transaction and claim service | listings, purchase/trade operations, claims, escrow, notifications |

ChestShop legal operation transitions are:

```text
CREATED -> MONEY_COMPLETED -> ITEMS_APPLIED -> COMPLETED
   |            |                 |
   +------------+-----------------+-> ROLLED_BACK
                                  \-> RECOVERY_REQUIRED
```

The SQL journal stores the transaction identity, shop coordinates, participant,
owner, exact amount, quantity, item snapshot, financial and compensation keys,
checkpoints, retry count, error, and version. A MySQL deployment uses SQL as the
authoritative journal; JSON is retained only for non-database compatibility.

## Reproduced root causes

### Release-blocking ChestShop settlement defect

The old path performed `withdraw buyer`, item movement, and `deposit owner` as
independent synchronous calls. The owner credit could fail after the buyer had
paid and received the item. The same design existed in the sell direction with
owner debit and seller credit. It also blocked the server thread through legacy
manager wrappers and persisted only a best-effort JSON journal.

Severity: P0 money/item integrity.

Correction: a player-owned operation now uses one fee-free,
idempotent `commercialTransferAsync` operation, and the item checkpoint and
compensation are recorded in the durable ChestShop journal. Offline or missing
owner accounts are handled by the database account transaction. Maximum-balance,
insufficient-funds, database-unavailable, replay, and conflict results remain
typed instead of becoming `boolean`.

### Other findings

| Finding | Severity | Correction |
|---|---:|---|
| AdminShop menu invoked the blocking transaction path | P1 | Active GUI path is a server-thread/world + async SQL/economy saga |
| PokéMarket instantiated JSON economy when DATABASE was unavailable | P0 | Initialization, purchase, refund, and money claim now fail closed |
| PokéMarket pricing used a fixed two-decimal assumption | P1 | Gross, tax, and net use configured scale and rounding |
| Commerce permissions were used in code/docs but missing from the explicit registry | P1 | ChestShop, AdminShop, and PokéMarket nodes are registered and documented |
| `/shop` was described as a ChestShop alias | P2 | Command comment/documentation corrected; `/shop` remains AdminShop |
| Admin inspection and PokéMarket stats used blocking joins | P1 | Asynchronous query continuations and server-thread message dispatch added |

## Implemented corrections

* Added `CommercialTransferReceipt` and `CommercialTransferStatus`.
* Added atomic commerce transfer in `DatabaseEconomyService`, with deterministic
  account locking, one SQL transaction, exact configured currency scale,
  idempotency fingerprinting, replay, conflict, maximum-balance, unavailable,
  saturation, and technical-failure outcomes.
* Added the `bbe_chestshop_operations` migration (V028) and SQL-backed journal.
* Routed active ChestShop sign interactions through asynchronous durable BUY/SELL
  orchestration and strict item insertion/removal checks.
* Added restart/recovery checkpoints and reverse commercial-transfer keys.
* Routed active AdminShop GUI actions through an asynchronous saga. Arbitrary
  command products require `{transaction}` and failures remain auditable as
  reconciliation-required because command side effects are not inherently
  compensable.
* Removed DATABASE-to-JSON fallback from PokéMarket money purchase, refund, and
  claim paths and made module initialization require a ready database.
* Registered explicit commerce permissions and aligned `/shop`, `/chestshop`,
  `/cshop`, `/pokemarket`, `/gts`, and `/pm` documentation.

## Test evidence

Final Common result: 892 tests, 0 failures, 0 errors, 0 skipped. The dedicated
`CommercialTransferTest` proves exact buyer/owner settlement, single replay,
payload conflict, concurrent independent transfers, and concurrent replay of
one key. `CommercialTransferLoadTest` ran 40 concurrent transfers and conserved
the owner balance exactly; the measured SQLite run was P50 3,644 µs, P95 4,528
µs, and P99 4,622 µs. The focused commerce/concurrency tests were rerun with
`--rerun-tasks` and passed.

`MySqlIntegrationTest` passed with Testcontainers, including the migration and
database locking/transaction suite. The final `./gradlew build --no-daemon`
passed Common, Fabric, and NeoForge. No Jobs, Rankup, or Crates production
files are present in the final diff.

## Recovery and operations

Administrators must inspect the transaction ID and original idempotency key
before retrying. A retry uses the same key; a different payload with that key is
an idempotency conflict. Recovery never treats a failed compensation as a
successful rollback. See `docs/runbooks/shop-market-operations.md`.

## Remaining risks

* A command-backed AdminShop reward is only as retry-safe as the command itself;
  a failed or ambiguous command remains `RECONCILIATION_REQUIRED`.
* Legacy synchronous methods remain for source compatibility, but no active
  Shop/AdminShop GUI path uses them. Callers outside these modules must migrate
  to structured async receipts.
* The load numbers are a bounded SQLite development measurement, not a capacity
  promise for production MySQL; run the same scenario against the production
  pool size before capacity changes.
