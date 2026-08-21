package com.pedrodalben.bigbangessentials.economy.managers;

import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage.PlayerPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
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
    private volatile PlayerPreferencesStorage storage;

    private PayToggleManager() {
        // Lazy storage resolution happens on first use so the manager works
        // even if it is instantiated before DatabaseManager is ready.
    }

    public boolean getPayToggle(UUID player) {
        PlayerPreferencesStorage storage = resolveStorage();
        if (storage == null) {
            return ConfigManager.getPayToggleDefault();
        }

        return paytoggleCache.computeIfAbsent(player, uuid -> {
            try {
                return storage.loadPreferences(uuid)
                        .thenApply(PlayerPreferences::payToggle)
                        .get();
            } catch (Exception e) {
                LOGGER.debug("Failed to load pay toggle for {}. Falling back to default.", uuid, e);
                return ConfigManager.getPayToggleDefault();
            }
        });
    }

    public void setPayToggle(UUID player, boolean enabled) {
        paytoggleCache.put(player, enabled);
        PlayerPreferencesStorage storage = resolveStorage();
        if (storage == null) {
            return;
        }

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

    @SuppressWarnings("unused")
    public ConcurrentHashMap<UUID, Boolean> getAllToggles() {
        return new ConcurrentHashMap<>(paytoggleCache);
    }

    public void shutdown() {
        LOGGER.info("PayToggleManager shutdown complete.");
    }

    private PlayerPreferencesStorage resolveStorage() {
        PlayerPreferencesStorage current = storage;
        if (current == null) {
            current = BigBangEssentialsManager.getInstance().getPreferencesStorage();
            storage = current;
        }
        return current;
    }
}
