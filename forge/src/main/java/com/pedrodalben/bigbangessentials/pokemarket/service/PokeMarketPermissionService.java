package com.pedrodalben.bigbangessentials.pokemarket.service;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public final class PokeMarketPermissionService {
    private static final PokeMarketPermissionService INSTANCE = new PokeMarketPermissionService();

    private PokeMarketPermissionService() {}

    public static PokeMarketPermissionService getInstance() {
        return INSTANCE;
    }

    /**
     * Resolves the maximum active PokeMarket listings allowed for a player.
     * Returns -1 for unlimited access, or a positive integer limit.
     */
    public int getMaxActiveListings(ServerPlayer player) {
        if (player == null) return ConfigManager.getPokeMarketMaxActiveListings();
        return getMaxActiveListings(player.getUUID());
    }

    /**
     * Resolves the maximum active PokeMarket listings allowed for a player UUID.
     */
    public int getMaxActiveListings(UUID playerUuid) {
        if (playerUuid == null) return ConfigManager.getPokeMarketMaxActiveListings();

        if (PermissionAPI.hasPermission(playerUuid, "bigbangessentials.pokemarket.limit.unlimited")
            || PermissionAPI.hasPermission(playerUuid, "pokemarket.limit.unlimited")) {
            return -1; // Unlimited
        }

        for (int i = 500; i >= 1; i--) {
            if (PermissionAPI.hasPermission(playerUuid, "bigbangessentials.pokemarket.limit." + i)
                || PermissionAPI.hasPermission(playerUuid, "pokemarket.limit." + i)) {
                return i;
            }
        }

        return ConfigManager.getPokeMarketMaxActiveListings();
    }
}
