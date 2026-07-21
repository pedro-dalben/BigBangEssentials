package com.pedrodalben.bigbangessentials.database;

import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
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
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile()); manager.initialize(); assertTrue(manager.isReady()); assertEquals(22, manager.getRegisteredMigrations().getLast().version());
            DatabaseEconomyService economy = new DatabaseEconomyService(manager); UUID player = UUID.randomUUID(); economy.createAccount(player, "mysql:create:" + player).join(); assertEquals(EconomyOperationStatus.COMPLETED, economy.credit(player, BigDecimal.TEN, "mysql:credit:" + player, "test", Map.of()).join().status()); assertEquals(0, economy.getBalanceDecimal(player).compareTo(BigDecimal.valueOf(1010, 2)));
        } finally { manager.shutdown(); }
    }
}
