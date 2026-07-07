package com.pedrodalben.bigbangessentials.jobs.catalog;

public enum JobAvailability {
    AVAILABLE,
    DISABLED_BY_CONFIG,
    CONFIGURATION_REQUIRED,
    INTEGRATION_MISSING,
    BRIDGE_DEGRADED,
    BRIDGE_ERROR,
    REQUIREMENTS_NOT_MET;

    public boolean isOperational() {
        return this == AVAILABLE || this == BRIDGE_DEGRADED;
    }

    public boolean isVisible() {
        return this != DISABLED_BY_CONFIG;
    }
}
