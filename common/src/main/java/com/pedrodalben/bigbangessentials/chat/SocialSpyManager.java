package com.pedrodalben.bigbangessentials.chat;

import net.minecraft.server.level.ServerPlayer;
import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SocialSpyManager {
    private static final Set<String> socialSpyPlayers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> socialSpyUuids = ConcurrentHashMap.newKeySet();
    private static PlayerPreferencesStorage dbStorage;

    public static void init() {
        dbStorage = BigBangEssentialsManager.getInstance().getPreferencesStorage();
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

    private static void saveToDatabase(UUID playerId, boolean enabled) {
        if (dbStorage == null) return;
        dbStorage.updateToggle(playerId, "socialspy", enabled);
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
