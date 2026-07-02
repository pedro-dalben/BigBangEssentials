package com.pedrodalben.bigbangessentials.teleportation;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeManagerPermissionLimitTest {
    private HomeManager homeManager;
    private ServerPlayer player;
    private UUID playerId;
    private int originalMaxHomesPerPlayer;

    @BeforeEach
    void setUp() throws Exception {
        HomeManager.setInstance(null);
        homeManager = HomeManager.getInstance();
        originalMaxHomesPerPlayer = getIntField(homeManager, "maxHomesPerPlayer");
        setIntField(homeManager, "maxHomesPerPlayer", 5);
        homeManager.clearMaxHomesCache();

        playerId = UUID.randomUUID();
        player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(playerId);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (homeManager != null) {
            setIntField(homeManager, "maxHomesPerPlayer", originalMaxHomesPerPlayer);
            homeManager.clearMaxHomesCache();
        }
        HomeManager.setInstance(null);
    }

    @Test
    void permissionLimitOverridesHigherConfigLimit() {
        try (MockedStatic<PermissionAPI> permissionApi = Mockito.mockStatic(PermissionAPI.class)) {
            permissionApi.when(() -> PermissionAPI.hasPermission(playerId, "bigbangessentials.home.1"))
                .thenReturn(true);

            assertEquals(1, homeManager.getMaxHomesForPlayer(player));
        }
    }

    @Test
    void higherPermissionLimitIsReturnedWhenPresent() {
        try (MockedStatic<PermissionAPI> permissionApi = Mockito.mockStatic(PermissionAPI.class)) {
            permissionApi.when(() -> PermissionAPI.hasPermission(playerId, "bigbangessentials.home.10"))
                .thenReturn(true);

            assertEquals(10, homeManager.getMaxHomesForPlayer(player));
        }
    }

    @Test
    void fallsBackToConfigWhenNoPermissionLimitExists() {
        try (MockedStatic<PermissionAPI> permissionApi = Mockito.mockStatic(PermissionAPI.class)) {
            assertEquals(5, homeManager.getMaxHomesForPlayer(player));
        }
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static int getIntField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
