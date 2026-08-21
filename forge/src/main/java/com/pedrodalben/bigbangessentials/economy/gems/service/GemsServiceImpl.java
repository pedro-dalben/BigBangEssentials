package com.pedrodalben.bigbangessentials.economy.gems.service;

import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.domain.*;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GemsServiceImpl implements GemsService {

    @Override
    public GemCurrencyDescriptor descriptor() {
        return GemsManager.getInstance().getCurrencyDescriptor();
    }

    @Override
    public GemBalanceView getBalance(UUID playerUuid) {
        return GemsManager.getInstance().getBalanceView(playerUuid);
    }

    @Override
    public boolean hasAvailable(UUID playerUuid, long amount) {
        return GemsManager.getInstance().hasAvailable(playerUuid, amount);
    }

    @Override
    public GemOperationResult credit(GemCreditRequest request) {
        return GemsManager.getInstance().credit(request);
    }

    @Override
    public GemOperationResult debit(GemDebitRequest request) {
        return GemsManager.getInstance().debit(request);
    }

    @Override
    public GemOperationResult setBalance(GemSetBalanceRequest request) {
        return GemsManager.getInstance().setBalance(request);
    }

    @Override
    public GemReservationResult reserve(GemReservationRequest request) {
        return GemsManager.getInstance().reserve(request);
    }

    @Override
    public GemOperationResult capture(GemCaptureRequest request) {
        return GemsManager.getInstance().capture(request);
    }

    @Override
    public GemOperationResult release(GemReleaseRequest request) {
        return GemsManager.getInstance().release(request);
    }

    @Override
    public GemOperationResult renew(GemRenewRequest request) {
        return GemsManager.getInstance().renew(request);
    }

    @Override
    public Optional<GemReservation> findReservation(UUID reservationId) {
        return GemsManager.getInstance().findReservation(reservationId);
    }

    @Override
    public Optional<GemReservation> findReservationByIdempotencyKey(String idempotencyKey) {
        return GemsManager.getInstance().findReservationByIdempotencyKey(idempotencyKey);
    }

    @Override
    public List<GemTransaction> getHistory(UUID playerUuid, int page, int pageSize) {
        return GemsManager.getInstance().getHistory(playerUuid, page, pageSize);
    }
}
