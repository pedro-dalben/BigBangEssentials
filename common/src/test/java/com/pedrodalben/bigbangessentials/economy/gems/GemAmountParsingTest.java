package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.economy.gems.api.GemCreditRequest;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemDebitRequest;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemOperationFailure;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemOperationResult;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.io.File;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class GemAmountParsingTest {

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
    void testCreditZeroAmountIsRejected() {
        GemCreditRequest req = new GemCreditRequest(
            UUID.randomUUID(), 0, "test-source", "TEST_PURPOSE", null,
            UUID.randomUUID().toString(), null, Map.of()
        );
        GemOperationResult res = GemsManager.getInstance().credit(req);
        assertFalse(res.success());
        assertEquals(GemOperationFailure.INVALID_AMOUNT, res.failure());
    }

    @Test
    void testCreditNegativeAmountIsRejected() {
        GemCreditRequest req = new GemCreditRequest(
            UUID.randomUUID(), -500L, "test-source", "TEST_PURPOSE", null,
            UUID.randomUUID().toString(), null, Map.of()
        );
        GemOperationResult res = GemsManager.getInstance().credit(req);
        assertFalse(res.success());
        assertEquals(GemOperationFailure.INVALID_AMOUNT, res.failure());
    }

    @Test
    void testDebitZeroAmountIsRejected() {
        GemDebitRequest req = new GemDebitRequest(
            UUID.randomUUID(), 0, "test-source", "TEST_PURPOSE", null,
            UUID.randomUUID().toString(), null, Map.of()
        );
        GemOperationResult res = GemsManager.getInstance().debit(req);
        assertFalse(res.success());
        assertEquals(GemOperationFailure.INVALID_AMOUNT, res.failure());
    }

    @Test
    void testDebitNegativeAmountIsRejected() {
        GemDebitRequest req = new GemDebitRequest(
            UUID.randomUUID(), -10L, "test-source", "TEST_PURPOSE", null,
            UUID.randomUUID().toString(), null, Map.of()
        );
        GemOperationResult res = GemsManager.getInstance().debit(req);
        assertFalse(res.success());
        assertEquals(GemOperationFailure.INVALID_AMOUNT, res.failure());
    }
}
