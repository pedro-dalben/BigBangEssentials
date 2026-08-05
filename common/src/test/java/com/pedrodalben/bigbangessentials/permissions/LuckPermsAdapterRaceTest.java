package com.pedrodalben.bigbangessentials.permissions;

import com.pedrodalben.bigbangessentials.BigBangEssentials;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Race scenario: UserDataRecalculateEvent is fired concurrently with adapter.shutdown().
 * Expected: once shutdown completes, the listener callback is short-circuited and no further
 * blocking work is dispatched into the dying LuckPerms worker pool.
 */
class LuckPermsAdapterRaceTest {

    private LuckPermsAdapter adapter;
    private Consumer<UserDataRecalculateEvent> handler;
    private EventSubscription<UserDataRecalculateEvent> sub;
    private AtomicInteger handlerInvocationsPreShutdown;
    private AtomicInteger handlerInvocationsPostShutdown;

    @BeforeEach
    void setUp() {
        adapter = new LuckPermsAdapter(true);
        handlerInvocationsPreShutdown = new AtomicInteger();
        handlerInvocationsPostShutdown = new AtomicInteger();

        LuckPerms api = mock(LuckPerms.class);
        EventBus bus = mock(EventBus.class);
        sub = mock(EventSubscription.class);
        when(sub.isActive()).thenReturn(true);
        when(api.getEventBus()).thenReturn(bus);

        when(bus.subscribe(eq(UserDataRecalculateEvent.class), any(Consumer.class)))
            .thenAnswer(inv -> {
                handler = inv.getArgument(1);
                return sub;
            });

        adapter.setLuckPermsApiForTest(api);
        BigBangEssentials.setServerStoppingForTest(false);
        adapter.registerEventBusListenerForTest(api);
        assertNotNull(handler); // listener was actually wired
    }

    @AfterEach
    void tearDown() {
        BigBangEssentials.setServerStoppingForTest(false);
    }

    @Test
    void firesConcurrentWithShutdownDontLeaveFuturePending() throws Exception {
        int threads = 8;
        int perThread = 250;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        UserDataRecalculateEvent event = mock(UserDataRecalculateEvent.class);
        User user = mock(User.class);
        when(user.getUniqueId()).thenReturn(UUID.randomUUID());
        when(event.getUser()).thenReturn(user);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                for (int i = 0; i < perThread; i++) {
                    boolean wasShutdown = adapter.isShutdownRequestedForTest() || BigBangEssentials.isServerStopping();
                    try {
                        handler.accept(event);
                    } catch (Throwable ignored) {
                    }
                    if (wasShutdown) {
                        handlerInvocationsPostShutdown.incrementAndGet();
                    } else {
                        handlerInvocationsPreShutdown.incrementAndGet();
                    }
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        // Race: fire shutdown while workers are still racing
        start.countDown();
        long shutdownStart = System.nanoTime();
        adapter.shutdown();
        long shutdownMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - shutdownStart);

        // Worker threads may still be running handler; verify shutdown returns quickly
        assertTrue(shutdownMs < 500, "shutdown took too long: " + shutdownMs + "ms");
        verify(sub, times(1)).close();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "pending futures never completed");

        // After shutdown, the subscription must be cleared and the handler must be a no-op.
        assertNull(adapter.getEventSubscriptionForTest());

        // Direct calls of handler post-shutdown must NOT touch caches — verify by firing again;
        // since the listener guard returns immediately, no exception expected and managers untouched.
        assertDoesNotThrow(() -> handler.accept(event));
    }
}