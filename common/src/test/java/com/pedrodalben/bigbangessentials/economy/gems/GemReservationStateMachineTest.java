package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.domain.*;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class GemReservationStateMachineTest {

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
    void testReservationReducesAvailableButNotTotal() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(
            playerId, 100L, "test-source", "TEST", null,
            UUID.randomUUID().toString(), null, Map.of()
        ));

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test-source", "TEST", UUID.randomUUID().toString(),
            "ref-op", Duration.ofSeconds(60), Map.of()
        ));

        assertTrue(res.success());
        assertNotNull(res.reservationId());
        assertEquals(100L, res.balance().totalBalance());
        assertEquals(30L, res.balance().heldBalance());
        assertEquals(70L, res.balance().availableBalance());
    }

    @Test
    void testCaptureDeductsTotalAndHeld() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(
            playerId, 100L, "test-source", "TEST", null,
            UUID.randomUUID().toString(), null, Map.of()
        ));

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test-source", "TEST", UUID.randomUUID().toString(),
            "ref-op", Duration.ofSeconds(60), Map.of()
        ));

        GemOperationResult captureRes = GemsManager.getInstance().capture(new GemCaptureRequest(
            res.reservationId(), "test-source", "TEST", null,
            UUID.randomUUID().toString(), "ref-capture", Map.of()
        ));

        assertTrue(captureRes.success());
        assertEquals(70L, captureRes.balance().totalBalance());
        assertEquals(0L, captureRes.balance().heldBalance());
        assertEquals(70L, captureRes.balance().availableBalance());

        GemReservation r = GemsManager.getInstance().findReservation(res.reservationId()).orElseThrow();
        assertEquals(GemReservationStatus.CAPTURED, r.getStatus());
    }

    @Test
    void testReleaseRestoresAvailable() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(
            playerId, 100L, "test-source", "TEST", null,
            UUID.randomUUID().toString(), null, Map.of()
        ));

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test-source", "TEST", UUID.randomUUID().toString(),
            "ref-op", Duration.ofSeconds(60), Map.of()
        ));

        GemOperationResult releaseRes = GemsManager.getInstance().release(new GemReleaseRequest(
            res.reservationId(), "test-source", "TEST", null, "manual abort", null, null, Map.of()
        ));

        assertTrue(releaseRes.success());
        assertEquals(100L, releaseRes.balance().totalBalance());
        assertEquals(0L, releaseRes.balance().heldBalance());
        assertEquals(100L, releaseRes.balance().availableBalance());

        GemReservation r = GemsManager.getInstance().findReservation(res.reservationId()).orElseThrow();
        assertEquals(GemReservationStatus.RELEASED, r.getStatus());
    }

    @Test
    void testInvalidTransitions() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(
            playerId, 100L, "test-source", "TEST", null,
            UUID.randomUUID().toString(), null, Map.of()
        ));

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test-source", "TEST", UUID.randomUUID().toString(),
            "ref-op", Duration.ofSeconds(60), Map.of()
        ));

        assertTrue(GemsManager.getInstance().capture(new GemCaptureRequest(
            res.reservationId(), "test-source", "TEST", null,
            UUID.randomUUID().toString(), "ref-capture", Map.of()
        )).success());

        GemOperationResult releaseRes = GemsManager.getInstance().release(new GemReleaseRequest(
            res.reservationId(), "test-source", "TEST", null, "manual abort", null, null, Map.of()
        ));
        assertFalse(releaseRes.success());
        assertEquals(GemOperationFailure.RESERVATION_ALREADY_CAPTURED, releaseRes.failure());
    }
}
