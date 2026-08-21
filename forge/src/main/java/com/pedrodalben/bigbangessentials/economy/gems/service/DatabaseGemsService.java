package com.pedrodalben.bigbangessentials.economy.gems.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.execution.DatabaseExecutor;
import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.config.GemConfig;
import com.pedrodalben.bigbangessentials.economy.gems.domain.*;
import com.pedrodalben.bigbangessentials.economy.gems.event.*;
import com.pedrodalben.bigbangessentials.util.Platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** MySQL/SQL database source of truth for gems. No JSON reads or writes occur here. */
public final class DatabaseGemsService implements GemsService {
    private final DatabaseManager database;
    private final GemConfig config;
    private final Gson gson = new Gson();

    public DatabaseGemsService(DatabaseManager database, GemConfig config) {
        this.database = Objects.requireNonNull(database);
        this.config = Objects.requireNonNull(config);
    }

    public GemCurrencyDescriptor descriptor() {
        GemConfig.Display d = config.display;
        return new GemCurrencyDescriptor(config.technicalId, d.symbol, d.singular, d.plural, d.symbolBeforeAmount, d.thousandsSeparator);
    }

    public GemBalanceView getBalance(UUID playerUuid) {
        expireDue(playerUuid);
        long total = query("SELECT balance_minor FROM bbe_gem_accounts WHERE player_uuid=?", s -> s.setString(1, playerUuid.toString()), r -> r.getLong(1)).orElse(config.balances.startingBalance);
        long held = query("SELECT held_minor FROM bbe_gem_accounts WHERE player_uuid=?", s -> s.setString(1, playerUuid.toString()), r -> r.getLong(1)).orElse(0L);
        return new GemBalanceView(playerUuid, total, held, total - held);
    }

    public boolean hasAvailable(UUID playerUuid, long amount) { return amount >= 0 && getBalance(playerUuid).availableBalance() >= amount; }

    public GemOperationResult credit(GemCreditRequest request) {
        if (request == null || request.playerUuid() == null || request.amount() <= 0) return failure(GemOperationFailure.INVALID_AMOUNT);
        if (!valid(request.source()) || !valid(request.purpose())) return failure(GemOperationFailure.UNAUTHORIZED_SOURCE);
        String key = key(request.idempotencyKey(), "credit");
        Op op = mutateBalance("CREDIT", request.playerUuid(), request.amount(), request.source(), request.purpose(), request.actorUuid(), key,
                request.externalReference(), request.metadata(), true);
        if (op.status.equals("COMPLETED")) post(new GemBalanceChangedEvent(request.playerUuid(), request.amount(), request.source(), request.purpose(), op.id, null, key, op.before, op.after, op.heldBefore, op.heldAfter));
        return result(op, "credit_success");
    }

    public GemOperationResult debit(GemDebitRequest request) {
        if (request == null || request.playerUuid() == null || request.amount() <= 0) return failure(GemOperationFailure.INVALID_AMOUNT);
        if (!valid(request.source()) || !valid(request.purpose())) return failure(GemOperationFailure.UNAUTHORIZED_SOURCE);
        String key = key(request.idempotencyKey(), "debit");
        Op op = mutateBalance("DEBIT", request.playerUuid(), request.amount(), request.source(), request.purpose(), request.actorUuid(), key,
                request.externalReference(), request.metadata(), false);
        if (op.status.equals("COMPLETED")) post(new GemBalanceChangedEvent(request.playerUuid(), request.amount(), request.source(), request.purpose(), op.id, null, key, op.before, op.after, op.heldBefore, op.heldAfter));
        return result(op, "debit_success");
    }

    public GemOperationResult setBalance(GemSetBalanceRequest request) {
        if (request == null || request.playerUuid() == null || request.amount() < 0 || request.amount() > config.balances.maxBalance) return failure(GemOperationFailure.INVALID_AMOUNT);
        if (!valid(request.source()) || !valid(request.purpose())) return failure(GemOperationFailure.UNAUTHORIZED_SOURCE);
        String key = "gems:set:" + request.playerUuid() + ":" + UUID.randomUUID();
        Op op = transaction("gems.set", c -> {
            ensureAccount(c, request.playerUuid());
            Account account = lockAccount(c, request.playerUuid());
            String fingerprint = fingerprint("ADMIN_SET", request.playerUuid(), request.amount(), request.source(), request.purpose(), null, null, null, request.metadata());
            Op old = operation(c, key);
            if (old != null) return compatible(old, fingerprint, request.playerUuid(), request.amount());
            if (request.amount() < account.held) return rejectedOperation(c, request.playerUuid(), "ADMIN_SET", request.amount(), key, fingerprint, request.source(), request.purpose(), null, null, request.metadata(), GemOperationFailure.DATA_INTEGRITY_FAILURE.name(), account.balance, account.balance, account.held, account.held);
            UUID id = UUID.randomUUID();
            insertOperation(c, id, request.playerUuid(), "ADMIN_SET", request.amount(), null, key, fingerprint, request.source(), request.purpose(), request.actorUuid(), null, request.metadata(), account.balance, request.amount(), account.held, account.held);
            updateAccount(c, request.playerUuid(), request.amount(), account.held, account.version);
            return complete(c, key, "COMPLETED", null, account.balance, request.amount(), account.held, account.held);
        });
        return result(op, "set_success");
    }

