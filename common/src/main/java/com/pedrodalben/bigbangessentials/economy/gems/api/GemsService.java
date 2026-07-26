package com.pedrodalben.bigbangessentials.economy.gems.api;

import com.pedrodalben.bigbangessentials.economy.gems.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GemsService {

    GemCurrencyDescriptor descriptor();

    GemBalanceView getBalance(UUID playerUuid);

    boolean hasAvailable(UUID playerUuid, long amount);

    GemOperationResult credit(GemCreditRequest request);

    GemOperationResult debit(GemDebitRequest request);

    GemOperationResult setBalance(GemSetBalanceRequest request);

    GemReservationResult reserve(GemReservationRequest request);

    GemOperationResult capture(GemCaptureRequest request);

    GemOperationResult release(GemReleaseRequest request);

    GemOperationResult renew(GemRenewRequest request);

    Optional<GemReservation> findReservation(UUID reservationId);

    Optional<GemReservation> findReservationByIdempotencyKey(String idempotencyKey);

    List<GemTransaction> getHistory(UUID playerUuid, int page, int pageSize);

    /** Async integration boundary. Legacy synchronous methods remain source-compatible. */
    default CompletableFuture<GemBalanceView> getBalanceAsync(UUID playerUuid) { return CompletableFuture.supplyAsync(() -> getBalance(playerUuid)); }
    default CompletableFuture<GemOperationResult> creditAsync(GemCreditRequest request) { return CompletableFuture.supplyAsync(() -> credit(request)); }
    default CompletableFuture<GemOperationResult> debitAsync(GemDebitRequest request) { return CompletableFuture.supplyAsync(() -> debit(request)); }
    default CompletableFuture<GemReservationResult> reserveAsync(GemReservationRequest request) { return CompletableFuture.supplyAsync(() -> reserve(request)); }
    default CompletableFuture<GemOperationResult> captureAsync(GemCaptureRequest request) { return CompletableFuture.supplyAsync(() -> capture(request)); }
    default CompletableFuture<GemOperationResult> releaseAsync(GemReleaseRequest request) { return CompletableFuture.supplyAsync(() -> release(request)); }
}
