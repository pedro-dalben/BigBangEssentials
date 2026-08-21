package com.pedrodalben.bigbangessentials.npcs.skin;

import com.google.gson.*;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Global, per-player-name skin cache with memory + persistent storage.
 *
 * <p>Guarantees at most one Mojang request per player name at a time
 * (atomic {@code computeIfAbsent} dedup), a bounded executor with a capped
 * queue (no unbounded Mojang request buildup), and debounced persistence
 * (multiple resolutions coalesce into a single disk write).</p>
 */
public class SkinCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkinCache.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE = "npcs/skin-cache.json";
    private static final long PERSIST_DEBOUNCE_MILLIS = 1_000;

    private final ConcurrentHashMap<String, SkinCacheEntry> memoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<SkinCacheEntry>> inflightRequests = new ConcurrentHashMap<>();
    private final SkinResolver resolver;
    private final ThreadPoolExecutor executor;
    private final Path dataFile; // null = disk persistence disabled

    private volatile long freshTtlMillis;
    private volatile long staleTtlMillis;
    private volatile long negativeTtlMillis;

    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger misses = new AtomicInteger();
    private final AtomicInteger staleHits = new AtomicInteger();
    private final AtomicInteger negativeHits = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();

    private final AtomicBoolean persistDirty = new AtomicBoolean();
    private final AtomicLong lastPersistMillis = new AtomicLong();

    public SkinCache(int freshTtlHours, int staleTtlDays, int negativeCacheMinutes,
                     int maxConcurrentRequests, int connectTimeoutMillis, int requestTimeoutMillis) {
        this(new MojangSkinResolver(connectTimeoutMillis, requestTimeoutMillis),
            freshTtlHours, staleTtlDays, negativeCacheMinutes, maxConcurrentRequests);
    }

    public SkinCache(SkinResolver resolver, int freshTtlHours, int staleTtlDays, int negativeCacheMinutes,
                     int maxConcurrentRequests) {
        this(resolver, freshTtlHours, staleTtlDays, negativeCacheMinutes, maxConcurrentRequests,
            ResourceUtil.getMigratedDataPath(FILE));
    }

    /**
     * Constructor with explicit storage file. Pass {@code null} to disable
     * disk persistence — tests use this so no skin entry ever leaks through
     * the shared skin-cache.json file between test cases.
     */
    public SkinCache(SkinResolver resolver, int freshTtlHours, int staleTtlDays, int negativeCacheMinutes,
                     int maxConcurrentRequests, Path dataFile) {
        this.resolver = resolver;
        this.dataFile = dataFile;
        this.freshTtlMillis = freshTtlHours * 3600_000L;
        this.staleTtlMillis = staleTtlDays * 86_400_000L;
        this.negativeTtlMillis = negativeCacheMinutes * 60_000L;
        this.executor = createExecutor(maxConcurrentRequests);
        if (dataFile != null) {
            loadPersistent();
        }
    }

    private static ThreadPoolExecutor createExecutor(int maxConcurrentRequests) {
        int workers = Math.max(1, maxConcurrentRequests);
        return new ThreadPoolExecutor(workers, workers, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(Math.max(8, workers * 4)),
            r -> {
                Thread t = new Thread(r, "BigBangEssentials-NpcSkin");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardPolicy());
    }

    /**
     * Resolves a player name to a skin entry. The returned future is completed
     * either from cache (already completed), from an in-flight shared request,
     * or by a new (deduplicated) Mojang request.
     */
    public CompletableFuture<SkinCacheEntry> resolve(String playerName) {
        String key = normalize(playerName);

        SkinCacheEntry memEntry = memoryCache.get(key);
        if (memEntry != null) {
            if (!memEntry.negative() && memEntry.isFresh()) {
                hits.incrementAndGet();
                return CompletableFuture.completedFuture(memEntry);
            }
            if (!memEntry.negative() && memEntry.isStale(staleTtlMillis)) {
                staleHits.incrementAndGet();
                refreshInBackground(key, playerName);
                return CompletableFuture.completedFuture(memEntry);
            }
            if (memEntry.negative() && memEntry.isFresh()) {
                negativeHits.incrementAndGet();
                return CompletableFuture.completedFuture(memEntry);
            }
            // expired (or expired negative): fall through to re-resolution
        }

        CompletableFuture<SkinCacheEntry> future = inflightRequests.computeIfAbsent(key, k -> {
            misses.incrementAndGet();
            CompletableFuture<SkinCacheEntry> result = new CompletableFuture<>();
            try {
                executor.execute(() -> resolveAndCache(key, playerName, result, false));
            } catch (RejectedExecutionException e) {
                failures.incrementAndGet();
                LOGGER.warn("Skin executor saturated; using cached/negative skin for '{}'", playerName);
                SkinCacheEntry stale = memoryCache.get(key);
                result.complete(stale != null ? stale : SkinCacheEntry.negative(key, negativeTtlMillis));
            }
            return result;
        });
        return future.whenComplete((r, t) -> inflightRequests.remove(key, future));
    }

    private void refreshInBackground(String key, String playerName) {
        CompletableFuture<SkinCacheEntry> future = inflightRequests.computeIfAbsent(key, k -> {
            CompletableFuture<SkinCacheEntry> result = new CompletableFuture<>();
            try {
                executor.execute(() -> resolveAndCache(key, playerName, result, true));
            } catch (RejectedExecutionException e) {
                result.complete(null);
            }
            return result;
        });
        future.whenComplete((r, t) -> inflightRequests.remove(key, future));
    }

    private void resolveAndCache(String key, String playerName, CompletableFuture<SkinCacheEntry> future,
                                 boolean backgroundRefresh) {
        try {
            SkinCacheEntry result = resolver.resolve(playerName);
            if (backgroundRefresh) {
                // Background refreshes only upgrade the cache with a real skin;
                // negative results never overwrite a previously known skin.
                if (!result.negative()) {
                    memoryCache.put(key, result);
                }
            } else {
                memoryCache.put(key, result);
            }
            future.complete(result);
            persistDirty.set(true);
        } catch (Exception e) {
            failures.incrementAndGet();
            LOGGER.warn("Skin resolution failed for '{}': {}", playerName, e.getMessage());
            SkinCacheEntry stale = memoryCache.get(key);
            future.complete(stale != null && !stale.negative() ? stale : SkinCacheEntry.negative(key, negativeTtlMillis));
        }
    }

    public void configure(int freshTtlHours, int staleTtlDays, int negativeCacheMinutes,
                          int maxConcurrentRequests, int connectTimeoutMillis, int requestTimeoutMillis) {
        this.freshTtlMillis = freshTtlHours * 3600_000L;
        this.staleTtlMillis = staleTtlDays * 86_400_000L;
        this.negativeTtlMillis = negativeCacheMinutes * 60_000L;
        int workers = Math.max(1, maxConcurrentRequests);
        executor.setCorePoolSize(workers);
        executor.setMaximumPoolSize(workers);
        if (resolver instanceof MojangSkinResolver mojangResolver) {
            mojangResolver.configure(connectTimeoutMillis, requestTimeoutMillis);
        }
    }

    /** Flushes dirty entries to disk at most once per debounce interval. Call from the server tick. */
    public void persistIfDirtyDebounced() {
        if (dataFile == null) return;
        if (!persistDirty.get()) return;
        long now = System.currentTimeMillis();
        if (now - lastPersistMillis.get() < PERSIST_DEBOUNCE_MILLIS) return;
        lastPersistMillis.set(now);
        persistDirty.set(false);
        persist();
    }

    private void loadPersistent() {
        if (dataFile == null) return;
        Path file = dataFile;
        if (!Files.exists(file)) return;
        try (Reader reader = new FileReader(file.toFile())) {
            JsonArray arr = GSON.fromJson(reader, JsonArray.class);
            if (arr == null) return;
            for (JsonElement el : arr) {
                try {
                    JsonObject obj = el.getAsJsonObject();
                    String normalizedName = obj.get("normalizedName").getAsString();
                    String originalName = obj.has("originalName") ? obj.get("originalName").getAsString() : normalizedName;
                    String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : "";
                    String textureValue = obj.has("textureValue") ? obj.get("textureValue").getAsString() : "";
                    String textureSignature = obj.has("textureSignature") ? obj.get("textureSignature").getAsString() : "";
                    String model = obj.has("model") ? obj.get("model").getAsString() : "default";
                    long fetchedAt = obj.has("fetchedAt") ? obj.get("fetchedAt").getAsLong() : 0;
                    long expiresAt = obj.has("expiresAt") ? obj.get("expiresAt").getAsLong() : 0;
                    boolean negative = obj.has("negative") && obj.get("negative").getAsBoolean();
                    SkinCacheEntry entry = new SkinCacheEntry(normalizedName, originalName, uuid,
                        textureValue, textureSignature, model, fetchedAt, expiresAt, negative);
                    memoryCache.put(normalizedName, entry);
                } catch (Exception e) {
                    LOGGER.debug("Skipping invalid skin cache entry", e);
                }
            }
            LOGGER.info("Loaded {} skin cache entries from disk", memoryCache.size());
        } catch (Exception e) {
            LOGGER.warn("Failed to load persistent skin cache: {}", e.getMessage());
        }
    }

    public void persist() {
        if (dataFile == null) return;
        try {
            Path file = dataFile;
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);

            JsonArray arr = new JsonArray();
            for (SkinCacheEntry entry : memoryCache.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("normalizedName", entry.normalizedName());
                obj.addProperty("originalName", entry.originalName());
                obj.addProperty("uuid", entry.uuid());
                obj.addProperty("textureValue", entry.textureValue());
                obj.addProperty("textureSignature", entry.textureSignature());
                obj.addProperty("model", entry.model());
                obj.addProperty("fetchedAt", entry.fetchedAt());
                obj.addProperty("expiresAt", entry.expiresAt());
                obj.addProperty("negative", entry.negative());
                arr.add(obj);
            }
            Files.writeString(file, GSON.toJson(arr), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            LOGGER.warn("Failed to persist skin cache: {}", e.getMessage());
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        persist();
    }

    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    public SkinCacheEntry peek(String playerName) {
        return memoryCache.get(normalize(playerName));
    }

    /** Test hook: seeds the memory cache directly (deterministic cached spawns). */
    public void seedForTest(String playerName, SkinCacheEntry entry) {
        memoryCache.put(normalize(playerName), entry);
    }

    /** HIT (fresh) / STALE / NEGATIVE / EXPIRED / MISS. */
    public String describeCacheStatus(String playerName) {
        SkinCacheEntry entry = memoryCache.get(normalize(playerName));
        if (entry == null) return "MISS";
        if (entry.negative()) return entry.isFresh() ? "NEGATIVE" : "NEGATIVE_EXPIRED";
        if (entry.isFresh()) return "HIT";
        if (entry.isStale(staleTtlMillis)) return "STALE";
        return "EXPIRED";
    }

    public int memorySize() { return memoryCache.size(); }
    public int hits() { return hits.get(); }
    public int misses() { return misses.get(); }
    public int staleHits() { return staleHits.get(); }
    public int negativeHits() { return negativeHits.get(); }
    public int failures() { return failures.get(); }
    public int inflightCount() { return inflightRequests.size(); }
}
