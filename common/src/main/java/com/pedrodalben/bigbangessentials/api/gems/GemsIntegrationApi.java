package com.pedrodalben.bigbangessentials.api.gems;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GemsIntegrationApi {
    GemsProviderSnapshot status();

    CompletableFuture<GemsBalance> balanceAsync(UUID playerUuid);

    CompletableFuture<GemsReservationResult> reserveAsync(GemsReserveRequest request);

    CompletableFuture<GemsOperationResult> renewAsync(GemsRenewRequest request);

    CompletableFuture<GemsOperationResult> captureAsync(GemsCaptureRequest request);

    CompletableFuture<GemsOperationResult> releaseAsync(GemsReleaseRequest request);

    CompletableFuture<GemsReservationLookupResult> findReservationAsync(UUID reservationUuid);

    CompletableFuture<GemsReservationLookupResult> findReservationByIdempotencyKeyAsync(String idempotencyKey);
}
