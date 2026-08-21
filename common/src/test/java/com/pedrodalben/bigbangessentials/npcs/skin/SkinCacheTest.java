package com.pedrodalben.bigbangessentials.npcs.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Skin cache tests that never touch Mojang: the resolver is injected and
 * deterministic (CI-safe). Persistence tests use a JUnit temp directory so no
 * test ever touches the real skin-cache.json file.
 */
class SkinCacheTest {

    @TempDir
    Path tempDir;

    static final class FakeSkinResolver implements SkinResolver {
        final ConcurrentHashMap<String, SkinCacheEntry> results = new ConcurrentHashMap<>();
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean offline;
        volatile CountDownLatch blockLatch;
        volatile boolean resolvedBlocked;

        void queue(String playerName, String texture, String model) {
            results.put(SkinCache.normalize(playerName), SkinCacheEntry.resolved(
                SkinCache.normalize(playerName), playerName,
                "00000000-0000-0000-0000-00000000000" + (results.size() + 1),
                texture, "sig-" + texture, model, 3600_000L));
        }

        @Override
        public SkinCacheEntry resolve(String playerName) {
            calls.incrementAndGet();
            if (offline) {
                throw new RuntimeException("Mojang unreachable (test)");
            }
            CountDownLatch latch = blockLatch;
            if (latch != null) {
                resolvedBlocked = true;
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            SkinCacheEntry entry = results.get(SkinCache.normalize(playerName));
            if (entry == null) return SkinCacheEntry.negative(SkinCache.normalize(playerName), 600_000);
            return entry;
        }
    }

    @Test
    void freshHitDoesNotCallResolverTwice() {
        FakeSkinResolver resolver = new FakeSkinResolver();
        resolver.queue("Dalbesmr", "tex-1", "default");
        SkinCache cache = new SkinCache(resolver, 24, 30, 10, 2, null);
        try {
            SkinCacheEntry first = cache.resolve("Dalbesmr").join();
            assertEquals("tex-1", first.textureValue());

            SkinCacheEntry second = cache.resolve("dalbesmr").join();
            assertEquals("tex-1", second.textureValue());
            assertEquals(1, resolver.calls.get(), "cache hit must not re-resolve");
            assertTrue(cache.hits() >= 1);
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void concurrentResolvesDeduplicateToOneRequest() throws Exception {
        FakeSkinResolver resolver = new FakeSkinResolver();
        resolver.queue("Dalbesmr", "tex-1", "slim");
        resolver.blockLatch = new CountDownLatch(1);
        SkinCache cache = new SkinCache(resolver, 24, 30, 10, 2, null);
        try {
            CompletableFuture<?>[] futures = new CompletableFuture<?>[8];
            for (int i = 0; i < futures.length; i++) {
                futures[i] = cache.resolve("Dalbesmr");
            }
            // Give the executor a moment to enqueue the (single) task.
            long deadline = System.currentTimeMillis() + 2000;
            while (!resolver.resolvedBlocked && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(resolver.resolvedBlocked, "resolver should have been invoked");
            resolver.blockLatch.countDown();
            for (CompletableFuture<?> future : futures) {
                SkinCacheEntry entry = (SkinCacheEntry) future.get(5, TimeUnit.SECONDS);
                assertEquals("tex-1", entry.textureValue());
            }
            assertEquals(1, resolver.calls.get(), "all concurrent resolves must share one request");
            assertEquals("slim", cache.peek("Dalbesmr").model());
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void offlineResolverUsesPersistedCacheAcrossRestart() {
        Path cacheFile = tempDir.resolve("skin-cache.json");
        FakeSkinResolver resolver = new FakeSkinResolver();
        resolver.queue("Dalbesmr", "persisted-tex", "default");
        SkinCache cache = new SkinCache(resolver, 24, 30, 10, 2, cacheFile);
        SkinCacheEntry entry = cache.resolve("Dalbesmr").join();
        assertEquals("persisted-tex", entry.textureValue());
        cache.persist();
        cache.shutdown();

        assertTrue(Files.exists(cacheFile), "cache must be persisted to disk");

        // Restart with an offline resolver: the NPC must still use the cached skin.
        // The persisted entry is still fresh, so it is served as a HIT without
        // contacting the resolver; if it were stale, the offline failure would
        // fall back to the persisted entry instead.
        FakeSkinResolver offline = new FakeSkinResolver();
        offline.offline = true;
        SkinCache cache2 = new SkinCache(offline, 24, 30, 10, 2, cacheFile);
        try {
            SkinCacheEntry cached = cache2.resolve("Dalbesmr").join();
            assertEquals("persisted-tex", cached.textureValue(), "offline restart must use persisted skin");
            assertTrue(offline.calls.get() <= 1, "offline restart must not retry Mojang repeatedly");
        } finally {
            cache2.shutdown();
        }
    }

    @Test
    void negativeResultIsCachedAndHonored() {
        FakeSkinResolver resolver = new FakeSkinResolver(); // no results → negative
        SkinCache cache = new SkinCache(resolver, 24, 30, 10, 2, null);
        try {
            SkinCacheEntry first = cache.resolve("UnknownPlayer").join();
            assertTrue(first.negative());
            assertEquals("NEGATIVE", cache.describeCacheStatus("UnknownPlayer"));

            SkinCacheEntry second = cache.resolve("unknownplayer").join();
            assertTrue(second.negative());
            assertEquals(1, resolver.calls.get(), "negative entries must be cached");
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void staleEntryIsReturnedAndRefreshedInBackground() throws Exception {
        FakeSkinResolver resolver = new FakeSkinResolver();
        resolver.queue("Dalbesmr", "new-tex", "default");
        SkinCache cache = new SkinCache(resolver, 24, 30, 10, 2, null);
        try {
            // Seed a stale entry (expired fresh TTL, still within stale TTL).
            long now = System.currentTimeMillis();
            SkinCacheEntry stale = new SkinCacheEntry("dalbesmr", "Dalbesmr", UUID.randomUUID().toString(),
                "old-tex", "old-sig", "default", now - 48 * 3600_000L, now - 24 * 3600_000L, false);
            cache.seedForTest("dalbesmr", stale);

            SkinCacheEntry result = cache.resolve("Dalbesmr").join();
            assertEquals("old-tex", result.textureValue(), "stale entry must be served immediately");

            // Background refresh should eventually re-resolve.
            long deadline = System.currentTimeMillis() + 5000;
            while (resolver.calls.get() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(1, resolver.calls.get(), "stale hit must trigger background refresh");
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void debouncedPersistCoalescesWrites() {
        Path cacheFile = tempDir.resolve("skin-cache.json");
        FakeSkinResolver resolver = new FakeSkinResolver();
        resolver.queue("Dalbesmr", "tex-a", "default");
        resolver.queue("Notch", "tex-b", "slim");
        SkinCache cache = new SkinCache(resolver, 24, 30, 10, 2, cacheFile);
        try {
            cache.resolve("Dalbesmr").join();
            cache.resolve("Notch").join();
            assertFalse(Files.exists(cacheFile), "dirty cache must not be written until debounce flush");
            cache.persistIfDirtyDebounced();
            cache.persistIfDirtyDebounced(); // second call in same window is a no-op, still one file
            assertTrue(Files.exists(cacheFile));
            assertEquals(2, cache.memorySize());
        } finally {
            cache.shutdown();
        }
    }
}
