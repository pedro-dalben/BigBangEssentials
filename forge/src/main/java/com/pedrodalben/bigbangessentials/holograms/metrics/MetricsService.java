package com.pedrodalben.bigbangessentials.holograms.metrics;

import com.pedrodalben.bigbangessentials.holograms.api.HologramStats;
import com.pedrodalben.bigbangessentials.holograms.render.RendererHealth;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public final class MetricsService {

    private static final MetricsService INSTANCE = new MetricsService();

    private final AtomicLong spawnPackets = new AtomicLong();
    private final AtomicLong updatePackets = new AtomicLong();
    private final AtomicLong destroyPackets = new AtomicLong();
    private final AtomicLong teleportPackets = new AtomicLong();
    private final AtomicLong totalUpdateNanos = new AtomicLong();
    private final AtomicLong totalUpdates = new AtomicLong();
    private final AtomicLong legacyEntitiesRemoved = new AtomicLong();
    private volatile Instant lastLegacyCleanup;
    private volatile RendererHealth rendererHealth = RendererHealth.HEALTHY;
    private final AtomicLong fingerprintCacheHits = new AtomicLong();
    private final AtomicLong fingerprintCacheMisses = new AtomicLong();
    private final AtomicLong placeholderCacheHits = new AtomicLong();
    private final AtomicLong placeholderCacheMisses = new AtomicLong();
    private final AtomicLong activeAnimations = new AtomicLong();
    private final AtomicLong actionExecutions = new AtomicLong();
    private final AtomicLong rendererErrors = new AtomicLong();
    private final AtomicLong storageErrors = new AtomicLong();

    private MetricsService() {
    }

    public static MetricsService getInstance() {
        return INSTANCE;
    }

    public void incrementSpawnPackets() {
        spawnPackets.incrementAndGet();
    }

    public void incrementUpdatePackets() {
        updatePackets.incrementAndGet();
    }

    public void incrementDestroyPackets() {
        destroyPackets.incrementAndGet();
    }

    public void incrementTeleportPackets() {
        teleportPackets.incrementAndGet();
    }

    public void addUpdateNanos(long nanos) {
        totalUpdateNanos.addAndGet(nanos);
        totalUpdates.incrementAndGet();
    }

    public void incrementLegacyEntitiesRemoved() {
        legacyEntitiesRemoved.incrementAndGet();
    }

    public void setLastLegacyCleanup(Instant instant) {
        this.lastLegacyCleanup = instant;
    }

    public void setRendererHealth(RendererHealth health) {
        this.rendererHealth = health;
    }

    public void incrementFingerprintCacheHits() {
        fingerprintCacheHits.incrementAndGet();
    }

    public void incrementFingerprintCacheMisses() {
        fingerprintCacheMisses.incrementAndGet();
    }

    public void incrementPlaceholderCacheHits() {
        placeholderCacheHits.incrementAndGet();
    }

    public void incrementPlaceholderCacheMisses() {
        placeholderCacheMisses.incrementAndGet();
    }

    public void incrementActiveAnimations() {
        activeAnimations.incrementAndGet();
    }

    public void decrementActiveAnimations() {
        activeAnimations.decrementAndGet();
    }

    public void incrementActionExecutions() {
        actionExecutions.incrementAndGet();
    }

    public void incrementRendererErrors() {
        rendererErrors.incrementAndGet();
    }

    public void incrementStorageErrors() {
        storageErrors.incrementAndGet();
    }

    public HologramStats buildStats(int registeredHolograms, int persistentHolograms,
                                      int crateHolograms, int activePlayers,
                                      int activeViewerEntries, double avgVisible,
                                      int pendingUpdates) {
        long updates = totalUpdates.get();
        double avgNanos = updates > 0
                ? (double) totalUpdateNanos.get() / updates
                : 0.0;

        return new HologramStats(
                registeredHolograms,
                persistentHolograms,
                crateHolograms,
                activePlayers,
                activeViewerEntries,
                avgVisible,
                pendingUpdates,
                spawnPackets.get(),
                updatePackets.get(),
                destroyPackets.get(),
                avgNanos,
                (int) legacyEntitiesRemoved.get(),
                lastLegacyCleanup,
                rendererHealth
        );
    }

    public void reset() {
        spawnPackets.set(0);
        updatePackets.set(0);
        destroyPackets.set(0);
        teleportPackets.set(0);
        totalUpdateNanos.set(0);
        totalUpdates.set(0);
        legacyEntitiesRemoved.set(0);
        lastLegacyCleanup = null;
        fingerprintCacheHits.set(0);
        fingerprintCacheMisses.set(0);
        placeholderCacheHits.set(0);
        placeholderCacheMisses.set(0);
        activeAnimations.set(0);
        actionExecutions.set(0);
        rendererErrors.set(0);
        storageErrors.set(0);
    }

    public String snapshotDiagnostics(String hologramId) {
        long totalPackets = spawnPackets.get() + updatePackets.get()
                + destroyPackets.get() + teleportPackets.get();
        long totalCacheHits = fingerprintCacheHits.get() + placeholderCacheHits.get();
        long totalCacheMisses = fingerprintCacheMisses.get() + placeholderCacheMisses.get();

        return String.format(
                "[Metrics hologram=%s] packets(total=%d spawn=%d update=%d destroy=%d teleport=%d) "
                        + "updates(total=%d avgNanos=%.1f) "
                        + "legacyRemoved=%d renderer=%s "
                        + "cache(hits=%d misses=%d) "
                        + "animActive=%d actions=%d "
                        + "errors(renderer=%d storage=%d)",
                hologramId,
                totalPackets,
                spawnPackets.get(),
                updatePackets.get(),
                destroyPackets.get(),
                teleportPackets.get(),
                totalUpdates.get(),
                totalUpdates.get() > 0
                        ? (double) totalUpdateNanos.get() / totalUpdates.get()
                        : 0.0,
                legacyEntitiesRemoved.get(),
                rendererHealth,
                totalCacheHits,
                totalCacheMisses,
                activeAnimations.get(),
                actionExecutions.get(),
                rendererErrors.get(),
                storageErrors.get()
        );
    }
}
