package com.pedrodalben.bigbangessentials.database.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage.LegacyImportResult;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage.PlayerPreferences;
import com.pedrodalben.bigbangessentials.menu.integration.teleportation.CommandDisplayMode;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class LegacyJsonPlayerPreferencesImporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyJsonPlayerPreferencesImporter.class);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path NICK_DATA_FILE = ResourceUtil.getMigratedDataPath("nickname_data.json");

    private final PlayerPreferencesStorage storage;

    public LegacyJsonPlayerPreferencesImporter(PlayerPreferencesStorage storage) {
        this.storage = storage;
    }

    public CompletableFuture<ImportSummary> importAll() {
        LOGGER.info("Starting legacy JSON import for Phase 1 (player preferences, nicknames, tags)...");

        ImportSummary summary = new ImportSummary();

        return importPlayerPreferences(summary)
                .thenCompose(v -> importPayToggles(summary))
                .thenCompose(v -> importNicknames(summary))
                .thenCompose(v -> importSelectedTags(summary))
                .thenCompose(v -> importMenuPreferences(summary))
                .thenApply(v -> {
                    LOGGER.info("Legacy import completed: {}", summary);
                    return summary;
                });
    }

    private CompletableFuture<Void> importPlayerPreferences(ImportSummary summary) {
        Path playerdataDir = Paths.get(ResourceUtil.DATA_DIR, "playerdata");
        if (!Files.exists(playerdataDir)) {
            return CompletableFuture.completedFuture(null);
        }

        File dir = playerdataDir.toFile();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            String uuidStr = name.substring(0, name.length() - 5);
            try {
                UUID playerId = UUID.fromString(uuidStr);
                futures.add(importPlayerPreferencesFile(playerId, file, summary));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Skipping invalid player data file: {}", name);
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Void> importPlayerPreferencesFile(UUID playerId, File file, ImportSummary summary) {
        if (!file.exists()) {
            return CompletableFuture.completedFuture(null);
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            boolean vanishMode = getBoolean(root, "vanishMode");
            boolean godMode = getBoolean(root, "godMode");
            boolean flyMode = getBoolean(root, "flyMode");
            boolean tpToggle = getBoolean(root, "tpToggle", true);
            boolean msgToggle = getBoolean(root, "msgToggle", true);
            String lastLocation = root.has("lastLocation") && !root.get("lastLocation").isJsonNull()
                    ? root.get("lastLocation").getAsString() : null;

            PlayerPreferences prefs = new PlayerPreferences(
                    vanishMode, godMode, flyMode, tpToggle, msgToggle,
                    true, false, true,
                    CommandDisplayMode.MENU, CommandDisplayMode.MENU,
                    CommandDisplayMode.MENU, lastLocation
            );

            return storage.savePreferences(playerId, prefs).thenAccept(v -> {
                summary.preferencesImported.incrementAndGet();

                if (root.has("ignoreList") && root.get("ignoreList").isJsonArray()) {
                    for (JsonElement elem : root.getAsJsonArray("ignoreList")) {
                        try {
                            UUID ignoredUuid = UUID.fromString(elem.getAsString());
                            storage.addIgnoredPlayer(playerId, ignoredUuid);
                            summary.ignoreEntriesImported.incrementAndGet();
                        } catch (Exception ignored) {}
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.warn("Failed to import player data from {}: {}", file.getName(), e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> importPayToggles(ImportSummary summary) {
        File togglesFile = ResourceUtil.getDataFile("paytoggles.json");
        if (!togglesFile.exists()) {
            return CompletableFuture.completedFuture(null);
        }

        try (FileReader reader = new FileReader(togglesFile)) {
            java.lang.reflect.Type type = new TypeToken<Map<String, Boolean>>() {}.getType();
            Map<String, Boolean> data = GSON.fromJson(reader, type);
            if (data == null || data.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (Map.Entry<String, Boolean> entry : data.entrySet()) {
                try {
                    UUID playerId = UUID.fromString(entry.getKey());
                    boolean enabled = entry.getValue();
                    futures.add(storage.savePreferences(playerId,
                            new PlayerPreferences(false, false, false, true, true,
                                    enabled, false, true,
                                    CommandDisplayMode.MENU, CommandDisplayMode.MENU,
                                    CommandDisplayMode.MENU, null)
                    ).thenAccept(v -> summary.payTogglesImported.incrementAndGet()));
                } catch (Exception ignored) {}
            }

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        } catch (Exception e) {
            LOGGER.warn("Failed to import pay toggles: {}", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> importNicknames(ImportSummary summary) {
        try {
            if (!Files.exists(NICK_DATA_FILE)) {
                return CompletableFuture.completedFuture(null);
            }

            String json = Files.readString(NICK_DATA_FILE);
            JsonObject data = JsonParser.parseString(json).getAsJsonObject();

            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                try {
                    UUID playerId = UUID.fromString(entry.getKey());
                    String nickname = entry.getValue().getAsString();
                    futures.add(storage.saveNickname(playerId, nickname)
                            .thenAccept(v -> summary.nicknamesImported.incrementAndGet()));
                } catch (Exception ignored) {}
            }

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        } catch (Exception e) {
            LOGGER.warn("Failed to import nicknames: {}", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> importSelectedTags(ImportSummary summary) {
        Path tagsDir = Paths.get(ResourceUtil.DATA_DIR, "playerdata", "tags");
        if (!Files.exists(tagsDir)) {
            return CompletableFuture.completedFuture(null);
        }

        File[] files = tagsDir.toFile().listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            String uuidStr = name.substring(0, name.length() - 5);
            try {
                UUID playerId = UUID.fromString(uuidStr);
                try (FileReader reader = new FileReader(file)) {
                    JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
                    if (data.has("selectedTag")) {
                        String tagName = data.get("selectedTag").getAsString();
                        futures.add(storage.saveTag(playerId, tagName)
                                .thenAccept(v -> summary.tagsImported.incrementAndGet()));
                    }
                }
            } catch (Exception ignored) {}
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Void> importMenuPreferences(ImportSummary summary) {
        Path menuDir = Paths.get(ResourceUtil.DATA_DIR, "playerdata", "menupreferences");
        if (!Files.exists(menuDir)) {
            return CompletableFuture.completedFuture(null);
        }

        File[] files = menuDir.toFile().listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            String uuidStr = name.substring(0, name.length() - 5);
            try {
                UUID playerId = UUID.fromString(uuidStr);
                try (FileReader reader = new FileReader(file)) {
                    JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
                    if (data == null || data.entrySet().isEmpty()) continue;

                    boolean enabled = getBoolean(data, "teleport-menus-enabled", true);
                    CommandDisplayMode warpsMode = parseDisplayMode(data, "warps-display-mode", CommandDisplayMode.MENU);
                    CommandDisplayMode homesMode = parseDisplayMode(data, "homes-display-mode", CommandDisplayMode.MENU);
                    CommandDisplayMode pwarpsMode = parseDisplayMode(data, "pwarps-display-mode", CommandDisplayMode.MENU);

                    PlayerPreferences prefs = new PlayerPreferences(
                            false, false, false, true, true, true, false,
                            enabled, warpsMode, homesMode, pwarpsMode, null
                    );

                    futures.add(storage.savePreferences(playerId, prefs)
                            .thenAccept(v -> summary.menuPrefsImported.incrementAndGet()));
                }
            } catch (Exception ignored) {}
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private static boolean getBoolean(JsonObject obj, String key) {
        return getBoolean(obj, key, false);
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : defaultValue;
    }

    private static CommandDisplayMode parseDisplayMode(JsonObject data, String key, CommandDisplayMode defaultValue) {
        if (data.has(key)) {
            try {
                return CommandDisplayMode.valueOf(data.get(key).getAsString().toUpperCase());
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    public static class ImportSummary {
        public final AtomicInteger preferencesImported = new AtomicInteger(0);
        public final AtomicInteger payTogglesImported = new AtomicInteger(0);
        public final AtomicInteger nicknamesImported = new AtomicInteger(0);
        public final AtomicInteger tagsImported = new AtomicInteger(0);
        public final AtomicInteger menuPrefsImported = new AtomicInteger(0);
        public final AtomicInteger ignoreEntriesImported = new AtomicInteger(0);

        @Override
        public String toString() {
            return String.format(
                    "Preferences: %d, Pay toggles: %d, Nicknames: %d, Tags: %d, Menu prefs: %d, Ignore entries: %d",
                    preferencesImported.get(), payTogglesImported.get(), nicknamesImported.get(),
                    tagsImported.get(), menuPrefsImported.get(), ignoreEntriesImported.get()
            );
        }

        public int total() {
            return preferencesImported.get() + payTogglesImported.get() + nicknamesImported.get()
                    + tagsImported.get() + menuPrefsImported.get() + ignoreEntriesImported.get();
        }
    }
}
