package com.pedrodalben.bigbangessentials.api.gems;

import java.util.UUID;

public record GemsOperationResult(
    boolean success,
    GemsFailure failure,
    UUID transactionUuid,
    UUID reservationUuid,
    long leaseExpiresAt
) {
    public static GemsOperationResult success(UUID transactionUuid, UUID reservationUuid, long leaseExpiresAt) {
        return new GemsOperationResult(true, null, transactionUuid, reservationUuid, leaseExpiresAt);
    }

    public static GemsOperationResult failure(GemsFailure failure) {
        return new GemsOperationResult(false, failure, null, null, 0);
    }
}
