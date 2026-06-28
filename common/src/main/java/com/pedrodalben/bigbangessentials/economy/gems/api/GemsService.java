package com.pedrodalben.bigbangessentials.economy.gems.api;

import com.pedrodalben.bigbangessentials.economy.gems.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
