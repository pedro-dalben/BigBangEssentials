package com.pedrodalben.bigbangessentials.jobs.health;

import com.pedrodalben.bigbangessentials.jobs.compat.IntegrationState;
import com.pedrodalben.bigbangessentials.jobs.compat.PokemonIntegrationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class IntegrationHealthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationHealthService.class);
    private static final IntegrationHealthService INSTANCE = new IntegrationHealthService();

    private final Map<String, IntegrationHealthResult> cache = new ConcurrentHashMap<>();
    private long lastCacheFlush = 0;
    private static final long CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(30);

    private IntegrationHealthService() {}

    public static IntegrationHealthService getInstance() { return INSTANCE; }

    public IntegrationHealthResult getHealth(String integrationId) {
        if (integrationId == null || integrationId.isBlank()) {
            return new IntegrationHealthResult("none", IntegrationHealthStatus.AVAILABLE, false,
                "No integration required", System.currentTimeMillis());
        }
        flushCacheIfExpired();
        return cache.computeIfAbsent(integrationId, this::probe);
    }

    public IntegrationHealthResult probe(String integrationId) {
        try {
            var compatStatus = PokemonIntegrationRegistry.getInstance().getStatus(integrationId);
            if (compatStatus == null) {
                return new IntegrationHealthResult(integrationId, IntegrationHealthStatus.NOT_INSTALLED, false,
                    "Integration '" + integrationId + "' is not registered", System.currentTimeMillis());
            }
            IntegrationState state = compatStatus.state();
            IntegrationHealthStatus healthStatus;
            if (state == IntegrationState.ACTIVE) healthStatus = IntegrationHealthStatus.AVAILABLE;
            else if (state == IntegrationState.DEGRADED) healthStatus = IntegrationHealthStatus.DEGRADED;
            else healthStatus = IntegrationHealthStatus.UNAVAILABLE;

            boolean required = compatStatus.unavailabilityReason() != null
                && !compatStatus.unavailabilityReason().isBlank();
            return new IntegrationHealthResult(integrationId, healthStatus, required,
                state.name() + ": " + compatStatus.details(), System.currentTimeMillis());
        } catch (Exception e) {
            LOGGER.warn("Failed to probe integration '{}': {}", integrationId, e.getMessage());
            return new IntegrationHealthResult(integrationId, IntegrationHealthStatus.MISCONFIGURED, false,
                "Error probing: " + e.getMessage(), System.currentTimeMillis());
        }
    }

    public Map<String, IntegrationHealthResult> getAllHealth() {
        flushCacheIfExpired();
        return Map.copyOf(cache);
    }

    public void invalidate(String integrationId) {
        cache.remove(integrationId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private void flushCacheIfExpired() {
        long now = System.currentTimeMillis();
        if (now - lastCacheFlush > CACHE_TTL_MS) {
            cache.clear();
            lastCacheFlush = now;
        }
    }
}
