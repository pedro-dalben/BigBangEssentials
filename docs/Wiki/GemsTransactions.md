# Gems Ledger and Idempotency Reference

This page describes the transaction logging schema and the idempotency mechanisms that guarantee safety across restarts and crash recoveries.

## Transaction Log Schema (`gems_transactions.jsonl`)

The ledger log at `bigbangessentials/gems_transactions.jsonl` is written in JSON Lines format. Each line is an independent JSON object:

```json
{
  "transactionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "timestamp": 1782782400000,
  "type": "RESERVATION_CREATED",
  "playerUuid": "069a7d2b-aa7b-4bcf-90f7-0e6fb1c49a56",
  "amount": 30,
  "totalBefore": 100,
  "totalAfter": 100,
  "heldBefore": 0,
  "heldAfter": 30,
  "availableBefore": 100,
  "availableAfter": 70,
  "actorUuid": null,
  "source": "bigbangregions",
  "purpose": "PLAYER_REGION_RESIZE",
  "reservationId": "8fa85f64-5717-4562-b3fc-2c963f66afa6",
  "idempotencyKey": "bigbangregions:resize:12:3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "externalReference": "region-ref-12",
  "metadata": {}
}
```

### Transaction Types (`GemTransactionType`)
- **CREDIT:** Direct wallet credit.
- **DEBIT:** Direct wallet debit.
- **SET_BALANCE:** Administrator command forced balance reset.
- **RESERVATION_CREATED:** A new balance reservation was locked.
- **RESERVATION_CAPTURED:** Reserved balance was successfully deducted.
- **RESERVATION_RELEASED:** Reserved balance was cancelled and restored.
- **RESERVATION_EXPIRED:** A reservation lease timed out and funds were automatically restored.

---

## Idempotency Engine

Idempotency guarantees that duplicate requests (due to retries, network glitches, or crashes) do not cause duplicate charges.

- **Idempotency Keys:** Clients supply an arbitrary string key (e.g. `bigbangregions:resize:<regionId>:<operationId>`).
- **Conflict Checking:** If a key is repeated, the manager inspects the registered payload:
  - If the player UUID, amount, and operation type match, the original result is returned directly without repeating the mutation.
  - If any payload field differs, the operation fails immediately with `IDEMPOTENCY_CONFLICT`.
- **In-Memory Registry:** An in-memory cache registry tracks active/recent keys to provide sub-millisecond response times. On boot, the registry is re-populated from the active ledger to maintain persistent idempotency across server restarts.
