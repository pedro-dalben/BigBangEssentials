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

/**
 * Fabric-module wiring smoke test: ensures the shutdown fix in the common module is reachable
 * from the fabric runtime classpath (that's the actual deployed mod in 1.21.1 / Loader 0.19.3 /
 * Java 21). The full behaviour contract lives in {@code common: LuckPermsAdapterLifecycleTest};
 * this parity test guards against fabric-loom remap or classpath regressions hiding the fix.
 */
class LuckPermsAdapterFabricWiringTest {

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
        try { adapter.shutdown(); } catch (Exception ignored) {}
    }

    @Test
    void listenerRegisteredAndClosedExactlyOnceOnShutdown() {
        adapter.registerEventBusListenerForTest(api);
        verify(bus, times(1)).subscribe(eq(UserDataRecalculateEvent.class), any(Consumer.class));
        assertSame(sub, adapter.getEventSubscriptionForTest());

        adapter.shutdown();
        verify(sub, times(1)).close();
        assertNull(adapter.getEventSubscriptionForTest());

        adapter.shutdown();
        verify(sub, times(1)).close();
    }

    @Test
    void publicSurfaceShortCircuitsWhenServerStopping() {
        BigBangEssentials.setServerStoppingForTest(true);
        UUID id = UUID.randomUUID();
        assertFalse(adapter.hasPermission(id, "bigbangessentials.teleport.spawn"));
        assertNull(adapter.getPrefix(id));
        assertNull(adapter.getSuffix(id));
        assertNull(adapter.getPrimaryGroup(id));
        assertTrue(adapter.getInheritedGroups(id).isEmpty());
        assertFalse(adapter.hasExactPermission(id, "bigbangessentials.foo"));
        assertFalse(adapter.setupPlayerAsVip(id, "Player"));
        assertNull(adapter.getApi());
        assertFalse(adapter.isAvailable());
        verifyNoInteractions(bus);
    }

    @Test
    void publicSurfaceShortCircuitsAfterAdapterShutdown() {
        adapter.registerEventBusListenerForTest(api);
        adapter.shutdown();
        UUID id = UUID.randomUUID();
        assertFalse(adapter.hasPermission(id, "bigbangessentials.teleport.spawn"));
        assertNull(adapter.getApi());
        assertFalse(adapter.isAvailable());
    }
}