    public GemReservationResult reserve(GemReservationRequest request) {
        if (request == null || request.playerUuid() == null || request.amount() <= 0) return GemReservationResult.fail(GemOperationFailure.INVALID_AMOUNT, "invalid_amount");
        if (!config.reservations.enabled) return GemReservationResult.fail(GemOperationFailure.DISABLED, "disabled");
        if (!valid(request.source()) || !valid(request.purpose())) return GemReservationResult.fail(GemOperationFailure.UNAUTHORIZED_SOURCE, "invalid_source");
        long lease = request.lease() == null ? config.reservations.defaultLeaseSeconds : request.lease().toSeconds();
        if (lease <= 0 || lease > config.reservations.maxLeaseSeconds) return GemReservationResult.fail(GemOperationFailure.INVALID_LEASE, "invalid_lease");
        String key = key(request.idempotencyKey(), "reserve");
        Op op = transaction("gems.reserve", c -> {
            ensureAccount(c, request.playerUuid());
            Account account = lockAccount(c, request.playerUuid());
            String fingerprint = fingerprint("RESERVE", request.playerUuid(), request.amount(), request.source(), request.purpose(), null, Duration.ofSeconds(lease), request.externalReference(), request.metadata());
            Op old = operation(c, key);
            if (old != null) return compatible(old, fingerprint, request.playerUuid(), request.amount());
            long heldBefore = account.held;
            if (account.balance - heldBefore < request.amount()) return rejectedOperation(c, request.playerUuid(), "RESERVATION_CREATED", request.amount(), key, fingerprint, request.source(), request.purpose(), null, request.externalReference(), request.metadata(), GemOperationFailure.INSUFFICIENT_AVAILABLE_BALANCE.name(), account.balance, account.balance, heldBefore, heldBefore);
            UUID reservationId = UUID.randomUUID();
            long now = System.currentTimeMillis();
            if (!updateAccountHeld(c, request.playerUuid(), request.amount())) return rejectedOperation(c, request.playerUuid(), "RESERVATION_CREATED", request.amount(), key, fingerprint, request.source(), request.purpose(), null, request.externalReference(), request.metadata(), GemOperationFailure.INSUFFICIENT_AVAILABLE_BALANCE.name(), account.balance, account.balance, heldBefore, heldBefore);
            insertReservation(c, reservationId, request, key, fingerprint, now, now + lease * 1000L);
            UUID tx = UUID.randomUUID();
            insertOperation(c, tx, request.playerUuid(), "RESERVATION_CREATED", request.amount(), reservationId, key, fingerprint, request.source(), request.purpose(), null, request.externalReference(), request.metadata(), account.balance, account.balance, heldBefore, heldBefore + request.amount());
            return complete(c, key, "COMPLETED", null, account.balance, account.balance, heldBefore, heldBefore + request.amount(), tx, reservationId);
        });
        if (op.status.equals("COMPLETED")) post(new GemReservationCreatedEvent(request.playerUuid(), request.amount(), request.source(), request.purpose(), op.id, op.reservationId, key, op.before, op.after, op.heldBefore, op.heldAfter));
        return op.status.equals("COMPLETED") ? GemReservationResult.succeed(op.reservationId, view(op.playerUuid, op.after, op.heldAfter), "reserve_success") : GemReservationResult.fail(failure(op.error), "reserve_failed");
    }

    public GemOperationResult capture(GemCaptureRequest request) {
        if (request == null || request.reservationId() == null) return failure(GemOperationFailure.RESERVATION_NOT_FOUND);
        return transition(request.reservationId(), request.source(), request.purpose(), request.actorUuid(), request.idempotencyKey(), request.externalReference(), request.metadata(), "CAPTURE");
    }

    public GemOperationResult release(GemReleaseRequest request) {
        if (request == null || request.reservationId() == null) return failure(GemOperationFailure.RESERVATION_NOT_FOUND);
        return transition(request.reservationId(), request.source(), request.purpose(), request.actorUuid(), request.idempotencyKey(), request.externalReference(), request.metadata(), "RELEASE");
    }

