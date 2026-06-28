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

    @Test
    void testCrashBeforeStateWriteRollsBackReservation() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null, null, null, Map.of()));

        // Set failpoint before state file write
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_WRITE_TEMP;

        GemReservationRequest request = new GemReservationRequest(playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of());
        
        GemReservationResult result = GemsManager.getInstance().reserve(request);
        assertFalse(result.success());
        assertEquals(GemOperationFailure.PERSISTENCE_FAILURE, result.failure());

        // Memory should not have changed because of Copy-on-Write
        GemBalanceView viewBeforeReload = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, viewBeforeReload.totalBalance());
        assertEquals(0L, viewBeforeReload.heldBalance());

        // Reload to simulate recovery
        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();

        // On reload, verify no reservation was persisted
        GemBalanceView viewAfterReload = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, viewAfterReload.totalBalance());
        assertEquals(0L, viewAfterReload.heldBalance());
        assertTrue(GemsManager.getInstance().getActiveReservations(playerId).isEmpty());
    }

    @Test
    void testCrashBeforeCacheSwapKeepsOldMemoryButSavesState() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null, null, null, Map.of()));

        // Set failpoint before cache swap
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_CACHE_SWAP;

        GemReservationRequest request = new GemReservationRequest(playerId, 40L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of());

        GemReservationResult result = GemsManager.getInstance().reserve(request);
        assertFalse(result.success());
        assertEquals(GemOperationFailure.PERSISTENCE_FAILURE, result.failure());

        // Since failpoint triggered before cache swap, memory remains unchanged (0 held)
        GemBalanceView viewBeforeReload = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, viewBeforeReload.totalBalance());
        assertEquals(0L, viewBeforeReload.heldBalance());

        // Reload from disk to simulate crash recovery
        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();

        // Since state file was updated, reloading must correctly compute 40L held balance
        GemBalanceView viewAfterReload = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, viewAfterReload.totalBalance());
        assertEquals(40L, viewAfterReload.heldBalance());
        assertEquals(60L, viewAfterReload.availableBalance());
        assertFalse(GemsManager.getInstance().getActiveReservations(playerId).isEmpty());
    }

    @Test
    void testCrashBeforeAppendLedgerSwapsMemoryAndSavesState() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null, null, null, Map.of()));

        // Set failpoint before ledger append
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_APPEND_LEDGER;

        GemReservationRequest request = new GemReservationRequest(playerId, 40L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of());

        GemReservationResult result = GemsManager.getInstance().reserve(request);
        assertFalse(result.success());
        assertEquals(GemOperationFailure.PERSISTENCE_FAILURE, result.failure());

        // Since failpoint triggered after cache swap, in-memory reference was swapped (40 held)
        GemBalanceView viewBeforeReload = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, viewBeforeReload.totalBalance());
        assertEquals(40L, viewBeforeReload.heldBalance());

        // Reload from disk to simulate crash recovery
        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();

        // Reloaded state still has 40L held
        GemBalanceView viewAfterReload = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, viewAfterReload.totalBalance());
        assertEquals(40L, viewAfterReload.heldBalance());
        assertEquals(60L, viewAfterReload.availableBalance());
    }

    @Test
    void testCrashDuringCapturePreventsBalanceLoss() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null, null, null, Map.of()));

        GemReservationResult resResult = GemsManager.getInstance().reserve(
            new GemReservationRequest(playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of())
        );
        assertTrue(resResult.success());
        UUID reservationId = resResult.reservationId();

        // Fail before writing capture state
        GemsPersistence.activeFailpoint = GemsPersistenceFailpoint.BEFORE_WRITE_TEMP;

        GemCaptureRequest captureRequest = new GemCaptureRequest(reservationId, "test", "capture", null, null, null, Map.of());
        GemOperationResult captureResult = GemsManager.getInstance().capture(captureRequest);
        assertFalse(captureResult.success());
        assertEquals(GemOperationFailure.PERSISTENCE_FAILURE, captureResult.failure());

        // State not updated in memory
        GemBalanceView viewBeforeReload = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, viewBeforeReload.totalBalance());
        assertEquals(30L, viewBeforeReload.heldBalance());

        // Reload and check state is preserved correctly as ACTIVE reservation
        GemsPersistence.activeFailpoint = null;
        GemsManager.getInstance().reload();

        GemBalanceView viewAfterReload = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(100L, viewAfterReload.totalBalance());
        assertEquals(30L, viewAfterReload.heldBalance());

        GemReservation reservation = GemsManager.getInstance().findReservation(reservationId).orElseThrow();
        assertEquals(GemReservationStatus.ACTIVE, reservation.getStatus());
    }
}
