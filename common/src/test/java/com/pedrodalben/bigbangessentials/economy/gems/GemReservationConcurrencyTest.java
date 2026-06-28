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
}
