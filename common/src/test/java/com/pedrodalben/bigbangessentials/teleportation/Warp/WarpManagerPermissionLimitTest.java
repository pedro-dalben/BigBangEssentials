package com.pedrodalben.bigbangessentials.teleportation.Warp;

import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WarpManagerPermissionLimitTest {
    private WarpManager warpManager;
    private UUID playerId;
    private int originalMaxPlayerWarps;

    @BeforeEach
    void setUp() throws Exception {
        WarpManager.setInstance(null);
        warpManager = WarpManager.getInstance();
        originalMaxPlayerWarps = getIntField(warpManager, "maxPlayerWarps");
        setIntField(warpManager, "maxPlayerWarps", 3);
        warpManager.clearMaxPlayerWarpsCache();

        playerId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (warpManager != null) {
            setIntField(warpManager, "maxPlayerWarps", originalMaxPlayerWarps);
            warpManager.clearMaxPlayerWarpsCache();
        }
        WarpManager.setInstance(null);
    }

    @Test
    void permissionLimitOverridesHigherConfigLimit() {
        try (MockedStatic<PermissionAPI> permissionApi = Mockito.mockStatic(PermissionAPI.class)) {
            permissionApi.when(() -> PermissionAPI.hasExactPermission(playerId, "bigbangessentials.warp.limit.5"))
                .thenReturn(true);

            assertEquals(5, warpManager.getMaxPlayerWarpsForPlayer(playerId));
        }
    }

    @Test
    void unlimitedPermissionLimitIsReturnedWhenPresent() {
        try (MockedStatic<PermissionAPI> permissionApi = Mockito.mockStatic(PermissionAPI.class)) {
            permissionApi.when(() -> PermissionAPI.hasExactPermission(playerId, "bigbangessentials.warp.limit.unlimited"))
                .thenReturn(true);

            assertEquals(-1, warpManager.getMaxPlayerWarpsForPlayer(playerId));
        }
    }

    @Test
    void fallsBackToConfigWhenNoPermissionLimitExists() {
        try (MockedStatic<PermissionAPI> permissionApi = Mockito.mockStatic(PermissionAPI.class)) {
            assertEquals(3, warpManager.getMaxPlayerWarpsForPlayer(playerId));
        }
    }

    @Test
    void cacheInvalidationUpdatesValue() {
        try (MockedStatic<PermissionAPI> permissionApi = Mockito.mockStatic(PermissionAPI.class)) {
            permissionApi.when(() -> PermissionAPI.hasExactPermission(playerId, "bigbangessentials.warp.limit.10"))
                .thenReturn(true);
            assertEquals(10, warpManager.getMaxPlayerWarpsForPlayer(playerId));
            
            // Assume permission changed to 20
            permissionApi.when(() -> PermissionAPI.hasExactPermission(playerId, "bigbangessentials.warp.limit.10"))
                .thenReturn(false);
            permissionApi.when(() -> PermissionAPI.hasExactPermission(playerId, "bigbangessentials.warp.limit.20"))
                .thenReturn(true);
                
            // Should still return 10 due to cache
            assertEquals(10, warpManager.getMaxPlayerWarpsForPlayer(playerId));
            
            // Clear cache
            warpManager.invalidateMaxPlayerWarpsCache(playerId);
            
            // Should return new value
            assertEquals(20, warpManager.getMaxPlayerWarpsForPlayer(playerId));
        }
    }

    @Test
    void wildcardDoesNotImplicitlyGrantMaxLimit() {
        try (MockedStatic<PermissionAPI> permissionApi = Mockito.mockStatic(PermissionAPI.class)) {
            // Player has wildcard, but exact check returns false for limits
            permissionApi.when(() -> PermissionAPI.hasPermission(playerId, "bigbangessentials.*"))
                .thenReturn(true);
            permissionApi.when(() -> PermissionAPI.hasPermission(playerId, "bigbangessentials.warp.*"))
                .thenReturn(true);
                
            assertEquals(3, warpManager.getMaxPlayerWarpsForPlayer(playerId));
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
