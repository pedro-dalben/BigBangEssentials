package com.pedrodalben.bigbangessentials.chat;

import com.pedrodalben.bigbangessentials.BigBangEssentialsManager;
import com.pedrodalben.bigbangessentials.database.api.PlayerPreferencesStorage;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MsgToggleManager {
    private static final Set<String> toggledPlayers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> toggledUuids = ConcurrentHashMap.newKeySet();
    private static PlayerPreferencesStorage dbStorage;

    public static void init() {
        dbStorage = BigBangEssentialsManager.getInstance().getPreferencesStorage();
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

    private static void saveToDatabase(UUID playerId, boolean receiving) {
        if (dbStorage == null) return;
        dbStorage.updateToggle(playerId, "msgToggle", receiving);
    }
}
