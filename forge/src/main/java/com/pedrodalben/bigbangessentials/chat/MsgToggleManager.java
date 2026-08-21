package com.pedrodalben.bigbangessentials.chat;

import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage.PlayerPreferences;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class MsgToggleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MsgToggleManager.class);
    private static final Set<String> toggledPlayers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> toggledUuids = ConcurrentHashMap.newKeySet();
    private static volatile PlayerPreferencesStorage dbStorage;

    public static void init() {
        resolveStorage();
    }

    public static boolean toggleMsg(ServerPlayer player) {
        String name = player.getName().getString().toLowerCase();
        UUID uuid = player.getUUID();
        boolean nowReceiving;

        if (toggledPlayers.contains(name)) {
            toggledPlayers.remove(name);
            toggledUuids.remove(uuid);
            nowReceiving = true;
        } else {
            toggledPlayers.add(name);
            toggledUuids.add(uuid);
            nowReceiving = false;
        }

        saveToDatabase(uuid, nowReceiving);
        return nowReceiving;
    }

    public static boolean isMsgToggled(ServerPlayer player) {
        return toggledPlayers.contains(player.getName().getString().toLowerCase());
    }

    public static void clearPlayer(ServerPlayer player) {
        String name = player.getName().getString().toLowerCase();
        UUID uuid = player.getUUID();
        toggledPlayers.remove(name);
        toggledUuids.remove(uuid);
    }

    public static CompletableFuture<Void> refreshFromDatabase(ServerPlayer player) {
        PlayerPreferencesStorage storage = resolveStorage();
        if (storage == null) {
            return CompletableFuture.completedFuture(null);
        }

        UUID uuid = player.getUUID();
        String name = player.getName().getString().toLowerCase();
        return storage.loadPreferences(uuid).thenAccept((PlayerPreferences prefs) -> {
            if (prefs.msgToggle()) {
                toggledPlayers.remove(name);
                toggledUuids.remove(uuid);
            } else {
                toggledPlayers.add(name);
                toggledUuids.add(uuid);
            }
        }).exceptionally(err -> {
            LOGGER.debug("Failed to refresh msg toggle for {}", uuid, err);
            return null;
        });
    }

    private static void saveToDatabase(UUID playerId, boolean receiving) {
        PlayerPreferencesStorage storage = resolveStorage();
        if (storage == null) return;
        storage.updateToggle(playerId, "msgToggle", receiving).exceptionally(err -> {
            LOGGER.warn("Failed to save msg toggle for {}", playerId, err);
            return null;
        });
    }

    private static PlayerPreferencesStorage resolveStorage() {
        PlayerPreferencesStorage storage = dbStorage;
        if (storage == null) {
            storage = BigBangEssentialsManager.getInstance().getPreferencesStorage();
            dbStorage = storage;
        }
        return storage;
    }
}
