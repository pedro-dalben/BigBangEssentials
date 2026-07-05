package com.pedrodalben.bigbangessentials.jobs.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PokemonIntegrationRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(PokemonIntegrationRegistry.class);
    private static final PokemonIntegrationRegistry INSTANCE = new PokemonIntegrationRegistry();

    private final Map<String, OptionalJobsIntegration> integrations = new LinkedHashMap<>();
    private final Map<String, IntegrationStatus> statuses = new ConcurrentHashMap<>();

    public static PokemonIntegrationRegistry getInstance() {
        return INSTANCE;
    }

    private PokemonIntegrationRegistry() {
        registerIntegration(new CobblemonJobsBridge());
        registerIntegration(new TrainerJobsBridge());
        registerIntegration(new BreedingJobsBridge());
        registerIntegration(new PastureJobsBridge());
        registerIntegration(new FossilJobsBridge());
        registerIntegration(new RaidDensJobsBridge());
    }

    private void registerIntegration(OptionalJobsIntegration integration) {
        integrations.put(integration.integrationId(), integration);
    }

    public synchronized void initializeAll() {
        LOGGER.info("[Jobs Compat] Starting compatibility spike and initialization for Cobbleverse bridges...");
        for (OptionalJobsIntegration integration : integrations.values()) {
            try {
                IntegrationStatus status = integration.initialize();
                statuses.put(integration.integrationId(), status);
                LOGGER.info("[Jobs Compat] Integration [{}]: State={}, DetectedMod={}, Version={}, Details={}",
                        status.integrationId(), status.state(), status.detectedModId(), status.detectedVersion(), status.details());
                if (status.isOperational()) {
                    integration.registerListeners();
                    LOGGER.info("[Jobs Compat] Registered listeners for [{}]", integration.integrationId());
                }
            } catch (Exception e) {
                LOGGER.error("[Jobs Compat] Error initializing integration [{}]", integration.integrationId(), e);
                IntegrationStatus errStatus = new IntegrationStatus(
                        integration.integrationId(),
                        IntegrationState.ERROR,
                        "unknown",
                        "N/A",
                        "N/A",
                        "Erro grave na inicialização: " + e.getMessage(),
                        List.of(),
                        List.of()
                );
                statuses.put(integration.integrationId(), errStatus);
            }
        }
    }

    public synchronized void shutdownAll() {
        for (OptionalJobsIntegration integration : integrations.values()) {
            try {
                integration.shutdown();
            } catch (Exception e) {
                LOGGER.error("[Jobs Compat] Error shutting down integration [{}]", integration.integrationId(), e);
            }
        }
    }

    public synchronized void reload() {
        LOGGER.info("[Jobs Compat] Reloading integration configurations and probing status...");
        shutdownAll();
        initializeAll();
    }

    public IntegrationStatus probe(String integrationId) {
        OptionalJobsIntegration integration = integrations.get(integrationId.toLowerCase());
        if (integration == null) {
            return new IntegrationStatus(integrationId, IntegrationState.DISABLED_NOT_INSTALLED, "none", "N/A", "N/A", "Integração não encontrada", List.of(), List.of());
        }
        return integration.initialize();
    }

    public IntegrationStatus getStatus(String integrationId) {
        return statuses.getOrDefault(integrationId.toLowerCase(), new IntegrationStatus(integrationId, IntegrationState.DISABLED_NOT_INSTALLED, "none", "N/A", "N/A", "Não inicializado", List.of(), List.of()));
    }

    public Collection<IntegrationStatus> getAllStatuses() {
        return Collections.unmodifiableCollection(statuses.values());
    }

    public boolean isOperational(String integrationId) {
        IntegrationStatus status = getStatus(integrationId);
        return status != null && status.isOperational();
    }
}
