package com.pedrodalben.bigbangessentials.api.economy;

import java.math.BigDecimal;
import java.util.UUID;

public record EconomyOperationReceipt(UUID id, UUID playerId, BigDecimal amount, EconomyOperationStatus status,
                                      BigDecimal balanceBefore, BigDecimal balanceAfter, String idempotencyKey,
                                      String fingerprint) {
    public EconomyOperationReceipt(UUID id, UUID playerId, BigDecimal amount, EconomyOperationStatus status,
                                    BigDecimal balanceBefore, BigDecimal balanceAfter, String idempotencyKey) {
        this(id, playerId, amount, status, balanceBefore, balanceAfter, idempotencyKey, null);
    }
}
