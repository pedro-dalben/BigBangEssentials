package com.pedrodalben.bigbangessentials.api.economy;

import java.math.BigDecimal;
import java.util.UUID;

public record EconomyOperationReceipt(UUID id, UUID playerId, BigDecimal amount, EconomyOperationStatus status,
                                      BigDecimal balanceBefore, BigDecimal balanceAfter, String idempotencyKey,
                                      String fingerprint, String currency, String reason, String error,
                                      boolean idempotentReplay, long timestamp, String sourceModule,
                                      String externalReference) {
    public EconomyOperationReceipt(UUID id, UUID playerId, BigDecimal amount, EconomyOperationStatus status,
                                    BigDecimal balanceBefore, BigDecimal balanceAfter, String idempotencyKey,
                                    String fingerprint) {
        this(id, playerId, amount, status, balanceBefore, balanceAfter, idempotencyKey, fingerprint,
                "money", null, null, false, System.currentTimeMillis(), null, null);
    }

    public EconomyOperationReceipt(UUID id, UUID playerId, BigDecimal amount, EconomyOperationStatus status,
                                    BigDecimal balanceBefore, BigDecimal balanceAfter, String idempotencyKey) {
        this(id, playerId, amount, status, balanceBefore, balanceAfter, idempotencyKey, null);
    }

    public EconomyOperationReceipt replay() {
        return new EconomyOperationReceipt(id, playerId, amount, status, balanceBefore, balanceAfter, idempotencyKey,
                fingerprint, currency, reason, error, true, timestamp, sourceModule, externalReference);
    }
}
