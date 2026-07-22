package com.pedrodalben.bigbangessentials.adminshop;

import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdminShopAuditStoreTest {
    @TempDir Path temp;

    @Test
    void recordsCompletedSagaWithoutChangingLegacyRows() throws Exception {
        Path config = temp.resolve("database.json");
        Path dbFile = temp.resolve("adminshop.db");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + dbFile.toString().replace("\\", "\\\\") + "\"}}");
        DatabaseManager database = DatabaseManager.getInstance();
        database.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            database.initialize();
            AdminShopSqlStore store = new AdminShopSqlStore();
            UUID player = UUID.randomUUID();
            String tx = UUID.randomUUID().toString();
            assertTrue(store.startAudit(tx, player, "diamond", "SELL", "gems", 1, BigDecimal.ONE, "adminshop:sell:" + tx, "NOT_APPLICABLE", "CHECKED"));
            assertTrue(store.updateAudit(tx, AdminShopAuditStatus.COMPLETED,
                new EconomyOperationReceipt(UUID.randomUUID(), player, BigDecimal.ONE, EconomyOperationStatus.COMPLETED,
                    BigDecimal.TEN, BigDecimal.valueOf(11), "adminshop:sell:" + tx),
                "APPLIED", "NOT_APPLICABLE", "APPLIED", "APPLIED", null));
            assertTrue(store.log(tx, player, "diamond", "SELL", "gems", BigDecimal.ONE));
            assertEquals(AdminShopAuditStatus.COMPLETED, store.inspect(tx).orElseThrow().status());
            assertTrue(store.forPlayer(player, 10).getFirst().format().contains("status=COMPLETED"));

            String legacyTx = UUID.randomUUID().toString();
            assertTrue(store.log(legacyTx, player, "coal", "SELL", "gems", BigDecimal.ONE));
            assertTrue(store.reconcile().stream().anyMatch(s -> s.contains("HISTORICO_SEM_AUDITORIA tx=" + legacyTx)));
        } finally {
            database.shutdown();
        }
    }
}
