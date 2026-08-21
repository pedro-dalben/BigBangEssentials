package com.pedrodalben.bigbangessentials.chat;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage.PlayerPreferences;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SocialSpyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocialSpyManager.class);
    private static final Set<String> socialSpyPlayers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> socialSpyUuids = ConcurrentHashMap.newKeySet();
    private static volatile PlayerPreferencesStorage dbStorage;

    public static void init() {
        resolveStorage();
    }

    public static void toggleSocialSpy(ServerPlayer player) {
        String name = player.getName().getString().toLowerCase();
        UUID uuid = player.getUUID();
        if (socialSpyPlayers.contains(name)) {
            socialSpyPlayers.remove(name);
            socialSpyUuids.remove(uuid);
        } else {
            socialSpyPlayers.add(name);
            socialSpyUuids.add(uuid);
        }
        saveToDatabase(uuid, socialSpyPlayers.contains(name));
    }

    public static boolean hasSocialSpy(ServerPlayer player) {
        return socialSpyPlayers.contains(player.getName().getString().toLowerCase());
    }

    public static void clearPlayer(ServerPlayer player) {
        String name = player.getName().getString().toLowerCase();
        UUID uuid = player.getUUID();
        socialSpyPlayers.remove(name);
        socialSpyUuids.remove(uuid);
    }

    public static CompletableFuture<Void> refreshFromDatabase(ServerPlayer player) {
        PlayerPreferencesStorage storage = resolveStorage();
        if (storage == null) {
            return CompletableFuture.completedFuture(null);
        }

        UUID uuid = player.getUUID();
        String name = player.getName().getString().toLowerCase();
        return storage.loadPreferences(uuid).thenAccept((PlayerPreferences prefs) -> {
            if (prefs.socialspy()) {
                socialSpyPlayers.add(name);
                socialSpyUuids.add(uuid);
            } else {
                socialSpyPlayers.remove(name);
                socialSpyUuids.remove(uuid);
            }
        }).exceptionally(err -> {
            LOGGER.debug("Failed to refresh social spy for {}", uuid, err);
            return null;
        });
    }

    private static void saveToDatabase(UUID playerId, boolean enabled) {
        PlayerPreferencesStorage storage = resolveStorage();
        if (storage == null) return;
        storage.updateToggle(playerId, "socialspy", enabled).exceptionally(err -> {
            LOGGER.warn("Failed to save social spy toggle for {}", playerId, err);
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

    public static void broadcast(ServerPlayer sender, ServerPlayer target, String message) {
        if (com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(sender.getUUID(), "bigbangessentials.chat.socialspy.exempt")) {
            return;
        }

        for (ServerPlayer player : sender.getServer().getPlayerList().getPlayers()) {
            if (hasSocialSpy(player) && !player.equals(sender) && !player.equals(target)) {
                if (com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI.hasPermission(player.getUUID(), "bigbangessentials.chat.socialspy")) {
                    player.sendSystemMessage(MessageUtil.component(
                        "bigbangessentials.socialspy.format", sender.getName().getString(), target.getName().getString(), message));
                } else {
                    socialSpyPlayers.remove(player.getName().getString().toLowerCase());
                }
            }
        }
    }
}
