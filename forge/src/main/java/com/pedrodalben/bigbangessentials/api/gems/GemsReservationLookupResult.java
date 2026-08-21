package com.pedrodalben.bigbangessentials.api.gems;

import java.util.UUID;

public record GemsReservationLookupResult(
    boolean found,
    UUID reservationUuid,
    UUID playerUuid,
    long amount,
    String status,
    long leaseExpiresAt,
    GemsFailure failure
) {
    public static GemsReservationLookupResult missing() {
        return new GemsReservationLookupResult(false, null, null, 0, null, 0, null);
    }

    public static GemsReservationLookupResult failure(GemsFailure failure) {
        return new GemsReservationLookupResult(false, null, null, 0, null, 0, failure);
    }
}
