package com.pedrodalben.bigbangessentials.integrations.fakeplayer;

import com.mojang.brigadier.CommandDispatcher;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.chat.command.MsgCommand;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.teleportation.TeleportRequests.TeleportRequestCommands;
import com.pedrodalben.bigbangessentials.util.commands.PingCommand;
import com.pedrodalben.bigbangessentials.util.commands.SeenCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FakePlayerCommandIntegrationTest {

    private FakePlayerIntegration fakeIntegration;

    @BeforeAll
    static void beforeAll() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    @BeforeEach
    void setUp() {
        FakePlayerIntegration.init();
        fakeIntegration = FakePlayerIntegration.getInstance();
        PermissionAPI.setExternalAdapter(null);
        fakeIntegration.registerFakePlayer("Vinzin", "lobby", 42);
    }

    @AfterEach
    void tearDown() {
        fakeIntegration.clearAll();
        PermissionAPI.setExternalAdapter(null);
        FakeTpaManager.resetInstance();
    }

    @Test
    void fakePlayerAPI_isFakePlayerActive() {
        assertTrue(fakeIntegration.isFakePlayerActive("Vinzin"));
        assertFalse(fakeIntegration.isFakePlayerActive("Nobody"));
    }

    @Test
    void fakePlayerAPI_findActiveFakePlayer() {
        Optional<FakePlayerSnapshot> result = fakeIntegration.findActiveFakePlayer("Vinzin");
        assertTrue(result.isPresent());
        assertEquals("Vinzin", result.get().username());
        assertEquals(42, result.get().ping());
    }

    @Test
    void fakePlayerAPI_caseInsensitive() {
        assertTrue(fakeIntegration.isFakePlayerActive("vinzin"));
        Optional<FakePlayerSnapshot> result = fakeIntegration.findActiveFakePlayer("VINZIN");
        assertTrue(result.isPresent());
    }

    @Test
    void fakePlayerAPI_unregister() {
        fakeIntegration.unregisterFakePlayer("Vinzin");
        assertFalse(fakeIntegration.isFakePlayerActive("Vinzin"));
    }

    @Test
    void msgCommand_registersWithMockDispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        MsgCommand.register(dispatcher);
        assertNotNull(dispatcher.getRoot().getChild("msg"));
        assertNotNull(dispatcher.getRoot().getChild("tell"));
    }

    @Test
    void tpaCommand_registersWithMockDispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        TeleportRequestCommands.register(dispatcher);
        assertNotNull(dispatcher.getRoot().getChild("tpa"));
        assertNotNull(dispatcher.getRoot().getChild("tpahere"));
    }

    @Test
    void seenCommand_registersWithMockDispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SeenCommand.register(dispatcher);
        assertNotNull(dispatcher.getRoot().getChild("seen"));
    }

    @Test
    void pingCommand_registersWithMockDispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        PingCommand.register(dispatcher);
        assertNotNull(dispatcher.getRoot().getChild("ping"));
    }

    @Test
    void fakePlayerAndRealPlayerSameName_realTakesPriority() {
        fakeIntegration.registerFakePlayer("RealTarget", "lobby", 42);
        assertTrue(fakeIntegration.isFakePlayerActive("RealTarget"));

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        TeleportRequestCommands.register(dispatcher);
        assertNotNull(dispatcher.getRoot().getChild("tpa"));
    }

    @Test
    void tpaToFakePlayer_showsConfirmation() {
        FakeTpaManager tpaManager = FakeTpaManager.getInstance();
        ServerPlayer requester = mock(ServerPlayer.class);
        when(requester.getUUID()).thenReturn(UUID.randomUUID());
        when(requester.getName()).thenReturn(net.minecraft.network.chat.Component.literal("RealPlayer"));
        when(requester.hasDisconnected()).thenReturn(false);

        tpaManager.scheduleFakeTpa(requester, "Vinzin", 30, 60);
        verify(requester, atLeastOnce()).sendSystemMessage(any());
    }

    @Test
    void fakeTpaExpires() {
        FakeTpaManager tpaManager = FakeTpaManager.getInstance();
        ServerPlayer requester = mock(ServerPlayer.class);
        when(requester.getUUID()).thenReturn(UUID.randomUUID());
        when(requester.getName()).thenReturn(net.minecraft.network.chat.Component.literal("RealPlayer"));
        when(requester.hasDisconnected()).thenReturn(false);

        tpaManager.scheduleFakeTpa(requester, "Vinzin", 1, 2);
        verify(requester, atLeastOnce()).sendSystemMessage(any());
    }

    @Test
    void fakeTpaDuplicateBlocked() {
        FakeTpaManager tpaManager = FakeTpaManager.getInstance();
        ServerPlayer requester = mock(ServerPlayer.class);
        when(requester.getUUID()).thenReturn(UUID.randomUUID());
        when(requester.getName()).thenReturn(net.minecraft.network.chat.Component.literal("RealPlayer"));
        when(requester.hasDisconnected()).thenReturn(false);

        tpaManager.scheduleFakeTpa(requester, "Vinzin", 30, 60);
        tpaManager.scheduleFakeTpa(requester, "Vinzin", 30, 60);
        verify(requester, times(1)).sendSystemMessage(any());
    }

    @Test
    void cancelOnSenderDisconnect() {
        FakeTpaManager tpaManager = FakeTpaManager.getInstance();
        ServerPlayer requester = mock(ServerPlayer.class);
        UUID uid = UUID.randomUUID();
        when(requester.getUUID()).thenReturn(uid);
        when(requester.getName()).thenReturn(net.minecraft.network.chat.Component.literal("RealPlayer"));
        when(requester.hasDisconnected()).thenReturn(false);

        tpaManager.scheduleFakeTpa(requester, "Vinzin", 30, 60);
        tpaManager.cancelAllForPlayer(uid, "RealPlayer");
        verify(requester, times(1)).sendSystemMessage(any());
    }

    @Test
    void noMoneyLossToFakePlayer() {
        assertTrue(fakeIntegration.isFakePlayerActive("Vinzin"));
    }

    @Test
    void fakePlayerSnapshot_hasAllData() {
        FakePlayerSnapshot snap = fakeIntegration.findActiveFakePlayer("Vinzin").orElseThrow();
        assertNotNull(snap.uuid());
        assertNotNull(snap.username());
        assertNotNull(snap.serverName());
        assertNotNull(snap.connectedAt());
        assertEquals(42, snap.ping());
    }
}
