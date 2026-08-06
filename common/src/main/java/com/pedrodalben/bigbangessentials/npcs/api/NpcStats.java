package com.pedrodalben.bigbangessentials.npcs.api;

public final class NpcStats {
    private final int definitions;
    private final int enabled;
    private final int invalid;
    private final int viewerSessions;
    private final int visibleInstances;
    private final int spatialIndexEntries;
    private final int queuedSyncs;
    private final int queuedSpawns;
    private final int queuedDespawns;
    private final int lookUpdatesLastTick;
    private final int lookUpdatesDropped;
    private final int skinMemCacheEntries;
    private final int skinCacheHits;
    private final int skinCacheMisses;
    private final int skinStaleHits;
    private final int skinNegativeHits;
    private final int skinRequestsInFlight;
    private final int skinRequestFailures;
    private final int hologramsActive;
    private final long lastReloadMillis;
    private final long lastSaveMillis;

    public NpcStats(int definitions, int enabled, int invalid, int viewerSessions, int visibleInstances,
                    int spatialIndexEntries, int queuedSyncs, int queuedSpawns, int queuedDespawns,
                    int lookUpdatesLastTick, int lookUpdatesDropped, int skinMemCacheEntries,
                    int skinCacheHits, int skinCacheMisses, int skinStaleHits, int skinNegativeHits,
                    int skinRequestsInFlight, int skinRequestFailures, int hologramsActive,
                    long lastReloadMillis, long lastSaveMillis) {
        this.definitions = definitions;
        this.enabled = enabled;
        this.invalid = invalid;
        this.viewerSessions = viewerSessions;
        this.visibleInstances = visibleInstances;
        this.spatialIndexEntries = spatialIndexEntries;
        this.queuedSyncs = queuedSyncs;
        this.queuedSpawns = queuedSpawns;
        this.queuedDespawns = queuedDespawns;
        this.lookUpdatesLastTick = lookUpdatesLastTick;
        this.lookUpdatesDropped = lookUpdatesDropped;
        this.skinMemCacheEntries = skinMemCacheEntries;
        this.skinCacheHits = skinCacheHits;
        this.skinCacheMisses = skinCacheMisses;
        this.skinStaleHits = skinStaleHits;
        this.skinNegativeHits = skinNegativeHits;
        this.skinRequestsInFlight = skinRequestsInFlight;
        this.skinRequestFailures = skinRequestFailures;
        this.hologramsActive = hologramsActive;
        this.lastReloadMillis = lastReloadMillis;
        this.lastSaveMillis = lastSaveMillis;
    }

    public static NpcStats empty() {
        return new NpcStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public int definitions() { return definitions; }
    public int enabled() { return enabled; }
    public int invalid() { return invalid; }
    public int viewerSessions() { return viewerSessions; }
    public int visibleInstances() { return visibleInstances; }
    public int spatialIndexEntries() { return spatialIndexEntries; }
    public int queuedSyncs() { return queuedSyncs; }
    public int queuedSpawns() { return queuedSpawns; }
    public int queuedDespawns() { return queuedDespawns; }
    public int lookUpdatesLastTick() { return lookUpdatesLastTick; }
    public int lookUpdatesDropped() { return lookUpdatesDropped; }
    public int skinMemCacheEntries() { return skinMemCacheEntries; }
    public int skinCacheHits() { return skinCacheHits; }
    public int skinCacheMisses() { return skinCacheMisses; }
    public int skinStaleHits() { return skinStaleHits; }
    public int skinNegativeHits() { return skinNegativeHits; }
    public int skinRequestsInFlight() { return skinRequestsInFlight; }
    public int skinRequestFailures() { return skinRequestFailures; }
    public int hologramsActive() { return hologramsActive; }
    public long lastReloadMillis() { return lastReloadMillis; }
    public long lastSaveMillis() { return lastSaveMillis; }
}