    public GemOperationResult renew(GemRenewRequest request) {
        if (request == null || request.reservationId() == null) return failure(GemOperationFailure.RESERVATION_NOT_FOUND);
        if (!valid(request.source()) || !valid(request.purpose())) return failure(GemOperationFailure.UNAUTHORIZED_SOURCE);
        long lease = request.lease() == null ? config.reservations.defaultLeaseSeconds : request.lease().toSeconds();
        if (!config.reservations.allowExternalRenewal || lease <= 0 || lease > config.reservations.maxLeaseSeconds) return failure(GemOperationFailure.INVALID_LEASE);
        GemReservation reservation = findReservation(request.reservationId()).orElse(null);
        if (reservation == null) return failure(GemOperationFailure.RESERVATION_NOT_FOUND);
        if (reservation.getStatus() == GemReservationStatus.ACTIVE && reservation.getExpiresAt() <= System.currentTimeMillis()) {
            expireDue(reservation.getPlayerUuid());
            reservation = findReservation(request.reservationId()).orElse(reservation);
        }
        GemReservation selected = reservation;
        String key = key(request.idempotencyKey(), "renew");
        Op op = transaction("gems.renew", c -> {
            Account account = lockAccount(c, selected.getPlayerUuid());
            String fp = fingerprint("RENEW", selected.getPlayerUuid(), selected.getAmount(), request.source(), request.purpose(), selected.getReservationId(), Duration.ofSeconds(lease), request.externalReference(), request.metadata());
            Op old = operation(c, key);
            if (old != null) return compatible(old, fp, selected.getPlayerUuid(), selected.getAmount());
            ReservationRow row = lockReservation(c, request.reservationId());
            if (row == null) return rejectedOperation(c, selected.getPlayerUuid(), "RESERVATION_RENEWED", 0, key, fp, request.source(), request.purpose(), request.reservationId(), request.externalReference(), request.metadata(), GemOperationFailure.RESERVATION_NOT_FOUND.name(), account.balance, account.balance, account.held, account.held);
            if ("ACTIVE".equals(row.status) && row.expiresAt <= System.currentTimeMillis()) {
                Op expired = expireLocked(c, account, row, System.currentTimeMillis());
                return rejectedOperation(c, row.playerUuid, "RESERVATION_RENEWED", row.amount, key, fp, request.source(), request.purpose(), request.reservationId(), request.externalReference(), request.metadata(), GemOperationFailure.RESERVATION_EXPIRED.name(), account.balance, account.balance, expired.heldAfter, expired.heldAfter);
            }
            if (!"ACTIVE".equals(row.status) || row.expiresAt <= System.currentTimeMillis()) return rejectedOperation(c, row.playerUuid, "RESERVATION_RENEWED", row.amount, key, fp, request.source(), request.purpose(), request.reservationId(), request.externalReference(), request.metadata(), GemOperationFailure.RESERVATION_NOT_ACTIVE.name(), account.balance, account.balance, account.held, account.held);
            long now = System.currentTimeMillis();
            updateReservationExpiry(c, request.reservationId(), now + lease * 1000L);
            UUID tx = UUID.randomUUID();
            insertOperation(c, tx, row.playerUuid, "RESERVATION_RENEWED", row.amount, row.id, key, fp, request.source(), request.purpose(), request.actorUuid(), request.externalReference(), request.metadata(), account.balance, account.balance, account.held, account.held);
            return complete(c, key, "COMPLETED", null, account.balance, account.balance, account.held, account.held, tx, row.id);
        });
        return result(op, "renew_success");
    }

    public Optional<GemReservation> findReservation(UUID reservationId) {
        return query("SELECT reservation_id,player_uuid,amount,status,source,purpose,idempotency_key,external_reference,metadata_json,created_at,expires_at,captured_at,released_at FROM bbe_gem_reservations WHERE reservation_id=?", s -> s.setString(1, reservationId.toString()), this::mapReservation);
    }

    public Optional<GemReservation> findReservationByIdempotencyKey(String key) {
        if (key == null) return Optional.empty();
        return query("SELECT reservation_id,player_uuid,amount,status,source,purpose,idempotency_key,external_reference,metadata_json,created_at,expires_at,captured_at,released_at FROM bbe_gem_reservations WHERE idempotency_key=?", s -> s.setString(1, key), this::mapReservation);
    }

    public List<GemReservation> getActiveReservations(UUID playerUuid) {
        return database.getExecutor().queryList("gems.reservations.active", "SELECT reservation_id,player_uuid,amount,status,source,purpose,idempotency_key,external_reference,metadata_json,created_at,expires_at,captured_at,released_at FROM bbe_gem_reservations WHERE player_uuid=? AND status='ACTIVE' AND expires_at>? ORDER BY created_at", s -> { s.setString(1, playerUuid.toString()); s.setLong(2, System.currentTimeMillis()); }, this::mapReservation).join();
    }

    public List<GemTransaction> getHistory(UUID playerUuid, int page, int pageSize) {
        int safePage = Math.max(1, page), safeSize = Math.max(1, Math.min(pageSize, 100));
        return database.getExecutor().queryList("gems.history", "SELECT id,player_uuid,operation_type,amount,reservation_id,actor_uuid,source,purpose,idempotency_key,external_reference,metadata_json,created_at,balance_before,balance_after,held_before,held_after FROM bbe_gem_operations WHERE player_uuid=? ORDER BY created_at DESC LIMIT ? OFFSET ?", s -> { s.setString(1, playerUuid.toString()); s.setInt(2, safeSize); s.setInt(3, (safePage - 1) * safeSize); }, this::mapTransaction).join();
    }

    public Map<UUID, Long> getAllBalances() {
        return database.getExecutor().queryList("gems.all_balances", "SELECT player_uuid, balance_minor FROM bbe_gem_accounts", null, r -> new AbstractMap.SimpleEntry<>(UUID.fromString(r.getString(1)), r.getLong(2)))
            .thenApply(list -> {
                Map<UUID, Long> map = new java.util.concurrent.ConcurrentHashMap<>();
                for (Map.Entry<UUID, Long> entry : list) map.put(entry.getKey(), entry.getValue());
                return map;
            }).join();
    }

    public String format(long amount) {
        String separator = config.display.thousandsSeparator;
        String value = String.format(Locale.US, "%,d", amount).replace(",", separator == null ? "," : separator);
        return config.display.symbolBeforeAmount ? config.display.symbol + " " + value : value + " " + config.display.symbol;
    }

