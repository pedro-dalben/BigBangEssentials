package com.pedrodalben.bigbangessentials.api;

import com.pedrodalben.bigbangessentials.economy.gems.api.GemsService;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import com.pedrodalben.bigbangessentials.economy.gems.service.GemsServiceImpl;
import com.pedrodalben.bigbangessentials.api.gems.*;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservation;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemOperationFailure;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemReservationResult;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BigBangEssentialsApi {
    private static final GemsService SERVICE = new GemsServiceImpl();
    private static final GemsIntegrationApi INTEGRATION = new IntegrationAdapter();

    public static Optional<GemsService> gems() {
        if (isGemsEnabled()) {
            return Optional.of(SERVICE);
        }
        return Optional.empty();
    }

    public static GemsService requireGems() {
        if (!isGemsEnabled()) {
            throw new IllegalStateException("Gems system is disabled");
        }
        return SERVICE;
    }

    public static boolean isGemsEnabled() {
        return GemsManager.getInstance().isGemsEnabled();
    }

    public static int gemsApiVersion() {
        return 1;
    }

    public static GemsIntegrationApi gemsIntegration() {
        return INTEGRATION;
    }

    private static final class IntegrationAdapter implements GemsIntegrationApi {
        @Override
        public GemsProviderSnapshot status() {
            try {
                var config = GemsManager.getInstance().getConfig();
                var database = DatabaseManager.getInstance();
                boolean configured = config != null;
                boolean enabled = configured && config.enabled;
                boolean databaseReady = database.isReady();
                var databaseState = database.getState();
                GemsProviderState state;
                String failure = null;
                if (!enabled) {
                    state = GemsProviderState.DISABLED;
                } else {
                    state = switch (databaseState) {
                        case READY -> GemsManager.getInstance().isGemsEnabled()
                            ? GemsProviderState.READY : GemsProviderState.TEMPORARILY_UNAVAILABLE;
                        case STOPPING, STOPPED -> GemsProviderState.SHUTTING_DOWN;
                        case FAILED, DEGRADED -> GemsProviderState.TEMPORARILY_UNAVAILABLE;
                        default -> GemsProviderState.WAITING_FOR_DATABASE;
                    };
                    if (state != GemsProviderState.READY) failure = databaseState.name();
                }
                return new GemsProviderSnapshot(
                    gemsApiVersion(), state, configured, enabled, databaseReady,
                    database.getType() == null ? null : database.getType().name(),
                    new GemsCapabilities(true, enabled, enabled, enabled, enabled, enabled), failure);
            } catch (LinkageError e) {
                return new GemsProviderSnapshot(gemsApiVersion(), GemsProviderState.FAILED,
                    false, false, false, null,
                    new GemsCapabilities(false, false, false, false, false, false), e.getClass().getSimpleName());
            } catch (RuntimeException e) {
                return new GemsProviderSnapshot(gemsApiVersion(), GemsProviderState.FAILED,
                    false, false, false, null,
                    new GemsCapabilities(false, false, false, false, false, false), e.getClass().getSimpleName());
            }
        }

        @Override
        public CompletableFuture<GemsBalance> balanceAsync(UUID playerUuid) {
            return ready().thenApplyAsync(ignored -> {
                var view = SERVICE.getBalance(playerUuid);
                return new GemsBalance(view.playerUuid(), view.totalBalance(), view.heldBalance(), view.availableBalance());
            });
        }

        @Override
        public CompletableFuture<GemsReservationResult> reserveAsync(GemsReserveRequest request) {
            return ready().thenApplyAsync(ignored -> {
                GemReservationResult result = SERVICE.reserve(new com.pedrodalben.bigbangessentials.economy.gems.api.GemReservationRequest(
                    request.playerUuid(), request.amount(), request.source(), request.purpose(), request.idempotencyKey(),
                    request.externalReference(), request.lease(), request.metadata()));
                return result.success()
                    ? GemsReservationResult.success(result.reservationId(), reservationExpiry(result.reservationId()), request.amount())
                    : GemsReservationResult.failure(mapFailure(result.failure()));
            });
        }

        @Override
        public CompletableFuture<GemsOperationResult> renewAsync(GemsRenewRequest request) {
            return ready().thenApplyAsync(ignored -> mapOperation(SERVICE.renew(new com.pedrodalben.bigbangessentials.economy.gems.api.GemRenewRequest(
                request.reservationUuid(), request.lease(), request.source(), request.purpose(), request.actorUuid(),
                request.idempotencyKey(), request.externalReference(), request.metadata()))));
        }

        @Override
        public CompletableFuture<GemsOperationResult> captureAsync(GemsCaptureRequest request) {
            return ready().thenApplyAsync(ignored -> mapOperation(SERVICE.capture(new com.pedrodalben.bigbangessentials.economy.gems.api.GemCaptureRequest(
                request.reservationUuid(), request.source(), request.purpose(), request.actorUuid(), request.idempotencyKey(),
                request.externalReference(), request.metadata()))));
        }

        @Override
        public CompletableFuture<GemsOperationResult> releaseAsync(GemsReleaseRequest request) {
            return ready().thenApplyAsync(ignored -> mapOperation(SERVICE.release(new com.pedrodalben.bigbangessentials.economy.gems.api.GemReleaseRequest(
                request.reservationUuid(), request.source(), request.purpose(), request.actorUuid(), request.reason(),
                request.idempotencyKey(), request.externalReference(), request.metadata()))));
        }

        @Override
        public CompletableFuture<GemsReservationLookupResult> findReservationAsync(UUID reservationUuid) {
            return ready().thenApplyAsync(ignored -> SERVICE.findReservation(reservationUuid)
                .map(r -> new GemsReservationLookupResult(true, r.getReservationId(), r.getPlayerUuid(), r.getAmount(),
                    r.getStatus().name(), r.getExpiresAt(), null))
                .orElseGet(GemsReservationLookupResult::missing));
        }

        @Override
        public CompletableFuture<GemsReservationLookupResult> findReservationByIdempotencyKeyAsync(String idempotencyKey) {
            return ready().thenApplyAsync(ignored -> SERVICE.findReservationByIdempotencyKey(idempotencyKey)
                .map(r -> new GemsReservationLookupResult(true, r.getReservationId(), r.getPlayerUuid(), r.getAmount(),
                    r.getStatus().name(), r.getExpiresAt(), null))
                .orElseGet(GemsReservationLookupResult::missing));
        }

        private CompletableFuture<Void> ready() {
            GemsProviderSnapshot snapshot = status();
            if (snapshot.ready()) return CompletableFuture.completedFuture(null);
            return CompletableFuture.failedFuture(new IllegalStateException(snapshot.state().name()));
        }

        private long reservationExpiry(UUID reservationId) {
            return SERVICE.findReservation(reservationId).map(GemReservation::getExpiresAt).orElse(0L);
        }

        private GemsOperationResult mapOperation(com.pedrodalben.bigbangessentials.economy.gems.api.GemOperationResult result) {
            if (result.success()) {
                long expiry = result.reservationId() == null ? 0L : reservationExpiry(result.reservationId());
                return GemsOperationResult.success(result.transactionId(), result.reservationId(), expiry);
            }
            return GemsOperationResult.failure(mapFailure(result.failure()));
        }

        private GemsFailure mapFailure(GemOperationFailure failure) {
            if (failure == null) return GemsFailure.UNKNOWN;
            return switch (failure) {
                case INSUFFICIENT_AVAILABLE_BALANCE -> GemsFailure.INSUFFICIENT_BALANCE;
                case RESERVATION_ALREADY_CAPTURED -> GemsFailure.ALREADY_CAPTURED;
                case RESERVATION_ALREADY_RELEASED -> GemsFailure.ALREADY_RELEASED;
                case RESERVATION_NOT_FOUND -> GemsFailure.RESERVATION_NOT_FOUND;
                case RESERVATION_NOT_ACTIVE -> GemsFailure.RESERVATION_NOT_ACTIVE;
                case RESERVATION_EXPIRED -> GemsFailure.RESERVATION_EXPIRED;
                case IDEMPOTENCY_CONFLICT -> GemsFailure.IDEMPOTENCY_CONFLICT;
                case INVALID_AMOUNT, NEGATIVE_AMOUNT, FRACTIONAL_AMOUNT, OVERFLOW -> GemsFailure.INVALID_AMOUNT;
                case INVALID_LEASE -> GemsFailure.INVALID_LEASE;
                case MAX_BALANCE_EXCEEDED -> GemsFailure.MAX_BALANCE_EXCEEDED;
                case PERSISTENCE_FAILURE -> GemsFailure.PERSISTENCE_FAILURE;
                case DATA_INTEGRITY_FAILURE -> GemsFailure.DATA_INTEGRITY_FAILURE;
                case SHUTTING_DOWN -> GemsFailure.SHUTTING_DOWN;
                case DISABLED -> GemsFailure.DISABLED;
                case UNAUTHORIZED_SOURCE, UNKNOWN -> GemsFailure.UNKNOWN;
            };
        }
    }
}
