package com.pedrodalben.bigbangessentials.jobs.compat;

public interface OptionalJobsIntegration {
    String integrationId();

    IntegrationStatus initialize();

    void registerListeners();

    void shutdown();

    IntegrationStatus getStatus();
}
