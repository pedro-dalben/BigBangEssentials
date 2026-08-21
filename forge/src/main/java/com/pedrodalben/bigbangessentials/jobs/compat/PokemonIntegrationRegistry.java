package com.pedrodalben.bigbangessentials.jobs.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PokemonIntegrationRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(PokemonIntegrationRegistry.class);
    private static final PokemonIntegrationRegistry INSTANCE = new PokemonIntegrationRegistry();

    private final Map<String, OptionalJobsIntegration> integrations = new LinkedHashMap<>();
    private final Map<String, IntegrationStatus> statuses = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public static PokemonIntegrationRegistry getInstance() { return INSTANCE; }

    private PokemonIntegrationRegistry() {
        register(new CobblemonJobsBridge());
        register(new TrainerJobsBridge());
        register(new BreedingJobsBridge());
        register(new PastureJobsBridge());
        register(new FossilJobsBridge());
        register(new RaidDensJobsBridge());
    }

    private void register(OptionalJobsIntegration integration) {
        integrations.put(integration.integrationId(), integration);
    }

    public synchronized void initializeAll() {
        if (!initialized.compareAndSet(false, true)) {
            LOGGER.warn("[Jobs Compat] initializeAll() called but already initialized. Skipping.");
            return;
        }

        LOGGER.info("[Jobs Compat] Starting Cobbleverse integration probe and subscription...");
        for (Map.Entry<String, OptionalJobsIntegration> entry : integrations.entrySet()) {
            String id = entry.getKey();
            OptionalJobsIntegration integration = entry.getValue();
            try {
                IntegrationStatus probeStatus = integration.probeApi();
                statuses.put(id, probeStatus);
                logStatus(probeStatus);

                if (probeStatus.state() == IntegrationState.API_FOUND) {
                    SubscriptionResult subResult = integration.subscribeEvents();
                    IntegrationStatus updatedStatus = probeStatus.withSubscriptionResult(subResult);
                    statuses.put(id, updatedStatus);
                    logStatus(updatedStatus);

                    if (subResult.success()) {
                        LOGGER.info("[Jobs Compat] [{}] Event subscription succeeded. Adapter: {}",
                                id, subResult.adapterStrategy());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[Jobs Compat] Error initializing integration [{}]", id, e);
                IntegrationStatus errStatus = new IntegrationStatus(
                        id, IntegrationState.ERROR, "unknown", "N/A", "N/A",
                        "Critical initialization error: " + e.getMessage(),
                        List.of(), List.of(), "N/A", "N/A", "FAILED", "NONE",
                        0L, 0L, 0L, 0L, 0L,
                        e.getMessage(), System.currentTimeMillis(),
                        "Exception during initialization", false
                );
                statuses.put(id, errStatus);
            }
        }
        LOGGER.info("[Jobs Compat] All integrations probed and subscriptions attempted.");
    }

    public synchronized void shutdownAll() {
        initialized.set(false);
        for (OptionalJobsIntegration integration : integrations.values()) {
            try {
                integration.shutdown();
                IntegrationStatus current = statuses.get(integration.integrationId());
                if (current != null) {
                    statuses.put(integration.integrationId(), current.withState(IntegrationState.SHUTDOWN));
                }
            } catch (Exception e) {
                LOGGER.error("[Jobs Compat] Error shutting down [{}]", integration.integrationId(), e);
            }
        }
    }

    public synchronized void reload() {
        LOGGER.info("[Jobs Compat] Reloading integrations — full shutdown and re-probe...");
        shutdownAll();
        initialized.set(false);
        initializeAll();
    }

    public IntegrationStatus probe(String integrationId) {
        OptionalJobsIntegration integration = integrations.get(integrationId.toLowerCase());
        if (integration == null) {
            return IntegrationStatus.quick(integrationId, IntegrationState.MOD_NOT_INSTALLED, "none",
                    "Integration not found in registry", List.of(), List.of());
        }
        IntegrationStatus probed = integration.probeApi();
        statuses.put(integrationId, probed);
        return probed;
    }

    public void updateStatus(String integrationId, IntegrationStatus newStatus) {
        if (integrationId != null && newStatus != null) {
            statuses.put(integrationId, newStatus);
        }
    }

    public IntegrationStatus getStatus(String integrationId) {
        return statuses.getOrDefault(integrationId.toLowerCase(),
                IntegrationStatus.quick(integrationId, IntegrationState.NOT_PROBED, "none",
                        "Not initialized yet", List.of(), List.of()));
    }

    public Collection<IntegrationStatus> getAllStatuses() {
        return Collections.unmodifiableCollection(new ArrayList<>(statuses.values()));
    }

    public boolean isActive(String integrationId) {
        IntegrationStatus s = getStatus(integrationId);
        return s != null && s.state() == IntegrationState.ACTIVE;
    }

    public boolean isOperational(String integrationId) {
        IntegrationStatus s = getStatus(integrationId);
        return s != null && s.isOperational();
    }

    public boolean isHealthy(String integrationId) {
        IntegrationStatus s = getStatus(integrationId);
        return s != null && s.isHealthy();
    }

    public OptionalJobsIntegration getIntegration(String integrationId) {
        return integrations.get(integrationId.toLowerCase());
    }

    private void logStatus(IntegrationStatus status) {
        LOGGER.info("[Jobs Compat] Integration [{}]: State={}, Mod={}, Version={}, Events={}, SubStatus={}, Adapter={}, Details={}",
                status.integrationId(), status.state(), status.detectedModId(), status.detectedVersion(),
                status.eventClassName(), status.subscriptionStatus(), status.adapterStrategy(), status.details());
    }
}
