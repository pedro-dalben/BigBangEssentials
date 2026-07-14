package com.pedrodalben.bigbangessentials.jobs.health;

public record IntegrationHealthResult(
    String integrationId,
    IntegrationHealthStatus status,
    boolean required,
    String message,
    long lastCheckedAt
) {
    public boolean isAvailable() {
        return status == IntegrationHealthStatus.AVAILABLE;
    }
}
