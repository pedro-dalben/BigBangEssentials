package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemBalanceView;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservation;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservationStatus;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistence;
import com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistenceFailpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class GemCrashInjectionTest {

    @BeforeEach
    void setUp() {
        GemsPersistence.activeFailpoint = null;
        cleanData();
        GemsManager.getInstance().reload();
    }

    private void cleanData() {
        File dataDir = new File("bigbangessentials");
        if (dataDir.exists()) {
            new File(dataDir, "gems_state.json").delete();
            new File(dataDir, "gems_transactions.jsonl").delete();
            new File(dataDir, "gems.json").delete();
            File backupDir = new File(dataDir, "gems_backups");
            if (backupDir.exists()) {
                File[] files = backupDir.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
                backupDir.delete();
            }
        }
    }

    private UUID initPlayer(long amount) {
        UUID id = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(id, amount, "test", "init", null, null, null, Map.of()));
        return id;
    }

    private void assertInvariants(UUID playerId, long expectedTotal, long expectedHeld) {
        GemBalanceView view = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(expectedTotal, view.totalBalance());
        assertEquals(expectedHeld, view.heldBalance());
        assertEquals(expectedTotal - expectedHeld, view.availableBalance());
    }

    // ── BEFORE_WRITE_TEMP ──

    @Test
    void testCrashBeforeWriteTempOnReserve() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_WRITE_TEMP;

        GemReservationResult result = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertFalse(result.success());
        assertEquals(GemOperationFailure.PERSISTENCE_FAILURE, result.failure());
        assertInvariants(playerId, 100L, 0L);

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 100L, 0L);
        assertTrue(GemsManager.getInstance().getActiveReservations(playerId).isEmpty());
    }

    @Test
    void testCrashBeforeWriteTempOnCredit() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_WRITE_TEMP;

        GemOperationResult result = GemsManager.getInstance().credit(
            new GemCreditRequest(playerId, 50L, "test", "credit", null, null, null, Map.of()));
        assertFalse(result.success());
        assertInvariants(playerId, 100L, 0L);

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 100L, 0L);
    }

    @Test
    void testCrashBeforeWriteTempOnCapture() {
        UUID playerId = initPlayer(100L);
        GemReservationResult res = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(res.success());
        UUID reservationId = res.reservationId();

        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_WRITE_TEMP;
        GemOperationResult capResult = GemsManager.getInstance().capture(
            new GemCaptureRequest(reservationId, "test", "capture", null, null, null, Map.of()));
        assertFalse(capResult.success());
        assertInvariants(playerId, 100L, 30L);

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 100L, 30L);
        assertEquals(GemReservationStatus.ACTIVE, GemsManager.getInstance().findReservation(reservationId).orElseThrow().getStatus());
    }

    // ── AFTER_WRITE_TEMP (temp written, but atomic move not done) ──

    @Test
    void testCrashAfterWriteTempOnReserve() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.AFTER_WRITE_TEMP;

        GemReservationResult result = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertFalse(result.success());
        assertEquals(GemOperationFailure.PERSISTENCE_FAILURE, result.failure());
        assertInvariants(playerId, 100L, 0L);

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 100L, 0L);
    }

    // ── BEFORE_ATOMIC_MOVE ──

    @Test
    void testCrashBeforeAtomicMoveOnReserve() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_ATOMIC_MOVE;

        GemReservationResult result = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertFalse(result.success());
        assertInvariants(playerId, 100L, 0L);

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 100L, 0L);
    }

    // ── AFTER_ATOMIC_MOVE (atomic move succeeded → state saved to disk, but cache not swapped) ──

    @Test
    void testCrashAfterAtomicMoveOnReserve() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.AFTER_ATOMIC_MOVE;

        GemReservationResult result = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertFalse(result.success());
        // Cache not swapped yet → held=0 in memory
        assertInvariants(playerId, 100L, 0L);

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        // Atomic move persisted state to disk → after reload, reservation exists
        assertInvariants(playerId, 100L, 30L);
    }

    // ── BEFORE_CACHE_SWAP (disk has new state, memory has old) ──

    @Test
    void testCrashBeforeCacheSwapOnReserve() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_CACHE_SWAP;

        GemReservationResult result = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 40L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertFalse(result.success());
        assertInvariants(playerId, 100L, 0L);

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 100L, 40L);
    }

    @Test
    void testCrashBeforeCacheSwapOnDebit() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_CACHE_SWAP;

        GemOperationResult result = GemsManager.getInstance().debit(
            new GemDebitRequest(playerId, 30L, "test", "debit", null, null, null, Map.of()));
        assertFalse(result.success());
        assertInvariants(playerId, 100L, 0L);

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 70L, 0L);
    }

    @Test
    void testCrashBeforeCacheSwapOnCapture() {
        UUID playerId = initPlayer(100L);
        GemReservationResult res = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(res.success());
        UUID rid = res.reservationId();

        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_CACHE_SWAP;
        GemOperationResult capResult = GemsManager.getInstance().capture(
            new GemCaptureRequest(rid, "test", "capture", null, null, null, Map.of()));
        assertFalse(capResult.success());
        assertInvariants(playerId, 100L, 30L);

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 70L, 0L);
        assertEquals(GemReservationStatus.CAPTURED, GemsManager.getInstance().findReservation(rid).orElseThrow().getStatus());
    }

    // ── AFTER_CACHE_SWAP ──

    @Test
    void testCrashAfterCacheSwapOnCredit() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.AFTER_CACHE_SWAP;

        GemOperationResult result = GemsManager.getInstance().credit(
            new GemCreditRequest(playerId, 50L, "test", "credit", null, null, null, Map.of()));
        assertFalse(result.success());

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 150L, 0L);
    }

    @Test
    void testCrashAfterCacheSwapOnReserve() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.AFTER_CACHE_SWAP;

        GemReservationResult result = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 40L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertFalse(result.success());

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 100L, 40L);
    }

    // ── BEFORE_APPEND_LEDGER ──

    @Test
    void testCrashBeforeAppendLedgerOnReserve() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_APPEND_LEDGER;

        GemReservationResult result = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 40L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertFalse(result.success());
        assertInvariants(playerId, 100L, 40L);

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 100L, 40L);
    }

    // ── AFTER_APPEND_LEDGER ──

    @Test
    void testCrashAfterAppendLedgerOnReserve() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.AFTER_APPEND_LEDGER;

        GemReservationResult result = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 40L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertFalse(result.success());

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 100L, 40L);
    }

    @Test
    void testCrashAfterAppendLedgerOnCapture() {
        UUID playerId = initPlayer(100L);
        GemReservationResult res = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(res.success());
        UUID rid = res.reservationId();

        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.AFTER_APPEND_LEDGER;
        GemOperationResult capResult = GemsManager.getInstance().capture(
            new GemCaptureRequest(rid, "test", "capture", null, null, null, Map.of()));
        assertFalse(capResult.success());

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 70L, 0L);
        assertEquals(GemReservationStatus.CAPTURED, GemsManager.getInstance().findReservation(rid).orElseThrow().getStatus());
    }

    // ── BEFORE_IDEMPOTENCY_REGISTRY_UPDATE ──

    @Test
    void testCrashBeforeIdempotencyRegistryUpdateOnReserve() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_IDEMPOTENCY_REGISTRY_UPDATE;

        String key = "idem-key-before-reg";
        GemReservationResult result = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", key, null, Duration.ofSeconds(60), Map.of()));
        assertFalse(result.success());

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();

        // Should have the reservation persisted but the idempotency registry rebuilt from ledger
        assertInvariants(playerId, 100L, 30L);

        GemReservationResult retry = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", key, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(retry.success());
        assertInvariants(playerId, 100L, 30L);
    }

    // ── AFTER_IDEMPOTENCY_REGISTRY_UPDATE ──

    @Test
    void testCrashAfterIdempotencyRegistryUpdateOnReserve() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.AFTER_IDEMPOTENCY_REGISTRY_UPDATE;

        String key = "idem-key-after-reg";
        GemReservationResult result = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", key, null, Duration.ofSeconds(60), Map.of()));
        assertFalse(result.success());

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();

        assertInvariants(playerId, 100L, 30L);
        GemReservationResult retry = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", key, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(retry.success());
        assertInvariants(playerId, 100L, 30L);
    }

    // ── BEFORE_EVENT_PUBLISH ──

    @Test
    void testCrashBeforeEventPublishOnDebit() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_EVENT_PUBLISH;

        GemOperationResult result = GemsManager.getInstance().debit(
            new GemDebitRequest(playerId, 40L, "test", "debit", null, null, null, Map.of()));
        assertFalse(result.success());

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 60L, 0L);
    }

    // ── AFTER_EVENT_PUBLISH ──

    @Test
    void testCrashAfterEventPublishOnCredit() {
        UUID playerId = initPlayer(100L);
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.AFTER_EVENT_PUBLISH;

        GemOperationResult result = GemsManager.getInstance().credit(
            new GemCreditRequest(playerId, 50L, "test", "credit", null, null, null, Map.of()));
        assertFalse(result.success());

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 150L, 0L);
    }

    // ── Crash recovery during release ──

    @Test
    void testCrashDuringReleaseBeforeCacheSwap() {
        UUID playerId = initPlayer(100L);
        GemReservationResult res = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(res.success());
        UUID rid = res.reservationId();

        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_CACHE_SWAP;
        GemOperationResult relResult = GemsManager.getInstance().release(
            new GemReleaseRequest(rid, "test", "release", null, "test", null, null, Map.of()));
        assertFalse(relResult.success());
        assertInvariants(playerId, 100L, 30L);

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 100L, 0L);
        assertEquals(GemReservationStatus.RELEASED, GemsManager.getInstance().findReservation(rid).orElseThrow().getStatus());
    }

    @Test
    void testCrashDuringCaptureBeforeCacheSwapDeduplication() {
        UUID playerId = initPlayer(100L);
        GemReservationResult res = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(res.success());
        UUID rid = res.reservationId();

        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_CACHE_SWAP;
        GemOperationResult capResult = GemsManager.getInstance().capture(
            new GemCaptureRequest(rid, "test", "cap", null, null, null, Map.of()));
        assertFalse(capResult.success());

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 70L, 0L);

        GemOperationResult capRetry = GemsManager.getInstance().capture(
            new GemCaptureRequest(rid, "test", "cap", null, null, null, Map.of()));
        assertTrue(capRetry.success());
        assertEquals(GemReservationStatus.CAPTURED, GemsManager.getInstance().findReservation(rid).orElseThrow().getStatus());
        assertInvariants(playerId, 70L, 0L);
    }

    // ── Idempotent retry after crash ──

    @Test
    void testIdempotentRetryAfterCrashOnReserve() {
        UUID playerId = initPlayer(100L);
        String key = "retry-after-crash-key";

        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_CACHE_SWAP;
        GemReservationResult result = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", key, null, Duration.ofSeconds(60), Map.of()));
        assertFalse(result.success());

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();

        GemReservationResult retry = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", key, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(retry.success());
        assertInvariants(playerId, 100L, 30L);

        GemReservationResult doubleRetry = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", key, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(doubleRetry.success());
        assertInvariants(playerId, 100L, 30L);
    }

    @Test
    void testIdempotentRetryAfterCrashOnRelease() {
        UUID playerId = initPlayer(100L);
        GemReservationResult res = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(res.success());
        UUID rid = res.reservationId();
        String key = "retry-release-after-crash";

        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_CACHE_SWAP;
        GemOperationResult relResult = GemsManager.getInstance().release(
            new GemReleaseRequest(rid, "test", "release", null, "test", key, null, Map.of()));
        assertFalse(relResult.success());

        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();
        assertInvariants(playerId, 100L, 0L);

        GemOperationResult relRetry = GemsManager.getInstance().release(
            new GemReleaseRequest(rid, "test", "release", null, "test", key, null, Map.of()));
        assertTrue(relRetry.success());
        assertInvariants(playerId, 100L, 0L);
    }
}
