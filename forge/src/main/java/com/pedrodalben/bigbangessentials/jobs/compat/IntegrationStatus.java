package com.pedrodalben.bigbangessentials.jobs.compat;

import java.util.List;

public record IntegrationStatus(
    String integrationId,
    IntegrationState state,
    String detectedModId,
    String detectedVersion,
    String compatibilityVersion,
    String details,
    List<String> supportedActions,
    List<String> unavailableActions,
    String eventClassName,
    String eventBusName,
    String subscriptionStatus,
    String adapterStrategy,
    long eventsReceived,
    long eventsAccepted,
    long eventsRejected,
    long lastEventTimestamp,
    long lastSuccessTimestamp,
    String lastError,
    long lastErrorTimestamp,
    String unavailabilityReason,
    boolean initialized
) {
    public record Legacy(
        String integrationId,
        IntegrationState state,
        String detectedModId,
        String detectedVersion,
        String compatibilityVersion,
        String details,
        List<String> supportedActions,
        List<String> unavailableActions
    ) {
        public IntegrationStatus toFull() {
            return new IntegrationStatus(
                integrationId, state, detectedModId, detectedVersion, compatibilityVersion, details,
                supportedActions != null ? supportedActions : List.of(),
                unavailableActions != null ? unavailableActions : List.of(),
                "N/A", "N/A", "NOT_SUBSCRIBED", "NONE",
                0L, 0L, 0L, 0L, 0L, null, 0L,
                details, false
            );
        }
    }

    public static IntegrationStatus quick(String integrationId, IntegrationState state, String modId, String details, List<String> supported, List<String> unavailable) {
        return new IntegrationStatus(
            integrationId, state, modId != null ? modId : "none", "N/A", "1.0+", details != null ? details : "",
            supported != null ? supported : List.of(), unavailable != null ? unavailable : List.of(),
            "N/A", "N/A", state.isHealthy() ? "SUBSCRIBED" : "NOT_SUBSCRIBED", "NONE",
            0L, 0L, 0L, 0L, 0L, null, 0L, details, false
        );
    }

    public IntegrationStatus withSubscriptionResult(SubscriptionResult subResult) {
        return new IntegrationStatus(
            integrationId,
            subResult.success() ? IntegrationState.SUBSCRIPTION_SUCCEEDED : IntegrationState.ERROR,
            detectedModId, detectedVersion, compatibilityVersion,
            subResult.technicalMessage(),
            supportedActions, unavailableActions,
            subResult.eventClassName(), subResult.eventBusName(),
            subResult.listenerRegistered() ? "SUBSCRIBED" : "FAILED",
            subResult.adapterStrategy(),
            eventsReceived, eventsAccepted, eventsRejected,
            lastEventTimestamp, lastSuccessTimestamp,
            subResult.exception() != null ? subResult.exception().getMessage() : null,
            subResult.hasException() ? System.currentTimeMillis() : 0L,
            subResult.hasException() ? subResult.technicalMessage() : null,
            true
        );
    }

    public IntegrationStatus withState(IntegrationState newState) {
        return new IntegrationStatus(
            integrationId, newState, detectedModId, detectedVersion, compatibilityVersion, details,
            supportedActions, unavailableActions,
            eventClassName, eventBusName, subscriptionStatus, adapterStrategy,
            eventsReceived, eventsAccepted, eventsRejected,
            lastEventTimestamp, lastSuccessTimestamp,
            lastError, lastErrorTimestamp, unavailabilityReason, initialized
        );
    }

    public IntegrationStatus withEventReceived(boolean accepted) {
        return new IntegrationStatus(
            integrationId, IntegrationState.ACTIVE, detectedModId, detectedVersion, compatibilityVersion, details,
            supportedActions, unavailableActions,
            eventClassName, eventBusName, subscriptionStatus, adapterStrategy,
            eventsReceived + 1, accepted ? eventsAccepted + 1 : eventsAccepted,
            accepted ? eventsRejected : eventsRejected + 1,
            System.currentTimeMillis(), accepted ? System.currentTimeMillis() : lastSuccessTimestamp,
            lastError, accepted ? 0L : System.currentTimeMillis(), unavailabilityReason, initialized
        );
    }

    public IntegrationStatus withHandlerError(String error) {
        return new IntegrationStatus(
            integrationId, IntegrationState.DEGRADED, detectedModId, detectedVersion, compatibilityVersion, details,
            supportedActions, unavailableActions,
            eventClassName, eventBusName, subscriptionStatus, adapterStrategy,
            eventsReceived, eventsAccepted, eventsRejected + 1,
            System.currentTimeMillis(), lastSuccessTimestamp,
            error, System.currentTimeMillis(), "Handler error: " + error, initialized
        );
    }

    public boolean isOperational() {
        return state != null && state.isOperational();
    }

    public boolean isHealthy() {
        return state != null && state.isHealthy();
    }

    public boolean isErrorOrWorse() {
        return state != null && state.isErrorOrWorse();
    }
}
