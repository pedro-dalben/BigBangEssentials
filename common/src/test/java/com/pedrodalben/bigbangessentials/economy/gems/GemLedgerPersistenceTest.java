package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.economy.gems.api.GemCreditRequest;
import com.pedrodalben.bigbangessentials.economy.gems.api.GemDebitRequest;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemTransaction;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemTransactionType;
import com.pedrodalben.bigbangessentials.economy.gems.manager.GemsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class GemLedgerPersistenceTest {

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
    void testLedgerRecordsCreditAndDebit() {
        UUID playerId = UUID.randomUUID();

        GemsManager.getInstance().credit(new GemCreditRequest(
            playerId, 200L, "test-source", "TEST_CREDIT", null,
            UUID.randomUUID().toString(), "ref-123", Map.of("meta-key", "meta-val")
        ));

        GemsManager.getInstance().debit(new GemDebitRequest(
            playerId, 50L, "test-source", "TEST_DEBIT", null,
            UUID.randomUUID().toString(), "ref-456", Map.of("info", "details")
        ));

        List<GemTransaction> history = GemsManager.getInstance().getHistory(playerId, 1, 10);
        assertEquals(2, history.size());

        GemTransaction debitTx = history.get(0);
        assertEquals(GemTransactionType.DEBIT, debitTx.type());
        assertEquals(50L, debitTx.amount());
        assertEquals(200L, debitTx.balanceBefore());
        assertEquals(150L, debitTx.balanceAfter());

        GemTransaction creditTx = history.get(1);
        assertEquals(GemTransactionType.CREDIT, creditTx.type());
        assertEquals(200L, creditTx.amount());
        assertEquals(0L, creditTx.balanceBefore());
        assertEquals(200L, creditTx.balanceAfter());
        assertEquals("ref-123", creditTx.externalReference());
        assertEquals("meta-val", creditTx.metadata().get("meta-key"));
    }
}
