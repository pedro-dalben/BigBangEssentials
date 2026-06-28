# Gems Wallet System Design

This document details the system design, architectural decisions, and recovery protocols for the Gems wallet system in `BigBangEssentials`.

## Architecture & Decoupling
The Gems system is a completely independent secondary economy, separate from the primary Coins economy.
- **Separate Storage:** Gems does not share files with Coins. It uses `gems.json` for configuration, `gems_state.json` for balances/reservations, and `gems_transactions.jsonl` for its transaction log.
- **No Vault Integration:** Vault is only used for Coins. Gems are not exposed as a Vault currency.
- **Decimal-Free:** All operations use `long` (integers). There are no fractional values, preventing floating-point errors.
- **Decoupled API:** The API provides rich result objects (`GemOperationResult`, `GemReservationResult`) instead of simple booleans.

---

## State & Data Structures

### Balance Model
A player's balance consists of:
- `totalBalance`: The absolute persistent balance of the player.
- `heldBalance`: The sum of all active reservations (Gems reserved for external pending actions).
- `availableBalance`: Calculated as `totalBalance - heldBalance`.

The available balance can never drop below zero.

### Reservation States
A reservation goes through the following lifecycle:
```
       [Created]
           ↓
       +---+---+
       | ACTIVE|
       +---+---+
      /    |    \
     /     |     \
[Capture] [Release] [Expire]
   ↓       ↓         ↓
CAPTURED  RELEASED  EXPIRED
```
- **ACTIVE:** The Gems are reserved (held) from the player's balance.
- **CAPTURED:** The transaction is completed, and the reserved Gems are permanently deducted from `totalBalance` and removed from `heldBalance`.
- **RELEASED:** The transaction is aborted; reserved Gems are returned to the available balance.
- **EXPIRED:** The reservation duration (lease) has passed without capture/release. The cleanup task expires it and returns the Gems to the available balance.

---

## Durability & Write-Ahead-Logic (Atomic Writes)
Operations must write to disk *before* updating in-memory cache or returning success:
1. Validate transaction logic.
2. Generate state snapshot and transaction log entry.
3. Write new state to temporary file `gems_state.json.tmp`.
4. Perform atomic move/rename from `gems_state.json.tmp` to `gems_state.json`.
5. Append entry to `gems_transactions.jsonl` (write-ahead logging).
6. Update in-memory cache.
7. Publish event.
8. Return operation result to client.

This flow prevents in-memory/on-disk discrepancies if a crash occurs mid-operation.

---

## Recovery Protocol on Server Boot
During system boot, `BigBangEssentials` executes the recovery protocol:
1. Load `gems_state.json`.
2. Validate schema and file integrity.
3. Recalculate `heldBalance` for each player by scanning all `ACTIVE` reservations.
4. Verify that for every player, `heldBalance <= totalBalance`.
5. Identify any `ACTIVE` reservations that have exceeded their expiration time (`expiresAt < currentTime`).
6. For each expired reservation:
   - Transition to `EXPIRED`.
   - Append `RESERVATION_EXPIRED` to the transaction log.
   - Restore the available balance.
7. Save the corrected, cleaned-up state back to `gems_state.json`.
8. Log a recovery report detailing loaded balances, active reservations, and expired cleanups.
