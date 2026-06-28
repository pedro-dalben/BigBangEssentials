package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemBalanceView;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservation;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservationStatus;
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
class GemBalanceServiceTest {

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
    void testGemsStartAtZero() {
        UUID playerId = UUID.randomUUID();
        GemBalanceView view = GemsManager.getInstance().getBalanceView(playerId);
        assertEquals(0, view.totalBalance());
        assertEquals(0, view.availableBalance());
        assertEquals(0, view.heldBalance());
    }

    @Test
    void testCreditGems() {
        UUID playerId = UUID.randomUUID();
        GemCreditRequest req = new GemCreditRequest(
            playerId, 100L, "test-source", "TEST_PURPOSE", null,
            UUID.randomUUID().toString(), null, Map.of()
        );
        GemOperationResult res = GemsManager.getInstance().credit(req);
        assertTrue(res.success());
        assertEquals(100L, res.balance().totalBalance());
        assertEquals(100L, res.balance().availableBalance());
    }

    @Test
    void testDebitGems() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(
            playerId, 100L, "test-source", "TEST_PURPOSE", null,
            UUID.randomUUID().toString(), null, Map.of()
        ));

        GemDebitRequest req = new GemDebitRequest(
            playerId, 40L, "test-source", "TEST_PURPOSE", null,
            UUID.randomUUID().toString(), null, Map.of()
        );
        GemOperationResult res = GemsManager.getInstance().debit(req);
        assertTrue(res.success());
        assertEquals(60L, res.balance().totalBalance());
        assertEquals(60L, res.balance().availableBalance());
    }

    @Test
    void testInsufficientBalanceForDebit() {
        UUID playerId = UUID.randomUUID();
        GemDebitRequest req = new GemDebitRequest(
            playerId, 40L, "test-source", "TEST_PURPOSE", null,
            UUID.randomUUID().toString(), null, Map.of()
        );
        GemOperationResult res = GemsManager.getInstance().debit(req);
        assertFalse(res.success());
        assertEquals(GemOperationFailure.INSUFFICIENT_AVAILABLE_BALANCE, res.failure());
    }

    @Test
    void testMaxBalanceConstraint() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().getConfig().balances.maxBalance = 500L;

        GemCreditRequest req1 = new GemCreditRequest(
            playerId, 400L, "test-source", "TEST_PURPOSE", null,
            UUID.randomUUID().toString(), null, Map.of()
        );
        assertTrue(GemsManager.getInstance().credit(req1).success());

        GemCreditRequest req2 = new GemCreditRequest(
            playerId, 200L, "test-source", "TEST_PURPOSE", null,
            UUID.randomUUID().toString(), null, Map.of()
        );
        GemOperationResult res = GemsManager.getInstance().credit(req2);
        assertFalse(res.success());
        assertEquals(GemOperationFailure.MAX_BALANCE_EXCEEDED, res.failure());
    }

    // ── Admin command simulation tests (P0-09) ──

    @Test
    void testAdminSetCannotGoBelowHeld() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null,
            UUID.randomUUID().toString(), null, Map.of()));

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        assertTrue(res.success());

        // Set below held should fail
        GemOperationResult setRes = GemsManager.getInstance().setBalance(new GemSetBalanceRequest(
            playerId, 20L, "admin-command", "ADMIN_SET", null, "test", Map.of()));
        assertFalse(setRes.success());
        assertEquals(GemOperationFailure.INSUFFICIENT_AVAILABLE_BALANCE, setRes.failure());
    }

    @Test
    void testAdminSetCannotBeNegative() {
        UUID playerId = UUID.randomUUID();

        GemOperationResult setRes = GemsManager.getInstance().setBalance(new GemSetBalanceRequest(
            playerId, -5L, "admin-command", "ADMIN_SET", null, "test", Map.of()));
        assertFalse(setRes.success());
        assertEquals(GemOperationFailure.INVALID_AMOUNT, setRes.failure());
    }

    @Test
    void testAdminSetCannotExceedMaxBalance() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().getConfig().balances.maxBalance = 1000L;

        GemOperationResult setRes = GemsManager.getInstance().setBalance(new GemSetBalanceRequest(
            playerId, 2000L, "admin-command", "ADMIN_SET", null, "test", Map.of()));
        assertFalse(setRes.success());
        assertEquals(GemOperationFailure.MAX_BALANCE_EXCEEDED, setRes.failure());
    }

    @Test
    void testAdminTakeAboveAvailableFails() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 50L, "test", "init", null,
            UUID.randomUUID().toString(), null, Map.of()));

        // Reserve 30 of 50 → 20 available
        GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));

        // Take 40 > 20 available
        GemOperationResult takeRes = GemsManager.getInstance().debit(new GemDebitRequest(
            playerId, 40L, "admin-command", "ADMIN_TAKE", null, UUID.randomUUID().toString(), null, Map.of()));
        assertFalse(takeRes.success());
        assertEquals(GemOperationFailure.INSUFFICIENT_AVAILABLE_BALANCE, takeRes.failure());
    }

    @Test
    void testResetWithActiveReservationFails() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null,
            UUID.randomUUID().toString(), null, Map.of()));

        // Reserve 30
        GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));

        // Reset to startingBalance (0) should fail because 0 < 30 held
        long startingBalance = GemsManager.getInstance().getConfig().balances.startingBalance;
        GemOperationResult resetRes = GemsManager.getInstance().setBalance(new GemSetBalanceRequest(
            playerId, startingBalance, "admin-command", "ADMIN_RESET", null, "reason", Map.of()));
        assertFalse(resetRes.success());
        assertEquals(GemOperationFailure.INSUFFICIENT_AVAILABLE_BALANCE, resetRes.failure());
    }

    @Test
    void testAdminResetWithCustomStartingBalance() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().getConfig().balances.startingBalance = 100L;

        GemOperationResult resetRes = GemsManager.getInstance().setBalance(new GemSetBalanceRequest(
            playerId, 100L, "admin-command", "ADMIN_RESET", null, "reason", Map.of()));
        assertTrue(resetRes.success());
        assertEquals(100L, resetRes.balance().totalBalance());
    }

    @Test
    void testCheckReservationReleaseAfterCaptureFails() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null,
            UUID.randomUUID().toString(), null, Map.of()));

        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(60), Map.of()));
        UUID rid = res.reservationId();

        assertTrue(GemsManager.getInstance().capture(new GemCaptureRequest(
            rid, "test", "capture", null, null, null, Map.of())).success());

        GemOperationResult rel = GemsManager.getInstance().release(new GemReleaseRequest(
            rid, "test", "release", null, "test", null, null, Map.of()));
        assertFalse(rel.success());
        assertEquals(GemOperationFailure.RESERVATION_ALREADY_CAPTURED, rel.failure());
    }

    @Test
    void testReleaseOfExpiredViaRenewFails() {
        UUID playerId = UUID.randomUUID();
        GemsManager.getInstance().credit(new GemCreditRequest(playerId, 100L, "test", "init", null,
            UUID.randomUUID().toString(), null, Map.of()));

        // Create reservation with very short lease
        GemReservationResult res = GemsManager.getInstance().reserve(new GemReservationRequest(
            playerId, 30L, "test", "reserve", null, null, Duration.ofSeconds(1), Map.of()));
        UUID rid = res.reservationId();

        // Wait for it to expire
        try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
        GemsManager.getInstance().reload();

        // Renew of expired should fail
        GemOperationResult ren = GemsManager.getInstance().renew(new GemRenewRequest(
            rid, Duration.ofSeconds(60), "test", "renew", null, null, null, Map.of()));
        assertFalse(ren.success());
        assertEquals(GemOperationFailure.RESERVATION_NOT_ACTIVE, ren.failure());
    }
}
