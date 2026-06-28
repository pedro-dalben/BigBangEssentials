package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.domain.*;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsPersistence;
import com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class GemReservationRecoveryTest {

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
    void testRecoveryRecalculatesHeldBalancesAndExpiresVenced() {
        UUID playerId = UUID.randomUUID();
        GemsState state = new GemsState();
        state.schemaVersion = 1;
        state.balances.put(playerId.toString(), 100L);

        UUID resId1 = UUID.randomUUID();
        long now = System.currentTimeMillis();
        GemReservation res1 = new GemReservation(
            resId1, playerId, 30L, GemReservationStatus.ACTIVE,
            "test", "test", null, null, Map.of(), now, now + 1000000L, null, null
        );
        state.reservations.put(resId1.toString(), res1);

        UUID resId2 = UUID.randomUUID();
        GemReservation res2 = new GemReservation(
            resId2, playerId, 20L, GemReservationStatus.ACTIVE,
            "test", "test", null, null, Map.of(), now - 10000L, now - 1000L, null, null
        );
        state.reservations.put(resId2.toString(), res2);

        GemsPersistence persistence = new GemsPersistence();
        persistence.saveState(state);

        GemsManager.getInstance().reload();

        GemReservation recoveredRes1 = GemsManager.getInstance().findReservation(resId1).orElseThrow();
        assertEquals(GemReservationStatus.ACTIVE, recoveredRes1.getStatus());

        GemReservation recoveredRes2 = GemsManager.getInstance().findReservation(resId2).orElseThrow();
        assertEquals(GemReservationStatus.EXPIRED, recoveredRes2.getStatus());

        GemBalanceView view = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, view.totalBalance());
        assertEquals(30L, view.heldBalance());
        assertEquals(70L, view.availableBalance());
    }

    @Test
    void testShutdownAndReloadPreservesCreditNoDoubleSpend() {
        UUID playerId = UUID.randomUUID();
        String idemKey = "shutdown-reload-credit-key";

        GemCreditRequest req = new GemCreditRequest(playerId, 100L, "test", "init", null,
            idemKey, null, Map.of());
        GemOperationResult res1 = GemsManager.getInstance().credit(req);
        assertTrue(res1.success());

        GemsManager.getInstance().shutdown();
        GemsManager.getInstance().reload();

        GemBalanceView view = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, view.totalBalance());
        assertEquals(0L, view.heldBalance());

        // Retry with same key should return idempotent success (no double credit)
        GemOperationResult res2 = GemsManager.getInstance().credit(req);
        assertTrue(res2.success());
        assertEquals(100L, res2.balance().totalBalance());
        assertEquals(res1.transactionId(), res2.transactionId());
    }

    @Test
    void testShutdownAndReloadPreservesReservationCaptureFlow() {
        UUID playerId = UUID.randomUUID();
        String reserveKey = "shutdown-reload-reserve-key";
        String captureKey = "shutdown-reload-capture-key";

        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null,
            UUID.randomUUID().toString(), null, Map.of()));

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "reserve", reserveKey, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(res.success());
        UUID rid = res.reservationId();

        GemsManager.getInstance().shutdown();
        GemsManager.getInstance().reload();

        // Reservation should survive shutdown
        GemReservation loaded = GemsManager.getInstance().findReservation(rid).orElseThrow();
        assertEquals(GemReservationStatus.ACTIVE, loaded.getStatus());

        // Capture with idempotency key
        GemOperationResult cap = GemsManager.getInstance().capture(new GemCaptureRequest(
            rid, "test", "capture", null, captureKey, null, Map.of()));
        assertTrue(cap.success());
        assertEquals(70L, cap.balance().totalBalance());

        // Re-capture with same key should be idempotent
        GemsManager.getInstance().shutdown();
        GemsManager.getInstance().reload();
        GemOperationResult capRetry = GemsManager.getInstance().capture(new GemCaptureRequest(
            rid, "test", "capture", null, captureKey, null, Map.of()));
        assertTrue(capRetry.success());
        assertEquals(70L, capRetry.balance().totalBalance());
    }

    @Test
    void testShutdownAndReloadPreservesReleaseIdempotency() {
        UUID playerId = UUID.randomUUID();
        String releaseKey = "shutdown-reload-release-key";

        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null,
            UUID.randomUUID().toString(), null, Map.of()));

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(res.success());
        UUID rid = res.reservationId();

        GemOperationResult rel = GemsManager.getInstance().release(new GemReleaseRequest(
            rid, "test", "release", null, "test", releaseKey, null, Map.of()));
        assertTrue(rel.success());
        assertEquals(100L, rel.balance().totalBalance());
        assertEquals(0L, rel.balance().heldBalance());

        GemsManager.getInstance().shutdown();
        GemsManager.getInstance().reload();

        GemOperationResult relRetry = GemsManager.getInstance().release(new GemReleaseRequest(
            rid, "test", "release", null, "test", releaseKey, null, Map.of()));
        assertTrue(relRetry.success());
        assertEquals(100L, relRetry.balance().totalBalance());
    }

    @Test
    void testShutdownAndReloadPreservesRenewIdempotency() {
        UUID playerId = UUID.randomUUID();
        String renewKey = "shutdown-reload-renew-key";

        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null,
            UUID.randomUUID().toString(), null, Map.of()));

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(res.success());
        UUID rid = res.reservationId();

        GemOperationResult ren = GemsManager.getInstance().renew(new GemRenewRequest(
            rid, Duration.ofSeconds(120), "test", "renew", null, renewKey, null, Map.of()));
        assertTrue(ren.success());

        GemsManager.getInstance().shutdown();
        GemsManager.getInstance().reload();

        GemOperationResult renRetry = GemsManager.getInstance().renew(new GemRenewRequest(
            rid, Duration.ofSeconds(120), "test", "renew", null, renewKey, null, Map.of()));
        assertTrue(renRetry.success());
    }

    @Test
    void testShutdownPreservesStateAndIdempotencyRecords() {
        UUID playerId = UUID.randomUUID();
        String key = "shutdown-idem-key";

        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null,
            key, null, Map.of()));

        // Shutdown and manually inspect the state file to verify idempotencyRecords persisted
        GemsManager.getInstance().shutdown();

        GemsPersistence persistence = new GemsPersistence();
        GemsState loaded = persistence.loadState();
        assertNotNull(loaded.idempotencyRecords);
        assertTrue(loaded.idempotencyRecords.containsKey(key),
            "Idempotency record must be persisted in state after shutdown");
    }
}
