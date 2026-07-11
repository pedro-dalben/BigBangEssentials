package com.pedrodalben.bigbangessentials.jobs.compat;

public interface OptionalJobsIntegration {
    String integrationId();

    IntegrationStatus probeApi();

    SubscriptionResult subscribeEvents();

    void shutdown();

    IntegrationStatus getStatus();

    String requiredModId();

    String[] supportedActionTypes();

    default boolean isModAvailable() {
        return com.pedrodalben.bigbangessentials.util.Platform.isModLoaded(requiredModId());
    }

    default IntegrationState computeInitialState() {
        if (requiredModId() == null || requiredModId().isEmpty()) return IntegrationState.MOD_NOT_INSTALLED;
        if (!isModAvailable()) return IntegrationState.MOD_NOT_INSTALLED;

        for (String actionType : supportedActionTypes()) {
            if (actionType == null || actionType.trim().isEmpty()) continue;
            if (com.pedrodalben.bigbangessentials.jobs.JobActionType.fromString(actionType) == null) {
                return IntegrationState.API_CLASS_NOT_FOUND;
            }
        }
        return IntegrationState.API_FOUND;
    }

    @Deprecated
    default IntegrationStatus initialize() {
        return probeApi();
    }

    @Deprecated
    default void registerListeners() {
        subscribeEvents();
    }
}
