package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.pokemarket.repository.PokeMarketNotificationRepository;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PokeMarketNotificationRepositoryTest {
    @TempDir Path temp;

    @Test
    void retryCreatesOnePersistentNotification() throws Exception {
        Path config = temp.resolve("database.json");
        Files.writeString(config, "{\"enabled\":true,\"required\":true,\"type\":\"SQLITE\",\"sqlite\":{\"file\":\"" + temp.resolve("notifications.db") + "\"}}");
        DatabaseManager manager = DatabaseManager.getInstance();
        manager.shutdown();
        try (MockedStatic<ResourceUtil> ignored = Mockito.mockStatic(ResourceUtil.class, Mockito.CALLS_REAL_METHODS)) {
            ignored.when(() -> ResourceUtil.getConfigFile("database.json")).thenReturn(config.toFile());
            manager.initialize();
            PokeMarketNotificationRepository repository = new PokeMarketNotificationRepository();
            UUID player = UUID.randomUUID();
            assertEquals(1, repository.createOnce(player, "purchase:1", "PURCHASE_COMPLETED", "title", "message", "PURCHASE", "1", null).join());
            assertEquals(1, repository.createOnce(player, "purchase:1", "PURCHASE_COMPLETED", "title", "message", "PURCHASE", "1", null).join());
            assertEquals(1, repository.find(player, 0, 10).join().size());
        } finally {
            manager.shutdown();
        }
    }
}