    public String verify() {
        long accounts = queryOne("SELECT COUNT(*) FROM bbe_gem_accounts", null, r -> r.getLong(1));
        long negative = queryOne("SELECT COUNT(*) FROM bbe_gem_accounts WHERE balance_minor<0", null, r -> r.getLong(1));
        long pending = queryOne("SELECT COUNT(*) FROM bbe_gem_operations WHERE status='PENDING'", null, r -> r.getLong(1));
        long held = queryOne("SELECT COALESCE(SUM(amount),0) FROM bbe_gem_reservations WHERE status='ACTIVE' AND expires_at>?", s -> s.setLong(1, System.currentTimeMillis()), r -> r.getLong(1));
        return "Accounts: " + accounts + ", Active held gems: " + held + ", Pending operations: " + pending + ", Negative accounts: " + negative + ", Integrity OK: " + (negative == 0 && pending == 0);
    }

    public void repair(boolean confirm) {
        if (!confirm) throw new IllegalArgumentException("Repair requires literal confirmation");
        expireDue(null);
    }

    public void expireDueReservations() { expireDue(null); }

    private GemOperationResult transition(UUID reservationId, String source, String purpose, UUID actor, String requestedKey, String external, Map<String, String> metadata, String action) {
        if (!valid(source) || !valid(purpose)) return failure(GemOperationFailure.UNAUTHORIZED_SOURCE);
        GemReservation known = findReservation(reservationId).orElse(null);
        if (known == null) return failure(GemOperationFailure.RESERVATION_NOT_FOUND);
        if (known.getStatus() == GemReservationStatus.ACTIVE && known.getExpiresAt() <= System.currentTimeMillis()) {
            expireDue(known.getPlayerUuid());
            known = findReservation(reservationId).orElse(known);
        }
        GemReservation selected = known;
        String key = key(requestedKey, action.toLowerCase());
        Op op = transaction("gems." + action.toLowerCase(), c -> {
            Account account = lockAccount(c, selected.getPlayerUuid());
            String fp = fingerprint(action, selected.getPlayerUuid(), selected.getAmount(), source, purpose, reservationId, null, external, metadata);
            Op old = operation(c, key);
            if (old != null) return compatible(old, fp, selected.getPlayerUuid(), selected.getAmount());
            ReservationRow row = lockReservation(c, reservationId);
            if (row == null) return rejectedOperation(c, selected.getPlayerUuid(), "RESERVATION_" + action, selected.getAmount(), key, fp, source, purpose, reservationId, external, metadata, GemOperationFailure.RESERVATION_NOT_FOUND.name(), account.balance, account.balance, account.held, account.held);
            long now = System.currentTimeMillis();
            long heldBefore = account.held;
            if ("ACTIVE".equals(row.status) && row.expiresAt <= now) {
                Op expired = expireLocked(c, account, row, now);
                heldBefore = expired.heldAfter;
                row = new ReservationRow(row.id, row.playerUuid, row.amount, "EXPIRED", row.expiresAt, row.source, row.purpose, row.idempotencyKey);
            }
            if ("CAPTURE".equals(action) && "ACTIVE".equals(row.status)) {
                if (account.balance < row.amount) return rejectedOperation(c, row.playerUuid, "RESERVATION_CAPTURED", row.amount, key, fp, source, purpose, reservationId, external, metadata, GemOperationFailure.DATA_INTEGRITY_FAILURE.name(), account.balance, account.balance, heldBefore, heldBefore);
                long heldAfter = heldBefore - row.amount;
                insertOperation(c, UUID.randomUUID(), row.playerUuid, "RESERVATION_CAPTURED", row.amount, reservationId, key, fp, source, purpose, actor, external, metadata, account.balance, account.balance - row.amount, heldBefore, heldAfter);
                Op inserted = operation(c, key);
                updateAccount(c, row.playerUuid, account.balance - row.amount, heldAfter, account.version);
                updateReservationStatus(c, reservationId, "CAPTURED", now);
                return complete(c, key, "COMPLETED", null, account.balance, account.balance - row.amount, heldBefore, heldAfter, inserted.id, reservationId);
            }
            if ("RELEASE".equals(action) && "ACTIVE".equals(row.status)) {
                long heldAfter = heldBefore - row.amount;
                UUID tx = UUID.randomUUID();
                insertOperation(c, tx, row.playerUuid, "RESERVATION_RELEASED", row.amount, reservationId, key, fp, source, purpose, actor, external, metadata, account.balance, account.balance, heldBefore, heldAfter);
                updateAccount(c, row.playerUuid, account.balance, heldAfter, account.version);
                updateReservationStatus(c, reservationId, "RELEASED", now);
                return complete(c, key, "COMPLETED", null, account.balance, account.balance, heldBefore, heldAfter, tx, reservationId);
            }
            GemOperationFailure failure = "CAPTURED".equals(row.status) && "CAPTURE".equals(action) ? GemOperationFailure.RESERVATION_ALREADY_CAPTURED
                    : "RELEASED".equals(row.status) && "RELEASE".equals(action) ? GemOperationFailure.RESERVATION_ALREADY_RELEASED
                    : "EXPIRED".equals(row.status) ? GemOperationFailure.RESERVATION_EXPIRED
                    : "CAPTURE".equals(action) ? GemOperationFailure.RESERVATION_NOT_ACTIVE : GemOperationFailure.RESERVATION_ALREADY_CAPTURED;
            return rejectedOperation(c, row.playerUuid, "RESERVATION_" + action, row.amount, key, fp, source, purpose, reservationId, external, metadata, failure.name(), account.balance, account.balance, heldBefore, heldBefore);
        });
        if (op.status.equals("COMPLETED")) {
            if ("CAPTURE".equals(action)) post(new GemReservationCapturedEvent(op.playerUuid, op.amount, source, purpose, op.id, reservationId, key, op.before, op.after, op.heldBefore, op.heldAfter));
            else post(new GemReservationReleasedEvent(op.playerUuid, op.amount, source, purpose, op.id, reservationId, key, op.before, op.after, op.heldBefore, op.heldAfter));
        }
        return result(op, action.toLowerCase() + "_success");
    }

