package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.service.PokeMarketHealthService;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PokeMarketHealthServiceTest {
    @TempDir Path temp;

    @Test
    void fullScanRunsAllChecksAgainstFreshSqliteSchema() throws Exception {
        Path config = temp.resolve("database.json");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + temp.resolve("health.db") + "\"}}");
        DatabaseManager database = DatabaseManager.getInstance();
        database.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            database.initialize();
            PokeMarketHealthService.FullReport report = new PokeMarketHealthService().fullScan().join();
            assertEquals(0, report.tradeRowsScanned());
            assertEquals(0, report.findingCount());
        } finally { database.shutdown(); }
    }
}
