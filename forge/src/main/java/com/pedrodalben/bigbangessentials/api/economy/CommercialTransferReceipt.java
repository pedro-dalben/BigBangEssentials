package com.pedrodalben.bigbangessentials.api.economy;

import java.math.BigDecimal;
import java.util.UUID;

/** Durable result of a module-owned, fee-free player-to-player commerce transfer. */
public record CommercialTransferReceipt(
        UUID operationId,
        UUID sender,
        UUID receiver,
        BigDecimal amount,
        BigDecimal senderBalanceBefore,
        BigDecimal senderBalanceAfter,
        BigDecimal receiverBalanceBefore,
        BigDecimal receiverBalanceAfter,
        String idempotencyKey,
        String fingerprint,
        CommercialTransferStatus status,
        String error,
        boolean idempotentReplay,
        long timestamp) {

    public boolean success() {
        return status == CommercialTransferStatus.COMPLETED
                || status == CommercialTransferStatus.IDEMPOTENT_REPLAY;
    }
}