    private Op mutateBalance(String type, UUID player, long amount, String source, String purpose, UUID actor, String key, String external, Map<String, String> metadata, boolean credit) {
        return transaction("gems." + type.toLowerCase(), c -> {
            if (credit && findAccount(c, player) == null) ensureAccount(c, player);
            Account account = lockAccountOrNull(c, player);
            String fp = fingerprint(type, player, amount, source, purpose, null, null, external, metadata);
            if (account == null) return rejectedOperation(c, player, type, amount, key, fp, source, purpose, null, external, metadata, GemOperationFailure.INSUFFICIENT_AVAILABLE_BALANCE.name(), config.balances.startingBalance, config.balances.startingBalance, 0, 0);
            Op old = operation(c, key);
            if (old != null) return compatible(old, fp, player, amount);
            long heldBefore = account.held;
            long after;
            GemOperationFailure failure = null;
            try { after = credit ? Math.addExact(account.balance, amount) : Math.subtractExact(account.balance, amount); }
            catch (ArithmeticException e) { after = account.balance; failure = GemOperationFailure.OVERFLOW; }
            if (failure == null && !credit && account.balance - heldBefore < amount) failure = GemOperationFailure.INSUFFICIENT_AVAILABLE_BALANCE;
            if (failure == null && after < 0) failure = GemOperationFailure.NEGATIVE_AMOUNT;
            if (failure == null && after > config.balances.maxBalance) failure = GemOperationFailure.MAX_BALANCE_EXCEEDED;
            if (failure != null) return rejectedOperation(c, player, type, amount, key, fp, source, purpose, null, external, metadata, failure.name(), account.balance, account.balance, heldBefore, heldBefore);
            UUID tx = UUID.randomUUID();
            insertOperation(c, tx, player, type, amount, null, key, fp, source, purpose, actor, external, metadata, account.balance, after, heldBefore, heldBefore);
            updateAccount(c, player, after, account.held, account.version);
            return complete(c, key, "COMPLETED", null, account.balance, after, heldBefore, heldBefore, tx, null);
        });
    }

    private Op rejectedOperation(Connection c, UUID player, String type, long amount, String key, String fp, String source, String purpose, UUID reservation, String external, Map<String, String> metadata, String error, long before, long after, long heldBefore, long heldAfter) throws SQLException {
        UUID tx = UUID.randomUUID();
        insertOperation(c, tx, player, type, amount, reservation, key, fp, source, purpose, null, external, metadata, before, after, heldBefore, heldAfter);
        return complete(c, key, "REJECTED", error, before, after, heldBefore, heldAfter, tx, reservation);
    }

    private Op compatible(Op old, String fp, UUID player, long amount) {
        if (old.fingerprint.equals(fp)) return old;
        return new Op(old.id, old.reservationId, player, amount, "CONFLICT", old.before, old.after, old.heldBefore, old.heldAfter, GemOperationFailure.IDEMPOTENCY_CONFLICT.name(), fp);
    }

    private Op complete(Connection c, String key, String status, String error, long before, long after, long heldBefore, long heldAfter, UUID tx, UUID reservation) throws SQLException {
        try (var s = c.prepareStatement("UPDATE bbe_gem_operations SET status=?,completed_at=?,last_error=?,balance_before=?,balance_after=?,held_before=?,held_after=? WHERE idempotency_key=? AND status='PENDING'")) {
            s.setString(1, status); s.setLong(2, System.currentTimeMillis()); s.setString(3, error); s.setLong(4, before); s.setLong(5, after); s.setLong(6, heldBefore); s.setLong(7, heldAfter); s.setString(8, key); s.executeUpdate();
        }
        Op op = operation(c, key);
        if (op == null) throw new SQLException("Gem operation disappeared");
        return new Op(tx == null ? op.id : tx, reservation == null ? op.reservationId : reservation, op.playerUuid, op.amount, status, before, after, heldBefore, heldAfter, error, op.fingerprint);
    }

    private Op complete(Connection c, String key, String status, String error, long before, long after, long heldBefore, long heldAfter) throws SQLException { return complete(c, key, status, error, before, after, heldBefore, heldAfter, null, null); }

    private Op operation(Connection c, String key) throws SQLException {
        try (var s = c.prepareStatement("SELECT id,player_uuid,amount,reservation_id,status,balance_before,balance_after,held_before,held_after,last_error,fingerprint FROM bbe_gem_operations WHERE idempotency_key=?")) {
            s.setString(1, key); try (var r = s.executeQuery()) {
                if (!r.next()) return null;
                return new Op(UUID.fromString(r.getString(1)), r.getString(4) == null ? null : UUID.fromString(r.getString(4)), UUID.fromString(r.getString(2)), r.getLong(3), r.getString(5), r.getLong(6), r.getLong(7), r.getLong(8), r.getLong(9), r.getString(10), r.getString(11));
            }
        }
    }

