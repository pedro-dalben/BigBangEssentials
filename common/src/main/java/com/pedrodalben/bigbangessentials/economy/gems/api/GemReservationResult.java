package com.pedrodalben.bigbangessentials.economy.gems.api;

import com.pedrodalben.bigbangessentials.economy.gems.domain.GemBalanceView;
import java.util.UUID;

public record GemReservationResult(
    boolean success,
    GemOperationFailure failure,
    UUID reservationId,
    GemBalanceView balance,
    String messageKey
) {
    public static GemReservationResult succeed(UUID reservationId, GemBalanceView balance, String messageKey) {
        return new GemReservationResult(true, null, reservationId, balance, messageKey);
    }

    public static GemReservationResult fail(GemOperationFailure failure, String messageKey) {
        return new GemReservationResult(false, failure, null, null, messageKey);
    }

    public static GemReservationResult fail(GemOperationFailure failure, GemBalanceView balance, String messageKey) {
        return new GemReservationResult(false, failure, null, balance, messageKey);
    }
}
