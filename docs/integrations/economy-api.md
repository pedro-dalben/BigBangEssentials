# Economy API contract

This is the public integration boundary for money. Consumers must not access `bbe_economy_accounts`, `balances.json`, `DatabaseEconomyService` internals, or manager caches directly.

## Recommended calls

```java
CompletableFuture<EconomyOperationReceipt> receipt = EconomyAPI.depositAsync(
    player, amount, "jobs:reward:" + actionId, "job reward", Map.of("source", "jobs"));

CompletableFuture<EconomyOperationReceipt> receipt = EconomyAPI.withdrawAsync(
    player, amount, "rankup:charge:" + promotionId, "rank purchase", Map.of("source", "rankup"));

CompletableFuture<EconomyOperationReceipt> receipt = EconomyAPI.payPlayerAsync(
    sender, receiver, amount, "pay:" + paymentId);
```

Use one stable key for one logical operation. Persist that key with the caller's state before retrying after a timeout or restart. A newly generated key is a new financial operation.

## Receipt contract

`EconomyOperationReceipt` contains:

| Field | Meaning |
|---|---|
| `id` | Durable operation UUID |
| `status` | `COMPLETED`, `REJECTED`, `FAILED`, or `IDEMPOTENCY_CONFLICT` |
| `currency` | Currently `money` |
| `playerId`, `amount` | Operation owner and requested value |
| `balanceBefore`, `balanceAfter` | Committed sender/account transition |
| `idempotencyKey`, `fingerprint` | Request identity and payload identity |
| `error` | Stable rejection/persistence reason when present |
| `idempotentReplay` | True when the durable prior result was returned |
| `timestamp` | Operation creation time in epoch milliseconds |
| `sourceModule`, `externalReference` | Audit attribution |

Only `COMPLETED` means the requested balance mutation committed. `PENDING` is an internal journal state and is never returned as successful. `IDEMPOTENCY_CONFLICT` means the key was already used for a different request; do not retry with the same payload key.

## Atomic payment semantics

`payPlayerAsync` debits the sender and credits the receiver in one SQL transaction. The fee is removed from the transfer. The receipt's before/after values describe the sender; receiver UUID and fee are retained in operation metadata. A failed payment changes neither account.

## Compatibility methods

The older synchronous `EconomyAPI.deposit`, `withdraw`, `payPlayer`, Vault methods, and `EconomyManager` methods remain source-compatible. They may block on their backend and are not suitable for a server-thread hot path. Use the async methods for new integrations. Boolean wrappers should be used only when the caller cannot consume a receipt and must still persist its own idempotency key.

## Error handling

```java
receipt.handle((result, error) -> {
    if (error != null) { /* retry same key or mark reconciliation required */ }
    else if (result.status() == EconomyOperationStatus.COMPLETED) { /* commit caller state */ }
    else if (result.status() == EconomyOperationStatus.IDEMPOTENCY_CONFLICT) { /* operator review */ }
    return null;
});
```

Never approve a purchase or grant an item from a browser redirect or an unverified boolean. The caller must act on the committed receipt and retain the external reference for reconciliation.
