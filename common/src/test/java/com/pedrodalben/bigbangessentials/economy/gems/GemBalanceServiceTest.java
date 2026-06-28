package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemBalanceView;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.io.File;
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
}
