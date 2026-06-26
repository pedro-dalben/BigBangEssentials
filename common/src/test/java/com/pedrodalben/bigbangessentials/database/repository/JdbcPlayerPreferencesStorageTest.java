package com.pedrodalben.bigbangessentials.database.repository;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage.PlayerPreferences;
import com.pedrodalben.bigbangessentials.database.config.DatabaseConfigLoader;
import com.pedrodalben.bigbangessentials.menu.integration.teleportation.CommandDisplayMode;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class JdbcPlayerPreferencesStorageTest {

    @TempDir
    Path tempDir;

    private File tempConfigFile;
    private File tempDbFile;
    private PlayerPreferencesStorage storage;
    private MockedStatic<ResourceUtil> mockedResourceUtil;

    @BeforeEach
    public void setUp() throws Exception {
        DatabaseManager.getInstance().shutdown();

        tempConfigFile = tempDir.resolve("database.json").toFile();
        tempDbFile = tempDir.resolve("test.db").toFile();

        mockedResourceUtil = Mockito.mockStatic(ResourceUtil.class);
        mockedResourceUtil.when(() -> ResourceUtil.getConfigFile("database.json"))
                .thenReturn(tempConfigFile);

        String sqliteConfig = "{\n" +
                "  \"enabled\": true,\n" +
                "  \"type\": \"SQLITE\",\n" +
                "  \"sqlite\": {\n" +
                "    \"path\": \"" + tempDbFile.getAbsolutePath().replace("\\", "\\\\") + "\",\n" +
                "    \"journal_mode\": \"WAL\"\n" +
                "  },\n" +
                "  \"pool\": {\n" +
                "    \"name\": \"test-pool\",\n" +
                "    \"max_pool_size\": 5,\n" +
                "    \"min_idle\": 1\n" +
                "  },\n" +
                "  \"migrations\": {\n" +
                "    \"enabled\": true\n" +
                "  }\n" +
                "}";
        try (FileWriter writer = new FileWriter(tempConfigFile)) {
            writer.write(sqliteConfig);
        }

        DatabaseConfigLoader.load();
        DatabaseManager.getInstance().initialize();

        storage = new JdbcPlayerPreferencesStorage();
    }

    @AfterEach
    public void tearDown() {
        if (mockedResourceUtil != null) {
            mockedResourceUtil.close();
        }
        DatabaseManager.getInstance().shutdown();
    }

    @Test
    public void testLoadPreferencesReturnsDefaultsForNewPlayer() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerPreferences prefs = storage.loadPreferences(playerId).get(5, TimeUnit.SECONDS);

        assertNotNull(prefs);
        assertFalse(prefs.vanishMode());
        assertFalse(prefs.godMode());
        assertFalse(prefs.flyMode());
        assertTrue(prefs.tpToggle());
        assertTrue(prefs.msgToggle());
        assertTrue(prefs.payToggle());
        assertFalse(prefs.socialspy());
        assertTrue(prefs.teleportMenusEnabled());
    }

    @Test
    public void testSaveAndLoadPreferences() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerPreferences original = new PlayerPreferences(
                true, true, false,
                false, true, true,
                false, false,
                CommandDisplayMode.CHAT,
                CommandDisplayMode.BOTH,
                CommandDisplayMode.MENU,
                "world:100:64:200"
        );

        storage.savePreferences(playerId, original).get(5, TimeUnit.SECONDS);

        PlayerPreferences loaded = storage.loadPreferences(playerId).get(5, TimeUnit.SECONDS);
        assertEquals(original.vanishMode(), loaded.vanishMode());
        assertEquals(original.godMode(), loaded.godMode());
        assertEquals(original.flyMode(), loaded.flyMode());
        assertEquals(original.tpToggle(), loaded.tpToggle());
        assertEquals(original.msgToggle(), loaded.msgToggle());
        assertEquals(original.payToggle(), loaded.payToggle());
        assertEquals(original.socialspy(), loaded.socialspy());
        assertEquals(original.teleportMenusEnabled(), loaded.teleportMenusEnabled());
        assertEquals(original.warpsDisplayMode(), loaded.warpsDisplayMode());
        assertEquals(original.homesDisplayMode(), loaded.homesDisplayMode());
        assertEquals(original.pwarpsDisplayMode(), loaded.pwarpsDisplayMode());
        assertEquals(original.lastLocation(), loaded.lastLocation());
    }

    @Test
    public void testUpsertUpdatesExistingPreferences() throws Exception {
        UUID playerId = UUID.randomUUID();

        PlayerPreferences first = new PlayerPreferences(
                true, false, false,
                true, true, true,
                false, true,
                CommandDisplayMode.MENU, CommandDisplayMode.MENU,
                CommandDisplayMode.MENU, null
        );
        storage.savePreferences(playerId, first).get(5, TimeUnit.SECONDS);

        PlayerPreferences second = new PlayerPreferences(
                false, true, true,
                false, false, false,
                true, false,
                CommandDisplayMode.CHAT, CommandDisplayMode.CHAT,
                CommandDisplayMode.CHAT, "nether:0:64:0"
        );
        storage.savePreferences(playerId, second).get(5, TimeUnit.SECONDS);

        PlayerPreferences loaded = storage.loadPreferences(playerId).get(5, TimeUnit.SECONDS);
        assertEquals(second.vanishMode(), loaded.vanishMode());
        assertEquals(second.godMode(), loaded.godMode());
        assertEquals(second.flyMode(), loaded.flyMode());
        assertEquals(second.tpToggle(), loaded.tpToggle());
        assertEquals(second.msgToggle(), loaded.msgToggle());
        assertEquals(second.payToggle(), loaded.payToggle());
        assertEquals(second.socialspy(), loaded.socialspy());
        assertEquals(second.teleportMenusEnabled(), loaded.teleportMenusEnabled());
        assertEquals(second.lastLocation(), loaded.lastLocation());
    }

    @Test
    public void testUpdateToggle() throws Exception {
        UUID playerId = UUID.randomUUID();

        storage.updateToggle(playerId, "vanishMode", true).get(5, TimeUnit.SECONDS);
        storage.updateToggle(playerId, "godMode", true).get(5, TimeUnit.SECONDS);
        storage.updateToggle(playerId, "flyMode", true).get(5, TimeUnit.SECONDS);
        storage.updateToggle(playerId, "tpToggle", false).get(5, TimeUnit.SECONDS);
        storage.updateToggle(playerId, "msgToggle", false).get(5, TimeUnit.SECONDS);
        storage.updateToggle(playerId, "payToggle", false).get(5, TimeUnit.SECONDS);
        storage.updateToggle(playerId, "socialspy", true).get(5, TimeUnit.SECONDS);
        storage.updateToggle(playerId, "teleportMenusEnabled", false).get(5, TimeUnit.SECONDS);

        PlayerPreferences prefs = storage.loadPreferences(playerId).get(5, TimeUnit.SECONDS);
        assertTrue(prefs.vanishMode());
        assertTrue(prefs.godMode());
        assertTrue(prefs.flyMode());
        assertFalse(prefs.tpToggle());
        assertFalse(prefs.msgToggle());
        assertFalse(prefs.payToggle());
        assertTrue(prefs.socialspy());
        assertFalse(prefs.teleportMenusEnabled());
    }

    @Test
    public void testNicknameOperations() throws Exception {
        UUID playerId = UUID.randomUUID();

        assertNull(storage.loadNickname(playerId).get(5, TimeUnit.SECONDS));

        storage.saveNickname(playerId, "&aCoolPlayer").get(5, TimeUnit.SECONDS);
        assertEquals("&aCoolPlayer", storage.loadNickname(playerId).get(5, TimeUnit.SECONDS));

        storage.saveNickname(playerId, "&bNewNick").get(5, TimeUnit.SECONDS);
        assertEquals("&bNewNick", storage.loadNickname(playerId).get(5, TimeUnit.SECONDS));

        storage.deleteNickname(playerId).get(5, TimeUnit.SECONDS);
        assertNull(storage.loadNickname(playerId).get(5, TimeUnit.SECONDS));
    }

    @Test
    public void testTagOperations() throws Exception {
        UUID playerId = UUID.randomUUID();

        assertNull(storage.loadTag(playerId).get(5, TimeUnit.SECONDS));

        storage.saveTag(playerId, "vip").get(5, TimeUnit.SECONDS);
        assertEquals("vip", storage.loadTag(playerId).get(5, TimeUnit.SECONDS));

        storage.saveTag(playerId, "admin").get(5, TimeUnit.SECONDS);
        assertEquals("admin", storage.loadTag(playerId).get(5, TimeUnit.SECONDS));

        storage.deleteTag(playerId).get(5, TimeUnit.SECONDS);
        assertNull(storage.loadTag(playerId).get(5, TimeUnit.SECONDS));
    }

    @Test
    public void testIgnoreListOperations() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID ignored1 = UUID.randomUUID();
        UUID ignored2 = UUID.randomUUID();

        List<UUID> ignoreList = storage.loadIgnoreList(playerId).get(5, TimeUnit.SECONDS);
        assertTrue(ignoreList.isEmpty());

        storage.addIgnoredPlayer(playerId, ignored1).get(5, TimeUnit.SECONDS);
        storage.addIgnoredPlayer(playerId, ignored2).get(5, TimeUnit.SECONDS);

        ignoreList = storage.loadIgnoreList(playerId).get(5, TimeUnit.SECONDS);
        assertEquals(2, ignoreList.size());
        assertTrue(ignoreList.contains(ignored1));
        assertTrue(ignoreList.contains(ignored2));

        assertTrue(storage.isIgnored(playerId, ignored1).get(5, TimeUnit.SECONDS));
        assertFalse(storage.isIgnored(playerId, UUID.randomUUID()).get(5, TimeUnit.SECONDS));

        storage.removeIgnoredPlayer(playerId, ignored1).get(5, TimeUnit.SECONDS);
        ignoreList = storage.loadIgnoreList(playerId).get(5, TimeUnit.SECONDS);
        assertEquals(1, ignoreList.size());
        assertFalse(ignoreList.contains(ignored1));
        assertTrue(ignoreList.contains(ignored2));
    }

    @Test
    public void testMultiplePlayersDataIsIsolated() throws Exception {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        storage.savePreferences(player1, new PlayerPreferences(
                true, false, false, true, true, true,
                false, true, CommandDisplayMode.MENU,
                CommandDisplayMode.MENU, CommandDisplayMode.MENU, null
        )).get(5, TimeUnit.SECONDS);

        storage.savePreferences(player2, new PlayerPreferences(
                false, true, true, false, false, false,
                true, false, CommandDisplayMode.CHAT,
                CommandDisplayMode.CHAT, CommandDisplayMode.CHAT, null
        )).get(5, TimeUnit.SECONDS);

        storage.saveNickname(player1, "PlayerOne").get(5, TimeUnit.SECONDS);
        storage.saveNickname(player2, "PlayerTwo").get(5, TimeUnit.SECONDS);

        PlayerPreferences prefs1 = storage.loadPreferences(player1).get(5, TimeUnit.SECONDS);
        PlayerPreferences prefs2 = storage.loadPreferences(player2).get(5, TimeUnit.SECONDS);

        assertTrue(prefs1.vanishMode());
        assertFalse(prefs2.vanishMode());
        assertFalse(prefs1.godMode());
        assertTrue(prefs2.godMode());

        assertEquals("PlayerOne", storage.loadNickname(player1).get(5, TimeUnit.SECONDS));
        assertEquals("PlayerTwo", storage.loadNickname(player2).get(5, TimeUnit.SECONDS));
    }
}
