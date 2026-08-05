package com.pedrodalben.bigbangessentials.database;

import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.api.economy.CommercialTransferStatus;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.config.GemConfig;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemReservationStatus;
import com.pedrodalben.bigbangessentials.economy.gems.domain.GemTransactionType;
import com.pedrodalben.bigbangessentials.economy.gems.service.DatabaseGemsService;
import com.pedrodalben.bigbangessentials.economy.migration.GemsJsonMigrationService;
import com.pedrodalben.bigbangessentials.economy.gems.persistence.GemsState;
import com.google.gson.Gson;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MySqlIntegrationTest {
    @TempDir Path temp;
    @Test void mysqlMigrationsAndEconomyRoundTrip() throws Exception {
        String host = System.getenv("BBE_TEST_MYSQL_HOST");
        if (host != null && !host.isBlank()) {
            run(host, Optional.ofNullable(System.getenv("BBE_TEST_MYSQL_PORT")).orElse("3306"),
                Optional.ofNullable(System.getenv("BBE_TEST_MYSQL_DATABASE")).orElse("bbe_test"),
                Optional.ofNullable(System.getenv("BBE_TEST_MYSQL_USER")).orElse("root"),
                Optional.ofNullable(System.getenv("BBE_TEST_MYSQL_PASSWORD")).orElse(""));
            return;
        }
        MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");
        try {
            mysql.start();
        } catch (Throwable unavailable) {
            Assumptions.assumeTrue(false, "SKIPPED — no usable MySQL runtime: " + unavailable.getMessage());
        }
        try (mysql) {
            run(mysql.getHost(), Integer.toString(mysql.getFirstMappedPort()), mysql.getDatabaseName(), mysql.getUsername(), mysql.getPassword());
        }
    }

    private void run(String host, String port, String database, String user, String password) throws Exception {
        Path config = temp.resolve("database.json");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"MYSQL\",\"mysql\":{\"host\":\"" + host + "\",\"port\":" + port + ",\"database\":\"" + database + "\",\"username\":\"" + user + "\",\"password\":\"" + password + "\"}}");
        DatabaseManager manager = DatabaseManager.getInstance(); manager.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile()); manager.initialize(); assertTrue(manager.isReady()); assertEquals(manager.getRegisteredMigrations().getLast().version(), manager.getHealth().schemaVersion());
            DatabaseEconomyService economy = new DatabaseEconomyService(manager); UUID player = UUID.randomUUID(); economy.createAccount(player, "mysql:create:" + player).join(); var firstCredit = economy.credit(player, BigDecimal.TEN, "mysql:credit:" + player, "test", Map.of("source", "mysql-test")).join(); assertEquals(EconomyOperationStatus.COMPLETED, firstCredit.status()); assertEquals("money", firstCredit.currency()); assertTrue(firstCredit.timestamp() > 0); assertEquals("mysql-test", firstCredit.sourceModule()); assertEquals(0, economy.getBalanceDecimal(player).compareTo(BigDecimal.valueOf(110, 0).setScale(2)));

            economy.setBalance(player, BigDecimal.ZERO, "mysql:reset:" + player, "test", "reset", Map.of()).join();
            long benchmarkStarted = System.nanoTime();
            var credits = java.util.stream.IntStream.range(0, 100)
                    .mapToObj(i -> economy.credit(player, BigDecimal.ONE, "mysql:concurrent-credit:" + i, "test", Map.of("source", "mysql-test"))).toList();
            java.util.concurrent.CompletableFuture.allOf(credits.toArray(java.util.concurrent.CompletableFuture[]::new)).join();
            long benchmarkElapsedMs = (System.nanoTime() - benchmarkStarted) / 1_000_000;
            System.out.println("mysql-economy-benchmark ops=100 elapsed_ms=" + benchmarkElapsedMs);
            assertEquals(100, credits.stream().map(java.util.concurrent.CompletableFuture::join).filter(r -> r.status() == EconomyOperationStatus.COMPLETED).count());
            assertEquals(0, economy.getBalanceDecimal(player).compareTo(new BigDecimal("100.00")));

            String replayKey = "mysql:concurrent-replay:" + player;
            var replays = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(i -> economy.credit(player, BigDecimal.ONE, replayKey, "test", Map.of("source", "mysql-test"))).toList();
            java.util.concurrent.CompletableFuture.allOf(replays.toArray(java.util.concurrent.CompletableFuture[]::new)).join();
            assertEquals(1, replays.stream().map(java.util.concurrent.CompletableFuture::join).map(r -> r.id()).distinct().count());
            assertTrue(replays.stream().map(java.util.concurrent.CompletableFuture::join).anyMatch(r -> r.idempotentReplay()));
            assertEquals(EconomyOperationStatus.IDEMPOTENCY_CONFLICT, economy.credit(player, BigDecimal.TWO, replayKey, "test", Map.of("source", "mysql-test")).join().status());
            assertEquals(0, economy.getBalanceDecimal(player).compareTo(new BigDecimal("101.00")));

            UUID left = UUID.randomUUID(), right = UUID.randomUUID();
            economy.setBalance(left, BigDecimal.ZERO, "mysql:left:set:" + left, "test", "setup", Map.of()).join();
            economy.setBalance(right, BigDecimal.ZERO, "mysql:right:set:" + right, "test", "setup", Map.of()).join();
            economy.credit(left, new BigDecimal("100.00"), "mysql:left:credit:" + left, "test", Map.of()).join();
            economy.credit(right, new BigDecimal("100.00"), "mysql:right:credit:" + right, "test", Map.of()).join();
            var crossed = new ArrayList<java.util.concurrent.CompletableFuture<Boolean>>();
            for (int i = 0; i < 20; i++) {
                crossed.add(economy.transfer(left, right, BigDecimal.ONE, BigDecimal.ZERO, "mysql:cross:left:" + i));
                crossed.add(economy.transfer(right, left, BigDecimal.ONE, BigDecimal.ZERO, "mysql:cross:right:" + i));
            }
            java.util.concurrent.CompletableFuture.allOf(crossed.toArray(java.util.concurrent.CompletableFuture[]::new)).join();
            assertTrue(crossed.stream().allMatch(java.util.concurrent.CompletableFuture::join));
            assertEquals(new BigDecimal("100.00"), economy.getBalanceDecimal(left));
            assertEquals(new BigDecimal("100.00"), economy.getBalanceDecimal(right));

            String commerceKey = "mysql:commerce:" + left;
            var commerce = economy.commercialTransfer(left, right, new BigDecimal("12.34"), commerceKey, "chestshop").join();
            assertEquals(CommercialTransferStatus.COMPLETED, commerce.status());
            assertEquals(new BigDecimal("87.66"), economy.getBalanceDecimal(left));
            assertEquals(new BigDecimal("112.34"), economy.getBalanceDecimal(right));
            assertEquals("COMMERCE_TRANSFER", manager.getExecutor().queryOne("mysql.commerce.type",
                    "SELECT operation_type FROM bbe_economy_operations WHERE idempotency_key=?",
                    statement -> statement.setString(1, commerceKey), row -> row.getString(1)).join());
            assertEquals(32L, manager.getExecutor().queryOne("mysql.commerce.type.length",
                    "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bbe_economy_operations' AND COLUMN_NAME='operation_type'",
                    null, row -> row.getLong(1)).join());

            GemConfig gemsConfig = new GemConfig();
            gemsConfig.balances.startingBalance = 0;
            gemsConfig.balances.maxBalance = 1_000_000;
            DatabaseGemsService gems = new DatabaseGemsService(manager, gemsConfig);
            UUID gemPlayer = UUID.randomUUID();
            assertTrue(gems.credit(new GemCreditRequest(gemPlayer, 1_000, "mysql-test", "credit", null, "gems:initial:" + gemPlayer, null, Map.of())).success());
            var debits = java.util.stream.IntStream.range(0, 100)
                    .mapToObj(i -> java.util.concurrent.CompletableFuture.supplyAsync(() -> gems.debit(new GemDebitRequest(gemPlayer, 1, "mysql-test", "debit", null, "gems:debit:" + i, null, Map.of())))).toList();
            java.util.concurrent.CompletableFuture.allOf(debits.toArray(java.util.concurrent.CompletableFuture[]::new)).join();
            assertEquals(100, debits.stream().map(java.util.concurrent.CompletableFuture::join).filter(GemOperationResult::success).count());
            assertEquals(900, gems.getBalance(gemPlayer).totalBalance());

            var reservations = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(i -> java.util.concurrent.CompletableFuture.supplyAsync(() -> gems.reserve(new GemReservationRequest(gemPlayer, 50, "mysql-test", "hold", "gems:reserve:" + i, null, java.time.Duration.ofMinutes(5), Map.of())))).toList();
            java.util.concurrent.CompletableFuture.allOf(reservations.toArray(java.util.concurrent.CompletableFuture[]::new)).join();
            long reservationSuccesses = reservations.stream().map(java.util.concurrent.CompletableFuture::join).filter(GemReservationResult::success).count();
            assertEquals(18, reservationSuccesses);
            assertEquals(0, gems.getBalance(gemPlayer).availableBalance());

            GemReservationResult reserved = gems.reserve(new GemReservationRequest(gemPlayer, 10, "mysql-test", "hold", "gems:single-reserve:" + gemPlayer, null, java.time.Duration.ofMinutes(5), Map.of()));
            assertFalse(reserved.success());
            GemConfig second = new GemConfig(); second.balances.startingBalance = 0; second.balances.maxBalance = 1_000_000;
            UUID replayPlayer = UUID.randomUUID();
            assertTrue(gems.credit(new GemCreditRequest(replayPlayer, 100, "mysql-test", "credit", null, "gems:replay-credit:" + replayPlayer, null, Map.of())).success());
            GemReservationResult created = gems.reserve(new GemReservationRequest(replayPlayer, 10, "mysql-test", "hold", "gems:replay-reserve:" + replayPlayer, null, java.time.Duration.ofMinutes(5), Map.of()));
            GemOperationResult captured = gems.capture(new GemCaptureRequest(created.reservationId(), "mysql-test", "capture", null, "gems:replay-capture:" + replayPlayer, null, Map.of()));
            GemOperationResult capturedAgain = gems.capture(new GemCaptureRequest(created.reservationId(), "mysql-test", "capture", null, "gems:replay-capture:" + replayPlayer, null, Map.of()));
            assertTrue(captured.success());
            assertTrue(capturedAgain.success());
            assertEquals(captured.transactionId(), capturedAgain.transactionId());
            assertEquals(GemReservationStatus.CAPTURED, gems.findReservation(created.reservationId()).orElseThrow().getStatus());

            GemReservationResult expiring = gems.reserve(new GemReservationRequest(replayPlayer, 5, "mysql-test", "expiry", "gems:expiry:" + replayPlayer, null, java.time.Duration.ofSeconds(1), Map.of()));
            assertTrue(expiring.success());
            Thread.sleep(1_100L);
            gems.expireDueReservations();
            assertEquals(GemReservationStatus.EXPIRED, gems.findReservation(expiring.reservationId()).orElseThrow().getStatus());
            assertTrue(gems.getHistory(replayPlayer, 1, 100).stream().anyMatch(t -> t.type() == GemTransactionType.RESERVATION_EXPIRED));

            manager.shutdown();
            manager.initialize();
            DatabaseGemsService restartedGems = new DatabaseGemsService(manager, second);
            assertEquals(90, restartedGems.getBalance(replayPlayer).totalBalance());
            assertEquals(GemReservationStatus.CAPTURED, restartedGems.findReservation(created.reservationId()).orElseThrow().getStatus());

            UUID legacyPlayer = UUID.randomUUID();
            GemsState legacy = new GemsState();
            legacy.balances.put(legacyPlayer.toString(), 42L);
            Path legacyPath = temp.resolve("gems_state.json");
            Files.writeString(legacyPath, new Gson().toJson(legacy));
            GemsJsonMigrationService migration = new GemsJsonMigrationService(manager, legacyPath, second);
            assertEquals(GemsJsonMigrationService.Status.PENDING, migration.dryRun().status());
            assertEquals(GemsJsonMigrationService.Status.COMPLETED, migration.execute().status());
            assertEquals(GemsJsonMigrationService.Status.COMPLETED, migration.execute().status());
            assertEquals(42, restartedGems.getBalance(legacyPlayer).totalBalance());
            assertTrue(Files.list(temp).anyMatch(p -> p.getFileName().toString().startsWith("gems_state.json.backup-")));
        } finally { manager.shutdown(); }
    }
}
