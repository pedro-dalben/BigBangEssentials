package com.pedrodalben.bigbangessentials.economy;

import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseEconomyServiceTest {
    @TempDir Path temp;

    @Test void concurrentDebitsAndReplayHaveOneFinancialEffect() throws Exception {
        Path config = temp.resolve("database.json");
        Path dbFile = temp.resolve("economy.db");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + dbFile.toString().replace("\\", "\\\\") + "\"}}");
        DatabaseManager manager = DatabaseManager.getInstance();
        manager.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            manager.initialize();
            DatabaseEconomyService economy = new DatabaseEconomyService(manager);
            UUID player = UUID.randomUUID();
            economy.createAccount(player, "test:create:" + player).join();
            economy.setBalance(player, new java.math.BigDecimal("1000"), "test:set:" + player, "test", "test", java.util.Map.of()).join();
            var futures = java.util.stream.IntStream.range(0, 20).mapToObj(i -> economy.debit(player, new java.math.BigDecimal("100"), "test:debit:" + player + ":" + i, "test", java.util.Map.of())).toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            assertEquals(10, futures.stream().map(CompletableFuture::join).filter(r -> r.status() == EconomyOperationStatus.COMPLETED).count());
            assertEquals(0, economy.getBalanceDecimal(player).compareTo(new java.math.BigDecimal("0.00")));
            var replay = java.util.stream.IntStream.range(0, 20).mapToObj(i -> economy.debit(player, new java.math.BigDecimal("1"), "test:replay:" + player, "test", java.util.Map.of())).toList();
            CompletableFuture.allOf(replay.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            assertEquals(1, replay.stream().map(CompletableFuture::join).map(r -> r.id()).distinct().count());
        } finally { manager.shutdown(); }
    }

    @Test void transferDoesNotDebitWhenRecipientIsAtTheBalanceLimit() throws Exception {
        Path config = temp.resolve("database-transfer.json");
        Path dbFile = temp.resolve("economy-transfer.db");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + dbFile.toString().replace("\\", "\\\\") + "\"}}");
        DatabaseManager manager = DatabaseManager.getInstance();
        manager.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            manager.initialize();
            DatabaseEconomyService economy = new DatabaseEconomyService(manager);
            UUID sender = UUID.randomUUID();
            UUID recipient = UUID.randomUUID();
            java.math.BigDecimal senderBalance = new java.math.BigDecimal("100.00");
            java.math.BigDecimal maximum = java.math.BigDecimal.valueOf(ConfigManager.getMaxBalance());
            economy.createAccount(sender, "test:create:" + sender).join();
            economy.createAccount(recipient, "test:create:" + recipient).join();
            economy.setBalance(sender, senderBalance, "test:set:" + sender, "test", "test", java.util.Map.of()).join();
            economy.setBalance(recipient, maximum, "test:set:" + recipient, "test", "test", java.util.Map.of()).join();

            assertFalse(economy.transfer(sender, recipient, new java.math.BigDecimal("10.00"), java.math.BigDecimal.ZERO, "test:transfer:" + sender).join());
            assertEquals(0, senderBalance.compareTo(economy.getBalanceDecimal(sender)));
            assertEquals(0, maximum.compareTo(economy.getBalanceDecimal(recipient)));
        } finally { manager.shutdown(); }
    }

    @Test void reusingAKeyWithDifferentPayloadReturnsConflict() throws Exception {
        Path config = temp.resolve("database-fingerprint.json");
        Path dbFile = temp.resolve("economy-fingerprint.db");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + dbFile.toString().replace("\\", "\\\\") + "\"}}");
        DatabaseManager manager = DatabaseManager.getInstance();
        manager.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            manager.initialize();
            DatabaseEconomyService economy = new DatabaseEconomyService(manager);
            UUID player = UUID.randomUUID();
            economy.createAccount(player, "test:create:fingerprint:" + player).join();
            String key = "test:fingerprint:" + player;
            assertEquals(EconomyOperationStatus.COMPLETED, economy.debit(player, new java.math.BigDecimal("10.00"), key, "test", java.util.Map.of("source", "test")).join().status());
            assertEquals(EconomyOperationStatus.IDEMPOTENCY_CONFLICT, economy.debit(player, new java.math.BigDecimal("11.00"), key, "test", java.util.Map.of("source", "test")).join().status());
            assertEquals(0, economy.getBalanceDecimal(player).compareTo(new java.math.BigDecimal("90.00")));
        } finally { manager.shutdown(); }
    }

    @Test void hasAccountReturnsFastAndCachesKnownAccounts() throws Exception {
        Path config = temp.resolve("database-hasaccount.json");
        Path dbFile = temp.resolve("economy-hasaccount.db");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + dbFile.toString().replace("\\", "\\\\") + "\"}}");
        DatabaseManager manager = DatabaseManager.getInstance();
        manager.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            manager.initialize();
            DatabaseEconomyService economy = new DatabaseEconomyService(manager);
            UUID player = UUID.randomUUID();
            assertFalse(economy.hasAccount(player));
            economy.createAccount(player, "test:create:" + player).join();
            assertTrue(economy.hasAccount(player));
        } finally { manager.shutdown(); }
    }
}
