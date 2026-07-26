package com.pedrodalben.bigbangessentials.economy;

import com.pedrodalben.bigbangessentials.api.economy.CommercialTransferStatus;
import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.Tag;
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

/** Reproducible bounded commerce load; correctness remains asserted separately. */
@Tag("load")
class CommercialTransferLoadTest {
    @TempDir Path temp;

    @Test
    void independentCommerceTransfersReportLatencyAndConserveMoney() throws Exception {
        Path config = temp.resolve("database.json");
        Path db = temp.resolve("commerce-load.db");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + db + "\"}}");
        DatabaseManager manager = DatabaseManager.getInstance();
        manager.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            manager.initialize();
            DatabaseEconomyService economy = new DatabaseEconomyService(manager);
            UUID owner = UUID.randomUUID();
            economy.setBalance(owner, BigDecimal.ZERO, "load:owner", "test", "setup", Map.of()).join();
            var futures = java.util.stream.IntStream.range(0, 40).mapToObj(i -> {
                UUID buyer = UUID.randomUUID();
                economy.setBalance(buyer, BigDecimal.TEN, "load:buyer:" + i, "test", "setup", Map.of()).join();
                long started = System.nanoTime();
                return economy.commercialTransfer(buyer, owner, BigDecimal.ONE, "load:transfer:" + i, "chestshop")
                        .thenApply(result -> new Timed(result.status(), System.nanoTime() - started));
            }).toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            var latency = futures.stream().map(future -> future.join().nanos).sorted().toList();
            assertTrue(futures.stream().allMatch(future -> future.join().status == CommercialTransferStatus.COMPLETED));
            assertEquals(new BigDecimal("40.00"), economy.getBalanceDecimal(owner));
            System.out.printf("commerce-load operations=%d p50_us=%d p95_us=%d p99_us=%d%n", latency.size(),
                    latency.get(percentileIndex(latency.size(), .50)) / 1_000,
                    latency.get(percentileIndex(latency.size(), .95)) / 1_000,
                    latency.get(percentileIndex(latency.size(), .99)) / 1_000);
        } finally {
            manager.shutdown();
        }
    }

    private static int percentileIndex(int size, double percentile) {
        return Math.min(size - 1, (int) Math.ceil(size * percentile) - 1);
    }

    private record Timed(CommercialTransferStatus status, long nanos) {}
}
