package com.pedrodalben.bigbangessentials;

import com.pedrodalben.bigbangessentials.api.economy.EconomyService;
import com.pedrodalben.bigbangessentials.api.economy.EconomyServiceImpl;
import com.pedrodalben.bigbangessentials.api.economy.DatabaseEconomyService;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import com.pedrodalben.bigbangessentials.database.repository.JdbcPlayerPreferencesStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigBangEssentialsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BigBangEssentialsManager.class);

    private static class SingletonHolder {
        private static final BigBangEssentialsManager INSTANCE = new BigBangEssentialsManager();
    }

    public static BigBangEssentialsManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private EconomyService economyService;
    private PlayerPreferencesStorage preferencesStorage;

    private static final String PLAYERDATA_DIR = com.pedrodalben.bigbangessentials.util.ResourceUtil.DATA_DIR + "playerdata/";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BigBangEssentialsManager() {
        this.economyService = "DATABASE".equals(com.pedrodalben.bigbangessentials.config.ConfigManager.getEconomyBackend())
                ? new DatabaseEconomyService()
                : new EconomyServiceImpl(com.pedrodalben.bigbangessentials.util.ResourceUtil.getDataPath("balances.json"));
        if (DatabaseManager.getInstance().isReady()) {
            this.preferencesStorage = new JdbcPlayerPreferencesStorage();
        }
    }

    public void setPreferencesStorage(PlayerPreferencesStorage storage) {
        this.preferencesStorage = storage;
    }

    public PlayerPreferencesStorage getPreferencesStorage() {
        if (preferencesStorage == null && DatabaseManager.getInstance().isReady()) {
            preferencesStorage = new JdbcPlayerPreferencesStorage();
        }
        return preferencesStorage;
    }

    @SuppressWarnings("unused")
    public void registerCommand(Object command) {
    }

    public PlayerData getPlayerData(UUID playerId) {
        return playerDataMap.computeIfAbsent(playerId, k -> new PlayerData());
    }

    @SuppressWarnings("unused")
    public void setEconomyService(EconomyService service) {
        this.economyService = service;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    @SuppressWarnings("unused")
    public static class PlayerData {
        public Map<String, Object> homes = new ConcurrentHashMap<>();
        public Map<String, Object> warps = new ConcurrentHashMap<>();
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        public Map<String, Object> mail = new ConcurrentHashMap<>();

        private boolean vanishMode = false;
        private boolean godMode = false;
        private boolean flyMode = false;
        private String lastLocation = null;
        private boolean tpToggle = true;
        private boolean msgToggle = true;
        private final java.util.List<String> ignoreList = new java.util.ArrayList<>();

        public boolean isVanishMode() { return vanishMode; }
        public void setVanishMode(boolean vanish) { this.vanishMode = vanish; }

        public boolean isGodMode() { return godMode; }
        public void setGodMode(boolean god) { this.godMode = god; }

        public boolean isFlyMode() { return flyMode; }
        public void setFlyMode(boolean fly) { this.flyMode = fly; }

        public String getLastLocation() { return lastLocation; }
        public void setLastLocation(String location) { this.lastLocation = location; }

        public boolean isTpToggle() { return tpToggle; }
        public void setTpToggle(boolean enabled) { this.tpToggle = enabled; }

        public boolean isMsgToggle() { return msgToggle; }
        public void setMsgToggle(boolean enabled) { this.msgToggle = enabled; }

        public java.util.List<String> getIgnoreList() { return ignoreList; }
        public void addToIgnoreList(String player) { ignoreList.add(player); }
        public void removeFromIgnoreList(String player) { ignoreList.remove(player); }
    }

    @SuppressWarnings("unused")
    public void savePlayerData(UUID playerId) {
        PlayerData data = playerDataMap.get(playerId);
        if (data == null) return;

        savePlayerDataToJson(playerId, data);
        savePlayerDataToDatabase(playerId, data);
    }

    private void savePlayerDataToJson(UUID playerId, PlayerData data) {
        try {
            Path dir = Paths.get(PLAYERDATA_DIR);
            if (!Files.exists(dir)) Files.createDirectories(dir);
            File file = dir.resolve(playerId + ".json").toFile();
            try (Writer writer = new FileWriter(file)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save player data for {}", playerId, e);
        }
    }

    private void savePlayerDataToDatabase(UUID playerId, PlayerData data) {
        PlayerPreferencesStorage storage = getPreferencesStorage();
        if (storage == null) return;

        storage.loadPreferences(playerId).thenCompose(prefs -> {
            PlayerPreferencesStorage.PlayerPreferences updated = new PlayerPreferencesStorage.PlayerPreferences(
                    data.vanishMode, data.godMode, data.flyMode,
                    data.tpToggle, data.msgToggle, prefs.payToggle(),
                    prefs.socialspy(), prefs.teleportMenusEnabled(),
                    prefs.warpsDisplayMode(), prefs.homesDisplayMode(),
                    prefs.pwarpsDisplayMode(), data.lastLocation
            );
            return storage.savePreferences(playerId, updated);
        }).thenCompose(v -> {
            java.util.List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();
            for (String ignoredName : data.ignoreList) {
                try {
                    UUID ignoredUuid = UUID.fromString(ignoredName);
                    futures.add(storage.addIgnoredPlayer(playerId, ignoredUuid));
                } catch (Exception ignored) {}
            }
            return futures.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        }).exceptionally(err -> {
            LOGGER.warn("Failed to save database player data for {} (JSON fallback already saved): {}",
                    playerId, err.getMessage());
            return null;
        });
    }

    @SuppressWarnings("unused")
    public void loadPlayerData(UUID playerId) {
        loadPlayerDataFromJson(playerId);
        loadPlayerDataFromDatabase(playerId);
    }

    private void loadPlayerDataFromJson(UUID playerId) {
        try {
            Path dir = Paths.get(PLAYERDATA_DIR);
            File file = dir.resolve(playerId + ".json").toFile();
            if (!file.exists()) return;
            try (Reader reader = new FileReader(file)) {
                PlayerData data = GSON.fromJson(reader, PlayerData.class);
                if (data != null) {
                    playerDataMap.put(playerId, data);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load player data for {}", playerId, e);
        }
    }

    private void loadPlayerDataFromDatabase(UUID playerId) {
        PlayerPreferencesStorage storage = getPreferencesStorage();
        if (storage == null) return;

        storage.loadPreferences(playerId).thenAccept(dbPrefs -> {
            PlayerData data = playerDataMap.computeIfAbsent(playerId, k -> new PlayerData());
            data.setVanishMode(dbPrefs.vanishMode());
            data.setGodMode(dbPrefs.godMode());
            data.setFlyMode(dbPrefs.flyMode());
            data.setTpToggle(dbPrefs.tpToggle());
            data.setMsgToggle(dbPrefs.msgToggle());
            if (dbPrefs.lastLocation() != null) {
                data.setLastLocation(dbPrefs.lastLocation());
            }

            storage.loadIgnoreList(playerId).thenAccept(ignoreList -> {
                data.ignoreList.clear();
                for (UUID ignored : ignoreList) {
                    data.ignoreList.add(ignored.toString());
                }
            });
        });
    }

    @SuppressWarnings("unused")
    public void saveAllPlayerData() {
        for (UUID uuid : playerDataMap.keySet()) {
            savePlayerData(uuid);
        }
    }

    @SuppressWarnings("unused")
    public void loadAllPlayerData() {
        try {
            Path dir = Paths.get(PLAYERDATA_DIR);
            if (!Files.exists(dir)) return;
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                    try (Reader reader = new FileReader(p.toFile())) {
                        PlayerData data = GSON.fromJson(reader, PlayerData.class);
                        String fileName = p.getFileName().toString();
                        String uuidStr = fileName.substring(0, fileName.length() - 5);
                        UUID uuid = UUID.fromString(uuidStr);
                        playerDataMap.put(uuid, data);
                    } catch (Exception e) {
                        LOGGER.error("Failed to load individual player data file", e);
                    }
                });
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load all player data", e);
        }
    }
}
