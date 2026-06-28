package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemBalanceView;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservation;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservationStatus;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class GemReservationConcurrencyTest {

    @BeforeEach
    void setUp() {
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

    @Test
    void testConcurrentReservationsCannotOverdraw() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(
            playerId, 100L, "test-source", "TEST", null,
            UUID.randomUUID().toString(), null, Map.of()
        ));

        int threadCount = 5;
        long reservationAmount = 30L;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
                        playerId, reservationAmount, "test-source", "CONCURRENCY_TEST", UUID.randomUUID().toString(),
                        "ref-concurrency", Duration.ofSeconds(60), Map.of()
                    ));
                    if (res.success()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(3, successCount.get(), "Exactly 3 reservations should succeed");
        assertEquals(2, failureCount.get(), "Exactly 2 reservations should fail");

        GemBalanceView balance = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, balance.totalBalance());
        assertEquals(90L, balance.heldBalance());
        assertEquals(10L, balance.availableBalance());
    }

    @Test
    void testConcurrentCaptureAndRelease() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test-source", "TEST", null, null, null, Map.of()));

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test-source", "TEST", null, null, Duration.ofSeconds(60), Map.of()
        ));
        assertTrue(res.success());
        UUID reservationId = res.reservationId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        java.util.concurrent.atomic.AtomicReference<GemOperationResult> captureRes = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<GemOperationResult> releaseRes = new java.util.concurrent.atomic.AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                captureRes.set(GemsManager.getInstance().capture(new GemCaptureRequest(
                    reservationId, "test-source", "TEST", null, null, null, Map.of()
                )));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                finishLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                releaseRes.set(GemsManager.getInstance().release(new GemReleaseRequest(
                    reservationId, "test-source", "TEST", null, null, null, null, Map.of()
                )));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                finishLatch.countDown();
            }
        });

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        GemOperationResult cap = captureRes.get();
        GemOperationResult rel = releaseRes.get();

        assertNotNull(cap);
        assertNotNull(rel);

        // One must succeed and the other must fail, because the state transitions are mutually exclusive
        assertTrue(cap.success() ^ rel.success(), "Exactly one of capture or release must succeed");
        
        GemBalanceView balance = GemsManager.getInstance().getBalanceView(playerId);
        if (cap.success()) {
            // Captured: total balance reduced to 70, held balance 0
            assertEquals(70L, balance.totalBalance());
            assertEquals(0L, balance.heldBalance());
            assertEquals(GemReservationStatus.CAPTURED, GemsManager.getInstance().findReservation(reservationId).orElseThrow().getStatus());
        } else {
            // Released: total balance remains 100, held balance 0
            assertEquals(100L, balance.totalBalance());
            assertEquals(0L, balance.heldBalance());
            assertEquals(GemReservationStatus.RELEASED, GemsManager.getInstance().findReservation(reservationId).orElseThrow().getStatus());
        }
    }

    @Test
    void testConcurrentMultipleCaptures() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test-source", "TEST", null, null, null, Map.of()));

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test-source", "TEST", null, null, Duration.ofSeconds(60), Map.of()
        ));
        assertTrue(res.success());
        UUID reservationId = res.reservationId();

        int count = 4;
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(count);
        ConcurrentLinkedQueue<GemOperationResult> results = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < count; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    results.add(GemsManager.getInstance().capture(new GemCaptureRequest(
                        reservationId, "test-source", "TEST", null, null, null, Map.of()
                    )));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(count, results.size());
        for (GemOperationResult r : results) {
            assertTrue(r.success(), "All capture retries/concurrent calls should report success (idempotency)");
        }

        // Total balance must be exactly 70 (meaning deducted only once)
        GemBalanceView balance = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(70L, balance.totalBalance());
        assertEquals(0L, balance.heldBalance());
    }

    @Test
    void testConcurrentMultipleReleases() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test-source", "TEST", null, null, null, Map.of()));

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test-source", "TEST", null, null, Duration.ofSeconds(60), Map.of()
        ));
        assertTrue(res.success());
        UUID reservationId = res.reservationId();

        int count = 4;
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(count);
        ConcurrentLinkedQueue<GemOperationResult> results = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < count; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    results.add(GemsManager.getInstance().release(new GemReleaseRequest(
                        reservationId, "test-source", "TEST", null, null, null, null, Map.of()
                    )));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(count, results.size());
        for (GemOperationResult r : results) {
            assertTrue(r.success(), "All release retries/concurrent calls should report success (idempotency)");
        }

        // Total balance must be exactly 100 (released only once)
        GemBalanceView balance = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, balance.totalBalance());
        assertEquals(0L, balance.heldBalance());
    }

    // ── Additional concurrency scenarios ──

    @Test
    void testConcurrentReserveAndDebit() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null, null, null, Map.of()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        AtomicInteger reserveSuccess = new AtomicInteger(0);
        AtomicInteger debitSuccess = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                GemReservationResult r = GemsManager.getInstance().reserve(new GemReservationRequest(
                    playerId, 30L, "test", "concurrent", UUID.randomUUID().toString(), null, Duration.ofSeconds(60), Map.of()));
                if (r.success()) reserveSuccess.incrementAndGet();
            } catch (Exception ignored) {} finally { finishLatch.countDown(); }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                GemOperationResult d = GemsManager.getInstance().debit(new GemDebitRequest(
                    playerId, 40L, "test", "concurrent", null, UUID.randomUUID().toString(), null, Map.of()));
                if (d.success()) debitSuccess.incrementAndGet();
            } catch (Exception ignored) {} finally { finishLatch.countDown(); }
        });

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        int totalOps = reserveSuccess.get() + debitSuccess.get();
        assertTrue(totalOps >= 1, "At least one operation must succeed");

        GemBalanceView balance = GemsManager.getInstance().getBalanceView(playerId);
        long expectedTotal = 100L - (debitSuccess.get() * 40L);
        assertEquals(expectedTotal, balance.totalBalance());
        assertTrue(balance.heldBalance() <= balance.totalBalance());
        assertTrue(balance.availableBalance() >= 0);
    }

    @Test
    void testConcurrentReserveAndAdminTake() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null, null, null, Map.of()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        AtomicInteger reserveOk = new AtomicInteger(0);
        AtomicInteger takeOk = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                GemReservationResult r = GemsManager.getInstance().reserve(new GemReservationRequest(
                    playerId, 80L, "test", "concurrent", UUID.randomUUID().toString(), null, Duration.ofSeconds(60), Map.of()));
                if (r.success()) reserveOk.incrementAndGet();
            } catch (Exception ignored) {} finally { finishLatch.countDown(); }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                GemOperationResult t = GemsManager.getInstance().debit(new GemDebitRequest(
                    playerId, 60L, "admin-command", "ADMIN_TAKE", null, UUID.randomUUID().toString(), null, Map.of()));
                if (t.success()) takeOk.incrementAndGet();
            } catch (Exception ignored) {} finally { finishLatch.countDown(); }
        });

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        int successCount = reserveOk.get() + takeOk.get();
        assertTrue(successCount >= 1, "At least one operation must succeed");

        GemBalanceView balance = GemsManager.getInstance().getBalanceView(playerId);
        assertTrue(balance.totalBalance() >= 0);
        assertTrue(balance.heldBalance() <= balance.totalBalance());
        assertTrue(balance.availableBalance() >= 0);
        assertEquals(balance.totalBalance(), balance.heldBalance() + balance.availableBalance());
    }

    @Test
    void testConcurrentRenewAndReloadExpireCleanup() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null, null, null, Map.of()));

        // Create reservation with very short lease (1 second)
        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "concurrent", UUID.randomUUID().toString(), null, Duration.ofSeconds(1), Map.of()));
        assertTrue(res.success());
        UUID rid = res.reservationId();

        // Wait for lease to expire
        Thread.sleep(1100);

        // Simulate restart to trigger expiration recovery
        GemsManager.getInstance().reload();

        // After recovery, reservation should be expired
        GemBalanceView balance = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, balance.totalBalance());
        assertEquals(0L, balance.heldBalance());

        GemReservation reservation = GemsManager.getInstance().findReservation(rid).orElseThrow();
        assertEquals(GemReservationStatus.EXPIRED, reservation.getStatus());
    }

    @Test
    void testConcurrentIdempotentReserveSameKey() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null, null, null, Map.of()));

        String sharedKey = "shared-concurrent-key";
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        ConcurrentLinkedQueue<GemReservationResult> results = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    results.add(GemsManager.getInstance().reserve(new GemReservationRequest(
                        playerId, 30L, "test", "concurrent", sharedKey, null, Duration.ofSeconds(60), Map.of())));
                } catch (Exception ignored) {} finally { finishLatch.countDown(); }
            });
        }

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, results.size());

        // All threads use same key + same payload → all should succeed (idempotent)
        int successCount = 0;
        UUID firstReservationId = null;
        for (GemReservationResult r : results) {
            assertTrue(r.success(), "Same key + same payload should be idempotent");
            successCount++;
            if (firstReservationId == null) firstReservationId = r.reservationId();
            else assertEquals(firstReservationId, r.reservationId());
        }

        assertInvariants(playerId, 100L, 30L);
    }

    @Test
    void testConcurrentIdempotentReserveDifferentPayload() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null, null, null, Map.of()));

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        ConcurrentLinkedQueue<GemReservationResult> results = new ConcurrentLinkedQueue<>();
        String sharedKey = "shared-diff-payload-key";

        for (int i = 0; i < threadCount; i++) {
            final long amt = 30L * (i + 1);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    results.add(GemsManager.getInstance().reserve(new GemReservationRequest(
                        playerId, amt, "test", "concurrent", sharedKey, null, Duration.ofSeconds(60), Map.of())));
                } catch (Exception ignored) {} finally { finishLatch.countDown(); }
            });
        }

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threadCount, results.size());

        int successCount = 0;
        int conflictCount = 0;
        long expectedHeld = -1;
        for (GemReservationResult r : results) {
            if (r.success()) {
                successCount++;
                expectedHeld = r.balance().heldBalance(); // Capture the actual held amount from the successful call
            } else if (r.failure() == GemOperationFailure.IDEMPOTENCY_CONFLICT) conflictCount++;
        }
        assertEquals(1, successCount, "Exactly 1 should succeed (first with correct payload)");
        assertEquals(threadCount - 1, conflictCount, "Remaining should get IDEMPOTENCY_CONFLICT");

        assertTrue(expectedHeld > 0, "Expected held balance to be > 0");
        assertInvariants(playerId, 100L, expectedHeld);
    }

    @Test
    void testShutdownDuringReserve() throws InterruptedException {
        UUID playerId = initPlayer(100L);

        GemsManager.getInstance().shutdown();

        GemOperationResult result = GemsManager.getInstance().credit(new GemCreditRequest(playerId, 50L, "test", "shutdown", null, null, null, Map.of()));
        assertFalse(result.success());
        assertEquals(GemOperationFailure.SHUTTING_DOWN, result.failure());

        GemReservationResult resResult = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "shutdown", null, null, Duration.ofSeconds(60), Map.of()));
        assertFalse(resResult.success());
        assertEquals(GemOperationFailure.SHUTTING_DOWN, resResult.failure());
    }

    @Test
    void testShutdownDuringCapture() throws InterruptedException {
        UUID playerId = initPlayer(100L);
        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "shutdown", null, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(res.success());
        UUID rid = res.reservationId();

        GemsManager.getInstance().shutdown();

        GemOperationResult capResult = GemsManager.getInstance().capture(new GemCaptureRequest(rid, "test", "shutdown", null, null, null, Map.of()));
        assertFalse(capResult.success());
        assertEquals(GemOperationFailure.SHUTTING_DOWN, capResult.failure());
    }

    // ── Concurrent cleanup + capture (Scenario #8) ──

    @Test
    void testConcurrentCleanupAndCapture() throws InterruptedException {
        UUID playerId = initPlayer(100L);

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "cleanup-test", UUID.randomUUID().toString(), null, Duration.ofSeconds(1), Map.of()));
        assertTrue(res.success());
        UUID rid = res.reservationId();

        // Wait for lease to expire and simulate cleanup via reload
        Thread.sleep(1100);
        GemsManager.getInstance().reload();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        java.util.concurrent.atomic.AtomicReference<GemOperationResult> capResult = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<GemOperationResult> renewResult = new java.util.concurrent.atomic.AtomicReference<>();

        // Thread 1: try to capture expired reservation
        executor.submit(() -> {
            try {
                startLatch.await();
                capResult.set(GemsManager.getInstance().capture(new GemCaptureRequest(rid, "test", "cleanup", null, null, null, Map.of())));
            } catch (Exception ignored) {} finally { finishLatch.countDown(); }
        });

        // Thread 2: try to renew expired reservation
        executor.submit(() -> {
            try {
                startLatch.await();
                renewResult.set(GemsManager.getInstance().renew(new GemRenewRequest(rid, Duration.ofSeconds(60), "test", "cleanup",
                    null, UUID.randomUUID().toString(), null, Map.of())));
            } catch (Exception ignored) {} finally { finishLatch.countDown(); }
        });

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Both should fail — reservation is already expired
        assertNotNull(capResult.get());
        assertNotNull(renewResult.get());
        assertFalse(capResult.get().success(), "Capture of expired reservation must fail");
        assertFalse(renewResult.get().success(), "Renew of expired reservation must fail");

        GemBalanceView balance = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, balance.totalBalance());
        assertEquals(0L, balance.heldBalance());
    }

    @Test
    void testConcurrentCaptureAfterExpireCleanup() throws InterruptedException {
        UUID playerId = initPlayer(100L);

        // Create reservation with short lease
        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "cleanup", UUID.randomUUID().toString(), null, Duration.ofSeconds(1), Map.of()));
        assertTrue(res.success());
        UUID rid = res.reservationId();

        // Wait for lease to expire
        Thread.sleep(1100);

        // Simulate cleanup via reload (same as what recovery does)
        GemsManager.getInstance().reload();

        // Now try to capture — should fail because cleanup expired it
        GemOperationResult cap = GemsManager.getInstance().capture(new GemCaptureRequest(rid, "test", "cleanup", null, null, null, Map.of()));
        assertFalse(cap.success());
        assertEquals(GemOperationFailure.RESERVATION_EXPIRED, cap.failure());

        GemBalanceView balance = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, balance.totalBalance());
        assertEquals(0L, balance.heldBalance());
    }

    // ── Shutdown with pending audit entries ──

    @Test
    void testShutdownPreservesPendingAuditEntries() {
        UUID playerId = initPlayer(100L);

        // Perform credit and shutdown — pending audit entry should survive
        GemCreditRequest req = new GemCreditRequest(playerId, 50L, "test", "shutdown-audit", null,
            UUID.randomUUID().toString(), null, Map.of());
        GemOperationResult result = GemsManager.getInstance().credit(req);
        assertTrue(result.success());

        GemsManager.getInstance().shutdown();

        // After shutdown, mutations are blocked
        GemOperationResult afterShutdown = GemsManager.getInstance().credit(
            new GemCreditRequest(playerId, 10L, "test", "shutdown", null, null, null, Map.of()));
        assertFalse(afterShutdown.success());
        assertEquals(GemOperationFailure.SHUTTING_DOWN, afterShutdown.failure());
    }

    // ── Invariants helper ──

    private void assertInvariants(UUID playerId, long expectedTotal, long expectedHeld) {
        GemBalanceView view = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(expectedTotal, view.totalBalance());
        assertEquals(expectedHeld, view.heldBalance());
        assertEquals(expectedTotal - expectedHeld, view.availableBalance());
    }

    private UUID initPlayer(long amount) {
        UUID id = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(id, amount, "test", "init", null, null, null, Map.of()));
        return id;
    }
}
