package com.pedrodalben.bigbangessentials.economy.gems;

import com.pedrodalben.bigbangessentials.api.BigBangEssentialsApi;
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
class GemApiContractTest {

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
    void testApiAvailability() {
        assertTrue(BigBangEssentialsApi.isGemsEnabled());
        assertEquals(1, BigBangEssentialsApi.gemsApiVersion());
        assertNotNull(BigBangEssentialsApi.requireGems());
        assertTrue(BigBangEssentialsApi.gems().isPresent());
    }

    @Test
    void testCreditViaService() {
        GemsService service = BigBangEssentialsApi.requireGems();
        UUID playerId = UUID.randomUUID();

        GemCreditRequest req = new GemCreditRequest(
            playerId, 500L, "api-source", "API_PURPOSE", null,
            UUID.randomUUID().toString(), null, Map.of()
        );
        GemOperationResult result = service.credit(req);
        assertTrue(result.success());
        assertNotNull(result.transactionId());
        assertEquals(500L, result.balance().totalBalance());

        GemBalanceView view = service.getBalance(playerId);
        assertEquals(500L, view.totalBalance());
        assertTrue(service.hasAvailable(playerId, 200L));
    }
}
