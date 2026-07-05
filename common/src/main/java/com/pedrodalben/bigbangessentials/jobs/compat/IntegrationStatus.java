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
    List<String> unavailableActions
) {
    public boolean isOperational() {
        return state == IntegrationState.ACTIVE || state == IntegrationState.DEGRADED;
    }
}
