package com.pedrodalben.bigbangessentials.api.economy;

/** Outcomes used by commerce modules; no commerce code should reduce these to a boolean. */
public enum CommercialTransferStatus {
    COMPLETED,
    INSUFFICIENT_FUNDS,
    MAXIMUM_BALANCE,
    INVALID_AMOUNT,
    IDEMPOTENT_REPLAY,
    IDEMPOTENCY_CONFLICT,
    DATABASE_UNAVAILABLE,
    EXECUTOR_SATURATED,
    TECHNICAL_FAILURE,
    RECONCILIATION_REQUIRED
}
