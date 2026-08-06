package com.pedrodalben.bigbangessentials.npcs.skin;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.pedrodalben.bigbangessentials.util.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SkinCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkinCache.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE = "npcs/skin-cache.json";

    private final ConcurrentHashMap<String, SkinCacheEntry> memoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<SkinCacheEntry>> inflightRequests = new ConcurrentHashMap<>();
    private final MojangSkinResolver resolver;
    private final ExecutorService executor;

    private long freshTtlMillis;
    private long staleTtlMillis;
    private long negativeTtlMillis;
    private int maxConcurrentRequests;

    private int hits;
    private int misses;
    private int staleHits;
    private int negativeHits;
    private int failures;

    public SkinCache(int freshTtlHours, int staleTtlDays, int negativeCacheMinutes,
                     int maxConcurrentRequests, int connectTimeoutMillis, int requestTimeoutMillis) {
        this.freshTtlMillis = freshTtlHours * 3600_000L;
        this.staleTtlMillis = staleTtlDays * 86_400_000L;
        this.negativeTtlMillis = negativeCacheMinutes * 60_000L;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.resolver = new MojangSkinResolver(connectTimeoutMillis, requestTimeoutMillis);
        this.executor = Executors.newFixedThreadPool(Math.max(1, maxConcurrentRequests), r -> {
            Thread t = new Thread(r, "BigBangEssentials-NpcSkin");
            t.setDaemon(true);
            return t;
        });

        loadPersistent();
    }

    public CompletableFuture<SkinCacheEntry> resolve(String playerName) {
        String key = normalize(playerName);

        SkinCacheEntry memEntry = memoryCache.get(key);
        if (memEntry != null) {
            if (!memEntry.negative() && memEntry.isFresh()) {
                hits++;
                return CompletableFuture.completedFuture(memEntry);
            }
            if (!memEntry.negative() && memEntry.isStale(staleTtlMillis)) {
                staleHits++;
                refreshInBackground(key, playerName);
                return CompletableFuture.completedFuture(memEntry);
            }
            if (memEntry.negative() && memEntry.isFresh()) {
                negativeHits++;
                return CompletableFuture.completedFuture(memEntry);
            }
        }

        CompletableFuture<SkinCacheEntry> existing = inflightRequests.get(key);
        if (existing != null) return existing;

        misses++;
        CompletableFuture<SkinCacheEntry> future = CompletableFuture.supplyAsync(() -> {
            try {
                SkinCacheEntry result = resolver.resolve(playerName);
                if (result.negative()) {
                    memoryCache.put(key, result);
                } else {
                    memoryCache.put(key, result);
                }
                persistAsync();
                return result;
            } catch (Exception e) {
                failures++;
                LOGGER.warn("Skin resolution failed for '{}': {}", playerName, e.getMessage());
                SkinCacheEntry stale = memoryCache.get(key);
                if (stale != null) return stale;
                return SkinCacheEntry.negative(key, negativeTtlMillis);
            }
        }, executor).whenComplete((r, t) -> inflightRequests.remove(key));

        inflightRequests.put(key, future);
        return future;
    }

    public void configure(int freshTtlHours, int staleTtlDays, int negativeCacheMinutes,
                          int maxConcurrentRequests, int connectTimeoutMillis, int requestTimeoutMillis) {
        this.freshTtlMillis = freshTtlHours * 3600_000L;
        this.staleTtlMillis = staleTtlDays * 86_400_000L;
        this.negativeTtlMillis = negativeCacheMinutes * 60_000L;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.resolver.configure(connectTimeoutMillis, requestTimeoutMillis);
    }

    private void refreshInBackground(String key, String playerName) {
        CompletableFuture<SkinCacheEntry> existing = inflightRequests.get(key);
        if (existing != null) return;
        CompletableFuture<SkinCacheEntry> future = CompletableFuture.supplyAsync(() -> {
            try {
                SkinCacheEntry result = resolver.resolve(playerName);
                if (!result.negative()) memoryCache.put(key, result);
                persistAsync();
                return result;
            } catch (Exception e) {
                LOGGER.debug("Background skin refresh failed for '{}': {}", playerName, e.getMessage());
                return null;
            }
        }, executor).whenComplete((r, t) -> inflightRequests.remove(key));
        inflightRequests.put(key, future);
    }

    private void loadPersistent() {
        Path file = ResourceUtil.getMigratedDataPath(FILE);
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
        try {
            Path file = ResourceUtil.getDataPath(FILE);
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

    private void persistAsync() {
        executor.submit(this::persist);
    }

    public void shutdown() {
        executor.shutdown();
        persist();
    }

    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    public int memorySize() { return memoryCache.size(); }
    public int hits() { return hits; }
    public int misses() { return misses; }
    public int staleHits() { return staleHits; }
    public int negativeHits() { return negativeHits; }
    public int failures() { return failures; }
    public int inflightCount() { return inflightRequests.size(); }
}
