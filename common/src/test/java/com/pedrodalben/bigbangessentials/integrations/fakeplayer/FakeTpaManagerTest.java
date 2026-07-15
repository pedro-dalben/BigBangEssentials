package com.pedrodalben.bigbangessentials.integrations.fakeplayer;

import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FakeTpaManagerTest {

    private FakeTpaManager manager;
    private ServerPlayer requester;

    @BeforeEach
    void setUp() {
        FakeTpaManager.resetInstance();
        manager = FakeTpaManager.getInstance();
        requester = mock(ServerPlayer.class);
        when(requester.getUUID()).thenReturn(UUID.randomUUID());
        when(requester.getName()).thenReturn(net.minecraft.network.chat.Component.literal("RealPlayer"));
        when(requester.hasDisconnected()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        FakeTpaManager.resetInstance();
    }

    @Test
    void scheduleFakeTpa_returnsConfirmation() {
        manager.scheduleFakeTpa(requester, "Vinzin", 30, 60);
        verify(requester, atLeastOnce()).sendSystemMessage(any());
    }

    @Test
    void scheduleFakeTpa_duplicateBlocked() {
        manager.scheduleFakeTpa(requester, "Vinzin", 30, 60);
        manager.scheduleFakeTpa(requester, "Vinzin", 30, 60);
        verify(requester, times(1)).sendSystemMessage(any());
    }

    @Test
    void cancelAllForPlayer_removesPendingRequests() {
        manager.scheduleFakeTpa(requester, "Vinzin", 30, 60);
        manager.cancelAllForPlayer(requester.getUUID(), "RealPlayer");
        verify(requester, times(1)).sendSystemMessage(any());
    }

    @Test
    void cancelAllForPlayer_otherPlayerNotAffected() {
        ServerPlayer other = mock(ServerPlayer.class);
        when(other.getUUID()).thenReturn(UUID.randomUUID());
        when(other.getName()).thenReturn(net.minecraft.network.chat.Component.literal("OtherPlayer"));

        manager.scheduleFakeTpa(other, "Vinzin", 30, 60);
        manager.cancelAllForPlayer(requester.getUUID(), "RealPlayer");
        verify(other, times(1)).sendSystemMessage(any());
    }

    @Test
    void scheduleFakeTpa_differentTargetsAllowed() {
        manager.scheduleFakeTpa(requester, "Alice", 30, 60);
        manager.scheduleFakeTpa(requester, "Bob", 30, 60);
        verify(requester, times(2)).sendSystemMessage(any());
    }
}
