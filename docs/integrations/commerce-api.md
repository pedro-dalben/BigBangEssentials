# Commerce API

Commerce modules must use structured asynchronous APIs. They must not mutate
economy tables directly, use `/pay` policy for internal sales, or treat a
boolean/exception-free callback as proof of settlement.

## Atomic player transfer

```java
CompletableFuture<CommercialTransferReceipt> result =
    EconomyManager.getInstance().commercialTransferAsync(
        buyer, owner, price, "chestshop:buy:" + transactionId, "chestshop");
```

This boundary debits the payer and credits the recipient in one SQL transaction.
It has no `/pay` toggle, cooldown, confirmation, or player-payment tax. The
recipient may be offline or have no existing account; account creation follows
the configured economy starting-balance policy. A maximum-balance rejection
changes neither account.

Use the same key after a timeout or restart. The same payload returns
`IDEMPOTENT_REPLAY`; a different payload returns `IDEMPOTENCY_CONFLICT`.

## ChestShop orchestration

```text
server-thread validation/reservation
 -> durable CREATED journal row
 -> async commercial transfer or admin debit/credit
 -> server-thread chest/inventory mutation
 -> durable checkpoint
 -> COMPLETED, or reverse transfer + ROLLED_BACK/RECOVERY_REQUIRED
```

Money success is not item success. The operation is only reported as complete
after the item checkpoint and final journal update commit.

## AdminShop money and Gems

Money uses `EconomyManager.debitAsync`/`creditAsync` with
`adminshop:<buy|sell>:<transaction-id>`. Gems use reservation/capture for buys
and idempotent credit for sells. The menu callback returns a completion stage;
inventory and command execution are scheduled on the server thread.

## PokéMarket purchase and claims

The monetary purchase keeps its existing JDBC transaction: listing reservation,
buyer debit, claims, SOLD transition, and audit are committed together. A money
claim is processed once, credits through the database economy service, and only
then becomes `CLAIMED`. A Pokémon claim verifies its payload/checksum and storage
capacity before completion. A retry reuses the claim/operation identity.

## Results and recovery

Commerce callers must distinguish `COMPLETED`, `INSUFFICIENT_FUNDS`,
`MAXIMUM_BALANCE`, `INVALID_AMOUNT`, `IDEMPOTENT_REPLAY`,
`IDEMPOTENCY_CONFLICT`, `DATABASE_UNAVAILABLE`, `EXECUTOR_SATURATED`,
`TECHNICAL_FAILURE`, and `RECONCILIATION_REQUIRED`.

Never mark a caller record complete before the receipt and durable checkpoint
are confirmed. `RECOVERY_REQUIRED` is an operator-visible state, not a success.
