package com.pedrodalben.bigbangessentials.economy;

import com.pedrodalben.bigbangessentials.api.economy.CommercialTransferStatus;
import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommercialTransferTest {
    @TempDir Path temp;

    @Test
    void commerceTransferSettlesBothAccountsOnceAndRejectsPayloadConflict() throws Exception {
        Path config = temp.resolve("database.json");
        Path db = temp.resolve("commerce.db");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + db + "\"}}");
        DatabaseManager manager = DatabaseManager.getInstance();
        manager.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            manager.initialize();
            DatabaseEconomyService economy = new DatabaseEconomyService(manager);
            UUID buyer = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            economy.setBalance(buyer, new BigDecimal("100.00"), "test:buyer:set", "test", "setup", Map.of()).join();
            economy.setBalance(owner, BigDecimal.ZERO, "test:owner:set", "test", "setup", Map.of()).join();

            String key = "chestshop:buy:test";
            var first = economy.commercialTransfer(buyer, owner, new BigDecimal("12.50"), key, "chestshop").join();
            assertEquals(CommercialTransferStatus.COMPLETED, first.status());
            assertEquals(new BigDecimal("87.50"), economy.getBalanceDecimal(buyer));
            assertEquals(new BigDecimal("12.50"), economy.getBalanceDecimal(owner));

            var replay = economy.commercialTransfer(buyer, owner, new BigDecimal("12.50"), key, "chestshop").join();
            assertEquals(CommercialTransferStatus.IDEMPOTENT_REPLAY, replay.status());
            assertEquals(new BigDecimal("87.50"), economy.getBalanceDecimal(buyer));
            assertEquals(new BigDecimal("12.50"), economy.getBalanceDecimal(owner));

            var conflict = economy.commercialTransfer(buyer, owner, new BigDecimal("13.50"), key, "chestshop").join();
            assertEquals(CommercialTransferStatus.IDEMPOTENCY_CONFLICT, conflict.status());
            assertTrue(conflict.error() != null && !conflict.error().isBlank());
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void concurrentCommerceTransfersConserveMoney() throws Exception {
        Path config = temp.resolve("database-concurrency.json");
        Path db = temp.resolve("commerce-concurrency.db");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + db + "\"}}");
        DatabaseManager manager = DatabaseManager.getInstance();
        manager.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            manager.initialize();
            DatabaseEconomyService economy = new DatabaseEconomyService(manager);
            UUID owner = UUID.randomUUID();
            economy.setBalance(owner, BigDecimal.ZERO, "test:owner:set:concurrency", "test", "setup", Map.of()).join();
            var buyers = java.util.stream.IntStream.range(0, 20).mapToObj(i -> {
                UUID buyer = UUID.randomUUID();
                economy.setBalance(buyer, BigDecimal.TEN, "test:buyer:set:" + i, "test", "setup", Map.of()).join();
                return buyer;
            }).toList();
            var transfers = buyers.stream().map(buyer -> economy.commercialTransfer(buyer, owner, BigDecimal.ONE,
                    "chestshop:concurrency:" + buyer, "chestshop")).toList();
            CompletableFuture.allOf(transfers.toArray(CompletableFuture[]::new)).join();
            assertEquals(new BigDecimal("20.00"), economy.getBalanceDecimal(owner));
            assertEquals(new BigDecimal("180.00"), buyers.stream().map(economy::getBalanceDecimal).reduce(BigDecimal.ZERO, BigDecimal::add));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void concurrentReplayOfOneKeySettlesOnlyOnce() throws Exception {
        Path config = temp.resolve("database-replay.json");
        Path db = temp.resolve("commerce-replay.db");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + db + "\"}}");
        DatabaseManager manager = DatabaseManager.getInstance();
        manager.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            manager.initialize();
            DatabaseEconomyService economy = new DatabaseEconomyService(manager);
            UUID buyer = UUID.randomUUID();
            UUID owner = UUID.randomUUID();
            economy.setBalance(buyer, new BigDecimal("20.00"), "test:replay:buyer", "test", "setup", Map.of()).join();
            economy.setBalance(owner, BigDecimal.ZERO, "test:replay:owner", "test", "setup", Map.of()).join();
            String key = "chestshop:replay:one-key";
            var results = java.util.stream.IntStream.range(0, 12)
                    .mapToObj(i -> economy.commercialTransfer(buyer, owner, new BigDecimal("2.50"), key, "chestshop"))
                    .toList();
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).join();
            assertEquals(new BigDecimal("17.50"), economy.getBalanceDecimal(buyer));
            assertEquals(new BigDecimal("2.50"), economy.getBalanceDecimal(owner));
            assertTrue(results.stream().allMatch(f -> f.join().status() == CommercialTransferStatus.COMPLETED
                    || f.join().status() == CommercialTransferStatus.IDEMPOTENT_REPLAY));
        } finally {
            manager.shutdown();
        }
    }
}
