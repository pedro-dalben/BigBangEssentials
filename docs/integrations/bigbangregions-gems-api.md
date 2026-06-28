# BigBang Regions Integration - Gems API Contract

This document defines the interface and protocols for the integration between `BigBang Regions` (the client/consumer) and `BigBang Essentials` (the Gems provider).

## Security and Architectural Boundaries

To ensure data integrity, idempotency, and clean decoupling:
- **No Direct Storage Access:** `BigBang Regions` MUST NOT read, write, or modify `gems_state.json`, `gems_transactions.jsonl`, or any other Gems storage file.
- **No Manager/Reflection Access:** Accessing `GemsManager` or any internal implementation class via reflection or direct references is strictly prohibited.
- **API Boundary:** All operations must go through the public interface `BigBangEssentialsApi.gems()` or `BigBangEssentialsApi.requireGems()`.
- **No Direct Balance Deductions:** Do not use `debit()` for region expansions; you MUST use the three-step reservation protocol (`reserve` -> `execute` -> `capture` or `release`).
- **No Vault Dependency:** Gems are not exposed through the Vault API. Do not attempt to query or modify Gems using Vault Economy.

---

## The Three-Step Transaction Protocol

All region expansion/purchase operations involving Gems must follow the reservation flow below. This ensures that if the server crashes or the region resize fails, the player's Gems are neither lost nor duplicate-debited.

### 1. Step 1: Pre-flight and Reservation
Before executing any technical resize or modifications:
1. `BigBang Regions` calculates the `RegionResizePlan` and checks limits, plot availability, overlap, and homes.
2. `BigBang Regions` generates a persistent `operationId` (UUID) for this specific request.
3. `BigBang Regions` constructs a stable, unique `idempotencyKey`:
   `bigbangregions:resize:<regionId>:<operationId>`
4. `BigBang Regions` calls `GemsService.reserve(...)` with the amount, source (`"bigbangregions"`), purpose (`"PLAYER_REGION_RESIZE"`), idempotency key, and metadata.
5. If `reserve()` returns success:
   - A `GemReservation` in `ACTIVE` state is created in `BigBang Essentials`.
   - The player's available balance is reduced by the reserved amount.
   - `BigBang Regions` persists the operation state locally as `PAYMENT_RESERVED`, along with the `reservationId`.

### 2. Step 2: Technical Execution
Once the Gems are reserved:
1. `BigBang Regions` applies the technical resize/expansion to the region.
2. `BigBang Regions` persists the operation state locally as `RESIZE_APPLIED`.

### 3. Step 3: Capture or Release
Depending on the success of Step 2:
- **On Success:** `BigBang Regions` calls `GemsService.capture(reservationId)` using the same `idempotencyKey` and `operationId` from Step 1.
  - The player's total balance is reduced.
  - The held balance is cleared.
  - The reservation is marked `CAPTURED`.
  - `BigBang Regions` persists the operation state as `PAYMENT_CAPTURED` or `COMPLETED`.
- **On Failure (before resize):** `BigBang Regions` calls `GemsService.release(reservationId)` using the same `idempotencyKey` and `operationId` from Step 1.
  - The player's available balance is restored.
  - The reservation is marked `RELEASED`.
  - `BigBang Regions` marks the operation state as `FAILED` or `CANCELLED`.

> **Note:** `GemReleaseRequest` now accepts an `idempotencyKey` field. Always pass the same `idempotencyKey` used during `reserve()`. This guarantees that retries after crashes do not double-release or corrupt state.

---

## Crash Recovery and Retry Protocol

If a crash or network interruption occurs during the resize, the state might be left as `PAYMENT_CAPTURE_PENDING` (where the resize completed but the capture call did not succeed or get confirmed).

- **Retry with Same Idempotency Key:** `BigBang Regions` MUST retry the `capture()`, `release()`, or `reserve()` call using the exact same `operationId` and `idempotencyKey`.
- **Idempotency Guarantees:**
  - Retrying `reserve()` with the same `idempotencyKey` and identical details will return the original successful `GemReservationResult` containing the `reservationId` without reserving double funds.
  - Retrying `capture()` is fully idempotent via both status check (already `CAPTURED`) and `idempotencyKey` (`checkIdempotencyWithStateFallback()`). If the reservation is already captured, it returns success without deducting the balance twice.
  - Retrying `release()` is now fully idempotent via `idempotencyKey`. `GemReleaseRequest` includes `idempotencyKey`, `source`, `purpose`, `externalReference`, and `metadata`. A retry with the same key returns `idempotent_success` without modifying state.
  - Retrying `renew()` is now fully idempotent via `idempotencyKey`. `GemRenewRequest` includes `idempotencyKey`, `lease`, `source`, `purpose`, `externalReference`, and `metadata`.
- **Never Generate New Keys for Retries:** Creating a new `idempotencyKey` for a retried expansion will cause a double-charge.
- **Idempotency Survives Crashes:** All idempotency records are persisted in `gems_state.json` under `idempotencyRecords`. Even if the server crashes between saving state and appending to the ledger, the `checkIdempotencyWithStateFallback()` method finds the persisted record on the next request and prevents duplicate operations.

---

## Recommended Metadata Schema

When reserving Gems, pass a sanitised metadata map:
```json
{
  "regionId": "<region-uuid>",
  "requestedWidth": "<width-blocks>",
  "requestedDepth": "<depth-blocks>"
}
```
All metadata keys must be simple strings (max 64 chars) and values must be simple strings (max 256 chars). Do not pass nested JSON objects.
