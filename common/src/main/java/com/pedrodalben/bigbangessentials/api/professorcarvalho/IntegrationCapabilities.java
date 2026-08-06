package com.pedrodalben.bigbangessentials.api.professorcarvalho;

/** Capabilities are explicit because deployments may disable optional modules. */
public record IntegrationCapabilities(
        boolean economy,
        boolean gems,
        boolean rank,
        boolean jobs,
        boolean playtime) {
}
