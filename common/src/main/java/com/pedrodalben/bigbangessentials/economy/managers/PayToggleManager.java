package com.pedrodalben.bigbangessentials.economy.managers;

import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage.PlayerPreferences;
import com.pedrodalben.bigbangessentials.database.repository.JdbcPlayerPreferencesStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PayToggleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PayToggleManager.class);

    private static class SingletonHolder {
        private static final PayToggleManager INSTANCE = new PayToggleManager();
    }

    public static PayToggleManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private final ConcurrentHashMap<UUID, Boolean> paytoggleCache = new ConcurrentHashMap<>();
    private final PlayerPreferencesStorage storage;

    private PayToggleManager() {
        this.storage = BigBangEssentialsManager.getInstance().getPreferencesStorage();
        if (storage != null) {
            loadTogglesFromDatabase();
        }
    }

    private void loadTogglesFromDatabase() {
        storage.loadPreferences(UUID.randomUUID()).whenComplete((prefs, err) -> {
            if (err != null) {
                LOGGER.warn("Pay toggle DB not available yet, using defaults: {}", err.getMessage());
            }
        });
    }

    public boolean getPayToggle(UUID player) {
        return paytoggleCache.computeIfAbsent(player,
            uuid -> {
                if (storage == null) {
                    return ConfigManager.getPayToggleDefault();
                }
                try {
                    return storage.loadPreferences(uuid)
                            .thenApply(PlayerPreferences::payToggle)
                            .get();
                } catch (Exception e) {
                    return ConfigManager.getPayToggleDefault();
                }
            });
    }

    public void setPayToggle(UUID player, boolean enabled) {
        paytoggleCache.put(player, enabled);
        if (storage != null) {
            storage.loadPreferences(player).thenCompose(prefs ->
                    storage.savePreferences(player, new PlayerPreferences(
                            prefs.vanishMode(), prefs.godMode(), prefs.flyMode(),
                            prefs.tpToggle(), prefs.msgToggle(), enabled,
                            prefs.socialspy(), prefs.teleportMenusEnabled(),
                            prefs.warpsDisplayMode(), prefs.homesDisplayMode(),
                            prefs.pwarpsDisplayMode(), prefs.lastLocation()
                    ))
            ).exceptionally(err -> {
                LOGGER.error("Failed to save pay toggle for {}: {}", player, err.getMessage());
                return null;
            });
        }
    }

    @SuppressWarnings("unused")
    public ConcurrentHashMap<UUID, Boolean> getAllToggles() {
        return new ConcurrentHashMap<>(paytoggleCache);
    }

    public void shutdown() {
        LOGGER.info("PayToggleManager shutdown complete.");
    }
}
