package com.pedrodalben.bigbangessentials.holograms.placeholder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlaceholderCache {

    private final Map<String, String> globalCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, CachedEntry>> viewerCache = new ConcurrentHashMap<>();
    private final long defaultTtlTicks;

    public PlaceholderCache(long defaultTtlTicks) {
        this.defaultTtlTicks = defaultTtlTicks;
    }

    public Optional<String> getGlobal(String key, long currentTick) {
        String value = globalCache.get(key);
        if (value == null) return Optional.empty();
        return Optional.of(value);
    }

    public Optional<String> getViewer(UUID viewer, String key, long currentTick) {
        Map<String, CachedEntry> entries = viewerCache.get(viewer);
        if (entries == null) return Optional.empty();
        CachedEntry entry = entries.get(key);
        if (entry == null) return Optional.empty();
        if (currentTick > entry.expiresAtTick) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    public void putGlobal(String key, String value, long currentTick) {
        globalCache.put(key, value);
    }

    public void putViewer(UUID viewer, String key, String value, long currentTick) {
        long expiresAt = currentTick + defaultTtlTicks;
        viewerCache.computeIfAbsent(viewer, k -> new ConcurrentHashMap<>())
                .put(key, new CachedEntry(value, expiresAt));
    }

    public void invalidateAll() {
        globalCache.clear();
        viewerCache.clear();
    }

    public void invalidateViewer(UUID viewer) {
        viewerCache.remove(viewer);
    }

    private record CachedEntry(String value, long expiresAtTick) {}
}
