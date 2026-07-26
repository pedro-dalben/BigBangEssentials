# Gems Public API Reference

The Gems system exposes a clean, decoupled public interface for integration with other modules, such as `BigBang Regions`.

When the database backend is ready, SQL is the only runtime source of truth. Do not read the legacy JSON state or ledger. Use the asynchronous methods (`getBalanceAsync`, `creditAsync`, `debitAsync`, `reserveAsync`, `captureAsync`, `releaseAsync`) from server-thread code; the synchronous methods remain compatibility wrappers.

## Accessing the API

To interact with the Gems system, consume the API exposed through the main entry point:

```java
import com.pedrodalben.bigbangessentials.api.BigBangEssentialsApi;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemsService;

// Retrieve the service instance (may return null if not loaded yet)
GemsService gemsService = BigBangEssentialsApi.gems();

// Or assert presence to obtain a guaranteed non-null reference:
GemsService gemsService = BigBangEssentialsApi.requireGems();
```

---

## Operations API

### Get Balance
Query a player's balance view:
```java
GemBalanceView balance = gemsService.getBalance(playerId);
long total = balance.totalBalance();
long held = balance.heldBalance();
long available = balance.availableBalance(); // total - held
```

### Credit (Add Gems)
Add gems to a player's wallet:
```java
GemOperationResult result = gemsService.credit(new GemCreditRequest(
    playerId, 
    100L, 
    "store", 
    "purchase", 
    actorUuid, 
    "idempotency-key", 
    "external-ref", 
    Map.of()
));
```

### Debit (Deduct Gems)
Directly deduct gems from a player's available balance:
```java
GemOperationResult result = gemsService.debit(new GemDebitRequest(
    playerId, 
    50L, 
    "shop", 
    "buy_item", 
    actorUuid, 
    "idempotency-key", 
    "external-ref", 
    Map.of()
));
```

---

## Reservation API (Three-Step Transaction Protocol)

For multi-step actions (such as region expansions), use the reservation flow to avoid double-charging or balance loss on server crashes.

### 1. Reserve
Hold a player's gems temporarily:
```java
GemReservationResult result = gemsService.reserve(new GemReservationRequest(
    playerId,
    30L,
    "bigbangregions",
    "PLAYER_REGION_RESIZE",
    "idempotency-key",
    "external-ref",
    Duration.ofMinutes(15), // Lease time
    Map.of()
));
if (result.success()) {
    UUID reservationId = result.reservationId();
    // Proceed with technical operations...
}
```

### 2. Capture
Permanently deduct the reserved gems:
```java
GemOperationResult result = gemsService.capture(new GemCaptureRequest(
    reservationId,
    "bigbangregions",
    "PLAYER_REGION_RESIZE",
    actorUuid,
    "idempotency-key",
    "external-ref",
    Map.of()
));
```

### 3. Release
Cancel the reservation and restore the player's available balance:
```java
GemOperationResult result = gemsService.release(new GemReleaseRequest(
    reservationId,
    "bigbangregions",
    "PLAYER_REGION_RESIZE",
    actorUuid,
    "cancel_reason",
    "idempotency-key",           // Idempotency key — guarantees safe retry after crash
    "external-ref",
    Map.of()
));
```

`GemReleaseRequest` now includes an `idempotencyKey` field. The release operation uses `checkIdempotencyWithStateFallback()` to check for duplicate requests. If the same key with the same reservation was already processed, it returns the original success result without modifying state. This ensures safe retries after crashes — the caller can retry `release()` with the same `idempotencyKey` without risk of double-release or data corruption.

### 4. Renew
Extend the reservation lease:
```java
GemReservationResult result = gemsService.renew(new GemRenewRequest(
    reservationId,
    Duration.ofMinutes(10), // Extension time
    "bigbangregions",
    "PLAYER_REGION_RESIZE",
    actorUuid,
    "idempotency-key",           // Idempotency key — guarantees safe retry
    "external-ref",
    Map.of()
));
```

`GemRenewRequest` now includes an `idempotencyKey` field. Like `release()`, the renew operation uses `checkIdempotencyWithStateFallback()` to ensure safe retries. The renewed lease duration is capped by the maximum lease configured in `gems.json`.
