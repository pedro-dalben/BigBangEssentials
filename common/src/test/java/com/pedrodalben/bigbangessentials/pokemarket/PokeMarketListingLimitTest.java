package com.pedrodalben.bigbangessentials.pokemarket;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.permissions.PermissionManager;
import com.pedrodalben.bigbangessentials.pokemarket.service.PokeMarketPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PokeMarketListingLimitTest {
    private PermissionManager permManager;

    @BeforeEach
    void setUp() {
        permManager = new PermissionManager();
        PermissionAPI.setManager(permManager);
    }

    @Test
    void defaultsToConfiguredActiveListingsLimit() {
        UUID player = UUID.randomUUID();
        assertEquals(ConfigManager.getPokeMarketMaxActiveListings(), PokeMarketPermissionService.getInstance().getMaxActiveListings(player));
    }

    @Test
    void permissionLimitOverridesDefault() {
        UUID player = UUID.randomUUID();
        permManager.getUser(player).addPermission("bigbangessentials.pokemarket.limit.10");
        assertEquals(10, PokeMarketPermissionService.getInstance().getMaxActiveListings(player));
    }

    @Test
    void legacyShortPermissionLimitOverridesDefault() {
        UUID player = UUID.randomUUID();
        permManager.getUser(player).addPermission("pokemarket.limit.25");
        assertEquals(25, PokeMarketPermissionService.getInstance().getMaxActiveListings(player));
    }

    @Test
    void higherPermissionLimitTakesPrecedence() {
        UUID player = UUID.randomUUID();
        permManager.getUser(player).addPermission("bigbangessentials.pokemarket.limit.5");
        permManager.getUser(player).addPermission("bigbangessentials.pokemarket.limit.20");
        assertEquals(20, PokeMarketPermissionService.getInstance().getMaxActiveListings(player));
    }

    @Test
    void unlimitedPermissionOverridesAll() {
        UUID player = UUID.randomUUID();
        permManager.getUser(player).addPermission("bigbangessentials.pokemarket.limit.10");
        permManager.getUser(player).addPermission("bigbangessentials.pokemarket.limit.unlimited");
        assertEquals(-1, PokeMarketPermissionService.getInstance().getMaxActiveListings(player));
    }
}
