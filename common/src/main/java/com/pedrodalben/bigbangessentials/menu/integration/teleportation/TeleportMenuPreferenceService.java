package com.pedrodalben.bigbangessentials.menu.integration.teleportation;

import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.util.PlayerDataStore;
import java.util.UUID;

public class TeleportMenuPreferenceService {
    private static TeleportMenuPreferenceService instance;
    private final PlayerDataStore dataStore;

    private TeleportMenuPreferenceService() {
        this.dataStore = new PlayerDataStore("menupreferences");
    }

    public static synchronized TeleportMenuPreferenceService getInstance() {
        if (instance == null) {
            instance = new TeleportMenuPreferenceService();
        }
        return instance;
    }

    public PlayerPreference getPreferences(UUID playerId) {
        JsonObject data = dataStore.load(playerId);
        if (data == null || data.entrySet().isEmpty()) {
            return new PlayerPreference(
                true,
                TeleportMenuConfig.getWarpsCommandMode(),
                TeleportMenuConfig.getHomesCommandMode(),
                TeleportMenuConfig.getPwarpsCommandMode()
            );
        }

        boolean enabled = data.has("teleport-menus-enabled") ? data.get("teleport-menus-enabled").getAsBoolean() : true;
        
        CommandDisplayMode warpsMode = TeleportMenuConfig.getWarpsCommandMode();
        if (data.has("warps-display-mode")) {
            try {
                warpsMode = CommandDisplayMode.valueOf(data.get("warps-display-mode").getAsString().toUpperCase());
            } catch (Exception ignored) {}
        }
        
        CommandDisplayMode homesMode = TeleportMenuConfig.getHomesCommandMode();
        if (data.has("homes-display-mode")) {
            try {
                homesMode = CommandDisplayMode.valueOf(data.get("homes-display-mode").getAsString().toUpperCase());
            } catch (Exception ignored) {}
        }
        
        CommandDisplayMode pwarpsMode = TeleportMenuConfig.getPwarpsCommandMode();
        if (data.has("pwarps-display-mode")) {
            try {
                pwarpsMode = CommandDisplayMode.valueOf(data.get("pwarps-display-mode").getAsString().toUpperCase());
            } catch (Exception ignored) {}
        }

        return new PlayerPreference(enabled, warpsMode, homesMode, pwarpsMode);
    }

    public void setPreferences(UUID playerId, PlayerPreference pref) {
        JsonObject data = new JsonObject();
        data.addProperty("teleport-menus-enabled", pref.teleportMenusEnabled());
        data.addProperty("warps-display-mode", pref.warpsDisplayMode().name());
        data.addProperty("homes-display-mode", pref.homesDisplayMode().name());
        data.addProperty("pwarps-display-mode", pref.pwarpsDisplayMode().name());
        dataStore.save(playerId, data);
        dataStore.flush(playerId);
    }

    public void resetPreferences(UUID playerId) {
        dataStore.save(playerId, new JsonObject());
        dataStore.flush(playerId);
    }

    public record PlayerPreference(
        boolean teleportMenusEnabled,
        CommandDisplayMode warpsDisplayMode,
        CommandDisplayMode homesDisplayMode,
        CommandDisplayMode pwarpsDisplayMode
    ) {}
}
