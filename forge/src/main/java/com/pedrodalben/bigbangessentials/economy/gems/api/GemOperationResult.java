package com.pedrodalben.bigbangessentials.economy.gems.api;

import com.pedrodalben.bigbangessentials.economy.gems.domain.GemBalanceView;
import java.util.UUID;

public record GemOperationResult(
    boolean success,
    GemOperationFailure failure,
    UUID transactionId,
    UUID reservationId,
    GemBalanceView balance,
    String messageKey
) {
    public static GemOperationResult succeed(UUID transactionId, UUID reservationId, GemBalanceView balance, String messageKey) {
        return new GemOperationResult(true, null, transactionId, reservationId, balance, messageKey);
    }

    public static GemOperationResult fail(GemOperationFailure failure, String messageKey) {
        return new GemOperationResult(false, failure, null, null, null, messageKey);
    }

    public static GemOperationResult fail(GemOperationFailure failure, GemBalanceView balance, String messageKey) {
        return new GemOperationResult(false, failure, null, null, balance, messageKey);
    }
}
