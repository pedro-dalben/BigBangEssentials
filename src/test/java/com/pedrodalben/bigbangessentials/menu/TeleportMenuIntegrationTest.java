package com.pedrodalben.bigbangessentials.menu;

import com.pedrodalben.bigbangessentials.menu.integration.teleportation.*;
import com.pedrodalben.bigbangessentials.menu.integration.teleportation.action.*;
import com.pedrodalben.bigbangessentials.menu.integration.teleportation.provider.*;
import com.pedrodalben.bigbangessentials.menu.pagination.MenuDataResult;
import com.pedrodalben.bigbangessentials.menu.pagination.PaginationRequest;
import com.pedrodalben.bigbangessentials.menu.session.MenuContext;
import com.pedrodalben.bigbangessentials.menu.session.MenuSession;
import com.pedrodalben.bigbangessentials.menu.action.ActionContext;
import com.pedrodalben.bigbangessentials.menu.action.ActionExecutionResult;
import com.pedrodalben.bigbangessentials.menu.model.ActionStatus;
import com.pedrodalben.bigbangessentials.teleportation.HomeManager;
import com.pedrodalben.bigbangessentials.teleportation.Warp.WarpManager;
import com.pedrodalben.bigbangessentials.teleportation.TeleportLocation;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.permissions.ExternalPermissionAdapter;
import com.pedrodalben.bigbangessentials.menu.model.MenuClickType;
import com.pedrodalben.bigbangessentials.menu.model.MenuDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuPageDefinition;
import com.pedrodalben.bigbangessentials.menu.model.MenuItemDefinition;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TeleportMenuIntegrationTest {

    private WarpManager mockWarpManager;
    private HomeManager mockHomeManager;

    @BeforeAll
    public static void beforeClass() {
        try {
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    @BeforeEach
    public void setUp() throws Exception {
        mockWarpManager = mock(WarpManager.class);
        mockHomeManager = mock(HomeManager.class);

        // Set static instances
        WarpManager.setInstance(mockWarpManager);
        HomeManager.setInstance(mockHomeManager);

        // Mock permission API
        ExternalPermissionAdapter mockAdapter = mock(ExternalPermissionAdapter.class);
        when(mockAdapter.hasPermission(any(UUID.class), any(String.class))).thenReturn(true);
        PermissionAPI.setExternalAdapter(mockAdapter);
    }

    @AfterEach
    public void tearDown() {
        WarpManager.setInstance(null);
        HomeManager.setInstance(null);
        PermissionAPI.setExternalAdapter(null);
    }

    @Test
    public void testGlobalWarpsDataProvider() {
        when(mockWarpManager.getWarpNames()).thenReturn(List.of("Spawn", "Nether"));
        TeleportLocation spawnLoc = mock(TeleportLocation.class);
        when(spawnLoc.getWorldName()).thenReturn("minecraft:overworld");
        when(spawnLoc.getX()).thenReturn(10.0);
        when(spawnLoc.getY()).thenReturn(64.0);
        when(spawnLoc.getZ()).thenReturn(-20.0);

        TeleportLocation netherLoc = mock(TeleportLocation.class);
        when(netherLoc.getWorldName()).thenReturn("minecraft:the_nether");
        when(netherLoc.getX()).thenReturn(0.0);
        when(netherLoc.getY()).thenReturn(128.0);
        when(netherLoc.getZ()).thenReturn(0.0);

        when(mockWarpManager.getWarp("Spawn")).thenReturn(spawnLoc);
        when(mockWarpManager.getWarp("Nether")).thenReturn(netherLoc);

        GlobalWarpsMenuDataProvider provider = new GlobalWarpsMenuDataProvider();
        ServerPlayer player = mock(ServerPlayer.class);
        MenuContext context = new MenuContext(UUID.randomUUID(), "pt_BR", Collections.emptyMap(), null, "test", "test", UUID.randomUUID());
        PaginationRequest request = new PaginationRequest(1, 10);

        MenuDataResult result = provider.provide(player, context, request).toCompletableFuture().join();
        assertEquals(2, result.totalItems());
        assertEquals(2, result.items().size());

        Map<String, Object> spawnItem = result.items().get(1); // sorted alphabetically, Nether first then Spawn
        assertEquals("Spawn", spawnItem.get("warp_name"));
        assertEquals("minecraft:overworld", spawnItem.get("warp_world"));
        assertEquals("10.0", spawnItem.get("warp_x"));
    }

    @Test
    public void testPlayerHomesDataProvider() {
        ServerPlayer player = mock(ServerPlayer.class);
        TeleportLocation homeLoc = mock(TeleportLocation.class);
        when(homeLoc.getWorldName()).thenReturn("minecraft:overworld");
        when(homeLoc.getX()).thenReturn(5.0);
        when(homeLoc.getY()).thenReturn(70.0);
        when(homeLoc.getZ()).thenReturn(5.0);

        Map<String, TeleportLocation> homes = new HashMap<>();
        homes.put("home1", homeLoc);
        when(mockHomeManager.getPlayerHomes(player)).thenReturn(homes);

        PlayerHomesMenuDataProvider provider = new PlayerHomesMenuDataProvider();
        MenuContext context = new MenuContext(UUID.randomUUID(), "pt_BR", Collections.emptyMap(), null, "test", "test", UUID.randomUUID());
        PaginationRequest request = new PaginationRequest(1, 10);

        MenuDataResult result = provider.provide(player, context, request).toCompletableFuture().join();
        assertEquals(1, result.totalItems());
        assertEquals("home1", result.items().get(0).get("home_name"));
    }

    @Test
    public void testPublicPlayerWarpsDataProvider() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());

        UUID ownerUuid = UUID.randomUUID();
        Map<UUID, Map<String, TeleportLocation>> pwarps = new HashMap<>();
        Map<String, TeleportLocation> userWarps = new HashMap<>();
        TeleportLocation pwarpLoc = mock(TeleportLocation.class);
        when(pwarpLoc.getWorldName()).thenReturn("minecraft:overworld");
        when(pwarpLoc.getX()).thenReturn(1.0);
        when(pwarpLoc.getY()).thenReturn(2.0);
        when(pwarpLoc.getZ()).thenReturn(3.0);
        userWarps.put("warp1", pwarpLoc);
        pwarps.put(ownerUuid, userWarps);

        when(mockWarpManager.getAllPlayerWarps()).thenReturn(pwarps);

        PublicPlayerWarpsMenuDataProvider provider = new PublicPlayerWarpsMenuDataProvider();
        MenuContext context = new MenuContext(UUID.randomUUID(), "pt_BR", Collections.emptyMap(), null, "test", "test", UUID.randomUUID());
        PaginationRequest request = new PaginationRequest(1, 10);

        MenuDataResult result = provider.provide(player, context, request).toCompletableFuture().join();
        assertEquals(1, result.totalItems());
        assertEquals("warp1", result.items().get(0).get("pwarp_name"));
    }

    @Test
    public void testTeleportWarpAction() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(mockWarpManager.hasWarp("spawn")).thenReturn(true);

        TeleportToWarpMenuAction action = new TeleportToWarpMenuAction();
        ActionContext actContext = new ActionContext(
            player, mock(MenuSession.class), mock(MenuDefinition.class), mock(MenuPageDefinition.class), mock(MenuItemDefinition.class),
            MenuClickType.LEFT, null, Map.of("warp-name", "spawn")
        );

        // Production execute() should fail when server is null
        ActionExecutionResult prodResult = action.execute(actContext).toCompletableFuture().join();
        assertEquals(ActionStatus.FAILED, prodResult.status());

        // Test logic using the runner
        ActionExecutionResult result = action.executeWithRunner(actContext, player, Runnable::run).toCompletableFuture().join();
        assertEquals(ActionStatus.SUCCESS, result.status());
        verify(mockWarpManager).teleportToWarp(player, "spawn");
    }

    @Test
    public void testTeleportHomeAction() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());

        Map<String, TeleportLocation> homes = Map.of("my_home", mock(TeleportLocation.class));
        when(mockHomeManager.getPlayerHomes(player)).thenReturn(homes);

        TeleportToHomeMenuAction action = new TeleportToHomeMenuAction();
        ActionContext actContext = new ActionContext(
            player, mock(MenuSession.class), mock(MenuDefinition.class), mock(MenuPageDefinition.class), mock(MenuItemDefinition.class),
            MenuClickType.LEFT, null, Map.of("home-name", "my_home")
        );

        // Production execute() should fail when server is null
        ActionExecutionResult prodResult = action.execute(actContext).toCompletableFuture().join();
        assertEquals(ActionStatus.FAILED, prodResult.status());

        // Test logic using the runner
        ActionExecutionResult result = action.executeWithRunner(actContext, player, Runnable::run).toCompletableFuture().join();
        assertEquals(ActionStatus.SUCCESS, result.status());
        verify(mockHomeManager).teleportToHome(player, "my_home");
    }

    @Test
    public void testPreferenceReset() {
        UUID playerId = UUID.randomUUID();
        TeleportMenuPreferenceService.getInstance().resetPreferences(playerId);
        TeleportMenuPreferenceService.PlayerPreference pref = TeleportMenuPreferenceService.getInstance().getPreferences(playerId);
        assertTrue(pref.teleportMenusEnabled());
        assertEquals(CommandDisplayMode.MENU, pref.warpsDisplayMode());
    }

    @Test
    public void testMenuConfigDisplayMode() {
        assertTrue(TeleportMenuConfig.isEnabled());
        assertEquals(CommandDisplayMode.MENU, TeleportMenuConfig.getWarpsCommandMode());
        assertEquals(CommandDisplayMode.MENU, TeleportMenuConfig.getHomesCommandMode());
    }
}
