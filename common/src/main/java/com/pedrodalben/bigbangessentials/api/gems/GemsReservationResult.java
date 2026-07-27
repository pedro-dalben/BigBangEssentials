package com.pedrodalben.bigbangessentials.api.gems;

import java.util.UUID;

public record GemsReservationResult(
    boolean success,
    GemsFailure failure,
    UUID reservationUuid,
    long leaseExpiresAt,
    long amount
) {
    public static GemsReservationResult success(UUID reservationUuid, long leaseExpiresAt, long amount) {
        return new GemsReservationResult(true, null, reservationUuid, leaseExpiresAt, amount);
    }

    public static GemsReservationResult failure(GemsFailure failure) {
        return new GemsReservationResult(false, failure, null, 0, 0);
    }
}
