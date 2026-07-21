package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.api.economy.*;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PokeMarketConcurrencyTest {
    @TempDir Path temp;
    @Test void twentyConcurrentDebitsLeaveExactlyTenWinnersAndReplayOnce() throws Exception {
        Path config = temp.resolve("database.json"), db = temp.resolve("market.db");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + db + "\"}}");
        DatabaseManager manager = DatabaseManager.getInstance(); manager.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            manager.initialize(); DatabaseEconomyService economy = new DatabaseEconomyService(manager); UUID player = UUID.randomUUID();
            economy.createAccount(player, "test:create:" + player).join(); economy.setBalance(player, new BigDecimal("1000"), "test:set:" + player, "test", "test", Map.of()).join();
            List<CompletableFuture<EconomyOperationReceipt>> debits = java.util.stream.IntStream.range(0, 20).mapToObj(i -> economy.debit(player, new BigDecimal("100"), "market:debit:" + i, "test", Map.of())).toList();
            CompletableFuture.allOf(debits.toArray(CompletableFuture[]::new)).join();
            assertEquals(10, debits.stream().map(CompletableFuture::join).filter(r -> r.status() == EconomyOperationStatus.COMPLETED).count());
            assertEquals(0, economy.getBalanceDecimal(player).compareTo(BigDecimal.ZERO));
            manager.shutdown();
            manager.initialize();
            DatabaseEconomyService restartedEconomy = new DatabaseEconomyService(manager);
            assertEquals(0, restartedEconomy.getBalanceDecimal(player).compareTo(BigDecimal.ZERO));
            List<CompletableFuture<EconomyOperationReceipt>> replay = java.util.stream.IntStream.range(0, 20).mapToObj(i -> restartedEconomy.credit(player, BigDecimal.ONE, "market:replay", "test", Map.of())).toList();
            CompletableFuture.allOf(replay.toArray(CompletableFuture[]::new)).join();
            assertEquals(1, replay.stream().map(f -> f.join().id()).distinct().count());
            assertEquals(1, restartedEconomy.getBalanceDecimal(player).compareTo(BigDecimal.ZERO));
        } finally { manager.shutdown(); }
    }
}
