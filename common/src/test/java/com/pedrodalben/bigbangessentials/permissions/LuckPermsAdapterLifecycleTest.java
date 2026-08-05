package com.pedrodalben.bigbangessentials.permissions;

import com.pedrodalben.bigbangessentials.BigBangEssentials;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LuckPermsAdapterLifecycleTest {

    private LuckPermsAdapter adapter;
    private LuckPerms api;
    private EventBus bus;
    private EventSubscription<UserDataRecalculateEvent> sub;

    @BeforeEach
    void setUp() {
        adapter = new LuckPermsAdapter(true);
        api = mock(LuckPerms.class);
        bus = mock(EventBus.class);
        sub = mock(EventSubscription.class);
        when(sub.isActive()).thenReturn(true);
        when(api.getEventBus()).thenReturn(bus);
        when(bus.subscribe(eq(UserDataRecalculateEvent.class), any(Consumer.class))).thenReturn(sub);
        adapter.setLuckPermsApiForTest(api);
        BigBangEssentials.setServerStoppingForTest(false);
    }

    @AfterEach
    void tearDown() {
        BigBangEssentials.setServerStoppingForTest(false);
    }

    @Test
    void listenerIsRegistered() {
        adapter.registerEventBusListenerForTest(api);
        verify(bus, times(1)).subscribe(eq(UserDataRecalculateEvent.class), any(Consumer.class));
        assertSame(sub, adapter.getEventSubscriptionForTest());
    }

    @Test
    void shutdownClosesSubscription() {
        adapter.registerEventBusListenerForTest(api);
        adapter.shutdown();
        verify(sub, times(1)).close();
        assertNull(adapter.getEventSubscriptionForTest());
        assertTrue(adapter.isShutdownRequestedForTest());
    }

    @Test
    void shutdownIsIdempotent() {
        adapter.registerEventBusListenerForTest(api);
        adapter.shutdown();
        adapter.shutdown();
        verify(sub, times(1)).close();
    }

    @Test
    void shutdownTwiceDoesNotThrow() {
        adapter.registerEventBusListenerForTest(api);
        assertDoesNotThrow(() -> adapter.shutdown());
        assertDoesNotThrow(() -> adapter.shutdown());
    }

    @Test
    void getApiReturnsNullAfterShutdown() {
        adapter.registerEventBusListenerForTest(api);
        assertEquals(api, adapter.getApi()); // before shutdown
        adapter.shutdown();
        assertNull(adapter.getApi());
    }

    @Test
    void getApiReturnsNullWhenServerStopping() {
        adapter.registerEventBusListenerForTest(api);
        BigBangEssentials.setServerStoppingForTest(true);
        assertNull(adapter.getApi());
    }

    @Test
    void registerListenerDoesNothingAfterShutdown() {
        adapter.shutdown();
        adapter.registerEventBusListenerForTest(api);
        verify(bus, never()).subscribe(eq(UserDataRecalculateEvent.class), any(Consumer.class));
        assertNull(adapter.getEventSubscriptionForTest());
    }

    @Test
    void registerListenerDoesNothingWhenServerStopping() {
        BigBangEssentials.setServerStoppingForTest(true);
        adapter.registerEventBusListenerForTest(api);
        verify(bus, never()).subscribe(eq(UserDataRecalculateEvent.class), any(Consumer.class));
    }

    @Test
    void registerListenerWithNullApiIsNoop() {
        adapter.registerEventBusListenerForTest(null);
        verifyNoInteractions(bus);
        assertNull(adapter.getEventSubscriptionForTest());
    }

    @Test
    void hasPermissionReturnsFalseAfterShutdown() {
        adapter.shutdown();
        assertFalse(adapter.hasPermission(UUID.randomUUID(), "bigbangessentials.teleport.spawn"));
        verifyNoInteractions(bus);
    }

    @Test
    void isAvailableReturnsFalseAfterShutdown() {
        assertTrue(adapter.isAvailable());
        adapter.shutdown();
        assertFalse(adapter.isAvailable());
    }
}