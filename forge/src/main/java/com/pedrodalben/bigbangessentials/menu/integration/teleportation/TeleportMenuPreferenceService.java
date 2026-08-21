package com.pedrodalben.bigbangessentials.menu.integration.teleportation;

import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage.PlayerPreferences;
import java.util.UUID;

public class TeleportMenuPreferenceService {
    private static TeleportMenuPreferenceService instance;
    private PlayerPreferencesStorage dbStorage;

    private TeleportMenuPreferenceService() {
        this.dbStorage = BigBangEssentialsManager.getInstance().getPreferencesStorage();
    }

    public static synchronized TeleportMenuPreferenceService getInstance() {
        if (instance == null) {
            instance = new TeleportMenuPreferenceService();
        }
        return instance;
    }

    public PlayerPreference getPreferences(UUID playerId) {
        PlayerPreferencesStorage storage = resolveDbStorage();
        if (storage != null) {
            try {
                PlayerPreferences prefs = storage.loadPreferences(playerId).get();
                if (prefs != null && !isDefaultPreferences(prefs)) {
                    return new PlayerPreference(
                        prefs.teleportMenusEnabled(),
                        prefs.warpsDisplayMode() != null ? prefs.warpsDisplayMode() : TeleportMenuConfig.getWarpsCommandMode(),
                        prefs.homesDisplayMode() != null ? prefs.homesDisplayMode() : TeleportMenuConfig.getHomesCommandMode(),
                        prefs.pwarpsDisplayMode() != null ? prefs.pwarpsDisplayMode() : TeleportMenuConfig.getPwarpsCommandMode()
                    );
                }
            } catch (Exception ignored) {}
        }

        return new PlayerPreference(
            true,
            TeleportMenuConfig.getWarpsCommandMode(),
            TeleportMenuConfig.getHomesCommandMode(),
            TeleportMenuConfig.getPwarpsCommandMode()
        );
    }

    private boolean isDefaultPreferences(PlayerPreferences prefs) {
        return prefs.teleportMenusEnabled() && prefs.warpsDisplayMode() == null
                && prefs.homesDisplayMode() == null && prefs.pwarpsDisplayMode() == null;
    }

    public void setPreferences(UUID playerId, PlayerPreference pref) {
        PlayerPreferencesStorage storage = resolveDbStorage();
        if (storage == null) return;

        storage.loadPreferences(playerId).thenCompose(current ->
            storage.savePreferences(playerId, new PlayerPreferences(
                current.vanishMode(), current.godMode(), current.flyMode(),
                current.tpToggle(), current.msgToggle(), current.payToggle(),
                current.socialspy(), pref.teleportMenusEnabled(),
                pref.warpsDisplayMode(), pref.homesDisplayMode(),
                pref.pwarpsDisplayMode(), current.lastLocation()
            ))
        ).exceptionally(err -> {
            System.err.println("Failed to save teleport menu preferences for " + playerId + ": " + err.getMessage());
            return null;
        });
    }

    public void resetPreferences(UUID playerId) {
        PlayerPreferencesStorage storage = resolveDbStorage();
        if (storage == null) return;

        storage.loadPreferences(playerId).thenCompose(current ->
            storage.savePreferences(playerId, new PlayerPreferences(
                current.vanishMode(), current.godMode(), current.flyMode(),
                current.tpToggle(), current.msgToggle(), current.payToggle(),
                current.socialspy(), true,
                TeleportMenuConfig.getWarpsCommandMode(),
                TeleportMenuConfig.getHomesCommandMode(),
                TeleportMenuConfig.getPwarpsCommandMode(),
                current.lastLocation()
            ))
        ).exceptionally(err -> {
            System.err.println("Failed to reset teleport menu preferences for " + playerId + ": " + err.getMessage());
            return null;
        });
    }

    private PlayerPreferencesStorage resolveDbStorage() {
        if (dbStorage == null) {
            dbStorage = BigBangEssentialsManager.getInstance().getPreferencesStorage();
        }
        return dbStorage;
    }

    public record PlayerPreference(
        boolean teleportMenusEnabled,
        CommandDisplayMode warpsDisplayMode,
        CommandDisplayMode homesDisplayMode,
        CommandDisplayMode pwarpsDisplayMode
    ) {}
}
