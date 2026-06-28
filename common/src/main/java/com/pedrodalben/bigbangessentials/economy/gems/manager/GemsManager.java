package com.pedrodalben.bigbangessentials.economy.gems.manager;

import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.config.GemConfig;
import com.pedrodalben.bigbangessentials.economy.gems.domain.*;
import com.pedrodalben.bigbangessentials.economy.gems.event.*;
import com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistence;
import com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsState;
import com.pedrodalben.bigbangessentials.util.Platform;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GemsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GemsManager.class);

    private static class SingletonHolder {
        private static final GemsManager INSTANCE = new GemsManager();
    }

    public static GemsManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public GemConfig getConfig() {
        return persistence.getConfig();
    }

    private final GemsPersistence persistence;
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock(true);
    private GemsState currentState;
    private boolean dataIntegrityError = false;
    private boolean shuttingDown = false;

    // In-memory idempotency registry mapping key to record
    private final Map<String, IdempotencyRecord> idempotencyRegistry = new ConcurrentHashMap<>();

    private ScheduledExecutorService cleanupScheduler;

    public GemsManager(File baseDir) {
        this.persistence = new GemsPersistence(baseDir);
        recover();
        startCleanupTask();
    }

    private GemsManager() {
        this.persistence = new GemsPersistence();
        recover();
        startCleanupTask();

        // Register JVM Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "Gems-Shutdown-Hook"));
    }

    public boolean isGemsEnabled() {
        return persistence.isGemsEnabled();
    }

    private void checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint expected) {
        if (com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistence.activeFailpoint == expected) {
            LOGGER.warn("TRIGGERING CRASH FAILPOINT: {}", expected);
            throw new RuntimeException("Crash Injection Failpoint: " + expected);
        }
    }

    public GemCurrencyDescriptor getCurrencyDescriptor() {
        GemConfig.Display display = persistence.getConfig().display;
        return new GemCurrencyDescriptor(
            persistence.getConfig().technicalId,
            display.symbol,
            display.singular,
            display.plural,
            display.symbolBeforeAmount,
            display.thousandsSeparator
        );
    }

    // IDEMPOTENCY RECORD MODEL
    public static class IdempotencyRecord {
        public final UUID playerUuid;
        public final long amount;
        public final UUID transactionId;
        public final UUID reservationId;
        public final boolean success;
        public final String type;
        public final String source;
        public final String purpose;

        public IdempotencyRecord(UUID playerUuid, long amount, UUID transactionId, UUID reservationId, boolean success, String type, String source, String purpose) {
            this.playerUuid = playerUuid;
            this.amount = amount;
            this.transactionId = transactionId;
            this.reservationId = reservationId;
            this.success = success;
            this.type = type;
            this.source = source;
            this.purpose = purpose;
        }
    }

    // CORE BALANCE QUERY (Must be called under stateLock)
    private long getBalanceTotal(GemsState state, UUID playerUuid) {
        return state.balances.getOrDefault(playerUuid.toString(), persistence.getConfig().balances.startingBalance);
    }

    private long calculateHeldBalance(GemsState state, UUID playerUuid) {
        long held = 0;
        for (GemReservation res : state.reservations.values()) {
            if (playerUuid.equals(res.getPlayerUuid()) && res.getStatus() == GemReservationStatus.ACTIVE) {
                held += res.getAmount();
            }
        }
        return held;
    }

    public String format(long amount) {
        if (!isGemsEnabled()) {
            return amount + " ✦";
        }
        GemConfig.Display display = persistence.getConfig().display;
        String formattedAmount;
        if (".".equals(display.thousandsSeparator)) {
            formattedAmount = String.format(Locale.GERMANY, "%,d", amount);
        } else if (",".equals(display.thousandsSeparator)) {
            formattedAmount = String.format(Locale.US, "%,d", amount);
        } else {
            formattedAmount = String.format(Locale.getDefault(), "%,d", amount);
            if (display.thousandsSeparator != null && !display.thousandsSeparator.isEmpty()) {
                char groupingSeparator = new java.text.DecimalFormatSymbols(Locale.getDefault()).getGroupingSeparator();
                formattedAmount = formattedAmount.replace(String.valueOf(groupingSeparator), display.thousandsSeparator);
            }
        }

        if (display.symbolBeforeAmount) {
            return display.symbol + " " + formattedAmount;
        } else {
            return formattedAmount + " " + display.symbol;
        }
    }

    public GemBalanceView getBalanceView(UUID playerUuid) {
        stateLock.readLock().lock();
        try {
            long total = getBalanceTotal(currentState, playerUuid);
            long held = calculateHeldBalance(currentState, playerUuid);
            long available = total - held;
            return new GemBalanceView(playerUuid, total, held, available);
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public List<GemReservation> getActiveReservations(UUID playerUuid) {
        stateLock.readLock().lock();
        try {
            List<GemReservation> list = new ArrayList<>();
            for (GemReservation res : currentState.reservations.values()) {
                if (playerUuid.equals(res.getPlayerUuid()) && res.getStatus() == GemReservationStatus.ACTIVE) {
                    list.add(res);
                }
            }
            return list;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public boolean hasAvailable(UUID playerUuid, long amount) {
        if (amount < 0) return false;
        stateLock.readLock().lock();
        try {
            long total = getBalanceTotal(currentState, playerUuid);
            long held = calculateHeldBalance(currentState, playerUuid);
            return (total - held) >= amount;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    // API OPERATIONS

    public GemOperationResult credit(GemCreditRequest request) {
        if (shuttingDown) return GemOperationResult.fail(GemOperationFailure.SHUTTING_DOWN, "Server is shutting down");
        if (!isGemsEnabled()) return GemOperationResult.fail(GemOperationFailure.DISABLED, "Gems system is disabled");
        if (dataIntegrityError) return GemOperationResult.fail(GemOperationFailure.DATA_INTEGRITY_FAILURE, "Gems state integrity check failed");

        if (request.amount() <= 0) return GemOperationResult.fail(GemOperationFailure.INVALID_AMOUNT, "Amount must be greater than zero");
        if (!isValidIdentifier(request.source())) return GemOperationResult.fail(GemOperationFailure.UNAUTHORIZED_SOURCE, "Invalid source format");
        if (!isValidIdentifier(request.purpose())) return GemOperationResult.fail(GemOperationFailure.UNKNOWN, "Invalid purpose format");

        stateLock.writeLock().lock();
        try {
            // Compute fingerprint early (before idempotency check) for validation
            String fingerprint = request.idempotencyKey() != null ? computeFingerprint(
                "CREDIT", request.playerUuid(), request.amount(),
                request.source(), request.purpose(), null, null, request.externalReference(), request.metadata()) : null;

            // Idempotency check with persisted state fallback and fingerprint validation
            if (request.idempotencyKey() != null) {
                IdempotencyRecord record = checkIdempotencyWithStateFallback(request.idempotencyKey(), request.playerUuid(), request.amount(), "CREDIT");
                if (record != null) {
                    if (record.playerUuid.equals(request.playerUuid()) && record.amount == request.amount() && "CREDIT".equals(record.type)) {
                        // Validate fingerprint: same key + same fingerprint → idempotent success
                        if (isFingerprintMatching(request.idempotencyKey(), fingerprint)) {
                            GemBalanceView view = getBalanceView(request.playerUuid());
                            return GemOperationResult.succeed(record.transactionId, null, view, "idempotent_success");
                        }
                        // Same key + different fingerprint → conflict
                        return GemOperationResult.fail(GemOperationFailure.IDEMPOTENCY_CONFLICT, "Idempotency key fingerprint mismatch");
                    }
                    return GemOperationResult.fail(GemOperationFailure.IDEMPOTENCY_CONFLICT, "Idempotency key conflict");
                }
            }

            long currentTotal = getBalanceTotal(currentState, request.playerUuid());
            long newTotal;
            try {
                newTotal = Math.addExact(currentTotal, request.amount());
            } catch (ArithmeticException e) {
                return GemOperationResult.fail(GemOperationFailure.OVERFLOW, "Balance overflow detected");
            }

            if (newTotal > persistence.getConfig().balances.maxBalance) {
                return GemOperationResult.fail(GemOperationFailure.MAX_BALANCE_EXCEEDED, "Transaction exceeds maximum allowed balance");
            }
            long held = calculateHeldBalance(currentState, request.playerUuid());

            // Clone state for transaction isolation (Copy-on-Write)
            GemsState nextState = currentState.cloneState();
            nextState.balances.put(request.playerUuid().toString(), newTotal);
            UUID transactionId = UUID.randomUUID();
            long timestamp = System.currentTimeMillis();

            // Persist idempotency record and pending audit entry in state BEFORE disk write
            if (request.idempotencyKey() != null) {
                nextState.idempotencyRecords.put(request.idempotencyKey(),
                    new GemsState.IdempotencyPersistedRecord(
                        transactionId.toString(), "CREDIT", fingerprint,
                        request.playerUuid(), request.amount(), null, "SUCCESS", timestamp));
            }
            addPendingAuditEntry(nextState, transactionId, nextState.revision + 1, "CREDIT",
                request.playerUuid(), request.amount(), currentTotal, newTotal,
                held, held, null, request.source(), request.purpose(),
                request.idempotencyKey(), fingerprint, request.externalReference(), request.actorUuid(), timestamp);

            // Save state first (disk authoritative write)
            persistence.saveState(nextState);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_CACHE_SWAP);

            // Once write succeeds, update in-memory reference and log ledger
            currentState = nextState;

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_CACHE_SWAP);

            GemTransaction tx = new GemTransaction(
                transactionId, timestamp, GemTransactionType.CREDIT, request.playerUuid(), request.amount(),
                currentTotal, newTotal, held, held, currentTotal - held, newTotal - held,
                request.actorUuid(), request.source(), request.purpose(), null,
                request.idempotencyKey(), request.externalReference(), request.metadata()
            );

            persistence.appendTransaction(tx);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_APPEND_LEDGER);

            // Ledger append succeeded — reconcile the pending audit entry
            reconcilePendingAuditEntry(transactionId);

            // Save to idempotency registry
            if (request.idempotencyKey() != null) {
                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_IDEMPOTENCY_REGISTRY_UPDATE);
                idempotencyRegistry.put(request.idempotencyKey(), new IdempotencyRecord(
                    request.playerUuid(), request.amount(), transactionId, null, true, "CREDIT", request.source(), request.purpose()
                ));
                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_IDEMPOTENCY_REGISTRY_UPDATE);
            }

            GemBalanceView newView = new GemBalanceView(request.playerUuid(), newTotal, held, newTotal - held);

            // Post event
            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_EVENT_PUBLISH);
            postEventSafely(new GemBalanceChangedEvent(
                request.playerUuid(), request.amount(), request.source(), request.purpose(),
                transactionId, null, request.idempotencyKey(),
                currentTotal, newTotal, held, held
            ));
            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_EVENT_PUBLISH);

            return GemOperationResult.succeed(transactionId, null, newView, "credit_success");

        } catch (Exception e) {
            LOGGER.error("Credit operation failed", e);
            return GemOperationResult.fail(GemOperationFailure.PERSISTENCE_FAILURE, "Failed to persist transaction");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public GemOperationResult debit(GemDebitRequest request) {
        if (shuttingDown) return GemOperationResult.fail(GemOperationFailure.SHUTTING_DOWN, "Server is shutting down");
        if (!isGemsEnabled()) return GemOperationResult.fail(GemOperationFailure.DISABLED, "Gems system is disabled");
        if (dataIntegrityError) return GemOperationResult.fail(GemOperationFailure.DATA_INTEGRITY_FAILURE, "Gems state integrity check failed");

        if (request.amount() <= 0) return GemOperationResult.fail(GemOperationFailure.INVALID_AMOUNT, "Amount must be greater than zero");
        if (!isValidIdentifier(request.source())) return GemOperationResult.fail(GemOperationFailure.UNAUTHORIZED_SOURCE, "Invalid source format");
        if (!isValidIdentifier(request.purpose())) return GemOperationResult.fail(GemOperationFailure.UNKNOWN, "Invalid purpose format");

        stateLock.writeLock().lock();
        try {
            // Compute fingerprint early for idempotency validation
            String fingerprint = request.idempotencyKey() != null ? computeFingerprint(
                "DEBIT", request.playerUuid(), request.amount(),
                request.source(), request.purpose(), null, null, request.externalReference(), request.metadata()) : null;

            // Idempotency check with persisted state fallback and fingerprint validation
            if (request.idempotencyKey() != null) {
                IdempotencyRecord record = checkIdempotencyWithStateFallback(request.idempotencyKey(), request.playerUuid(), request.amount(), "DEBIT");
                if (record != null) {
                    if (record.playerUuid.equals(request.playerUuid()) && record.amount == request.amount() && "DEBIT".equals(record.type)) {
                        if (isFingerprintMatching(request.idempotencyKey(), fingerprint)) {
                            GemBalanceView view = getBalanceView(request.playerUuid());
                            return GemOperationResult.succeed(record.transactionId, null, view, "idempotent_success");
                        }
                        return GemOperationResult.fail(GemOperationFailure.IDEMPOTENCY_CONFLICT, "Idempotency key fingerprint mismatch");
                    }
                    return GemOperationResult.fail(GemOperationFailure.IDEMPOTENCY_CONFLICT, "Idempotency key conflict");
                }
            }

            long currentTotal = getBalanceTotal(currentState, request.playerUuid());
            long held = calculateHeldBalance(currentState, request.playerUuid());
            long available = currentTotal - held;

            if (available < request.amount()) {
                return GemOperationResult.fail(GemOperationFailure.INSUFFICIENT_AVAILABLE_BALANCE, "Insufficient available balance");
            }

            long newTotal;
            try {
                newTotal = Math.subtractExact(currentTotal, request.amount());
            } catch (ArithmeticException e) {
                return GemOperationResult.fail(GemOperationFailure.NEGATIVE_AMOUNT, "Balance went negative");
            }

            // Clone state for transaction isolation (Copy-on-Write)
            GemsState nextState = currentState.cloneState();
            nextState.balances.put(request.playerUuid().toString(), newTotal);
            UUID transactionId = UUID.randomUUID();
            long timestamp = System.currentTimeMillis();

            // Persist idempotency record and pending audit entry in state BEFORE disk write
            if (request.idempotencyKey() != null) {
                nextState.idempotencyRecords.put(request.idempotencyKey(),
                    new GemsState.IdempotencyPersistedRecord(
                        transactionId.toString(), "DEBIT", fingerprint,
                        request.playerUuid(), request.amount(), null, "SUCCESS", timestamp));
            }
            addPendingAuditEntry(nextState, transactionId, nextState.revision + 1, "DEBIT",
                request.playerUuid(), request.amount(), currentTotal, newTotal,
                held, held, null, request.source(), request.purpose(),
                request.idempotencyKey(), fingerprint, request.externalReference(), request.actorUuid(), timestamp);

            // Save state first (disk authoritative write)
            persistence.saveState(nextState);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_CACHE_SWAP);

            // Once write succeeds, update in-memory reference
            currentState = nextState;

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_CACHE_SWAP);

            GemTransaction tx = new GemTransaction(
                transactionId, timestamp, GemTransactionType.DEBIT, request.playerUuid(), request.amount(),
                currentTotal, newTotal, held, held, available, newTotal - held,
                request.actorUuid(), request.source(), request.purpose(), null,
                request.idempotencyKey(), request.externalReference(), request.metadata()
            );

            persistence.appendTransaction(tx);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_APPEND_LEDGER);

            // Ledger append succeeded — reconcile the pending audit entry
            reconcilePendingAuditEntry(transactionId);

            // Save to idempotency registry
            if (request.idempotencyKey() != null) {
                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_IDEMPOTENCY_REGISTRY_UPDATE);
                idempotencyRegistry.put(request.idempotencyKey(), new IdempotencyRecord(
                    request.playerUuid(), request.amount(), transactionId, null, true, "DEBIT", request.source(), request.purpose()
                ));
                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_IDEMPOTENCY_REGISTRY_UPDATE);
            }

            GemBalanceView newView = new GemBalanceView(request.playerUuid(), newTotal, held, newTotal - held);

            // Post event
            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_EVENT_PUBLISH);
            postEventSafely(new GemBalanceChangedEvent(
                request.playerUuid(), request.amount(), request.source(), request.purpose(),
                transactionId, null, request.idempotencyKey(),
                currentTotal, newTotal, held, held
            ));
            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_EVENT_PUBLISH);

            return GemOperationResult.succeed(transactionId, null, newView, "debit_success");

        } catch (Exception e) {
            LOGGER.error("Debit operation failed", e);
            return GemOperationResult.fail(GemOperationFailure.PERSISTENCE_FAILURE, "Failed to persist transaction");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public GemOperationResult setBalance(GemSetBalanceRequest request) {
        if (shuttingDown) return GemOperationResult.fail(GemOperationFailure.SHUTTING_DOWN, "Server is shutting down");
        if (!isGemsEnabled()) return GemOperationResult.fail(GemOperationFailure.DISABLED, "Gems system is disabled");
        if (dataIntegrityError) return GemOperationResult.fail(GemOperationFailure.DATA_INTEGRITY_FAILURE, "Gems state integrity check failed");

        if (request.amount() < 0) return GemOperationResult.fail(GemOperationFailure.INVALID_AMOUNT, "Amount cannot be negative");
        if (request.amount() > persistence.getConfig().balances.maxBalance) {
            return GemOperationResult.fail(GemOperationFailure.MAX_BALANCE_EXCEEDED, "Exceeds maximum allowed balance");
        }

        stateLock.writeLock().lock();
        try {
            long currentTotal = getBalanceTotal(currentState, request.playerUuid());
            long held = calculateHeldBalance(currentState, request.playerUuid());

            if (request.amount() < held) {
                return GemOperationResult.fail(GemOperationFailure.INSUFFICIENT_AVAILABLE_BALANCE, "Cannot set balance below held (reserved) balance");
            }

            // Clone state for transaction isolation (Copy-on-Write)
            GemsState nextState = currentState.cloneState();
            nextState.balances.put(request.playerUuid().toString(), request.amount());
            UUID transactionId = UUID.randomUUID();
            long timestamp = System.currentTimeMillis();

            // Add pending audit entry (admin set is a financial mutation)
            addPendingAuditEntry(nextState, transactionId, nextState.revision + 1, "ADMIN_SET",
                request.playerUuid(), request.amount(), currentTotal, request.amount(),
                held, held, null, request.source(), request.purpose(),
                null, null, null, request.actorUuid(), timestamp);

            // Save state first (disk authoritative write)
            persistence.saveState(nextState);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_CACHE_SWAP);

            // Once write succeeds, update in-memory reference and log ledger
            currentState = nextState;

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_CACHE_SWAP);

            GemTransaction tx = new GemTransaction(
                transactionId, timestamp, GemTransactionType.ADMIN_SET, request.playerUuid(), request.amount(),
                currentTotal, request.amount(), held, held, currentTotal - held, request.amount() - held,
                request.actorUuid(), request.source() != null ? request.source() : "admin-command",
                request.purpose() != null ? request.purpose() : "ADMIN_SET", null,
                null, null, request.metadata()
            );

            persistence.appendTransaction(tx);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_APPEND_LEDGER);

            reconcilePendingAuditEntry(transactionId);

            GemBalanceView newView = new GemBalanceView(request.playerUuid(), request.amount(), held, request.amount() - held);

            // Post event
            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_EVENT_PUBLISH);
            postEventSafely(new GemBalanceChangedEvent(
                request.playerUuid(), request.amount(), tx.source(), tx.purpose(),
                transactionId, null, null,
                currentTotal, request.amount(), held, held
            ));
            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_EVENT_PUBLISH);

            return GemOperationResult.succeed(transactionId, null, newView, "set_success");

        } catch (Exception e) {
            LOGGER.error("Set balance operation failed", e);
            return GemOperationResult.fail(GemOperationFailure.PERSISTENCE_FAILURE, "Failed to persist transaction");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public GemReservationResult reserve(GemReservationRequest request) {
        if (shuttingDown) return GemReservationResult.fail(GemOperationFailure.SHUTTING_DOWN, "Server is shutting down");
        if (!isGemsEnabled()) return GemReservationResult.fail(GemOperationFailure.DISABLED, "Gems system is disabled");
        if (dataIntegrityError) return GemReservationResult.fail(GemOperationFailure.DATA_INTEGRITY_FAILURE, "Gems state integrity check failed");

        if (request.amount() <= 0) return GemReservationResult.fail(GemOperationFailure.INVALID_AMOUNT, "Amount must be greater than zero");
        if (!isValidIdentifier(request.source())) return GemReservationResult.fail(GemOperationFailure.UNAUTHORIZED_SOURCE, "Invalid source format");
        if (!isValidIdentifier(request.purpose())) return GemReservationResult.fail(GemOperationFailure.UNKNOWN, "Invalid purpose format");

        // Validate lease duration
        long defaultLease = persistence.getConfig().reservations.defaultLeaseSeconds;
        long maxLease = persistence.getConfig().reservations.maxLeaseSeconds;
        long leaseSeconds = request.lease() != null ? request.lease().toSeconds() : defaultLease;

        if (leaseSeconds <= 0 || leaseSeconds > maxLease) {
            return GemReservationResult.fail(GemOperationFailure.INVALID_LEASE, "Lease duration is invalid or exceeds max limit");
        }

        stateLock.writeLock().lock();
        try {
            // Compute fingerprint early for idempotency validation
            String fingerprint = request.idempotencyKey() != null ? computeFingerprint(
                "RESERVE", request.playerUuid(), request.amount(),
                request.source(), request.purpose(), null, request.lease(), request.externalReference(), request.metadata()) : null;

            // Idempotency check with persisted state fallback and fingerprint validation
            if (request.idempotencyKey() != null) {
                IdempotencyRecord record = checkIdempotencyWithStateFallback(request.idempotencyKey(), request.playerUuid(), request.amount(), "RESERVE");
                if (record != null) {
                    if (record.playerUuid.equals(request.playerUuid()) && record.amount == request.amount() && "RESERVE".equals(record.type) && record.reservationId != null) {
                        if (isFingerprintMatching(request.idempotencyKey(), fingerprint)) {
                            GemBalanceView view = getBalanceView(request.playerUuid());
                            return GemReservationResult.succeed(record.reservationId, view, "idempotent_success");
                        }
                        return GemReservationResult.fail(GemOperationFailure.IDEMPOTENCY_CONFLICT, "Idempotency key fingerprint mismatch");
                    }
                    return GemReservationResult.fail(GemOperationFailure.IDEMPOTENCY_CONFLICT, "Idempotency key conflict");
                }
            }

            long currentTotal = getBalanceTotal(currentState, request.playerUuid());
            long heldBefore = calculateHeldBalance(currentState, request.playerUuid());
            long availableBefore = currentTotal - heldBefore;

            if (availableBefore < request.amount()) {
                return GemReservationResult.fail(GemOperationFailure.INSUFFICIENT_AVAILABLE_BALANCE, "Insufficient available balance");
            }

            UUID reservationId = UUID.randomUUID();
            long timestamp = System.currentTimeMillis();
            long expiresAt = timestamp + (leaseSeconds * 1000L);

            GemReservation reservation = new GemReservation(
                reservationId, request.playerUuid(), request.amount(), GemReservationStatus.ACTIVE,
                request.source(), request.purpose(), request.idempotencyKey(), request.externalReference(),
                request.metadata(), timestamp, expiresAt, null, null
            );

            // Clone state for transaction isolation (Copy-on-Write)
            GemsState nextState = currentState.cloneState();
            nextState.reservations.put(reservationId.toString(), reservation);

            // Generate transactionId FIRST — same UUID used for idempotency, pending audit, and ledger
            UUID transactionId = UUID.randomUUID();
            long heldAfter = heldBefore + request.amount();
            long availableAfter = currentTotal - heldAfter;

            // Persist idempotency record in state BEFORE disk write (fingerprint already computed)
            if (request.idempotencyKey() != null) {
                nextState.idempotencyRecords.put(request.idempotencyKey(),
                    new GemsState.IdempotencyPersistedRecord(
                        transactionId.toString(), "RESERVE", fingerprint,
                        request.playerUuid(), request.amount(), reservationId, "SUCCESS", timestamp));
            }
            addPendingAuditEntry(nextState, transactionId, nextState.revision + 1, "RESERVATION_CREATED",
                request.playerUuid(), request.amount(), currentTotal, currentTotal,
                heldBefore, heldAfter, reservationId, request.source(), request.purpose(),
                request.idempotencyKey(), fingerprint, request.externalReference(), null, timestamp);

            // Save state first (disk authoritative write)
            persistence.saveState(nextState);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_CACHE_SWAP);

            // Once write succeeds, update in-memory reference
            currentState = nextState;

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_CACHE_SWAP);

            // Log ledger (same transactionId as idempotency record)
            GemTransaction tx = new GemTransaction(
                transactionId, timestamp, GemTransactionType.RESERVATION_CREATED, request.playerUuid(), request.amount(),
                currentTotal, currentTotal, heldBefore, heldAfter, availableBefore, availableAfter,
                null, request.source(), request.purpose(), reservationId,
                request.idempotencyKey(), request.externalReference(), request.metadata()
            );

            persistence.appendTransaction(tx);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_APPEND_LEDGER);

            reconcilePendingAuditEntry(transactionId);

            // Save to idempotency registry
            if (request.idempotencyKey() != null) {
                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_IDEMPOTENCY_REGISTRY_UPDATE);
                idempotencyRegistry.put(request.idempotencyKey(), new IdempotencyRecord(
                    request.playerUuid(), request.amount(), transactionId, reservationId, true, "RESERVE", request.source(), request.purpose()
                ));
                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_IDEMPOTENCY_REGISTRY_UPDATE);
            }

            GemBalanceView newView = new GemBalanceView(request.playerUuid(), currentTotal, heldAfter, availableAfter);

            // Post event
            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_EVENT_PUBLISH);
            postEventSafely(new GemReservationCreatedEvent(
                request.playerUuid(), request.amount(), request.source(), request.purpose(),
                transactionId, reservationId, request.idempotencyKey(),
                currentTotal, currentTotal, heldBefore, heldAfter
            ));
            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_EVENT_PUBLISH);

            return GemReservationResult.succeed(reservationId, newView, "reserve_success");

        } catch (Exception e) {
            LOGGER.error("Reservation operation failed", e);
            return GemReservationResult.fail(GemOperationFailure.PERSISTENCE_FAILURE, "Failed to persist transaction");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public GemOperationResult capture(GemCaptureRequest request) {
        if (shuttingDown) return GemOperationResult.fail(GemOperationFailure.SHUTTING_DOWN, "Server is shutting down");
        if (!isGemsEnabled()) return GemOperationResult.fail(GemOperationFailure.DISABLED, "Gems system is disabled");
        if (dataIntegrityError) return GemOperationResult.fail(GemOperationFailure.DATA_INTEGRITY_FAILURE, "Gems state integrity check failed");

        stateLock.writeLock().lock();
        try {
            GemReservation reservation = currentState.reservations.get(request.reservationId().toString());
            if (reservation == null) {
                return GemOperationResult.fail(GemOperationFailure.RESERVATION_NOT_FOUND, "Reservation not found");
            }

            // Compute fingerprint early for idempotency validation
            String fingerprint = request.idempotencyKey() != null ? computeFingerprint(
                "CAPTURE", reservation.getPlayerUuid(), reservation.getAmount(),
                request.source(), request.purpose(), request.reservationId(), null, request.externalReference(), request.metadata()) : null;

            // Idempotency check for capture with persisted state fallback and fingerprint validation
            if (request.idempotencyKey() != null) {
                IdempotencyRecord record = checkIdempotencyWithStateFallback(request.idempotencyKey(), reservation.getPlayerUuid(), reservation.getAmount(), "CAPTURE");
                if (record != null) {
                    if (record.reservationId.equals(request.reservationId()) && "CAPTURE".equals(record.type)) {
                        if (isFingerprintMatching(request.idempotencyKey(), fingerprint)) {
                            GemBalanceView view = getBalanceView(reservation.getPlayerUuid());
                            return GemOperationResult.succeed(record.transactionId, request.reservationId(), view, "idempotent_success");
                        }
                        return GemOperationResult.fail(GemOperationFailure.IDEMPOTENCY_CONFLICT, "Idempotency key fingerprint mismatch");
                    }
                    return GemOperationResult.fail(GemOperationFailure.IDEMPOTENCY_CONFLICT, "Idempotency key conflict");
                }
            }

            // Check reservation status transitions
            if (reservation.getStatus() == GemReservationStatus.CAPTURED) {
                // If it is already captured, but didn't match the same idempotency key above (or was done without key),
                // we return success directly (as capture must be idempotent)
                GemBalanceView view = getBalanceView(reservation.getPlayerUuid());
                return GemOperationResult.succeed(UUID.randomUUID(), request.reservationId(), view, "already_captured");
            }

            if (reservation.getStatus() == GemReservationStatus.RELEASED) {
                return GemOperationResult.fail(GemOperationFailure.RESERVATION_ALREADY_RELEASED, "Reservation is already released and cannot be captured");
            }

            if (reservation.getStatus() == GemReservationStatus.EXPIRED) {
                return GemOperationResult.fail(GemOperationFailure.RESERVATION_EXPIRED, "Reservation has expired and cannot be captured");
            }

            if (reservation.getStatus() != GemReservationStatus.ACTIVE) {
                return GemOperationResult.fail(GemOperationFailure.RESERVATION_NOT_ACTIVE, "Reservation is not active");
            }

            // Validation: "reserva maior que saldo: registrar inconsistência e bloquear captura"
            long currentTotal = getBalanceTotal(currentState, reservation.getPlayerUuid());
            if (reservation.getAmount() > currentTotal) {
                LOGGER.error("Data inconsistency: Reservation {} amount ({}) is greater than player {} total balance ({}). Blocking capture.",
                             reservation.getReservationId(), reservation.getAmount(), reservation.getPlayerUuid(), currentTotal);
                return GemOperationResult.fail(GemOperationFailure.DATA_INTEGRITY_FAILURE, "Inconsistency: reservation amount greater than total balance");
            }

            long heldBefore = calculateHeldBalance(currentState, reservation.getPlayerUuid());
            long newTotal = currentTotal - reservation.getAmount();

            // Clone state for transaction isolation (Copy-on-Write)
            GemsState nextState = currentState.cloneState();
            GemReservation nextReservation = nextState.reservations.get(request.reservationId().toString());
            nextReservation.setStatus(GemReservationStatus.CAPTURED);
            nextReservation.setCapturedAt(System.currentTimeMillis());
            nextState.balances.put(nextReservation.getPlayerUuid().toString(), newTotal);

            // Generate transactionId FIRST — same UUID used everywhere
            UUID transactionId = UUID.randomUUID();
            long timestamp = System.currentTimeMillis();
            long heldAfter = heldBefore - reservation.getAmount();

            // Persist idempotency record in state BEFORE disk write (fingerprint already computed)
            if (request.idempotencyKey() != null) {
                nextState.idempotencyRecords.put(request.idempotencyKey(),
                    new GemsState.IdempotencyPersistedRecord(
                        transactionId.toString(), "CAPTURE", fingerprint,
                        reservation.getPlayerUuid(), reservation.getAmount(), reservation.getReservationId(), "SUCCESS", timestamp));
            }
            addPendingAuditEntry(nextState, transactionId, nextState.revision + 1, "RESERVATION_CAPTURED",
                reservation.getPlayerUuid(), reservation.getAmount(), currentTotal, newTotal,
                heldBefore, heldAfter, reservation.getReservationId(), request.source(), request.purpose(),
                request.idempotencyKey(), fingerprint, request.externalReference(), request.actorUuid(), timestamp);

            // Save state first (disk authoritative write)
            persistence.saveState(nextState);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_CACHE_SWAP);

            // Once write succeeds, update in-memory reference
            currentState = nextState;

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_CACHE_SWAP);

            // Log ledger
            GemTransaction tx = new GemTransaction(
                transactionId, timestamp, GemTransactionType.RESERVATION_CAPTURED, reservation.getPlayerUuid(), reservation.getAmount(),
                currentTotal, newTotal, heldBefore, heldAfter, currentTotal - heldBefore, newTotal - heldAfter,
                request.actorUuid(), request.source(), request.purpose(), reservation.getReservationId(),
                request.idempotencyKey(), request.externalReference(), request.metadata()
            );

            persistence.appendTransaction(tx);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_APPEND_LEDGER);

            reconcilePendingAuditEntry(transactionId);

            // Save to idempotency registry
            if (request.idempotencyKey() != null) {
                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_IDEMPOTENCY_REGISTRY_UPDATE);
                idempotencyRegistry.put(request.idempotencyKey(), new IdempotencyRecord(
                    reservation.getPlayerUuid(), reservation.getAmount(), transactionId, reservation.getReservationId(), true, "CAPTURE", request.source(), request.purpose()
                ));
                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_IDEMPOTENCY_REGISTRY_UPDATE);
            }

            GemBalanceView newView = new GemBalanceView(reservation.getPlayerUuid(), newTotal, heldAfter, newTotal - heldAfter);

            // Post event
            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_EVENT_PUBLISH);
            postEventSafely(new GemReservationCapturedEvent(
                reservation.getPlayerUuid(), reservation.getAmount(), request.source(), request.purpose(),
                transactionId, reservation.getReservationId(), request.idempotencyKey(),
                currentTotal, newTotal, heldBefore, heldAfter
            ));
            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_EVENT_PUBLISH);

            return GemOperationResult.succeed(transactionId, reservation.getReservationId(), newView, "capture_success");

        } catch (Exception e) {
            LOGGER.error("Capture operation failed", e);
            return GemOperationResult.fail(GemOperationFailure.PERSISTENCE_FAILURE, "Failed to persist transaction");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public GemOperationResult release(GemReleaseRequest request) {
        if (shuttingDown) return GemOperationResult.fail(GemOperationFailure.SHUTTING_DOWN, "Server is shutting down");
        if (!isGemsEnabled()) return GemOperationResult.fail(GemOperationFailure.DISABLED, "Gems system is disabled");
        if (dataIntegrityError) return GemOperationResult.fail(GemOperationFailure.DATA_INTEGRITY_FAILURE, "Gems state integrity check failed");

        if (request.source() != null && !isValidIdentifier(request.source())) {
            return GemOperationResult.fail(GemOperationFailure.UNAUTHORIZED_SOURCE, "Invalid source format");
        }
        if (request.purpose() != null && !isValidIdentifier(request.purpose())) {
            return GemOperationResult.fail(GemOperationFailure.UNKNOWN, "Invalid purpose format");
        }

        stateLock.writeLock().lock();
        try {
            GemReservation reservation = currentState.reservations.get(request.reservationId().toString());
            if (reservation == null) {
                return GemOperationResult.fail(GemOperationFailure.RESERVATION_NOT_FOUND, "Reservation not found");
            }

            // Compute fingerprint early for idempotency validation
            String fingerprint = request.idempotencyKey() != null ? computeFingerprint(
                "RELEASE", reservation.getPlayerUuid(), reservation.getAmount(),
                request.source(), request.purpose(), request.reservationId(), null, request.externalReference(), request.metadata()) : null;

            // Idempotency check by key with persisted state fallback and fingerprint validation
            if (request.idempotencyKey() != null) {
                IdempotencyRecord record = checkIdempotencyWithStateFallback(request.idempotencyKey(), reservation.getPlayerUuid(), reservation.getAmount(), "RELEASE");
                if (record != null) {
                    if (record.reservationId.equals(request.reservationId()) && "RELEASE".equals(record.type)) {
                        if (isFingerprintMatching(request.idempotencyKey(), fingerprint)) {
                            GemBalanceView view = getBalanceView(reservation.getPlayerUuid());
                            return GemOperationResult.succeed(record.transactionId, request.reservationId(), view, "idempotent_success");
                        }
                        return GemOperationResult.fail(GemOperationFailure.IDEMPOTENCY_CONFLICT, "Idempotency key fingerprint mismatch");
                    }
                    return GemOperationResult.fail(GemOperationFailure.IDEMPOTENCY_CONFLICT, "Idempotency key conflict");
                }
            }

            // Release is idempotent by status: if already released, return success
            if (reservation.getStatus() == GemReservationStatus.RELEASED) {
                GemBalanceView view = getBalanceView(reservation.getPlayerUuid());
                return GemOperationResult.succeed(UUID.randomUUID(), request.reservationId(), view, "already_released");
            }

            if (reservation.getStatus() == GemReservationStatus.CAPTURED) {
                return GemOperationResult.fail(GemOperationFailure.RESERVATION_ALREADY_CAPTURED, "Reservation is already captured and cannot be released");
            }

            if (reservation.getStatus() == GemReservationStatus.EXPIRED) {
                GemBalanceView view = getBalanceView(reservation.getPlayerUuid());
                return GemOperationResult.succeed(UUID.randomUUID(), request.reservationId(), view, "expired_cannot_release");
            }

            if (reservation.getStatus() != GemReservationStatus.ACTIVE) {
                return GemOperationResult.fail(GemOperationFailure.RESERVATION_NOT_ACTIVE, "Reservation is not active");
            }

            long currentTotal = getBalanceTotal(currentState, reservation.getPlayerUuid());
            long heldBefore = calculateHeldBalance(currentState, reservation.getPlayerUuid());

            // Clone state for transaction isolation (Copy-on-Write)
            GemsState nextState = currentState.cloneState();
            GemReservation nextReservation = nextState.reservations.get(request.reservationId().toString());
            nextReservation.setStatus(GemReservationStatus.RELEASED);
            nextReservation.setReleasedAt(System.currentTimeMillis());

            // Generate transactionId FIRST — same UUID used everywhere
            UUID transactionId = UUID.randomUUID();
            long timestamp = System.currentTimeMillis();
            long heldAfter = heldBefore - reservation.getAmount();

            // Persist idempotency record in state BEFORE disk write (fingerprint already computed)
            if (request.idempotencyKey() != null) {
                nextState.idempotencyRecords.put(request.idempotencyKey(),
                    new GemsState.IdempotencyPersistedRecord(
                        transactionId.toString(), "RELEASE", fingerprint,
                        reservation.getPlayerUuid(), reservation.getAmount(), reservation.getReservationId(), "SUCCESS", timestamp));
            }
            addPendingAuditEntry(nextState, transactionId, nextState.revision + 1, "RESERVATION_RELEASED",
                reservation.getPlayerUuid(), reservation.getAmount(), currentTotal, currentTotal,
                heldBefore, heldAfter, reservation.getReservationId(), request.source(), request.purpose(),
                request.idempotencyKey(), fingerprint, request.externalReference(), request.actorUuid(), timestamp);

            // Save state first (disk authoritative write)
            persistence.saveState(nextState);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_CACHE_SWAP);

            // Once write succeeds, update in-memory reference
            currentState = nextState;

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_CACHE_SWAP);

            // Log ledger
            GemTransaction tx = new GemTransaction(
                transactionId, timestamp, GemTransactionType.RESERVATION_RELEASED, reservation.getPlayerUuid(), reservation.getAmount(),
                currentTotal, currentTotal, heldBefore, heldAfter, currentTotal - heldBefore, currentTotal - heldAfter,
                request.actorUuid(), request.source(), request.purpose(), reservation.getReservationId(),
                request.idempotencyKey(), request.externalReference(), request.metadata()
            );

            persistence.appendTransaction(tx);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_APPEND_LEDGER);

            reconcilePendingAuditEntry(transactionId);

            // Save to idempotency registry
            if (request.idempotencyKey() != null) {
                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_IDEMPOTENCY_REGISTRY_UPDATE);
                idempotencyRegistry.put(request.idempotencyKey(), new IdempotencyRecord(
                    reservation.getPlayerUuid(), reservation.getAmount(), transactionId, reservation.getReservationId(), true, "RELEASE", request.source(), request.purpose()
                ));
                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_IDEMPOTENCY_REGISTRY_UPDATE);
            }

            GemBalanceView newView = new GemBalanceView(reservation.getPlayerUuid(), currentTotal, heldAfter, currentTotal - heldAfter);

            // Post event
            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_EVENT_PUBLISH);
            postEventSafely(new GemReservationReleasedEvent(
                reservation.getPlayerUuid(), reservation.getAmount(), request.source(), request.purpose(),
                transactionId, reservation.getReservationId(), request.idempotencyKey(),
                currentTotal, currentTotal, heldBefore, heldAfter
            ));
            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_EVENT_PUBLISH);

            return GemOperationResult.succeed(transactionId, reservation.getReservationId(), newView, "release_success");

        } catch (Exception e) {
            LOGGER.error("Release operation failed", e);
            return GemOperationResult.fail(GemOperationFailure.PERSISTENCE_FAILURE, "Failed to persist transaction");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public GemOperationResult renew(GemRenewRequest request) {
        if (shuttingDown) return GemOperationResult.fail(GemOperationFailure.SHUTTING_DOWN, "Server is shutting down");
        if (!isGemsEnabled()) return GemOperationResult.fail(GemOperationFailure.DISABLED, "Gems system is disabled");
        if (dataIntegrityError) return GemOperationResult.fail(GemOperationFailure.DATA_INTEGRITY_FAILURE, "Gems state integrity check failed");

        if (!persistence.getConfig().reservations.allowExternalRenewal) {
            return GemOperationResult.fail(GemOperationFailure.DISABLED, "External lease renewal is disabled by configuration");
        }

        if (request.source() != null && !isValidIdentifier(request.source())) {
            return GemOperationResult.fail(GemOperationFailure.UNAUTHORIZED_SOURCE, "Invalid source format");
        }
        if (request.purpose() != null && !isValidIdentifier(request.purpose())) {
            return GemOperationResult.fail(GemOperationFailure.UNKNOWN, "Invalid purpose format");
        }

        long defaultLease = persistence.getConfig().reservations.defaultLeaseSeconds;
        long maxLease = persistence.getConfig().reservations.maxLeaseSeconds;
        long leaseSeconds = request.lease() != null ? request.lease().toSeconds() : defaultLease;

        if (leaseSeconds <= 0 || leaseSeconds > maxLease) {
            return GemOperationResult.fail(GemOperationFailure.INVALID_LEASE, "Renewal lease duration is invalid or exceeds max limit");
        }

        stateLock.writeLock().lock();
        try {
            GemReservation reservation = currentState.reservations.get(request.reservationId().toString());
            if (reservation == null) {
                return GemOperationResult.fail(GemOperationFailure.RESERVATION_NOT_FOUND, "Reservation not found");
            }

            // Compute fingerprint early for idempotency validation
            String fingerprint = request.idempotencyKey() != null ? computeFingerprint(
                "RENEW", reservation.getPlayerUuid(), reservation.getAmount(),
                request.source(), request.purpose(), request.reservationId(), request.lease(), request.externalReference(), request.metadata()) : null;

            // Idempotency check by key with persisted state fallback and fingerprint validation
            if (request.idempotencyKey() != null) {
                IdempotencyRecord record = checkIdempotencyWithStateFallback(request.idempotencyKey(), reservation.getPlayerUuid(), reservation.getAmount(), "RENEW");
                if (record != null) {
                    if (record.reservationId.equals(request.reservationId()) && "RENEW".equals(record.type)) {
                        if (isFingerprintMatching(request.idempotencyKey(), fingerprint)) {
                            GemBalanceView view = getBalanceView(reservation.getPlayerUuid());
                            return GemOperationResult.succeed(record.transactionId, request.reservationId(), view, "idempotent_success");
                        }
                        return GemOperationResult.fail(GemOperationFailure.IDEMPOTENCY_CONFLICT, "Idempotency key fingerprint mismatch");
                    }
                    return GemOperationResult.fail(GemOperationFailure.IDEMPOTENCY_CONFLICT, "Idempotency key conflict");
                }
            }

            if (reservation.getStatus() != GemReservationStatus.ACTIVE) {
                return GemOperationResult.fail(GemOperationFailure.RESERVATION_NOT_ACTIVE, "Only active reservations can be renewed");
            }

            long currentTotal = getBalanceTotal(currentState, reservation.getPlayerUuid());
            long held = calculateHeldBalance(currentState, reservation.getPlayerUuid());
            long newExpiresAt = System.currentTimeMillis() + (leaseSeconds * 1000L);

            // Clone state for transaction isolation (Copy-on-Write)
            GemsState nextState = currentState.cloneState();
            GemReservation nextReservation = nextState.reservations.get(request.reservationId().toString());
            nextReservation.setExpiresAt(newExpiresAt);

            // Generate transactionId FIRST — same UUID used everywhere
            UUID transactionId = UUID.randomUUID();
            long timestamp = System.currentTimeMillis();

            // Persist idempotency record and pending audit entry in state BEFORE disk write (fingerprint already computed)
            if (request.idempotencyKey() != null) {
                nextState.idempotencyRecords.put(request.idempotencyKey(),
                    new GemsState.IdempotencyPersistedRecord(
                        transactionId.toString(), "RENEW", fingerprint,
                        reservation.getPlayerUuid(), reservation.getAmount(), reservation.getReservationId(), "SUCCESS", timestamp));
            }
            addPendingAuditEntry(nextState, transactionId, nextState.revision + 1, "RESERVATION_RENEWED",
                reservation.getPlayerUuid(), reservation.getAmount(), currentTotal, currentTotal,
                held, held, reservation.getReservationId(), request.source(), request.purpose(),
                request.idempotencyKey(), fingerprint, request.externalReference(), request.actorUuid(), timestamp);

            // Save state first (disk authoritative write)
            persistence.saveState(nextState);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_CACHE_SWAP);

            // Once write succeeds, update in-memory reference
            currentState = nextState;

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_CACHE_SWAP);

            // Log ledger
            GemTransaction tx = new GemTransaction(
                transactionId, timestamp, GemTransactionType.RESERVATION_RENEWED, reservation.getPlayerUuid(), reservation.getAmount(),
                currentTotal, currentTotal, held, held, currentTotal - held, currentTotal - held,
                request.actorUuid(), request.source(), request.purpose(), reservation.getReservationId(),
                request.idempotencyKey(), request.externalReference(), request.metadata()
            );

            persistence.appendTransaction(tx);

            checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_APPEND_LEDGER);

            reconcilePendingAuditEntry(transactionId);

            // Save to idempotency registry
            if (request.idempotencyKey() != null) {
                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_IDEMPOTENCY_REGISTRY_UPDATE);
                idempotencyRegistry.put(request.idempotencyKey(), new IdempotencyRecord(
                    reservation.getPlayerUuid(), reservation.getAmount(), transactionId, reservation.getReservationId(), true, "RENEW", request.source(), request.purpose()
                ));
                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.AFTER_IDEMPOTENCY_REGISTRY_UPDATE);
            }

            GemBalanceView newView = new GemBalanceView(reservation.getPlayerUuid(), currentTotal, held, currentTotal - held);

            return GemOperationResult.succeed(transactionId, reservation.getReservationId(), newView, "renew_success");

        } catch (Exception e) {
            LOGGER.error("Renew operation failed", e);
            return GemOperationResult.fail(GemOperationFailure.PERSISTENCE_FAILURE, "Failed to persist transaction");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public Optional<GemReservation> findReservation(UUID reservationId) {
        stateLock.readLock().lock();
        try {
            GemReservation res = currentState.reservations.get(reservationId.toString());
            return Optional.ofNullable(res);
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public Optional<GemReservation> findReservationByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        stateLock.readLock().lock();
        try {
            for (GemReservation res : currentState.reservations.values()) {
                if (idempotencyKey.equals(res.getIdempotencyKey())) {
                    return Optional.of(res);
                }
            }
            return Optional.empty();
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public List<GemTransaction> getHistory(UUID playerUuid, int page, int pageSize) {
        // Read directly from persistence history
        List<GemTransaction> all = persistence.getHistory(playerUuid);
        if (all.isEmpty()) return Collections.emptyList();

        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= all.size()) return Collections.emptyList();

        int toIndex = Math.min(fromIndex + pageSize, all.size());
        return all.subList(fromIndex, toIndex);
    }

    // SYSTEM RECOVERY ON BOOT
    public void recover() {
        stateLock.writeLock().lock();
        try {
            LOGGER.info("Starting Gems wallet recovery process...");
            GemsState state;
            try {
                state = persistence.loadState();
            } catch (Exception e) {
                LOGGER.error("Failed to load state during recovery: {}", e.getMessage());
                this.dataIntegrityError = true;
                return;
            }

            if (state.schemaVersion != 1) {
                LOGGER.error("Unsupported gems_state schemaVersion: {}. Required: 1", state.schemaVersion);
                this.dataIntegrityError = true;
                return;
            }

            long currentTime = System.currentTimeMillis();
            boolean stateChanged = false;
            long expiredCount = 0;

            Map<UUID, Long> calculatedHeldBalances = new HashMap<>();

            // 1. Process active and expired reservations
            for (Map.Entry<String, GemReservation> entry : state.reservations.entrySet()) {
                GemReservation res = entry.getValue();
                if (res == null) {
                    continue;
                }

                if (res.getReservationId() == null || res.getPlayerUuid() == null) {
                    LOGGER.error("Corrupted reservation found: ID or Player UUID is null.");
                    this.dataIntegrityError = true;
                    continue;
                }

                if (res.getAmount() < 0) {
                    LOGGER.error("Reservation {} has negative amount: {}", res.getReservationId(), res.getAmount());
                    this.dataIntegrityError = true;
                    continue;
                }

                if (res.getStatus() == GemReservationStatus.ACTIVE) {
                    if (res.getExpiresAt() <= currentTime) {
                        res.setStatus(GemReservationStatus.EXPIRED);
                        res.setReleasedAt(currentTime);
                        stateChanged = true;
                        expiredCount++;
                        LOGGER.info("Expired active reservation {} for player {} during recovery.", res.getReservationId(), res.getPlayerUuid());

                        // Append EXPIRED transaction to ledger
                        try {
                            long totalVal = getBalanceTotal(state, res.getPlayerUuid());
                            long currentHeld = calculatedHeldBalances.getOrDefault(res.getPlayerUuid(), 0L);
                            GemTransaction tx = new GemTransaction(
                                UUID.randomUUID(),
                                currentTime,
                                GemTransactionType.RESERVATION_EXPIRED,
                                res.getPlayerUuid(),
                                res.getAmount(),
                                totalVal, // balanceBefore
                                totalVal, // balanceAfter
                                currentHeld + res.getAmount(), // heldBefore
                                currentHeld, // heldAfter
                                totalVal - (currentHeld + res.getAmount()), // availableBefore
                                totalVal - currentHeld, // availableAfter
                                null,
                                res.getSource(),
                                res.getPurpose(),
                                res.getReservationId(),
                                res.getIdempotencyKey(),
                                res.getExternalReference(),
                                res.getMetadata()
                            );
                            persistence.appendTransaction(tx);
                        } catch (Exception ex) {
                            LOGGER.error("Failed to append expiration transaction to ledger", ex);
                        }
                    } else {
                        calculatedHeldBalances.merge(res.getPlayerUuid(), res.getAmount(), Long::sum);
                    }
                }
            }

            // 2. Validate player balances and verify held balance limits
            for (Map.Entry<String, Long> entry : state.balances.entrySet()) {
                UUID playerUuid;
                try {
                    playerUuid = UUID.fromString(entry.getKey());
                } catch (Exception e) {
                    LOGGER.error("Invalid player UUID key in balances: {}", entry.getKey());
                    this.dataIntegrityError = true;
                    continue;
                }

                long totalBal = entry.getValue();
                if (totalBal < 0) {
                    LOGGER.error("Player {} has negative balance: {}", playerUuid, totalBal);
                    this.dataIntegrityError = true;
                }

                long heldBal = calculatedHeldBalances.getOrDefault(playerUuid, 0L);
                if (heldBal > totalBal) {
                    LOGGER.error("Data inconsistency: Player {} has held balance ({}) greater than total balance ({}). Blocking updates.", 
                                 playerUuid, heldBal, totalBal);
                    this.dataIntegrityError = true;
                }
            }

            // 3. Reconcile pending audit entries (state persisted but ledger append may have failed)
            boolean hadPendingEntries = state.pendingAuditEntries != null && !state.pendingAuditEntries.isEmpty();
            if (hadPendingEntries) {
                LOGGER.info("Found {} pending audit entries to reconcile.", state.pendingAuditEntries.size());
                List<GemsState.PendingAuditEntry> remaining = new ArrayList<>();
                for (GemsState.PendingAuditEntry pending : state.pendingAuditEntries) {
                    try {
                        if (pending.reconciled) continue;
                        Map<String, String> meta = new HashMap<>();
                        meta.put("reconciled", "true");
                        if (pending.requestFingerprint != null) {
                            meta.put("originalFingerprint", pending.requestFingerprint);
                        }
                        GemTransaction tx = new GemTransaction(
                            pending.transactionId, pending.createdAt,
                            GemTransactionType.valueOf(pending.type),
                            pending.playerUuid, pending.amount,
                            pending.balanceBefore, pending.balanceAfter,
                            pending.heldBefore, pending.heldAfter,
                            pending.balanceBefore - pending.heldBefore,
                            pending.balanceAfter - pending.heldAfter,
                            pending.actorUuid,
                            pending.source != null ? pending.source : "bigbangessentials",
                            pending.purpose != null ? pending.purpose : "RECONCILIATION",
                            pending.reservationId,
                            pending.idempotencyKey,
                            pending.externalReference,
                            meta
                        );
                        persistence.appendTransaction(tx);
                        pending.reconciled = true;
                        LOGGER.info("Reconciled pending audit entry: {} ({}) for player {} amount {}",
                            pending.type, pending.transactionId, pending.playerUuid, pending.amount);
                    } catch (Exception ex) {
                        LOGGER.error("Failed to reconcile pending audit entry", ex);
                        remaining.add(pending);
                    }
                }
                state.pendingAuditEntries = remaining;
                stateChanged = true; // Persist cleanup even when all reconciled
            }

            if (stateChanged) {
                persistence.saveState(state);
            }

            this.currentState = state;
            loadIdempotencyFromLedger();

            LOGGER.info("Gems wallet recovery completed. Loaded {} balances, {} reservations ({} expired). Integrity error: {}",
                        state.balances.size(), state.reservations.size(), expiredCount, dataIntegrityError);

        } catch (Exception e) {
            LOGGER.error("Critical error during Gems wallet recovery", e);
            this.dataIntegrityError = true;
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    private void loadIdempotencyFromLedger() {
        idempotencyRegistry.clear();

        // 1. Load persisted idempotency records from currentState (survives crash even without ledger)
        if (currentState != null && currentState.idempotencyRecords != null) {
            for (Map.Entry<String, GemsState.IdempotencyPersistedRecord> entry : currentState.idempotencyRecords.entrySet()) {
                GemsState.IdempotencyPersistedRecord rec = entry.getValue();
                try {
                    idempotencyRegistry.put(entry.getKey(), new IdempotencyRecord(
                        rec.playerUuid, rec.amount,
                        rec.transactionId != null ? UUID.fromString(rec.transactionId) : null,
                        rec.reservationId, "SUCCESS".equals(rec.resultStatus),
                        rec.operationType, "", ""
                    ));
                } catch (Exception ignored) {}
            }
        }

        // 2. Also load from ledger (for records that exist in ledger but not in state due to legacy data)
        File file = com.pedrodalben.bigbangessentials.util.ResourceUtil.getDataFile("gems_transactions.jsonl");
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                        if (obj.has("idempotencyKey") && !obj.get("idempotencyKey").isJsonNull()) {
                            String key = obj.get("idempotencyKey").getAsString();
                            if (idempotencyRegistry.containsKey(key)) continue; // State records take precedence
                            UUID playerUuid = UUID.fromString(obj.get("playerUuid").getAsString());
                            long amount = obj.get("amount").getAsLong();
                            UUID txId = UUID.fromString(obj.get("transactionId").getAsString());
                            UUID resId = obj.has("reservationId") && !obj.get("reservationId").isJsonNull() ? UUID.fromString(obj.get("reservationId").getAsString()) : null;
                            String ledgerType = obj.get("type").getAsString();
                            String source = obj.get("source").getAsString();
                            String purpose = obj.has("purpose") && !obj.get("purpose").isJsonNull() ? obj.get("purpose").getAsString() : null;

                            String registryType;
                            switch (ledgerType) {
                                case "CREDIT": registryType = "CREDIT"; break;
                                case "DEBIT": registryType = "DEBIT"; break;
                                case "RESERVATION_CREATED": registryType = "RESERVE"; break;
                                case "RESERVATION_CAPTURED": registryType = "CAPTURE"; break;
                                case "RESERVATION_RELEASED": registryType = "RELEASE"; break;
                                case "RESERVATION_RENEWED": registryType = "RENEW"; break;
                                default: registryType = ledgerType; break;
                            }

                            idempotencyRegistry.put(key, new IdempotencyRecord(
                                playerUuid, amount, txId, resId, true, registryType, source, purpose
                            ));
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load idempotency registry from ledger", e);
            }
        }

        // 3. Add active reservations to idempotency registry if they have keys and aren't already present
        if (currentState != null) {
            for (GemReservation res : currentState.reservations.values()) {
                if (res.getIdempotencyKey() != null && !idempotencyRegistry.containsKey(res.getIdempotencyKey())) {
                    idempotencyRegistry.put(res.getIdempotencyKey(), new IdempotencyRecord(
                        res.getPlayerUuid(), res.getAmount(), null, res.getReservationId(), true, "RESERVE", res.getSource(), res.getPurpose()
                    ));
                }
            }
        }
    }

    // PERIODIC CLEANUP OF EXPIRED RESERVATIONS
    private void startCleanupTask() {
        if (!isGemsEnabled() || !persistence.getConfig().reservations.enabled) {
            return;
        }

        long interval = persistence.getConfig().reservations.cleanupIntervalSeconds;
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Gems-Reservation-Cleanup");
            t.setDaemon(true);
            return t;
        });

        cleanupScheduler.scheduleAtFixedRate(this::expireReservationsTask, interval, interval, TimeUnit.SECONDS);
    }

    private void expireReservationsTask() {
        stateLock.writeLock().lock();
        try {
            if (shuttingDown || !isGemsEnabled() || dataIntegrityError) return;

            long currentTime = System.currentTimeMillis();
            GemsState nextState = currentState.cloneState();
            boolean stateChanged = false;
            Map<UUID, UUID> expiredTxIds = new LinkedHashMap<>();

            for (GemReservation res : nextState.reservations.values()) {
                if (res.getStatus() == GemReservationStatus.ACTIVE && res.getExpiresAt() <= currentTime) {
                    res.setStatus(GemReservationStatus.EXPIRED);
                    res.setReleasedAt(currentTime);
                    stateChanged = true;

                    UUID transactionId = UUID.randomUUID();
                    expiredTxIds.put(res.getReservationId(), transactionId);
                    long currentTotal = getBalanceTotal(nextState, res.getPlayerUuid());
                    long heldBefore = calculateHeldBalance(nextState, res.getPlayerUuid()) + res.getAmount();
                    long heldAfter = heldBefore - res.getAmount();

                    // P0 flow: add pending audit entry BEFORE saveState (same transactionId)
                    addPendingAuditEntry(nextState, transactionId, nextState.revision + 1, "RESERVATION_EXPIRED",
                        res.getPlayerUuid(), res.getAmount(), currentTotal, currentTotal,
                        heldBefore, heldAfter, res.getReservationId(), res.getSource(), res.getPurpose(),
                        res.getIdempotencyKey(), null, res.getExternalReference(), null, currentTime);

                    LOGGER.info("Expiring reservation {} for player {}.", res.getReservationId(), res.getPlayerUuid());
                }
            }

            if (stateChanged) {
                // 1. Save state FIRST (disk authoritative write)
                persistence.saveState(nextState);

                checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_CACHE_SWAP);

                // 2. Swap cache
                currentState = nextState;

                // 3. Then append to ledger for each expired reservation (same transactionId as pending)
                for (Map.Entry<UUID, UUID> entry : expiredTxIds.entrySet()) {
                    UUID rid = entry.getKey();
                    UUID transactionId = entry.getValue();
                    GemReservation res = currentState.reservations.get(rid.toString());
                    if (res == null || res.getStatus() != GemReservationStatus.EXPIRED) continue;

                    long currentTotal = getBalanceTotal(currentState, res.getPlayerUuid());
                    long heldBefore = calculateHeldBalance(currentState, res.getPlayerUuid()) + res.getAmount();
                    long heldAfter = heldBefore - res.getAmount();

                    GemTransaction tx = new GemTransaction(
                        transactionId, currentTime, GemTransactionType.RESERVATION_EXPIRED, res.getPlayerUuid(), res.getAmount(),
                        currentTotal, currentTotal, heldBefore, heldAfter, currentTotal - heldBefore, currentTotal - heldAfter,
                        null, res.getSource(), res.getPurpose(), res.getReservationId(),
                        res.getIdempotencyKey(), res.getExternalReference(), res.getMetadata()
                    );

                    persistence.appendTransaction(tx);
                    reconcilePendingAuditEntry(transactionId);

                    checkFailpoint(com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint.BEFORE_EVENT_PUBLISH);
                    postEventSafely(new GemReservationExpiredEvent(
                        res.getPlayerUuid(), res.getAmount(), res.getSource(), res.getPurpose(),
                        transactionId, res.getReservationId(), res.getIdempotencyKey(),
                        currentTotal, currentTotal, heldBefore, heldAfter
                    ));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error running reservations cleanup task", e);
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    // DIAGNOSTICS & REPAIR
    public String verify() {
        stateLock.readLock().lock();
        try {
            long totalGems = 0;
            long totalHeld = 0;
            for (long bal : currentState.balances.values()) {
                totalGems += bal;
            }
            int activeResCount = 0;
            for (GemReservation res : currentState.reservations.values()) {
                if (res.getStatus() == GemReservationStatus.ACTIVE) {
                    totalHeld += res.getAmount();
                    activeResCount++;
                }
            }
            return String.format("Balances count: %d, Total Gems in server: %d, Active Reservations: %d (Total Held: %d), Integrity OK: %b",
                                 currentState.balances.size(), totalGems, activeResCount, totalHeld, !dataIntegrityError);
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public synchronized void repair(boolean confirm) {
        if (!confirm) {
            throw new IllegalArgumentException("Repair requires literal confirmation");
        }

        stateLock.writeLock().lock();
        try {
            LOGGER.warn("ADMIN REPAIR CONFIRMED. Creating state backup...");
            persistence.forceManualBackup("repair_confirm");

            long currentTime = System.currentTimeMillis();
            boolean stateChanged = false;

            // Clear dataIntegrityError flag and recalculate everything
            this.dataIntegrityError = false;

            Map<UUID, Long> calculatedHeldBalances = new HashMap<>();

            // Correct reservations
            for (GemReservation res : currentState.reservations.values()) {
                if (res.getAmount() < 0) {
                    LOGGER.info("Correcting negative reservation amount {} -> 0 for reservation {}", res.getAmount(), res.getReservationId());
                    // Since it has invalid amount, let's mark it as released
                    res.setStatus(GemReservationStatus.RELEASED);
                    res.setReleasedAt(currentTime);
                    stateChanged = true;
                    continue;
                }

                if (res.getStatus() == GemReservationStatus.ACTIVE) {
                    if (res.getExpiresAt() <= currentTime) {
                        res.setStatus(GemReservationStatus.EXPIRED);
                        res.setReleasedAt(currentTime);
                        stateChanged = true;
                        LOGGER.info("Expired active reservation {} during repair.", res.getReservationId());
                    } else {
                        calculatedHeldBalances.merge(res.getPlayerUuid(), res.getAmount(), Long::sum);
                    }
                }
            }

            // Correct balances
            for (Map.Entry<String, Long> entry : currentState.balances.entrySet()) {
                UUID playerUuid = UUID.fromString(entry.getKey());
                long val = entry.getValue();
                if (val < 0) {
                    LOGGER.info("Correcting negative balance for player {} from {} to 0.", playerUuid, val);
                    currentState.balances.put(entry.getKey(), 0L);
                    stateChanged = true;

                    // Log transaction
                    GemTransaction tx = new GemTransaction(
                        UUID.randomUUID(), currentTime, GemTransactionType.ADMIN_REPAIR, playerUuid, -val,
                        val, 0L, 0L, 0L, val, 0L,
                        null, "bigbangessentials", "SYSTEM_REPAIR", null,
                        null, null, Map.of("reason", "negative balance correction")
                    );
                    persistence.appendTransaction(tx);
                }

                long totalBal = currentState.balances.get(entry.getKey());
                long heldBal = calculatedHeldBalances.getOrDefault(playerUuid, 0L);
                if (heldBal > totalBal) {
                    LOGGER.info("Held balance ({}) was greater than total balance ({}) for player {}. Setting total balance to held balance.",
                                 heldBal, totalBal, playerUuid);
                    currentState.balances.put(entry.getKey(), heldBal);
                    stateChanged = true;

                    GemTransaction tx = new GemTransaction(
                        UUID.randomUUID(), currentTime, GemTransactionType.ADMIN_REPAIR, playerUuid, heldBal - totalBal,
                        totalBal, heldBal, heldBal, heldBal, totalBal - heldBal, 0L,
                        null, "bigbangessentials", "SYSTEM_REPAIR", null,
                        null, null, Map.of("reason", "balance adjusted to match held reservations")
                    );
                    persistence.appendTransaction(tx);
                }
            }

            if (stateChanged) {
                persistence.saveState(currentState);
            }

            loadIdempotencyFromLedger();
            LOGGER.info("Gems wallet state repaired successfully. Integrity error: {}", dataIntegrityError);

        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public void reload() {
        stateLock.writeLock().lock();
        try {
            LOGGER.info("Reloading Gems configuration and state...");
            persistence.forceManualBackup("pre_reload");
            persistence.setGemsEnabled(true); // reset
            this.currentState = null;
            this.dataIntegrityError = false;
            this.shuttingDown = false;

            // Load config again
            persistence.loadConfig();

            // Recover state again
            recover();

            // Restart cleanup task
            if (cleanupScheduler != null) {
                cleanupScheduler.shutdownNow();
            }
            startCleanupTask();

            LOGGER.info("Gems reload completed successfully.");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public void shutdown() {
        stateLock.writeLock().lock();
        try {
            if (shuttingDown) return;
            LOGGER.info("Shutting down Gems manager executor...");
            shuttingDown = true;

            if (cleanupScheduler != null) {
                cleanupScheduler.shutdown();
                try {
                    if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        cleanupScheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    cleanupScheduler.shutdownNow();
                }
            }

            if (currentState != null && !dataIntegrityError) {
                persistence.saveState(currentState);
            }
            LOGGER.info("Gems manager shutdown complete.");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    // FINGERPRINT COMPUTATION
    private String computeFingerprint(String operationType, UUID playerUuid, long amount, String source, String purpose,
                                      UUID reservationId, Duration lease, String externalReference,
                                      Map<String, String> metadata) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(operationType != null ? operationType : "").append('|');
            sb.append(playerUuid != null ? playerUuid.toString() : "").append('|');
            sb.append(amount).append('|');
            sb.append(source != null ? source : "").append('|');
            sb.append(purpose != null ? purpose : "").append('|');
            sb.append(reservationId != null ? reservationId.toString() : "").append('|');
            sb.append(lease != null ? String.valueOf(lease.toMillis()) : "").append('|');
            sb.append(externalReference != null ? externalReference : "").append('|');
            if (metadata != null) {
                metadata.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue()).append(';'));
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            LOGGER.error("Failed to compute fingerprint", e);
            return null;
        }
    }

    // IDEMPOTENCY CHECK WITH PERSISTED STATE FALLBACK
    private IdempotencyRecord checkIdempotencyWithStateFallback(String idempotencyKey, UUID playerUuid, long amount, String expectedType) {
        if (idempotencyKey == null) return null;
        IdempotencyRecord record = idempotencyRegistry.get(idempotencyKey);
        if (record != null) return record;
        // Fallback to persisted records in currentState (survives restart)
        if (currentState != null && currentState.idempotencyRecords != null) {
            GemsState.IdempotencyPersistedRecord persisted = currentState.idempotencyRecords.get(idempotencyKey);
            if (persisted != null) {
                record = new IdempotencyRecord(persisted.playerUuid, persisted.amount,
                    persisted.transactionId != null ? UUID.fromString(persisted.transactionId) : null,
                    persisted.reservationId, "SUCCESS".equals(persisted.resultStatus),
                    persisted.operationType, "", "");
                idempotencyRegistry.put(idempotencyKey, record);
                return record;
            }
        }
        return null;
    }

    private boolean isFingerprintMatching(String idempotencyKey, String expectedFingerprint) {
        if (idempotencyKey == null) return true;
        if (currentState == null || currentState.idempotencyRecords == null) return true;
        GemsState.IdempotencyPersistedRecord persisted = currentState.idempotencyRecords.get(idempotencyKey);
        if (persisted == null) return true; // No persisted record yet — first attempt, no conflict
        if (persisted.requestFingerprint == null) return true; // Legacy record without fingerprint
        return expectedFingerprint != null && expectedFingerprint.equals(persisted.requestFingerprint);
    }

    // PENDING AUDIT ENTRY CREATION AND RECONCILIATION
    private void addPendingAuditEntry(GemsState state, UUID transactionId, long revision, String type,
                                      UUID playerUuid, long amount, long balanceBefore, long balanceAfter,
                                      long heldBefore, long heldAfter,
                                      UUID reservationId, String source, String purpose,
                                      String idempotencyKey, String requestFingerprint,
                                      String externalReference, UUID actorUuid, long createdAt) {
        if (state.pendingAuditEntries == null) {
            state.pendingAuditEntries = new ArrayList<>();
        }
        state.pendingAuditEntries.add(new GemsState.PendingAuditEntry(
            transactionId, revision, type, playerUuid,
            amount, balanceBefore, balanceAfter, heldBefore, heldAfter,
            reservationId, source, purpose,
            idempotencyKey, requestFingerprint, externalReference, actorUuid, createdAt
        ));
    }

    private void reconcilePendingAuditEntry(UUID transactionId) {
        if (currentState.pendingAuditEntries == null || currentState.pendingAuditEntries.isEmpty()) return;
        boolean hasPending = currentState.pendingAuditEntries.stream().anyMatch(e -> transactionId.equals(e.transactionId));
        if (!hasPending) return;
        GemsState nextState = currentState.cloneState();
        nextState.pendingAuditEntries.removeIf(e -> transactionId.equals(e.transactionId));
        persistence.saveState(nextState);
        currentState = nextState;
    }

    // STRINGS SANITIZER / VALIDATOR
    private boolean isValidIdentifier(String s) {
        if (s == null || s.isEmpty() || s.length() > 64) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLowerCase(c) && !Character.isUpperCase(c) && !Character.isDigit(c) && c != '-' && c != '_') {
                return false;
            }
        }
        return true;
    }

    private void postEventSafely(Object event) {
        try {
            Platform.postEvent(event);
        } catch (Exception e) {
            LOGGER.error("Exception thrown by event listener", e);
        }
    }
}
