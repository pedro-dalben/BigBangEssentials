package com.pedrodalben.bigbangessentials.database.api;

import com.pedrodalben.bigbangessentials.menu.integration.teleportation.CommandDisplayMode;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerPreferencesStorage {

    CompletableFuture<PlayerPreferences> loadPreferences(UUID playerId);

    CompletableFuture<Void> savePreferences(UUID playerId, PlayerPreferences prefs);

    CompletableFuture<Void> updateToggle(UUID playerId, String toggleKey, boolean value);

    record PlayerPreferences(
            boolean vanishMode,
            boolean godMode,
            boolean flyMode,
            boolean tpToggle,
            boolean msgToggle,
            boolean payToggle,
            boolean socialspy,
            boolean teleportMenusEnabled,
            CommandDisplayMode warpsDisplayMode,
            CommandDisplayMode homesDisplayMode,
            CommandDisplayMode pwarpsDisplayMode,
            String lastLocation
    ) {
        public static PlayerPreferences defaults() {
            return new PlayerPreferences(
                    false, false, false,
                    true, true, true,
                    false, true,
                    CommandDisplayMode.MENU,
                    CommandDisplayMode.MENU,
                    CommandDisplayMode.MENU,
                    null
            );
        }
    }

    // Nicknames

    CompletableFuture<String> loadNickname(UUID playerId);

    CompletableFuture<Void> saveNickname(UUID playerId, String nickname);

    CompletableFuture<Void> deleteNickname(UUID playerId);

    // Tags

    CompletableFuture<String> loadTag(UUID playerId);

    CompletableFuture<Void> saveTag(UUID playerId, String tagName);

    CompletableFuture<Void> deleteTag(UUID playerId);

    // Ignore list

    CompletableFuture<List<UUID>> loadIgnoreList(UUID playerId);

    CompletableFuture<Void> addIgnoredPlayer(UUID playerId, UUID ignoredPlayer);

    CompletableFuture<Void> removeIgnoredPlayer(UUID playerId, UUID ignoredPlayer);

    CompletableFuture<Boolean> isIgnored(UUID playerId, UUID ignoredPlayer);

    // Legacy import

    CompletableFuture<LegacyImportResult> importFromJson(UUID playerId, Map<String, Object> legacyData);

    record LegacyImportResult(int imported, int skipped, String message) {}
}
