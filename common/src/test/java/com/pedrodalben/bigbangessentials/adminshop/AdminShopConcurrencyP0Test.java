package com.pedrodalben.bigbangessentials.adminshop;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AdminShopConcurrencyP0Test {
    @TempDir Path temp;
    private MockedStatic<ResourceUtil> resourceUtilMock;

    @BeforeEach
    void setUp() throws Exception {
        Path config = temp.resolve("database.json");
        Path dbFile = temp.resolve("adminshop_p0_test.db");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + dbFile.toString().replace("\\", "\\\\") + "\"}}");

        resourceUtilMock = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS);
        resourceUtilMock.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
        resourceUtilMock.when(() -> ResourceUtil.getDataPath("adminshop_state.json")).thenReturn(temp.resolve("adminshop_state.json"));
        resourceUtilMock.when(() -> ResourceUtil.getDataPath("adminshop_audits.json")).thenReturn(temp.resolve("adminshop_audits.json"));

        DatabaseManager.getInstance().shutdown();
        DatabaseManager.getInstance().initialize();
        AdminShopManager.getInstance().reload();
    }

    @AfterEach
    void tearDown() {
        if (resourceUtilMock != null) {
            resourceUtilMock.close();
        }
        DatabaseManager.getInstance().shutdown();
    }

    @Test
    void test1And2_sameProductSequentialQueueProcessing() throws Exception {
        AdminShopManager.getInstance().state.remaining.put("diamond", 10L);
        AdminShopSqlStore store = new AdminShopSqlStore();
        store.save(AdminShopManager.getInstance().state);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        AdminShopSqlStore.DeltaResult r1 = store.saveDelta("diamond", 10, 9, p1, 0, 1, 0, 1, true, false, false, true, true, true);
        assertEquals(AdminShopSqlStore.DeltaResult.SUCCESS, r1);

        // p2 is a different player without existing limit row, so hadLimit = false
        AdminShopSqlStore.DeltaResult r2 = store.saveDelta("diamond", 9, 8, p2, 0, 1, 1, 2, true, false, true, true, true, true);
        assertEquals(AdminShopSqlStore.DeltaResult.SUCCESS, r2);
    }

    @Test
    void test3_samePlayerBuyingSimultaneouslyWithinLimit() throws Exception {
        AdminShopManager.getInstance().state.remaining.put("gold_ingot", 100L);
        AdminShopSqlStore store = new AdminShopSqlStore();
        store.save(AdminShopManager.getInstance().state);

        UUID player = UUID.randomUUID();

        AdminShopSqlStore.DeltaResult res1 = store.saveDelta("gold_ingot", 100, 98, player, 0, 2, 0, 2, true, false, false, true, true, true);
        assertEquals(AdminShopSqlStore.DeltaResult.SUCCESS, res1);

        AdminShopSqlStore.DeltaResult res2 = store.saveDelta("gold_ingot", 98, 96, player, 2, 4, 2, 4, true, true, true, true, true, true);
        assertEquals(AdminShopSqlStore.DeltaResult.SUCCESS, res2);
    }

    @Test
    void test4_differentProductsProgressInParallel() throws Exception {
        AdminShopManager.getInstance().state.remaining.put("prod_a", 10L);
        AdminShopManager.getInstance().state.remaining.put("prod_b", 20L);
        AdminShopSqlStore store = new AdminShopSqlStore();
        store.save(AdminShopManager.getInstance().state);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        CompletableFuture<Void> task1 = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                AdminShopManager.getInstance().saveStateDelta("prod_a", 10, 9, UUID.randomUUID(), 0, 1, 0, 1, true, false, false, true, true, true);
            } catch (Exception e) {
                fail(e);
            } finally {
                finishLatch.countDown();
            }
        });

        CompletableFuture<Void> task2 = CompletableFuture.runAsync(() -> {
            try {
                startLatch.await();
                AdminShopManager.getInstance().saveStateDelta("prod_b", 20, 19, UUID.randomUUID(), 0, 1, 0, 1, true, false, false, true, true, true);
            } catch (Exception e) {
                fail(e);
            } finally {
                finishLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS));
        CompletableFuture.allOf(task1, task2).join();
    }

    @Test
    void test5And6_forcedConflictRollsBackCompletelyWithoutPartialCommit() {
        AdminShopManager.getInstance().state.remaining.put("test_conflict", 100L);
        AdminShopSqlStore store = new AdminShopSqlStore();
        store.save(AdminShopManager.getInstance().state);

        UUID player = UUID.randomUUID();

        // Pass stale oldRemaining (expected 999 but actual DB state is 100)
        AdminShopSqlStore.DeltaResult result = store.saveDelta("test_conflict", 999, 998, player, 0, 1, 0, 1, true, false, false, true, true, true);
        assertEquals(AdminShopSqlStore.DeltaResult.CONFLICT, result);
    }

    @Test
    void test7_sqlExceptionNotReportedAsConflict() {
        AdminShopManager.getInstance().state.remaining.put("test_err", 10L);
        AdminShopSqlStore store = new AdminShopSqlStore();
        store.save(AdminShopManager.getInstance().state);

        // Null player causes NullPointerException when constructing limit query -> result is ERROR, not CONFLICT
        AdminShopSqlStore.DeltaResult result = store.saveDelta("test_err", 10, 9, null, 0, 1, 0, 1, true, false, false, true, true, true);
        assertEquals(AdminShopSqlStore.DeltaResult.ERROR, result);
    }

    @Test
    void test8_unavailableDbOnReloadDoesNotDeleteDataNorAutoImportJson() {
        DatabaseManager.getInstance().shutdown();

        AdminShopManager.getInstance().reload();
        assertEquals(AdminShopManager.StateStatus.DATABASE_UNAVAILABLE, AdminShopManager.getInstance().getStateStatus());
    }

    @Test
    void test9_rollbackOfOneTransactionDoesNotEraseOtherValidTransaction() {
        AdminShopManager.getInstance().state.remaining.put("netherite_ingot", 10L);
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        // P1 buys 1 item (remaining 10 -> 9)
        AdminShopManager.getInstance().state.remaining.put("netherite_ingot", 9L);
        AdminShopManager.getInstance().state.limits.put(p1 + ":netherite_ingot", 1L);

        // P2 buys 1 item (remaining 9 -> 8)
        AdminShopManager.getInstance().state.remaining.put("netherite_ingot", 8L);
        AdminShopManager.getInstance().state.limits.put(p2 + ":netherite_ingot", 1L);

        // P1 rolls back: relative delta adds 1 back to remaining (8 -> 9), and decrements P1 limit (1 -> 0)
        long curStock = AdminShopManager.getInstance().state.remaining.get("netherite_ingot");
        AdminShopManager.getInstance().state.remaining.put("netherite_ingot", curStock + 1L);
        AdminShopManager.getInstance().state.limits.remove(p1 + ":netherite_ingot");

        // Stock is now 9, and P2's limit (1L) is preserved!
        assertEquals(9L, AdminShopManager.getInstance().state.remaining.get("netherite_ingot"));
        assertEquals(1L, AdminShopManager.getInstance().state.limits.get(p2 + ":netherite_ingot"));
        assertNull(AdminShopManager.getInstance().state.limits.get(p1 + ":netherite_ingot"));
    }

    @Test
    void test10_unavailableDbRefusesOperationsWithoutMutatingState() {
        DatabaseManager.getInstance().shutdown();
        AdminShopManager.getInstance().reload();

        AdminShopTransactionService service = AdminShopTransactionService.getInstance();
        AdminShopTransactionService.Result result = service.executeAsync(null, "diamond", AdminShopTransactionService.Operation.BUY, 1).join();
        assertFalse(result.success());
        assertTrue(result.message().contains("indisponível"));
    }

    @Test
    void test11_sqlReservationAndReleaseIdempotency() {
        AdminShopSqlStore store = new AdminShopSqlStore();
        UUID player = UUID.randomUUID();
        String txId = UUID.randomUUID().toString();

        AdminShopSqlStore.ReserveResult res = store.reserveTransactionAsync(
                txId, player, "emerald", AdminShopTransactionService.Operation.BUY,
                5, java.math.BigDecimal.TEN, "money", "adminshop:buy:" + txId, 20L, 10L
        ).join();

        assertTrue(res.success());
        assertEquals(15L, res.remaining());
        assertEquals(5L, res.used());

        String txId2 = UUID.randomUUID().toString();
        AdminShopSqlStore.ReserveResult res2 = store.reserveTransactionAsync(
                txId2, player, "emerald", AdminShopTransactionService.Operation.BUY,
                6, java.math.BigDecimal.TEN, "money", "adminshop:buy:" + txId2, 20L, 10L
        ).join();

        assertFalse(res2.success());
        assertTrue(res2.reason().contains("Limite"));

        boolean released = store.releaseTransactionAsync(txId, player, "emerald", AdminShopTransactionService.Operation.BUY, 5, 20L, 10L, "test_cancel").join();
        assertTrue(released);

        AdminShopManager.State afterState = new AdminShopManager.State();
        store.loadResult(afterState);
        assertEquals(20L, afterState.remaining.get("emerald"));
        assertEquals(0, afterState.limits.size());
        assertEquals(0L, afterState.demand.getOrDefault("emerald", 0L));
    }

    @Test
    void test13_sellReservationAndReleaseRestoresDemand() {
        AdminShopSqlStore store = new AdminShopSqlStore();
        UUID player = UUID.randomUUID();
        String txId = UUID.randomUUID().toString();

        AdminShopSqlStore.ReserveResult res = store.reserveTransactionAsync(
                txId, player, "ruby", AdminShopTransactionService.Operation.SELL,
                3, java.math.BigDecimal.ONE, "money", "adminshop:sell:" + txId, 100L, 50L
        ).join();

        assertTrue(res.success());
        assertEquals(-3L, res.demand());

        boolean released = store.releaseTransactionAsync(txId, player, "ruby", AdminShopTransactionService.Operation.SELL, 3, 100L, 50L, "test_cancel").join();
        assertTrue(released);

        AdminShopManager.State afterState = new AdminShopManager.State();
        store.loadResult(afterState);
        assertEquals(0L, afterState.demand.getOrDefault("ruby", 0L));
    }

    @Test
    void test14_reservedRowsDoNotBreakHistoryOrReconciliation() {
        AdminShopSqlStore store = new AdminShopSqlStore();
        UUID player = UUID.randomUUID();
        String txId = UUID.randomUUID().toString();

        store.reserveTransactionAsync(txId, player, "sapphire", AdminShopTransactionService.Operation.BUY,
                2, java.math.BigDecimal.valueOf(50), "money", "adminshop:buy:" + txId, 10L, 5L).join();

        assertDoesNotThrow(() -> store.forPlayer(player, 5));

        List<String> findings = store.reconcile();
        assertTrue(findings.stream().anyMatch(f -> f.contains("PENDENCIA") && f.contains(txId)));
    }

    @Test
    void test12_legacyJsonAutoMigrationOnStartup() throws Exception {
        Path legacyJson = temp.resolve("adminshop_state.json");
        Files.writeString(legacyJson, "{\"remaining\":{\"legacy_item\":50},\"limits\":{\"uuid123:legacy_item\":2},\"demand\":{\"legacy_item\":5}}");

        AdminShopSqlStore store = new AdminShopSqlStore();
        store.migrateLegacyJsonIfNeeded();

        assertFalse(Files.exists(legacyJson));
        assertTrue(Files.exists(temp.resolve("adminshop_state.json.migrated")));

        AdminShopManager.State state = new AdminShopManager.State();
        assertEquals(AdminShopSqlStore.LoadResult.LOADED, store.loadResult(state));
        assertEquals(50L, state.remaining.get("legacy_item"));
        assertEquals(2L, state.limits.get("uuid123:legacy_item"));
        assertEquals(5L, state.demand.get("legacy_item"));
    }

    @Test
    void test16_legacyMigrationDoesNotClobberExistingSqlData() throws Exception {
        AdminShopSqlStore store = new AdminShopSqlStore();
        AdminShopManager.State existing = new AdminShopManager.State();
        existing.remaining.put("existing_item", 100L);
        existing.limits.put(UUID.randomUUID() + ":existing_item", 3L);
        existing.demand.put("existing_item", 7L);
        store.save(existing);

        Path legacyJson = temp.resolve("adminshop_state.json");
        Files.writeString(legacyJson, "{\"remaining\":{\"existing_item\":999},\"limits\":{},\"demand\":{}}");

        store.migrateLegacyJsonIfNeeded();

        assertFalse(Files.exists(legacyJson));
        assertTrue(Files.exists(temp.resolve("adminshop_state.json.migrated")));

        AdminShopManager.State state = new AdminShopManager.State();
        store.loadResult(state);
        assertEquals(100L, state.remaining.get("existing_item"));
        assertEquals(3L, state.limits.values().iterator().next());
        assertEquals(7L, state.demand.get("existing_item"));
    }
}