    private void insertOperation(Connection c, UUID id, UUID player, String type, long amount, UUID reservation, String key, String fp, String source, String purpose, UUID actor, String external, Map<String, String> metadata, long before, long after, long heldBefore, long heldAfter) throws SQLException {
        try (var s = c.prepareStatement("INSERT INTO bbe_gem_operations (id,player_uuid,operation_type,amount,reservation_id,idempotency_key,fingerprint,status,balance_before,balance_after,held_before,held_after,actor_uuid,source,purpose,external_reference,metadata_json,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            s.setString(1, id.toString()); s.setString(2, player.toString()); s.setString(3, type); s.setLong(4, amount); s.setString(5, reservation == null ? null : reservation.toString()); s.setString(6, key); s.setString(7, fp); s.setString(8, "PENDING"); s.setLong(9, before); s.setLong(10, after); s.setLong(11, heldBefore); s.setLong(12, heldAfter); s.setString(13, actor == null ? null : actor.toString()); s.setString(14, source == null ? "system" : source); s.setString(15, purpose == null ? "system" : purpose); s.setString(16, external); s.setString(17, gson.toJson(metadata == null ? Map.of() : metadata)); s.setLong(18, System.currentTimeMillis()); s.executeUpdate();
        }
    }

    private void insertReservation(Connection c, UUID id, GemReservationRequest request, String key, String fp, long created, long expires) throws SQLException {
        try (var s = c.prepareStatement("INSERT INTO bbe_gem_reservations (reservation_id,player_uuid,amount,status,source,purpose,idempotency_key,external_reference,metadata_json,created_at,expires_at,fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            s.setString(1, id.toString()); s.setString(2, request.playerUuid().toString()); s.setLong(3, request.amount()); s.setString(4, "ACTIVE"); s.setString(5, request.source()); s.setString(6, request.purpose()); s.setString(7, key); s.setString(8, request.externalReference()); s.setString(9, gson.toJson(request.metadata() == null ? Map.of() : request.metadata())); s.setLong(10, created); s.setLong(11, expires); s.setString(12, fp); s.executeUpdate();
        }
    }

    private ReservationRow lockReservation(Connection c, UUID id) throws SQLException {
        String lock = database.getType() == DatabaseType.MYSQL ? " FOR UPDATE" : "";
        try (var s = c.prepareStatement("SELECT reservation_id,player_uuid,amount,status,expires_at,source,purpose,idempotency_key FROM bbe_gem_reservations WHERE reservation_id=?" + lock)) { s.setString(1, id.toString()); try (var r = s.executeQuery()) { return r.next() ? new ReservationRow(UUID.fromString(r.getString(1)), UUID.fromString(r.getString(2)), r.getLong(3), r.getString(4), r.getLong(5), r.getString(6), r.getString(7), r.getString(8)) : null; } }
    }

    private void updateReservationStatus(Connection c, UUID id, String status, long at) throws SQLException { try (var s = c.prepareStatement("UPDATE bbe_gem_reservations SET status=?,released_at=?,captured_at=? WHERE reservation_id=?")) { s.setString(1, status); if ("RELEASED".equals(status) || "EXPIRED".equals(status)) s.setLong(2, at); else s.setNull(2, Types.BIGINT); if ("CAPTURED".equals(status)) s.setLong(3, at); else s.setNull(3, Types.BIGINT); s.setString(4, id.toString()); s.executeUpdate(); } }
    private void updateReservationExpiry(Connection c, UUID id, long expires) throws SQLException { try (var s = c.prepareStatement("UPDATE bbe_gem_reservations SET expires_at=? WHERE reservation_id=?")) { s.setLong(1, expires); s.setString(2, id.toString()); s.executeUpdate(); } }

    private Account lockAccount(Connection c, UUID id) throws SQLException { Account account = lockAccountOrNull(c, id); if (account == null) throw new SQLException("Gem account does not exist"); return account; }
    private Account lockAccountOrNull(Connection c, UUID id) throws SQLException {
        String suffix = database.getType() == DatabaseType.MYSQL ? " FOR UPDATE" : "";
        try (var s = c.prepareStatement("SELECT balance_minor,version,held_minor FROM bbe_gem_accounts WHERE player_uuid=?" + suffix)) { s.setString(1, id.toString()); try (var r = s.executeQuery()) { return r.next() ? new Account(r.getLong(1), r.getLong(2), r.getLong(3)) : null; } }
    }
    private Account findAccount(Connection c, UUID id) throws SQLException { try (var s = c.prepareStatement("SELECT balance_minor,version,held_minor FROM bbe_gem_accounts WHERE player_uuid=?")) { s.setString(1, id.toString()); try (var r = s.executeQuery()) { return r.next() ? new Account(r.getLong(1), r.getLong(2), r.getLong(3)) : null; } } }
    private void ensureAccount(Connection c, UUID id) throws SQLException { if (findAccount(c, id) != null) return; String prefix = database.getType() == DatabaseType.MYSQL ? "INSERT IGNORE" : "INSERT OR IGNORE"; try (var s = c.prepareStatement(prefix + " INTO bbe_gem_accounts (player_uuid,balance_minor,version,held_minor,created_at,updated_at) VALUES (?,?,?,?,?,?)")) { long now = System.currentTimeMillis(); s.setString(1, id.toString()); s.setLong(2, config.balances.startingBalance); s.setLong(3, 0); s.setLong(4, 0); s.setLong(5, now); s.setLong(6, now); s.executeUpdate(); } }
    private void updateAccount(Connection c, UUID id, long balance, long held, long version) throws SQLException { try (var s = c.prepareStatement("UPDATE bbe_gem_accounts SET balance_minor=?,held_minor=?,version=version+1,updated_at=? WHERE player_uuid=? AND version=?")) { s.setLong(1, balance); s.setLong(2, held); s.setLong(3, System.currentTimeMillis()); s.setString(4, id.toString()); s.setLong(5, version); if (s.executeUpdate() != 1) throw new SQLException("Gem account version conflict"); } }
    private boolean updateAccountHeld(Connection c, UUID id, long amount) throws SQLException { try (var s = c.prepareStatement("UPDATE bbe_gem_accounts SET held_minor=held_minor+?,version=version+1,updated_at=? WHERE player_uuid=? AND balance_minor-held_minor>=?")) { s.setLong(1, amount); s.setLong(2, System.currentTimeMillis()); s.setString(3, id.toString()); s.setLong(4, amount); return s.executeUpdate() == 1; } }
    private long held(UUID id) { return queryOne("SELECT held_minor FROM bbe_gem_accounts WHERE player_uuid=?", s -> s.setString(1, id.toString()), r -> r.getLong(1)); }

    private Op expireLocked(Connection c, Account account, ReservationRow row, long now) throws SQLException {
        String key = "gems:expire:" + row.id;
        String fp = fingerprint("RESERVATION_EXPIRED", row.playerUuid, row.amount, "system", "reservation_expiry", row.id, null, null, Map.of("expiresAt", Long.toString(row.expiresAt)));
        Op old = operation(c, key);
        if (old != null) return old;
        long heldAfter = Math.max(0, account.held - row.amount);
        UUID tx = UUID.randomUUID();
        insertOperation(c, tx, row.playerUuid, "RESERVATION_EXPIRED", row.amount, row.id, key, fp, "system", "reservation_expiry", null, null, Map.of("expiresAt", Long.toString(row.expiresAt)), account.balance, account.balance, account.held, heldAfter);
        updateAccount(c, row.playerUuid, account.balance, heldAfter, account.version);
        updateReservationStatus(c, row.id, "EXPIRED", now);
        return complete(c, key, "COMPLETED", null, account.balance, account.balance, account.held, heldAfter, tx, row.id);
    }

    private void expireDue(UUID onlyPlayer) {
        String where = onlyPlayer == null ? "" : " AND player_uuid=?";
        List<Op> expired = transaction("gems.expire", c -> {
            List<ReservationRow> rows = new ArrayList<>();
            try (var s = c.prepareStatement("SELECT reservation_id,player_uuid,amount,status,expires_at,source,purpose,idempotency_key FROM bbe_gem_reservations WHERE status='ACTIVE' AND expires_at<?" + where + " ORDER BY player_uuid,reservation_id")) {
                s.setLong(1, System.currentTimeMillis());
                if (onlyPlayer != null) s.setString(2, onlyPlayer.toString());
                try (var r = s.executeQuery()) {
                    while (r.next()) rows.add(new ReservationRow(UUID.fromString(r.getString(1)), UUID.fromString(r.getString(2)), r.getLong(3), r.getString(4), r.getLong(5), r.getString(6), r.getString(7), r.getString(8)));
                }
            }
            List<Op> result = new ArrayList<>();
            for (ReservationRow selected : rows) {
                Account account = lockAccountOrNull(c, selected.playerUuid);
                ReservationRow row = lockReservation(c, selected.id);
                if (account == null || row == null || !"ACTIVE".equals(row.status) || row.expiresAt > System.currentTimeMillis()) continue;
                String key = "gems:expire:" + row.id;
                String fp = fingerprint("RESERVATION_EXPIRED", row.playerUuid, row.amount, "system", "reservation_expiry", row.id, null, null, Map.of("expiresAt", Long.toString(row.expiresAt)));
                Op old = operation(c, key);
                if (old != null) {
                    updateAccount(c, row.playerUuid, account.balance, Math.max(0, account.held - row.amount), account.version);
                    updateReservationStatus(c, row.id, "EXPIRED", System.currentTimeMillis());
                    result.add(old);
                    continue;
                }
                long heldBefore = account.held;
                long heldAfter = Math.max(0, heldBefore - row.amount);
                UUID tx = UUID.randomUUID();
                insertOperation(c, tx, row.playerUuid, "RESERVATION_EXPIRED", row.amount, row.id, key, fp, "system", "reservation_expiry", null, null, Map.of("expiresAt", Long.toString(row.expiresAt)), account.balance, account.balance, heldBefore, heldAfter);
                updateAccount(c, row.playerUuid, account.balance, heldAfter, account.version);
                updateReservationStatus(c, row.id, "EXPIRED", System.currentTimeMillis());
                result.add(complete(c, key, "COMPLETED", null, account.balance, account.balance, heldBefore, heldAfter, tx, row.id));
            }
            return result;
        });
        for (Op op : expired) post(new GemReservationExpiredEvent(op.playerUuid, op.amount, "system", "reservation_expiry", op.id, op.reservationId, "gems:expire:" + op.reservationId, op.before, op.after, op.heldBefore, op.heldAfter));
    }

    private <T> T transaction(String name, com.pedrodalben.bigbangessentials.database.execution.TransactionCallback<T> callback) { return database.getExecutor().transaction(name, callback).join(); }
    private <T> Optional<T> query(String sql, com.pedrodalben.bigbangessentials.database.execution.StatementBinder binder, com.pedrodalben.bigbangessentials.database.execution.RowMapper<T> mapper) { return database.getExecutor().querySingle("gems.query", sql, binder, mapper).join(); }
    private <T> T queryOne(String sql, com.pedrodalben.bigbangessentials.database.execution.StatementBinder binder, com.pedrodalben.bigbangessentials.database.execution.RowMapper<T> mapper) { return query(sql, binder, mapper).orElseThrow(); }
    private <T> T queryOne(Connection c, String sql, com.pedrodalben.bigbangessentials.database.execution.StatementBinder binder, com.pedrodalben.bigbangessentials.database.execution.RowMapper<T> mapper) throws SQLException { try (var s = c.prepareStatement(sql)) { if (binder != null) binder.bind(s); try (var r = s.executeQuery()) { if (!r.next()) throw new SQLException("Expected query result"); return mapper.map(r); } } }

    private GemReservation mapReservation(ResultSet r) throws SQLException { Number captured = (Number) r.getObject(12), released = (Number) r.getObject(13); return new GemReservation(UUID.fromString(r.getString(1)), UUID.fromString(r.getString(2)), r.getLong(3), GemReservationStatus.valueOf(r.getString(4)), r.getString(5), r.getString(6), r.getString(7), r.getString(8), parseMetadata(r.getString(9)), r.getLong(10), r.getLong(11), captured == null ? null : captured.longValue(), released == null ? null : released.longValue()); }
    private GemTransaction mapTransaction(ResultSet r) throws SQLException { UUID tx = UUID.fromString(r.getString(1)); UUID player = UUID.fromString(r.getString(2)); UUID reservation = r.getString(5) == null ? null : UUID.fromString(r.getString(5)); UUID actor = r.getString(6) == null ? null : UUID.fromString(r.getString(6)); return new GemTransaction(tx, r.getLong(12), GemTransactionType.valueOf(r.getString(3)), player, r.getLong(4), r.getLong(13), r.getLong(14), r.getLong(15), r.getLong(16), r.getLong(13) - r.getLong(15), r.getLong(14) - r.getLong(16), actor, r.getString(7), r.getString(8), reservation, r.getString(9), r.getString(10), parseMetadata(r.getString(11))); }
    private Map<String, String> parseMetadata(String json) { try { Map<String, String> value = json == null ? null : gson.fromJson(json, new TypeToken<Map<String, String>>(){}.getType()); return value == null ? Map.of() : value; } catch (Exception e) { return Map.of(); } }

    private GemBalanceView view(UUID player, long total, long held) { return new GemBalanceView(player, total, held, total - held); }
    private GemOperationResult result(Op op, String message) { if ("COMPLETED".equals(op.status)) return GemOperationResult.succeed(op.id, op.reservationId, view(op.playerUuid, op.after, op.heldAfter), message); GemOperationFailure f; try { f = GemOperationFailure.valueOf(op.error == null ? GemOperationFailure.UNKNOWN.name() : op.error); } catch (Exception e) { f = GemOperationFailure.UNKNOWN; } return GemOperationResult.fail(f, view(op.playerUuid, op.after, op.heldAfter), message); }
    private GemOperationResult failure(GemOperationFailure failure) { return GemOperationResult.fail(failure, failure.name().toLowerCase(Locale.ROOT)); }
    private GemOperationFailure failure(String value) { try { return GemOperationFailure.valueOf(value); } catch (Exception e) { return GemOperationFailure.UNKNOWN; } }
    private String key(String requested, String operation) { return requested == null || requested.isBlank() ? "gems:auto:" + operation + ":" + UUID.randomUUID() : requested; }
    private boolean valid(String value) { return value != null && value.length() <= 64 && value.matches("[A-Za-z0-9_-]+"); }
    private String fingerprint(String operation, UUID player, long amount, String source, String purpose, UUID reservation, Duration lease, String external, Map<String, String> metadata) { StringBuilder value = new StringBuilder(operation).append('|').append(player).append('|').append(amount).append('|').append(source).append('|').append(purpose).append('|').append(reservation).append('|').append(lease == null ? "" : lease.toMillis()).append('|').append(external).append('|'); new TreeMap<>(metadata == null ? Map.of() : metadata).forEach((k, v) -> value.append(k).append('=').append(v).append(';')); try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private void post(Object event) { try { Platform.postEvent(event); } catch (Exception ignored) { } }

    private record Account(long balance, long version, long held) {}
    private record ReservationRow(UUID id, UUID playerUuid, long amount, String status, long expiresAt, String source, String purpose, String idempotencyKey) {}
    private record Op(UUID id, UUID reservationId, UUID playerUuid, long amount, String status, long before, long after, long heldBefore, long heldAfter, String error, String fingerprint) {}
}
