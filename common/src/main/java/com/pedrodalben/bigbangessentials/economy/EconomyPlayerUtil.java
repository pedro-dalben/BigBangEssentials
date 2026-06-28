package com.pedrodalben.bigbangessentials.economy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;

public class EconomyPlayerUtil {
    // Lookup online player by name
    public static Optional<ServerPlayer> getOnlinePlayer(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayers().stream()
            .filter(p -> p.getGameProfile().getName().equalsIgnoreCase(name))
            .findFirst();
    }

    // Lookup UUID by name (online or offline)
    public static Optional<UUID> getUUIDByName(MinecraftServer server, String name) {
        // Try online first
        Optional<ServerPlayer> online = getOnlinePlayer(server, name);
        if (online.isPresent()) return Optional.of(online.get().getUUID());
        // Try offline (GameProfile cache)
        GameProfile profile = server.getProfileCache().get(name).orElse(null);
        if (profile != null) return Optional.of(profile.getId());
        return Optional.empty();
    }

    // Lookup name by UUID (online or offline)
    public static Optional<String> getNameByUUID(MinecraftServer server, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) return Optional.of(player.getGameProfile().getName());
        var profile = server.getProfileCache().get(uuid).orElse(null);
        if (profile != null) return Optional.of(profile.getName());
        return Optional.empty();
    }
}
