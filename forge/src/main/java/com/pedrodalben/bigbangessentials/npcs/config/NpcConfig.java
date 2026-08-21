package com.pedrodalben.bigbangessentials.npcs.config;

import com.pedrodalben.bigbangessentials.npcs.api.NpcDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NpcConfig {
    private final int schemaVersion;
    private final double defaultViewDistance;
    private final double defaultDespawnDistance;
    private final double defaultInteractionDistance;
    private final long defaultCooldownMillis;
    private final int visibilityScanIntervalTicks;
    private final int maxViewerSyncsPerTick;
    private final int maxSpawnsPerTick;
    private final int maxDespawnsPerTick;
    private final int maxLookUpdatesPerTick;
    private final int freshTtlHours;
    private final int staleTtlDays;
    private final int negativeCacheMinutes;
    private final int maxConcurrentRequests;
    private final int connectTimeoutMillis;
    private final int requestTimeoutMillis;
    private final Map<String, NpcDefinition> npcs;

    public NpcConfig(int schemaVersion, double defaultViewDistance, double defaultDespawnDistance,
                     double defaultInteractionDistance, long defaultCooldownMillis,
                     int visibilityScanIntervalTicks, int maxViewerSyncsPerTick,
                     int maxSpawnsPerTick, int maxDespawnsPerTick, int maxLookUpdatesPerTick,
                     int freshTtlHours, int staleTtlDays, int negativeCacheMinutes,
                     int maxConcurrentRequests, int connectTimeoutMillis, int requestTimeoutMillis,
                     Map<String, NpcDefinition> npcs) {
        this.schemaVersion = schemaVersion;
        this.defaultViewDistance = defaultViewDistance;
        this.defaultDespawnDistance = defaultDespawnDistance;
        this.defaultInteractionDistance = defaultInteractionDistance;
        this.defaultCooldownMillis = defaultCooldownMillis;
        this.visibilityScanIntervalTicks = visibilityScanIntervalTicks;
        this.maxViewerSyncsPerTick = maxViewerSyncsPerTick;
        this.maxSpawnsPerTick = maxSpawnsPerTick;
        this.maxDespawnsPerTick = maxDespawnsPerTick;
        this.maxLookUpdatesPerTick = maxLookUpdatesPerTick;
        this.freshTtlHours = freshTtlHours;
        this.staleTtlDays = staleTtlDays;
        this.negativeCacheMinutes = negativeCacheMinutes;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.requestTimeoutMillis = requestTimeoutMillis;
        this.npcs = Collections.unmodifiableMap(new LinkedHashMap<>(npcs != null ? npcs : Map.of()));
    }

    public static NpcConfig defaults() {
        return new NpcConfig(1, 48.0, 56.0, 4.5, 750,
            10, 50, 20, 50, 200,
            24, 30, 10, 2, 3000, 5000,
            new LinkedHashMap<>());
    }

    public int schemaVersion() { return schemaVersion; }
    public double defaultViewDistance() { return defaultViewDistance; }
    public double defaultDespawnDistance() { return defaultDespawnDistance; }
    public double defaultInteractionDistance() { return defaultInteractionDistance; }
    public long defaultCooldownMillis() { return defaultCooldownMillis; }
    public int visibilityScanIntervalTicks() { return visibilityScanIntervalTicks; }
    public int maxViewerSyncsPerTick() { return maxViewerSyncsPerTick; }
    public int maxSpawnsPerTick() { return maxSpawnsPerTick; }
    public int maxDespawnsPerTick() { return maxDespawnsPerTick; }
    public int maxLookUpdatesPerTick() { return maxLookUpdatesPerTick; }
    public int freshTtlHours() { return freshTtlHours; }
    public int staleTtlDays() { return staleTtlDays; }
    public int negativeCacheMinutes() { return negativeCacheMinutes; }
    public int maxConcurrentRequests() { return maxConcurrentRequests; }
    public int connectTimeoutMillis() { return connectTimeoutMillis; }
    public int requestTimeoutMillis() { return requestTimeoutMillis; }
    public Map<String, NpcDefinition> npcs() { return npcs; }

    public NpcConfig withNpcs(Map<String, NpcDefinition> npcs) {
        return new NpcConfig(schemaVersion, defaultViewDistance, defaultDespawnDistance,
            defaultInteractionDistance, defaultCooldownMillis,
            visibilityScanIntervalTicks, maxViewerSyncsPerTick, maxSpawnsPerTick,
            maxDespawnsPerTick, maxLookUpdatesPerTick,
            freshTtlHours, staleTtlDays, negativeCacheMinutes,
            maxConcurrentRequests, connectTimeoutMillis, requestTimeoutMillis, npcs);
    }
}
